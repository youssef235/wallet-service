CREATE TABLE wallets (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL UNIQUE REFERENCES users (id),
    balance_minor BIGINT      NOT NULL DEFAULT 0,
    currency      VARCHAR(3)     NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE ledger_entries (
    id           BIGSERIAL PRIMARY KEY,
    journal_id   VARCHAR(36)  NOT NULL,
    account      VARCHAR(40)  NOT NULL,
    amount_minor BIGINT       NOT NULL,
    description  TEXT         NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE  NOT NULL
);

CREATE INDEX idx_ledger_journal ON ledger_entries (journal_id);
CREATE INDEX idx_ledger_account ON ledger_entries (account);

CREATE TABLE transfers (
    id              BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(80) NOT NULL UNIQUE,
    from_wallet_id  BIGINT      NOT NULL REFERENCES wallets (id),
    to_wallet_id    BIGINT      NOT NULL REFERENCES wallets (id),
    amount_minor    BIGINT      NOT NULL,
    journal_id      VARCHAR(36) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL
);
