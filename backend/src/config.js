require("dotenv").config({ quiet: true });
const { z } = require("zod");

const booleanValue = z.string().default("false").transform((value) => value === "true");

const schema = z.object({
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
  PORT: z.coerce.number().int().min(1).max(65535).default(3000),
  TRUST_PROXY: booleanValue,
  DATABASE_URL: z.string().min(1),
  DATABASE_SSL: booleanValue,
  AI_API_KEY: z.string().min(1),
  AI_API_URL: z.string().url().default("https://api.groq.com/openai/v1/chat/completions"),
  AI_MODEL: z.string().min(1).default("openai/gpt-oss-20b"),
  AI_RATE_LIMIT_PER_MINUTE: z.coerce.number().int().positive().default(20)
});

const config = schema.parse(process.env);

module.exports = { config };
