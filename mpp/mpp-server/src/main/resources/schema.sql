-- MPP Server Schema
-- For production use with PostgreSQL/MySQL via R2DBC

CREATE TABLE IF NOT EXISTS mpp_transactions (
    id              VARCHAR(64) PRIMARY KEY,
    challenge_id    VARCHAR(128) NOT NULL,
    method          VARCHAR(32) NOT NULL,
    intent          VARCHAR(32) NOT NULL,
    source          VARCHAR(256) NOT NULL,
    amount          VARCHAR(64) NOT NULL,
    currency        VARCHAR(16) NOT NULL,
    recipient       VARCHAR(256),
    reference       VARCHAR(256),
    status          VARCHAR(16) NOT NULL DEFAULT 'pending',
    resource_path   VARCHAR(512),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    settled_at      TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mpp_accounts (
    source          VARCHAR(256) PRIMARY KEY,
    balance         DECIMAL(20, 4) NOT NULL DEFAULT 0,
    currency        VARCHAR(16) NOT NULL DEFAULT 'usd',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tx_challenge ON mpp_transactions(challenge_id);
CREATE INDEX IF NOT EXISTS idx_tx_source ON mpp_transactions(source);
CREATE INDEX IF NOT EXISTS idx_tx_created ON mpp_transactions(created_at);
