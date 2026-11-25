-- Money invariants enforced by the database itself, not only by Java code.
-- No balance check on wallets: a Paymob reversal (refund/chargeback) is
-- allowed to take a balance negative - the customer owes the money back.
ALTER TABLE transfers ADD CONSTRAINT chk_transfer_amount CHECK (amount_minor > 0);
ALTER TABLE transfers ADD CONSTRAINT chk_transfer_distinct CHECK (from_wallet_id <> to_wallet_id);
ALTER TABLE topups ADD CONSTRAINT chk_topup_amount CHECK (amount_minor > 0);
ALTER TABLE ledger_entries ADD CONSTRAINT chk_ledger_nonzero CHECK (amount_minor <> 0);
