# Wallet Service

A digital wallet REST API built with Spring Boot and Clean Architecture: users top up their
wallet by card through **Paymob**, send money to each other, and every piaster is tracked in a
**double-entry ledger** that can be re-verified at any time.

## Why this project is interesting

Money software is where backend engineering stops being CRUD. This project is built around the
three problems every wallet has to solve:

1. **"Did the payment really happen?"** - Paymob notifies us by webhook, but anyone can POST to a
   public URL. Every callback is verified with **HMAC-SHA512** against the merchant secret before
   any state changes. A bad signature means nothing is written, ever.
2. **"Can a payment credit the wallet twice?"** - Retried webhooks and double-clicks are normal,
   not edge cases. Processing is **idempotent**: the Paymob transaction id has a unique database
   constraint, the top-up row is locked while being settled, and retries return the original
   result instead of crediting again.
3. **"Can money appear or disappear?"** - Balances are a cached projection of a double-entry
   ledger: every movement writes a balanced journal (entries sum to zero, enforced in the domain)
   in the same transaction as the balance update. Transfers lock both wallets
   `SELECT ... FOR UPDATE` in a fixed order, so concurrent opposite transfers cannot deadlock and
   money is conserved exactly. A scheduled **reconciliation job** re-derives every balance from
   the ledger and raises an alarm on any drift.

## Features

- **Auth:** stateless JWT access tokens (HS256) + refresh tokens with **rotation and theft
  detection** (reusing a rotated token revokes all sessions). Passwords hashed with BCrypt;
  refresh tokens stored as SHA-256 hashes only.
- **Top-ups:** Paymob Accept integration (auth token, order registration, payment key, hosted
  iframe). HMAC-verified webhook, idempotent exactly-once crediting, stale top-up expiry job.
- **P2P transfers:** by recipient email, with client-supplied **idempotency keys**, pessimistic
  row locking in fixed order, and domain-level insufficient-funds rules.
- **Double-entry ledger:** every top-up and transfer is a balanced journal; full transaction
  history per wallet with pagination.
- **Notifications:** welcome, top-up receipt and money-received emails (SMTP; Mailhog in dev).
- **Housekeeping:** scheduled ledger reconciliation and stale top-up expiry.
- **API docs:** OpenAPI 3 / Swagger UI.

## Tech Stack

- **Backend:** Java 21, Spring Boot 4, Spring Security (stateless JWT), Spring Data JPA
- **Database:** PostgreSQL 17, Flyway migrations
- **Payments:** Paymob Accept (Egypt), HMAC-SHA512 webhook verification
- **Docs:** OpenAPI 3 / Swagger UI (springdoc)
- **Tests:** JUnit 5, Mockito, Spring Boot Test (29 tests incl. a concurrent-transfer
  conservation test)
- **Infrastructure:** Docker, Docker Compose (app + Postgres + Mailhog), GitHub Actions CI

## Architecture

Clean Architecture organized by feature (`user`, `wallet`, `payment`, `notification`, `recon`,
`shared`, `config`). Each feature has the same four layers:

- `domain`: pure Java - entities, value objects, business rules, repository interfaces.
  No framework imports. The double-entry invariant and insufficient-funds rules live here.
- `application`: pure Java use cases (one class per operation), wired explicitly as beans in
  `UseCaseConfig`. Transactions are started through the `TransactionRunner` port.
- `infrastructure`: JPA entities/mappers/adapters, BCrypt, JWT, the Paymob HTTP client and the
  HMAC verifier.
- `presentation`: REST controllers, request/response DTOs, bean validation, centralized error
  handling.

### How a top-up flows

```
User                API                 Paymob                  DB
 |  POST /topups     |                    |                      |
 |------------------>| create PENDING     |                      |
 |                   |------------------->| auth, order, paykey  |
 |  payment URL      |<-------------------|                      |
 |<------------------| save paymobOrderId |--------------------->|
 |  (pays by card)   |                    |                      |
 |                   |<-- webhook ?hmac---|                      |
 |                   | verify HMAC-SHA512 |                      |
 |                   | lock top-up row    |--------------------->|
 |                   | credit wallet +    |  wallet row lock     |
 |                   | balanced journal   |  unique txn id       |
 |                   |------------------------------------------->|
 |                   | 200 OK (retries are no-ops)                |
```

