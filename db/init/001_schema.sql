-- Initial schema (PostgreSQL best practices: identity PKs, timestamptz, snake_case)
CREATE TABLE IF NOT EXISTS app_metadata (
    metadata_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    key TEXT NOT NULL,
    value TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT app_metadata_key_unique UNIQUE (key),
    CONSTRAINT app_metadata_key_nonempty CHECK (length(trim(key)) > 0)
);

CREATE INDEX IF NOT EXISTS app_metadata_updated_at_idx ON app_metadata (updated_at);

INSERT INTO app_metadata (key, value)
VALUES ('schema_version', '1')
ON CONFLICT (key) DO NOTHING;
