--liquibase formatted sql
--changeset wealth:015

-- Optimistic locking for goals, as accounts and buckets already have: two edits
-- of the same goal no longer overwrite each other silently; the later one gets a 409.
ALTER TABLE goals ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
