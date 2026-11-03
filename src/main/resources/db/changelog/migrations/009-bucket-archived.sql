--liquibase formatted sql
--changeset wealth:009

-- Retiring a bucket archives it instead of deleting the row: transactions and
-- goals reference buckets(id), and the history must keep naming the bucket it
-- was allocated to.
ALTER TABLE buckets ADD COLUMN archived BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX buckets_user_active_idx ON buckets(user_id) WHERE archived = false;
