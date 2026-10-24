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
  assert.equal(createCacheKey({ ...context, input: "Call the dentist at 3 PM" }), null);
});

