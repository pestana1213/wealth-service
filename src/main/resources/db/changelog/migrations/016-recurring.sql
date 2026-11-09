--liquibase formatted sql
--changeset wealth:016

-- A recurring movement (rent, salary, Netflix). AUTO ones are recorded by a daily
-- job; CONFIRM ones wait for the user to confirm or skip each occurrence.
CREATE TABLE recurring (
    id                     UUID PRIMARY KEY,
    user_id                UUID NOT NULL REFERENCES users(id),
    name                   VARCHAR(255) NOT NULL,

    -- Same names as transactions: confirming an occurrence copies these as they are.
    provider_account_id    UUID REFERENCES accounts(id),
    destination_account_id UUID REFERENCES accounts(id),
    source_bucket_id       UUID REFERENCES buckets(id),
    destination_bucket_id  UUID REFERENCES buckets(id),
    transaction_amount     NUMERIC(19, 2) NOT NULL CHECK (transaction_amount > 0),
    category               VARCHAR(255) NOT NULL
        CHECK (category IN ('HOUSE', 'EMERGENCY', 'CAR', 'INVESTMENT', 'OTHER')),

    frequency              VARCHAR(16) NOT NULL CHECK (frequency IN ('WEEKLY', 'MONTHLY', 'YEARLY')),
    -- Day of the week (1 = Monday … 7 = Sunday) or of the month (31 = last day).
    -- YEARLY takes its month from starts_on.
    day                    INTEGER NOT NULL,
    starts_on              DATE NOT NULL,
    ends_on                DATE,
    mode                   VARCHAR(16) NOT NULL CHECK (mode IN ('CONFIRM', 'AUTO')),

    -- First occurrence not yet handled; NULL once the schedule is past ends_on.
    next_on                DATE,

    version                BIGINT NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT recurring_day CHECK (
        (frequency = 'WEEKLY' AND day BETWEEN 1 AND 7)
        OR (frequency IN ('MONTHLY', 'YEARLY') AND day BETWEEN 1 AND 31)
    ),
    CONSTRAINT recurring_period CHECK (ends_on IS NULL OR ends_on >= starts_on),
    -- The transactions source/destination rules, without the derived type column.
    CONSTRAINT recurring_accounts CHECK (
        (provider_account_id IS NOT NULL OR destination_account_id IS NOT NULL)
        AND (source_bucket_id IS NULL OR provider_account_id IS NOT NULL)
        AND (destination_bucket_id IS NULL OR destination_account_id IS NOT NULL)
    ),
    CONSTRAINT recurring_meaningful_transfer CHECK (
        provider_account_id IS NULL OR destination_account_id IS NULL
        OR provider_account_id <> destination_account_id
        OR source_bucket_id IS DISTINCT FROM destination_bucket_id
    )
);

CREATE INDEX recurring_user_idx ON recurring(user_id);
CREATE INDEX recurring_next_on_idx ON recurring(next_on) WHERE next_on IS NOT NULL;

-- Skipped occurrences. Confirmed ones live in transactions; without this a skip
-- would show up as due again.
CREATE TABLE recurring_skips (
    id            UUID PRIMARY KEY,
    recurring_id  UUID NOT NULL REFERENCES recurring(id) ON DELETE CASCADE,
    occurrence_on DATE NOT NULL,
    CONSTRAINT recurring_skips_occurrence_unique UNIQUE (recurring_id, occurrence_on)
);

-- The unique pair makes recording an occurrence twice impossible, even under a race.
-- Deleting a recurring keeps its transactions: only the link is cleared, so the
-- check allows occurrence_on without recurring_id but not the other way round.
ALTER TABLE transactions
    ADD COLUMN recurring_id  UUID REFERENCES recurring(id) ON DELETE SET NULL,
    ADD COLUMN occurrence_on DATE;
ALTER TABLE transactions ADD CONSTRAINT transactions_recurring_occurrence_unique
    UNIQUE (recurring_id, occurrence_on);
ALTER TABLE transactions ADD CONSTRAINT transactions_recurring_occurrence_present
    CHECK (recurring_id IS NULL OR occurrence_on IS NOT NULL);
