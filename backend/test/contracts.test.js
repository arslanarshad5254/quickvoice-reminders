const test = require("node:test");
const assert = require("node:assert/strict");

process.env.NODE_ENV = "test";
process.env.DATABASE_URL = "postgresql://test:test@localhost:5432/test";
process.env.AI_API_KEY = "test-key";

test("AI request contract accepts local ISO time and IANA zone", async () => {
  const { parseRequestSchema } = require("../src/ai.js");
  const result = parseRequestSchema.safeParse({
    input: "Call the dentist tomorrow at 3pm",
    currentDateTime: "2026-10-24T15:30:00",
    timeZone: "Asia/Karachi"
  });
  assert.equal(result.success, true);
});

test("AI request contract rejects oversized transcripts", async () => {
  const { parseRequestSchema } = require("../src/ai.js");
  const result = parseRequestSchema.safeParse({
    input: "x".repeat(2001),
    currentDateTime: "2026-10-24T15:30:00",
    timeZone: "Asia/Karachi"
  });
  assert.equal(result.success, false);
});

test("AI result normalization enforces consistent scheduling fields", () => {
  const { normalizeParsedReminder } = require("../src/ai.js");
  const normalized = normalizeParsedReminder({
    task: "  Call   Mom  ",
    due_date: "2026-10-25",
    due_time: null,
    end_date: null,
    end_time: null,
    is_all_day: false,
    category: "Personal",
    location: null,
    is_nag: false,
    is_recurring: false,
    recurrence: "weekly",
    recurrence_days: ["MONDAY"],
    recurrence_interval_days: null
  });

  assert.equal(normalized.task, "Call Mom");
  assert.equal(normalized.due_time, "09:00");
  assert.equal(normalized.recurrence, null);
  assert.equal(normalized.recurrence_days, null);
});

test("AI cache normalizes reusable prompts within the same local day", () => {
  const { createCacheKey } = require("../src/cache.js");
  const first = createCacheKey({
    input: "  Call   Mom tomorrow at 3 PM ",
    currentDateTime: "2026-10-24T09:15:00",
    timeZone: "Asia/Karachi"
  });
  const repeated = createCacheKey({
    input: "call mom TOMORROW at 3 pm",
    currentDateTime: "2026-10-24T11:45:00",
    timeZone: "Asia/Karachi"
  });
  const nextDay = createCacheKey({
    input: "call mom tomorrow at 3 pm",
    currentDateTime: "2026-10-25T09:15:00",
    timeZone: "Asia/Karachi"
  });

  assert.equal(first, repeated);
  assert.notEqual(first, nextDay);
});

test("AI cache bypasses requests whose answer changes with the current minute", () => {
  const { createCacheKey } = require("../src/cache.js");
  const context = {
    currentDateTime: "2026-10-24T09:15:00",
    timeZone: "Asia/Karachi"
  };

  assert.equal(createCacheKey({ ...context, input: "Remind me in 10 minutes" }), null);
  assert.equal(createCacheKey({ ...context, input: "30 minute baad motor band karvana" }), null);
  assert.equal(createCacheKey({ ...context, input: "30 minute bad motor band karvana" }), null);
  assert.equal(createCacheKey({ ...context, input: "Call the dentist at 3 PM" }), null);
});

test("semantic signatures match equivalent reminder wording without personal values", () => {
  const { semanticSignature, similarity } = require("../src/knowledge.js");
  const first = semanticSignature({
    input: "Schedule a reminder for 10 AM tomorrow",
    clientParsed: { due_date: "2026-10-25", due_time: "10:00" }
  });
  const second = semanticSignature({
    input: "Set a reminder tomorrow at 9 AM",
    clientParsed: { due_date: "2026-10-25", due_time: "09:00" }
  });

  assert.equal(similarity(first, second), 1);
  assert.equal(first.includes("10"), false);
  assert.equal(second.includes("9"), false);
});

test("semantic signatures keep different reminder actions separated", () => {
  const { semanticSignature, similarity } = require("../src/knowledge.js");
  const call = semanticSignature({
    input: "Call Mom tomorrow at 3 PM",
    clientParsed: { due_date: "2026-10-25", due_time: "15:00" }
  });
  const buy = semanticSignature({
    input: "Buy milk tomorrow at 3 PM",
    clientParsed: { due_date: "2026-10-25", due_time: "15:00" }
  });

  assert.ok(similarity(call, buy) < 0.85);
});

