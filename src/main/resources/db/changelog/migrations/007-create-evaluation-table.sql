--liquibase formatted sql
--changeset wealth:007
-- Market-value history for investment accounts. Kept separate from accounts.current_amount,
-- which tracks money moved in and out through transactions.
CREATE TABLE account_valuations (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    value NUMERIC(19, 2) NOT NULL CHECK (value >= 0),
    valued_at DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT account_valuations_account_date_unique UNIQUE (account_id, valued_at)
);
