--liquibase formatted sql
--changeset wealth:006

DO $$
BEGIN
CREATE TYPE account_type AS ENUM ('INVESTMENT', 'SAVINGS');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

ALTER TABLE accounts ADD COLUMN type account_type NOT NULL DEFAULT 'SAVINGS';