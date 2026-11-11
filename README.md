# Wealth Service

MVP for tracking money saved and where it is allocated, in EUR.
The model has only five JPA entities:

- `User`: owner of the accounts, buckets and transactions.
- `Account`: where the money is (bank, broker, etc.).
- `Bucket`: what the money is for (house, emergency fund, car, VWCE, etc.).
- `Goal`: target amount and optional date for a bucket.
- `Transaction`: income, expense or transfer, with a positive amount,
  category, date and description.

Investments are buckets whose contributions are categorised as `INVESTMENT`.
There are no quotes, positions or market valuation.

## Transaction conventions

`providerAccountId` and `sourceBucketId` identify the source;
`destinationAccountId` and `destinationBucketId` identify the destination.
A null bucket means money that is not allocated within that account.

| Operation | Type | Source → destination account | Source → destination bucket |
| --- | --- | --- | --- |
| Receive salary | INCOME | none → Bank | none → none |
| Allocate €3,000 to VWCE at Bank | TRANSFER | Bank → Bank | none → VWCE |
| Move the same €3,000 to XTB | TRANSFER | Bank → XTB | VWCE → VWCE |
| Release an allocation | TRANSFER | Bank → Bank | VWCE → none |
| Pay a house expense | EXPENSE | Bank → none | House → none |

Current balances are persisted in `Account.currentAmount` and
`Bucket.currentAmount`, as `BigDecimal` with two decimal places.
An initial balance can be passed to the constructor; it defaults to zero.
The dashboard can read these values directly:

- Account: `Account.currentAmount`, the money held in that account.
- Bucket: `Bucket.currentAmount`, the money allocated to that purpose.
- Total saved: sum of the account balances.
- Available cash: total saved minus the sum of the bucket balances.

Accounts and buckets are two views of the same money; they are never added
together. Transfers do not change the total saved. A transfer between accounts
that keeps the bucket does not change that bucket's balance either.
Initial balances can be set directly, without importing the full history.
An initial balance set directly must not be added again as income.

## Implementation status

Entities and the initial Liquibase migration. References between entities are
UUID fields, with no JPA associations; foreign keys keep referential integrity
in the database. There are endpoints to create users, accounts, buckets and
goals. The `INCOME`, `EXPENSE` and `TRANSFER` flows update the affected
accounts and buckets and save the transaction in the same `TransactionTemplate`.
Moving money happens in a single place, the `Ledger`: it acquires the locks,
applies the arithmetic and writes the history, all inside the same database
transaction. `CreateTransactionAction` validates the request, calls the
`Ledger` and maps the result to an HTTP status, outside the transaction. A
reversal goes through the same `Ledger`, linked to the original transaction
through a `NewTransaction`.
Source and destination references must belong to the transaction's user.
Accounts and buckets are loaded with `FOR UPDATE NOWAIT`, accounts first and
then buckets, ordered by UUID and never locking the same row twice.
If any of them is locked, the operation rolls back and returns `409 Conflict`,
without waiting for the row lock or retrying automatically. `@Version` stays
as an extra guard against concurrent updates.
`EXPENSE` subtracts from the account and from the optional source bucket;
`TRANSFER` subtracts from the source and adds to the destination. An account
or bucket present on both sides keeps its balance.

Sufficient balance is checked on buckets, not on accounts. An account can go
negative: an overdraft or a credit card is a real account state, and an
expense can take it below zero. A bucket cannot, because it is a share of
money that exists in the accounts; a movement that would take it below zero
returns `400 Bad Request` and changes nothing. The rule lives in the `Ledger`,
in the entity's `@PositiveOrZero` annotation and in the
`buckets_amount_not_negative` constraint; accounts have no equivalent
constraint.

Persisting a `Transaction` directly through the repository does not update
balances; use the action.
The database already validates positive amounts and the shape of transfers.

`POST /transactions` validates the request before touching any balance:
`userId`, `type`, `category`, `occurredAt` and `transactionAmount` are
required, the amount is positive with up to 17 integer digits and two decimal
places, `occurredAt` cannot be in the future and `description` is limited to
255 characters. Invalid data returns `400 Bad Request` as
`application/problem+json`, with the offending fields in the `errors`
property. Account and bucket references are not annotated: which of them may
be null depends on the `type`, so that rule stays in the action and in the
`transaction_accounts` constraint.

PostgreSQL is configured in `application.properties`; Hibernate validates the
schema created by Liquibase. Java 25 is required.

## Read endpoints

