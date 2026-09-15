--liquibase formatted sql
--changeset wealth:001
CREATE TABLE users (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);
CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(255) NOT NULL
);
CREATE TABLE buckets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(255) NOT NULL
);
CREATE TABLE goals (
    id UUID PRIMARY KEY,
    bucket_id UUID NOT NULL REFERENCES buckets(id),
    goal_amount NUMERIC(19, 2) NOT NULL CHECK (goal_amount > 0),
    goal_objective DATE
);
CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    provider_account_id UUID REFERENCES accounts(id),
    destination_account_id UUID REFERENCES accounts(id),
    source_bucket_id UUID REFERENCES buckets(id),
    destination_bucket_id UUID REFERENCES buckets(id),
    transaction_amount NUMERIC(19, 2) NOT NULL CHECK (transaction_amount > 0),
    type VARCHAR(255) NOT NULL CHECK (type IN ('INCOME', 'EXPENSE', 'TRANSFER')),
    category VARCHAR(255) NOT NULL CHECK (category IN ('HOUSE', 'EMERGENCY', 'CAR', 'INVESTMENT', 'OTHER')),
    occurred_at DATE NOT NULL,
    description VARCHAR(255),
    CONSTRAINT transaction_accounts CHECK (
        (type = 'INCOME' AND provider_account_id IS NULL AND destination_account_id IS NOT NULL AND source_bucket_id IS NULL)
        OR (type = 'EXPENSE' AND provider_account_id IS NOT NULL AND destination_account_id IS NULL AND destination_bucket_id IS NULL)
        OR (type = 'TRANSFER' AND provider_account_id IS NOT NULL AND destination_account_id IS NOT NULL)
    ),
    CONSTRAINT meaningful_transfer CHECK (
        type <> 'TRANSFER' OR provider_account_id <> destination_account_id
        OR source_bucket_id IS DISTINCT FROM destination_bucket_id
    )
);
CREATE INDEX accounts_user_idx ON accounts(user_id);
CREATE INDEX buckets_user_idx ON buckets(user_id);
CREATE INDEX goals_bucket_idx ON goals(bucket_id);
CREATE INDEX transactions_user_date_idx ON transactions(user_id, occurred_at);
