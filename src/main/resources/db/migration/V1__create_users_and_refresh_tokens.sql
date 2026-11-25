CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    full_name     VARCHAR(120) NOT NULL,
    password_hash VARCHAR(72)  NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE  NOT NULL
);

CREATE TABLE refresh_tokens (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users (id),
    token_hash       VARCHAR(64)  NOT NULL UNIQUE,
    expires_at       TIMESTAMP WITH TIME ZONE  NOT NULL,
    revoked          BOOLEAN      NOT NULL DEFAULT FALSE,
    replaced_by_hash VARCHAR(64),
    created_at       TIMESTAMP WITH TIME ZONE  NOT NULL
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
