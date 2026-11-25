-- Idempotency for refund/void callbacks: each reversal transaction applies once.
ALTER TABLE topups ADD COLUMN reversal_transaction_id BIGINT UNIQUE;
