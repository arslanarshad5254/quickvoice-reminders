const { z } = require("zod");
const { config } = require("./config.js");
const { pool } = require("./db.js");
const { reminderParserPrompt } = require("./prompt.js");

const parseRequestSchema = z.object({
  input: z.string().trim().min(1).max(2_000),
  currentDateTime: z.iso.datetime({ local: true }).or(z.iso.datetime()),
  timeZone: z.string().trim().min(1).max(100)
});

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

function extractJson(text) {
  const cleaned = text.trim().replace(/^```json\s*/i, "").replace(/^```\s*/, "").replace(/```$/, "").trim();
  return JSON.parse(cleaned);
}

async function parseReminder(payload) {
  const startedAt = Date.now();
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
          { role: "user", content: payload.input }
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
    return parsedReminderSchema.parse(extractJson(content));
  } finally {
    clearTimeout(timeout);
  }
}

async function recordAiRequest(clientId, inputLength, startedAt, errorCode = null, cacheHit = false) {
  try {
    await pool.query(
      `INSERT INTO ai_requests(client_id, input_length, succeeded, latency_ms, error_code, cache_hit)
       VALUES ($1, $2, $3, $4, $5, $6)`,
      [clientId, inputLength, errorCode === null, Date.now() - startedAt, errorCode, cacheHit]
    );
  } catch (error) {
    console.error("Unable to record AI request metadata", error);
  }
}

module.exports = { parseRequestSchema, parsedReminderSchema, parseReminder, recordAiRequest };
