ALTER TABLE ai_requests
    ADD COLUMN IF NOT EXISTS cache_strategy TEXT NOT NULL DEFAULT 'ai',
    ADD COLUMN IF NOT EXISTS ai_api_called BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS prompt_tokens INTEGER,
    ADD COLUMN IF NOT EXISTS completion_tokens INTEGER,
    ADD COLUMN IF NOT EXISTS total_tokens INTEGER,
    ADD COLUMN IF NOT EXISTS estimated_cost_usd NUMERIC(14, 8) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS saved_tokens INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS estimated_savings_usd NUMERIC(14, 8) NOT NULL DEFAULT 0;

ALTER TABLE ai_prompt_responses
    ADD COLUMN IF NOT EXISTS intent TEXT NOT NULL DEFAULT 'schedule_reminder',
    ADD COLUMN IF NOT EXISTS prompt_pattern TEXT,
    ADD COLUMN IF NOT EXISTS structured_parameters JSONB,
    ADD COLUMN IF NOT EXISTS prompt_tokens INTEGER,
    ADD COLUMN IF NOT EXISTS completion_tokens INTEGER,
    ADD COLUMN IF NOT EXISTS estimated_cost_usd NUMERIC(14, 8) NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS ai_knowledge (
    id BIGSERIAL PRIMARY KEY,
    intent TEXT NOT NULL,
    prompt_pattern TEXT NOT NULL,
    semantic_terms TEXT[] NOT NULL,
    response_template JSONB NOT NULL,
    parameter_schema JSONB NOT NULL,
    source_prompt_tokens INTEGER,
    source_completion_tokens INTEGER,
    source_cost_usd NUMERIC(14, 8) NOT NULL DEFAULT 0,
    confidence NUMERIC(4, 3) NOT NULL DEFAULT 1.0 CHECK (confidence BETWEEN 0 AND 1),
    version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    use_count BIGINT NOT NULL DEFAULT 0 CHECK (use_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMPTZ,
    UNIQUE (intent, prompt_pattern)
);

CREATE INDEX IF NOT EXISTS ai_knowledge_active_intent_idx
    ON ai_knowledge (intent, active, last_used_at DESC);

CREATE TABLE IF NOT EXISTS ai_knowledge_feedback (
    id BIGSERIAL PRIMARY KEY,
    knowledge_id BIGINT NOT NULL REFERENCES ai_knowledge(id) ON DELETE CASCADE,
    client_id UUID REFERENCES client_installations(client_id) ON DELETE SET NULL,
    accepted BOOLEAN NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE OR REPLACE VIEW ai_usage_summary AS
SELECT
    date_trunc('day', created_at) AS day,
    COUNT(*) AS total_requests,
    COUNT(*) FILTER (WHERE cache_strategy = 'exact') AS exact_cache_hits,
    COUNT(*) FILTER (WHERE cache_strategy = 'knowledge') AS knowledge_hits,
    COUNT(*) FILTER (WHERE ai_api_called) AS ai_api_calls,
    COALESCE(SUM(total_tokens), 0) AS ai_tokens_used,
    COALESCE(SUM(saved_tokens), 0) AS tokens_saved,
    COALESCE(SUM(estimated_cost_usd), 0) AS estimated_cost_usd,
    COALESCE(SUM(estimated_savings_usd), 0) AS estimated_savings_usd,
    ROUND(AVG(latency_ms), 2) AS average_response_ms
FROM ai_requests
GROUP BY date_trunc('day', created_at);
