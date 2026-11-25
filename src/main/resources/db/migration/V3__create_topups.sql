CREATE TABLE topups (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT      NOT NULL REFERENCES users (id),
    amount_minor          BIGINT      NOT NULL,
    currency              VARCHAR(3)     NOT NULL,
    status                VARCHAR(20) NOT NULL,
    merchant_order_id     VARCHAR(80) NOT NULL UNIQUE,
    paymob_order_id       BIGINT,
    -- Unique: one Paymob transaction can credit a wallet exactly once.
    paymob_transaction_id BIGINT UNIQUE,
    journal_id            VARCHAR(36),
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_topups_user ON topups (user_id);
CREATE INDEX idx_topups_status_created ON topups (status, created_at);
