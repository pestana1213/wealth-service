--liquibase formatted sql
--changeset wealth:011

-- A bucket is a share of money that exists in the accounts, so it cannot hold
-- less than nothing. Accounts deliberately have no such constraint: a debit
-- balance or a credit card is a real account state, and an expense is allowed
-- to overdraw one.
ALTER TABLE buckets ADD CONSTRAINT buckets_amount_not_negative CHECK (current_amount >= 0);
