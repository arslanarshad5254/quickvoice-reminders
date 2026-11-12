const { z } = require("zod");
const { config } = require("./config.js");
const { pool } = require("./db.js");
const { reminderParserPrompt } = require("./prompt.js");

const nullableDate = z.string().regex(/^\d{4}-\d{2}-\d{2}$/).nullable().default(null);
const nullableTime = z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/).nullable().default(null);
const categories = ["Personal", "Work", "Errands", "Health", "Finance", "General"];
const recurrenceTypes = ["daily", "weekly", "monthly", "yearly", "custom"];
const weekdays = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];

const parsedReminderSchema = z.object({
  task: z.string().trim().min(1).max(200),
  due_date: nullableDate,
  due_time: nullableTime,
  end_date: nullableDate,
  end_time: nullableTime,
  is_all_day: z.boolean().default(false),
  category: z.enum(categories).default("General"),
  location: z.string().trim().max(120).nullable().default(null),
  is_nag: z.boolean().default(false),
  is_recurring: z.boolean().default(false),
  recurrence: z.enum(recurrenceTypes).nullable().default(null),
  recurrence_days: z.array(z.enum(weekdays)).max(7).nullable().default(null),
  recurrence_interval_days: z.number().int().min(1).max(3650).nullable().default(null)
});

const parseRequestSchema = z.object({
  input: z.string().trim().min(1).max(2_000),
  currentDateTime: z.iso.datetime({ local: true }).or(z.iso.datetime()),
  timeZone: z.string().trim().min(1).max(100),
  clientParsed: parsedReminderSchema.optional()
});

function extractJson(text) {
  const cleaned = text.trim().replace(/^```json\s*/i, "").replace(/^```\s*/, "").replace(/```$/, "").trim();
  return JSON.parse(cleaned);
}

function normalizeParsedReminder(result, clientParsed = null) {
  const clean = { ...result };
  clean.task = clean.task.trim().replace(/\s+/g, " ").slice(0, 200);
  clean.location = clean.location?.trim().replace(/\s+/g, " ").slice(0, 120) || null;
  if (clean.is_all_day) {
    clean.due_time = null;
    clean.end_time = null;
  } else if (clean.due_date && !clean.due_time) {
    clean.due_time = "09:00";
  }
  if (!clean.due_date) clean.due_time = null;
  if (clean.end_time && !clean.end_date) clean.end_date = clean.due_date;
  if (clean.due_date && clean.due_time && clean.end_date && clean.end_time) {
    const start = `${clean.due_date}T${clean.due_time}`;
    const end = `${clean.end_date}T${clean.end_time}`;
    if (end < start) {
      clean.end_date = null;
      clean.end_time = null;
    }
  }
  if (!clean.is_recurring) {
    clean.recurrence = null;
    clean.recurrence_days = null;
    clean.recurrence_interval_days = null;
  } else if (!clean.recurrence && clientParsed?.is_recurring) {
    clean.recurrence = clientParsed.recurrence;
    clean.recurrence_days = clientParsed.recurrence_days ?? null;
    clean.recurrence_interval_days = clientParsed.recurrence_interval_days ?? null;
  }
  if (!clean.recurrence) {
    clean.is_recurring = false;
    clean.recurrence_days = null;
    clean.recurrence_interval_days = null;
  }
  return parsedReminderSchema.parse(clean);
}

async function parseReminder(payload) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 20_000);
  try {
    const response = await fetch(config.AI_API_URL, {
      method: "POST",
      headers: {
        authorization: `Bearer ${config.AI_API_KEY}`,
        "content-type": "application/json"
      },
      body: JSON.stringify({
        model: config.AI_MODEL,
        messages: [
          { role: "system", content: reminderParserPrompt(payload.currentDateTime, payload.timeZone) },
          {
            role: "user",
            content: JSON.stringify({
              transcript: payload.input,
              local_candidate: payload.clientParsed ?? null
            })
          }
        ],
        response_format: { type: "json_object" },
        temperature: 0.1
      }),
      signal: controller.signal
    });
    if (!response.ok) throw Object.assign(new Error("AI provider rejected the request"), { code: `AI_${response.status}` });
    const body = await response.json();
    const content = body?.choices?.[0]?.message?.content;
    if (!content) throw Object.assign(new Error("AI provider returned no content"), { code: "AI_EMPTY" });
    const promptTokens = Number(body?.usage?.prompt_tokens ?? 0);
    const completionTokens = Number(body?.usage?.completion_tokens ?? 0);
    const estimatedCostUsd =
      (promptTokens * config.AI_INPUT_COST_PER_MILLION +
       completionTokens * config.AI_OUTPUT_COST_PER_MILLION) / 1_000_000;
    return {
      result: normalizeParsedReminder(extractJson(content), payload.clientParsed),
      usage: {
        promptTokens,
        completionTokens,
        totalTokens: Number(body?.usage?.total_tokens ?? promptTokens + completionTokens),
        estimatedCostUsd
      }
    };
  } finally {
    clearTimeout(timeout);
  }
}

async function recordAiRequest(clientId, inputLength, startedAt, details = {}) {
  const {
    errorCode = null,
    cacheStrategy = "ai",
    aiApiCalled = true,
    usage = {},
    savedTokens = 0,
    savedCostUsd = 0
  } = details;
  try {
    await pool.query(
      `INSERT INTO ai_requests
         (client_id, input_length, succeeded, latency_ms, error_code, cache_hit,
          cache_strategy, ai_api_called, prompt_tokens, completion_tokens, total_tokens,
          estimated_cost_usd, saved_tokens, estimated_savings_usd)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14)`,
      [
        clientId, inputLength, errorCode === null, Date.now() - startedAt, errorCode,
        cacheStrategy === "exact" || cacheStrategy === "knowledge", cacheStrategy, aiApiCalled,
        usage.promptTokens ?? null, usage.completionTokens ?? null, usage.totalTokens ?? null,
        usage.estimatedCostUsd ?? 0, savedTokens, savedCostUsd
      ]
    );
  } catch (error) {
    console.error("Unable to record AI request metadata", error);
  }
}

module.exports = {
  parseRequestSchema,
  parsedReminderSchema,
  normalizeParsedReminder,
  parseReminder,
  recordAiRequest
};
