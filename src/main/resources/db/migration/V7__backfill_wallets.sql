-- Wallets are now created at registration; give every pre-existing user one.
INSERT INTO wallets (user_id, balance_minor, currency, created_at)
SELECT u.id, 0, 'EGP', now() FROM users u
WHERE NOT EXISTS (SELECT 1 FROM wallets w WHERE w.user_id = u.id);
