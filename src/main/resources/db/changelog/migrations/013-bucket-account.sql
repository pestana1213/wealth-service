--liquibase formatted sql
--changeset wealth:013

-- A bucket is a share of money held in one specific account.
ALTER TABLE buckets ADD COLUMN account_id UUID;

-- Backfill: attach each existing bucket to one of its owner's accounts
-- (the lowest id, so the result is deterministic).
UPDATE buckets b
SET account_id = (
    SELECT a.id FROM accounts a
    WHERE a.user_id = b.user_id
    ORDER BY a.id
    LIMIT 1
);

-- Fails if a user has buckets but no account; resolve those rows first.
ALTER TABLE buckets ALTER COLUMN account_id SET NOT NULL;

-- The bucket's account must belong to the same user as the bucket.
ALTER TABLE accounts ADD CONSTRAINT accounts_id_user_unique UNIQUE (id, user_id);
ALTER TABLE buckets ADD CONSTRAINT buckets_account_fk
    FOREIGN KEY (account_id, user_id) REFERENCES accounts (id, user_id);

CREATE INDEX buckets_account_idx ON buckets(account_id);
