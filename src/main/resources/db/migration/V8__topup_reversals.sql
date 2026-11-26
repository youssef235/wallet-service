-- Partial refunds: a top-up can be reversed in several callbacks, each exactly
-- once, keyed by its own reversal transaction id. Replaces the single
-- reversal_transaction_id column on topups.
CREATE TABLE topup_reversals (
    id                      BIGSERIAL PRIMARY KEY,
    topup_id                BIGINT      NOT NULL REFERENCES topups (id),
    reversal_transaction_id BIGINT      NOT NULL UNIQUE,
    amount_minor            BIGINT      NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_topup_reversals_topup ON topup_reversals (topup_id);

INSERT INTO topup_reversals (topup_id, reversal_transaction_id, amount_minor, created_at)
SELECT id, reversal_transaction_id, amount_minor, now()
FROM topups
WHERE reversal_transaction_id IS NOT NULL;

ALTER TABLE topups DROP COLUMN reversal_transaction_id;
