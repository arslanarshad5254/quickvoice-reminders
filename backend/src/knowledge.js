const { pool } = require("./db.js");
const { config } = require("./config.js");

const INTENT = "schedule_reminder";
const synonyms = new Map([
  ["schedule", "remind"], ["scheduled", "remind"], ["set", "remind"], ["create", "remind"],
  ["reminder", "remind"], ["reminders", "remind"], ["remember", "remind"],
  ["phone", "call"], ["ring", "call"], ["contact", "call"],
  ["purchase", "buy"], ["get", "buy"], ["shop", "buy"],
  ["message", "send"], ["text", "send"], ["mail", "email"],
  ["appointment", "meet"], ["meeting", "meet"],
  ["collect", "pickup"], ["pick", "pickup"],
  ["medication", "medicine"], ["pill", "medicine"],
  ["invoice", "bill"], ["payment", "pay"]
]);
const reusableConcepts = new Set([
  "remind", "call", "buy", "pay", "meet", "email", "send", "visit", "take", "pickup",
  "work", "doctor", "dentist", "medicine", "bill", "grocery", "birthday", "exercise",
  "water", "clean", "travel", "renew", "cancel", "book"
]);

function semanticSignature(payload) {
  const text = payload.input.toLocaleLowerCase("en-US");
  const words = text.match(/[a-z]+/g) ?? [];
  const terms = new Set(["reminder"]);
  for (const word of words) {
    const concept = synonyms.get(word) ?? word;
    if (reusableConcepts.has(concept)) terms.add(concept);
  }

  const parsed = payload.clientParsed;
  if (parsed?.due_date) terms.add("has_date");
  if (parsed?.due_time) terms.add("has_time");
  if (parsed?.location) terms.add("has_location");
  if (parsed?.is_recurring) terms.add("recurring");
  if (parsed?.is_nag) terms.add("urgent");
  return [...terms].sort();
}

function patternFor(terms) {
  return terms.join("|");
}

function similarity(left, right) {
  const a = new Set(left);
  const b = new Set(right);
  const intersection = [...a].filter((term) => b.has(term)).length;
  const union = new Set([...a, ...b]).size;
  return union === 0 ? 0 : intersection / union;
}

function requiredParameters(result) {
  const fields = [
    "due_date", "due_time", "end_date", "end_time", "location", "recurrence",
    "recurrence_days", "recurrence_interval_days"
  ];
  return fields.filter((field) => result[field] !== null && result[field] !== undefined);
}

function hasRequiredParameters(parsed, required) {
  return required.every((field) => {
    const value = parsed[field];
    return value !== null && value !== undefined && (!Array.isArray(value) || value.length > 0);
  });
}

function hasValidSchedule(parsed, currentDateTime) {
  if (!parsed.due_date) return true;
  const currentDate = currentDateTime.slice(0, 10);
  if (parsed.due_date < currentDate) return false;
  if (parsed.due_date > currentDate || !parsed.due_time) return true;
  return parsed.due_time > currentDateTime.slice(11, 16);
}

function fillTemplate(template, parsed) {
  const result = {};
  for (const [field, value] of Object.entries(template)) {
    if (typeof value === "string" && /^\{[a-z_]+\}$/.test(value)) {
      result[field] = parsed[value.slice(1, -1)] ?? null;
    } else {
      result[field] = value;
    }
  }
  return result;
}

function reusableTemplate() {
  return {
    task: "{task}",
    due_date: "{due_date}",
    due_time: "{due_time}",
    end_date: "{end_date}",
    end_time: "{end_time}",
    is_all_day: "{is_all_day}",
    category: "{category}",
    location: "{location}",
    is_nag: "{is_nag}",
    is_recurring: "{is_recurring}",
    recurrence: "{recurrence}",
    recurrence_days: "{recurrence_days}",
    recurrence_interval_days: "{recurrence_interval_days}"
  };
}

async function findKnowledge(payload) {
  if (!payload.clientParsed) return null;
  const terms = semanticSignature(payload);
  try {
    const candidates = await pool.query(
      `SELECT id, semantic_terms, response_template, parameter_schema,
              source_prompt_tokens, source_completion_tokens, source_cost_usd
         FROM ai_knowledge
        WHERE intent = $1 AND active = TRUE
        ORDER BY last_used_at DESC NULLS LAST, updated_at DESC
        LIMIT 200`,
      [INTENT]
    );

    let best = null;
    for (const candidate of candidates.rows) {
      const score = similarity(terms, candidate.semantic_terms);
      if (!best || score > best.score) best = { candidate, score };
    }
    if (!best || best.score < config.AI_SEMANTIC_THRESHOLD) return null;

    const required = best.candidate.parameter_schema?.required ?? [];
    if (!hasRequiredParameters(payload.clientParsed, required)) return null;
    if (!hasValidSchedule(payload.clientParsed, payload.currentDateTime)) return null;

    await pool.query(
      `UPDATE ai_knowledge
          SET use_count = use_count + 1, last_used_at = NOW()
        WHERE id = $1`,
      [best.candidate.id]
    );
    return {
      result: fillTemplate(best.candidate.response_template, payload.clientParsed),
      score: best.score,
      knowledgeId: best.candidate.id,
      savedTokens: (best.candidate.source_prompt_tokens ?? 0) + (best.candidate.source_completion_tokens ?? 0),
      savedCostUsd: Number(best.candidate.source_cost_usd ?? 0)
    };
  } catch (error) {
    console.error("Unable to search reusable AI knowledge", error);
    return null;
  }
}

async function learnKnowledge(payload, result, usage) {
  if (!payload.clientParsed) return;
  const terms = semanticSignature(payload);
  const pattern = patternFor(terms);
  const parameterSchema = { required: requiredParameters(result) };
  try {
    await pool.query(
      `INSERT INTO ai_knowledge
         (intent, prompt_pattern, semantic_terms, response_template, parameter_schema,
          source_prompt_tokens, source_completion_tokens, source_cost_usd)
       VALUES ($1, $2, $3, $4::jsonb, $5::jsonb, $6, $7, $8)
       ON CONFLICT (intent, prompt_pattern)
       DO UPDATE SET
         semantic_terms = EXCLUDED.semantic_terms,
         response_template = EXCLUDED.response_template,
         parameter_schema = EXCLUDED.parameter_schema,
         source_prompt_tokens = EXCLUDED.source_prompt_tokens,
         source_completion_tokens = EXCLUDED.source_completion_tokens,
         source_cost_usd = EXCLUDED.source_cost_usd,
         version = ai_knowledge.version + 1,
         active = TRUE,
         updated_at = NOW()`,
      [
        INTENT, pattern, terms, JSON.stringify(reusableTemplate()), JSON.stringify(parameterSchema),
        usage.promptTokens, usage.completionTokens, usage.estimatedCostUsd
      ]
    );
  } catch (error) {
    console.error("Unable to learn reusable AI knowledge", error);
  }
}

module.exports = {
  semanticSignature,
  similarity,
  findKnowledge,
  learnKnowledge
};
