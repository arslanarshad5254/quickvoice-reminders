const { createHash } = require("node:crypto");
const { pool } = require("./db.js");

const CACHE_TTL_DAYS = 30;
const HISTORY_RETENTION_DAYS = 90;

function normalizePrompt(prompt) {
  return prompt.trim().toLocaleLowerCase("en-US").replace(/\s+/g, " ");
}

function isUnsafeToCache(prompt) {
  const text = prompt.toLocaleLowerCase("en-US");
  const rollingOffset = /\b(?:(?:in|after)\s+(?:an?|one|two|three|four|five|six|seven|eight|nine|ten|a couple(?: of)?|a few|\d+)\s*(?:seconds?|minutes?|mins?|hours?|hrs?|days?|weeks?|months?|years?)|(?:an?|one|two|three|four|five|six|seven|eight|nine|ten|a couple(?: of)?|a few|\d+)\s*(?:seconds?|minutes?|mins?|hours?|hrs?|days?|weeks?|months?|years?)\s+(?:later|baad|bad))\b/;
  const vagueRelativeTime = /\b(?:now|soon|shortly|in a bit|in a moment|later today|later on)\b/;
  if (rollingOffset.test(text) || vagueRelativeTime.test(text)) return true;

  // A clock time without a date changes from "today" to "tomorrow" after it
  // passes, so it must not reuse a response even on the same calendar day.
  const clockTime = /\b(?:\d{1,2}(?::\d{2})?\s*(?:a\.?m\.?|p\.?m\.?)|[01]?\d:[0-5]\d|noon|midnight|morning|afternoon|evening|tonight|bedtime)\b/;
  const dateAnchor = /\b(?:today|tomorrow|day after tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday|week|weekend|month|year|january|february|march|april|may|june|july|august|september|october|november|december|daily|weekly|monthly|yearly|annually|every)\b/;
  return clockTime.test(text) && !dateAnchor.test(text);
}

function createCacheKey(payload) {
  if (isUnsafeToCache(payload.input)) return null;
  const localDate = payload.currentDateTime.slice(0, 10);
  const material = `${normalizePrompt(payload.input)}\n${localDate}\n${payload.timeZone}`;
  return createHash("sha256").update(material).digest("hex");
}

async function findCachedResponse(clientId, cacheKey) {
  if (!cacheKey) return null;
  try {
    const result = await pool.query(
      `UPDATE ai_prompt_responses
          SET hit_count = hit_count + 1, last_used_at = NOW()
        WHERE client_id = $1 AND cache_key = $2 AND expires_at > NOW()
        RETURNING response, prompt_tokens, completion_tokens, estimated_cost_usd`,
      [clientId, cacheKey]
    );
    const row = result.rows[0];
    if (!row) return null;
    return {
      result: row.response,
      savedTokens: (row.prompt_tokens ?? 0) + (row.completion_tokens ?? 0),
      savedCostUsd: Number(row.estimated_cost_usd ?? 0)
    };
  } catch (error) {
    console.error("Unable to read AI response cache", error);
    return null;
  }
}

async function storePromptResponse(clientId, payload, response, cacheKey, usage) {
  const context = JSON.stringify({
    currentDateTime: payload.currentDateTime,
    timeZone: payload.timeZone
  });
  const serializedResponse = JSON.stringify(response);
  try {
    if (cacheKey) {
      await pool.query(
        `INSERT INTO ai_prompt_responses
           (client_id, cache_key, prompt, request_context, response, cacheable, expires_at,
            intent, structured_parameters, prompt_tokens, completion_tokens, estimated_cost_usd)
         VALUES ($1, $2, $3, $4::jsonb, $5::jsonb, TRUE, NOW() + ($6::int * INTERVAL '1 day'),
                 'schedule_reminder', $5::jsonb, $7, $8, $9)
         ON CONFLICT (client_id, cache_key) WHERE cache_key IS NOT NULL
         DO UPDATE SET
           prompt = EXCLUDED.prompt,
           request_context = EXCLUDED.request_context,
           response = EXCLUDED.response,
           structured_parameters = EXCLUDED.structured_parameters,
           prompt_tokens = EXCLUDED.prompt_tokens,
           completion_tokens = EXCLUDED.completion_tokens,
           estimated_cost_usd = EXCLUDED.estimated_cost_usd,
           updated_at = NOW(),
           last_used_at = NOW(),
           expires_at = EXCLUDED.expires_at`,
        [
          clientId, cacheKey, payload.input, context, serializedResponse, CACHE_TTL_DAYS,
          usage.promptTokens, usage.completionTokens, usage.estimatedCostUsd
        ]
      );
    } else {
      await pool.query(
        `INSERT INTO ai_prompt_responses
           (client_id, cache_key, prompt, request_context, response, cacheable, expires_at,
            intent, structured_parameters, prompt_tokens, completion_tokens, estimated_cost_usd)
         VALUES ($1, NULL, $2, $3::jsonb, $4::jsonb, FALSE, NOW() + ($5::int * INTERVAL '1 day'),
                 'schedule_reminder', $4::jsonb, $6, $7, $8)`,
        [
          clientId, payload.input, context, serializedResponse, HISTORY_RETENTION_DAYS,
          usage.promptTokens, usage.completionTokens, usage.estimatedCostUsd
        ]
      );
    }

    // Occasional cleanup avoids requiring a cron job on shared hosting without
    // adding a retention query to every API request.
    if (Math.random() < 0.01) {
      await pool.query(
        `DELETE FROM ai_prompt_responses
          WHERE created_at < NOW() - ($1::int * INTERVAL '1 day')`,
        [HISTORY_RETENTION_DAYS]
      );
    }
  } catch (error) {
    console.error("Unable to store AI prompt and response", error);
  }
}

module.exports = {
  createCacheKey,
  findCachedResponse,
  storePromptResponse
};
