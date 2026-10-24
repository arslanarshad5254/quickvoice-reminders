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

