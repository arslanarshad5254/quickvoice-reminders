const express = require("express");
const helmet = require("helmet");
const { rateLimit } = require("express-rate-limit");
const { z } = require("zod");
const { config } = require("./config.js");
const { pool } = require("./db.js");
const { parseReminder, parseRequestSchema, recordAiRequest } = require("./ai.js");

const clientIdSchema = z.uuid();

async function identifyClient(req, res, next) {
  const parsed = clientIdSchema.safeParse(req.get("x-client-id"));
  if (!parsed.success) return res.status(400).json({ error: "INVALID_CLIENT_ID" });
  req.clientId = parsed.data;
  try {
    await pool.query(
      `INSERT INTO client_installations(client_id) VALUES ($1)
       ON CONFLICT (client_id) DO UPDATE SET last_seen_at = NOW()`,
      [req.clientId]
    );
    next();
  } catch (error) {
    next(error);
  }
}

function limiter(max) {
  return rateLimit({
    windowMs: 60_000,
    limit: max,
    standardHeaders: "draft-8",
    legacyHeaders: false
  });
}

const app = express();
app.set("trust proxy", config.TRUST_PROXY);
app.disable("x-powered-by");
app.use(helmet());
app.use(express.json({ limit: "32kb", strict: true }));
app.use("/v1", limiter(60));

app.get("/health/live", (_req, res) => res.json({ status: "ok" }));
app.get("/health/ready", async (_req, res, next) => {
  try {
    await pool.query("SELECT 1");
    res.json({ status: "ok" });
  } catch (error) {
    next(error);
  }
});

app.post("/v1/ai/parse", identifyClient, limiter(config.AI_RATE_LIMIT_PER_MINUTE), async (req, res, next) => {
  const input = parseRequestSchema.safeParse(req.body);
  if (!input.success) return res.status(400).json({ error: "INVALID_REQUEST" });
  const startedAt = Date.now();
  try {
    const result = await parseReminder(input.data);
    await recordAiRequest(req.clientId, input.data.input.length, startedAt);
    res.json(result);
  } catch (error) {
    await recordAiRequest(req.clientId, input.data.input.length, startedAt, error.code ?? "AI_FAILURE");
    next(error);
  }
});

app.use((_req, res) => res.status(404).json({ error: "NOT_FOUND" }));
app.use((error, _req, res, _next) => {
  console.error(error);
  const timeout = error?.name === "AbortError";
  res.status(timeout ? 504 : 502).json({ error: timeout ? "UPSTREAM_TIMEOUT" : "UPSTREAM_FAILURE" });
});

module.exports = { app };
