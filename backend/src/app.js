const express = require("express");
const helmet = require("helmet");
const { rateLimit } = require("express-rate-limit");
const { z } = require("zod");
const { config } = require("./config.js");
const { pool } = require("./db.js");
const { parseReminder, parseRequestSchema, parsedReminderSchema, recordAiRequest } = require("./ai.js");
const { createCacheKey, findCachedResponse, storePromptResponse } = require("./cache.js");

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

// Some cPanel/Passenger configurations pass the application URL prefix to
// Express, while others strip it. Mount the same router both ways so the API
// works consistently behind the configured /backend application URL.
const api = express.Router();
api.use("/v1", limiter(60));

api.get("/", (_req, res) => res.json({
  status: "ok",
  service: "QuickVoice AI API",
  health: "/health/ready"
}));
api.get("/health/live", (_req, res) => res.json({ status: "ok" }));
api.get("/health/ready", async (_req, res, next) => {
  try {
    await pool.query("SELECT 1");
    res.json({ status: "ok" });
  } catch (error) {
    next(error);
  }
});

api.post("/v1/ai/parse", identifyClient, limiter(config.AI_RATE_LIMIT_PER_MINUTE), async (req, res, next) => {
  const input = parseRequestSchema.safeParse(req.body);
  if (!input.success) return res.status(400).json({ error: "INVALID_REQUEST" });
  const startedAt = Date.now();
  try {
    const cacheKey = createCacheKey(input.data);
    const cached = await findCachedResponse(req.clientId, cacheKey);
    const validCached = parsedReminderSchema.safeParse(cached);
    if (validCached.success) {
      await recordAiRequest(req.clientId, input.data.input.length, startedAt, null, true);
      return res.set("X-AI-Cache", "HIT").json(validCached.data);
    }

    const result = await parseReminder(input.data);
    await storePromptResponse(req.clientId, input.data, result, cacheKey);
    await recordAiRequest(req.clientId, input.data.input.length, startedAt);
    res.set("X-AI-Cache", "MISS").json(result);
  } catch (error) {
    await recordAiRequest(req.clientId, input.data.input.length, startedAt, error.code ?? "AI_FAILURE");
    next(error);
  }
});

app.use(api);
app.use("/backend", api);
app.use((_req, res) => res.status(404).json({ error: "NOT_FOUND" }));
app.use((error, _req, res, _next) => {
  console.error(error);
  const timeout = error?.name === "AbortError";
  res.status(timeout ? 504 : 502).json({ error: timeout ? "UPSTREAM_TIMEOUT" : "UPSTREAM_FAILURE" });
});

module.exports = { app };
