ALTER TABLE ai_requests
    ADD COLUMN IF NOT EXISTS cache_hit BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS ai_prompt_responses (
    id BIGSERIAL PRIMARY KEY,
    client_id UUID NOT NULL REFERENCES client_installations(client_id) ON DELETE CASCADE,
    cache_key CHAR(64),
    prompt TEXT NOT NULL CHECK (char_length(prompt) BETWEEN 1 AND 2000),
    request_context JSONB NOT NULL,
    response JSONB NOT NULL,
    cacheable BOOLEAN NOT NULL,
    hit_count INTEGER NOT NULL DEFAULT 0 CHECK (hit_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '30 days')
);

CREATE UNIQUE INDEX IF NOT EXISTS ai_prompt_responses_client_cache_key_idx
    ON ai_prompt_responses (client_id, cache_key)
    WHERE cache_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS ai_prompt_responses_expiry_idx
    ON ai_prompt_responses (expires_at);

CREATE INDEX IF NOT EXISTS ai_prompt_responses_created_idx
    ON ai_prompt_responses (created_at);

CREATE INDEX IF NOT EXISTS ai_prompt_responses_client_created_idx
    ON ai_prompt_responses (client_id, created_at DESC);