- `GET /users/{userId}/dashboard`: total, allocated amount, available cash,
  accounts, buckets and goals (including each goal's current progress).
- `GET /users/{userId}/accounts`: accounts and their balances.
- `GET /users/{userId}/buckets`: buckets and their balances.
- `GET /users/{userId}/goals`: goals and current progress.

All of them return `404 Not Found` when the user does not exist.

## Swagger / OpenAPI

API docs are off by default. With `API_DOCS_ENABLED=true`, the interactive
docs are available at `/swagger-ui.html` and the OpenAPI document at
`/api-docs`.

## Metrics and logs

Every HTTP request is recorded by Spring's `http.server.requests`, with
method, route template (`uri`, for example `/buckets/{bucketId}`), status,
exception and duration, plus a histogram for percentiles.
The Actuator endpoints `health`, `info` and `metrics` are exposed under
`/actuator`. See, for example, `/actuator/metrics/http.server.requests`.

## Create a user and an account

`POST /users` — creates a user and returns `201 Created` with `id`, `name` and `email`.

```json
{"name": "João", "email": "joao@example.com"}
```

`POST /accounts` — creates an account and returns `201 Created` with `id`,
`userId`, `name` and `currentAmount`. Use the ID returned when the user was
created:

```json
{"userId": "<user UUID>", "name": "Bank", "currentAmount": 55000.00}
```

`currentAmount` is optional (omitted or null means `0.00`), with up to 17
integer digits and two decimal places. The balance is set directly; no initial
transaction is created. Negative balances are accepted for overdrawn
accounts. Names are required, limited to 255 characters, and stored without
leading or trailing spaces.

Invalid data returns `400 Bad Request`; a `userId` that does not exist returns
`404 Not Found`. These operations create domain records; authentication and
login are not implemented yet.

Email is required for new users, validated up to 254 characters, and stored
lowercase without leading or trailing spaces. Duplicate emails, regardless of
case, return `409 Conflict`. The migration keeps older users with a null
email; their real addresses will have to be filled in later. The unique index
applies to non-null emails, including concurrent inserts.

## Create a bucket and a goal

`POST /buckets` — returns `201 Created` with `id`, `userId`, `name` and `currentAmount`.

```json
{"userId": "<user UUID>", "name": "House", "currentAmount": 27000.00}
```

The user must exist. The name is required, up to 255 characters, and stored
without leading or trailing spaces. The balance is optional (omitted or null
means zero), non-negative and limited to 17 integer digits and two decimal
places. This initial balance represents an existing allocation; creating the
bucket does not change account balances or create a transaction.

`POST /goals` — returns `201 Created` with `id`, `bucketId`, `goalAmount` and `goalObjective`.

```json
{"bucketId": "<bucket UUID>", "goalAmount": 50000.00, "goalObjective": "2027-12-31"}
```

The bucket must exist. The amount is required, positive and limited to 17
integer digits and two decimal places. The date is optional, in `YYYY-MM-DD`
format; creating a goal does not change the bucket's balance.

Both return `400 Bad Request` for invalid data and `404 Not Found` when the
related user or bucket does not exist.

## Correct and retire buckets, goals and transactions

`PATCH /accounts/{accountId}` — changes the name and/or the type (`SAVINGS` or
`INVESTMENT`). Omitted fields stay unchanged; the balance only changes through
transactions.

`PATCH /buckets/{bucketId}` — changes the name and/or corrects the allocated
amount. Omitted fields stay unchanged. Correcting the amount is a correction,
not a movement: it creates no transaction and does not touch account balances,
so the total saved does not change and only the split between allocated and
cash adjusts. The amount is non-negative, with up to 17 integer digits and two
decimal places.

`DELETE /buckets/{bucketId}` — archives the bucket and returns
`204 No Content`. The row is kept: transactions and goals reference
`buckets(id)` and the history must keep naming the purpose the money was in.
An archived bucket leaves the dashboard and the listings, takes its goals with
it, and is rejected in new transactions. The bucket must be at zero; with
money allocated it returns `409 Conflict`, because that balance would vanish
from the allocated total. Archiving is idempotent and there is no endpoint to
unarchive.

`PATCH /goals/{goalId}` — changes the target amount and/or the date. Omitted
fields stay unchanged; `"goalObjective": null` removes the date (JSON Merge
Patch). The bucket is fixed: pointing a goal at another bucket makes it a
different goal.
`DELETE /goals/{goalId}` — returns `204 No Content` and removes the row;
nothing references a goal. The bucket and its allocation are not affected.

`POST /transactions/{transactionId}/reversal` — returns `201 Created` with a
compensating transaction that undoes the effect on balances and links to the
original through `reversalOf`. The original is kept, so the history shows both
the mistake and the correction. The reversal swaps source and destination on
both sides, which turns income into an expense and a transfer into the
opposite transfer; it is dated today and goes through the same
`CreateTransactionAction`, with the same locks and validations.
Reversing the same transaction twice, or reversing a reversal, returns
`409 Conflict`; a unique index on `reversal_of` guarantees this even under
races. A transaction whose bucket has already been archived cannot be
reversed.