### The ledger

Every journal entry has an account: `WALLET:<id>` for user wallets, or a system account such as
`SYSTEM:PAYMOB_CLEARING`. A top-up of 100.00 EGP writes:

| journal | account | amount (piasters) |
|---|---|---|
| j1 | SYSTEM:PAYMOB_CLEARING | -10000 |
| j1 | WALLET:42 | +10000 |

A wallet's balance is always `SUM(entries)` for its account; the `balance_minor` column on the
wallet row is just a cache of that sum, updated in the same transaction and audited by the
reconciliation job.

## Getting Started

### Prerequisites

- Java 21
- Docker Desktop

### 1. Configure environment

```bash
cp .env.example .env
# set JWT_SECRET (>= 32 chars) and, for real payments, your Paymob credentials
```

### 2. Run everything

```bash
docker compose up --build
```

- API: http://localhost:8081
- Swagger UI: http://localhost:8081/swagger-ui.html
- Mailhog (dev inbox): http://localhost:8025

Without Paymob credentials (`PAYMOB_ENABLED=false`) a clearly-marked stub returns a fake payment
URL so the API flow can be exercised end to end; the webhook, ledger and idempotency logic are
fully real either way. In production the webhook is called by Paymob - locally you can expose the
app with `ngrok http 8081` and set Paymob's notification URL to
`https://<your-ngrok>/api/webhooks/paymob`.

### 3. Try it

```bash
# register (wallet opens automatically on first use)
curl -X POST localhost:8081/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"me@example.com","fullName":"Me Myself","password":"secret123"}'

# start a top-up of 100.00 EGP (amounts are piasters)
curl -X POST localhost:8081/api/v1/topups \
  -H "Authorization: Bearer <accessToken>" -H 'Content-Type: application/json' \
  -d '{"amountMinor": 10000}'

# transfer to a friend
curl -X POST localhost:8081/api/v1/wallet/transfers \
  -H "Authorization: Bearer <accessToken>" -H 'Content-Type: application/json' \
  -d '{"recipientEmail":"friend@example.com","amountMinor":2500,"idempotencyKey":"<uuid>"}'
```

## Running Tests

```bash
./mvnw test          # Windows: mvnw.cmd test
```

Tests run on H2 (PostgreSQL mode), so no Docker is needed. Highlights:

- `TransferConcurrencyIntegrationTest`: 20 opposite-direction transfers in parallel - no
  deadlock, money conserved to the piaster.
- `PaymobHmacVerifierTest`: reproduces Paymob's own documented HMAC worked example; tampered
  payloads fail closed.
- `HandlePaymobWebhookUseCaseTest`: retried webhooks, amount mismatches and failed payments
  never double-credit.

## API Overview

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/auth/register` | Register, returns access + refresh tokens |
| POST | `/api/v1/auth/login` | Login |
| POST | `/api/v1/auth/refresh` | Rotate refresh token, new token pair |
| POST | `/api/v1/auth/logout` | Revoke a refresh token |
| GET | `/api/v1/wallet` | My wallet + balance (opens on first call) |
| POST | `/api/v1/wallet/transfers` | P2P transfer (idempotent) |
| GET | `/api/v1/wallet/transactions` | Ledger history (offset/limit) |
| POST | `/api/v1/topups` | Start a top-up, returns Paymob payment URL |
| GET | `/api/v1/topups` | My top-ups |
| POST | `/api/webhooks/paymob` | Paymob transaction callback (HMAC-verified) |

## Design decisions worth reading

- **Minor units everywhere.** All amounts are `long` piasters; no `double` ever touches money.
- **Idempotency is a database guarantee, not an `if`.** Unique constraints on the transfer
  idempotency key and the Paymob transaction id; a lost race replays the stored result.
- **Locks in a fixed order.** Transfers lock the two wallet rows in ascending user-id order, the
  standard trick that makes deadlock impossible.
- **The ledger is the truth.** The wallet balance is a projection; reconciliation re-derives it
  nightly and logs loudly on drift.
- **Notifications never roll back money.** Email failures are caught and logged after the fact.
- **Refresh tokens are secrets.** Only SHA-256 hashes are stored; rotation detects reuse and
  nukes all sessions.
