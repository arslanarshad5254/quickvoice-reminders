CREATE TABLE IF NOT EXISTS schema_migrations (
    name TEXT PRIMARY KEY,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS client_installations (
    client_id UUID PRIMARY KEY,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ai_requests (
    id BIGSERIAL PRIMARY KEY,
    client_id UUID REFERENCES client_installations(client_id) ON DELETE SET NULL,
    input_length INTEGER NOT NULL CHECK (input_length >= 0),
    succeeded BOOLEAN NOT NULL,
    latency_ms INTEGER NOT NULL CHECK (latency_ms >= 0),
    error_code TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ai_requests_client_created_idx
    ON ai_requests (client_id, created_at DESC);
