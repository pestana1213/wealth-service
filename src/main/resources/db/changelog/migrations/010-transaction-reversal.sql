--liquibase formatted sql
--changeset wealth:010

-- A reversal is a compensating transaction that points at the one it undoes.
-- The unique index makes a double reversal impossible even under a race: NULLs
-- are not compared in Postgres, so ordinary transactions are unaffected.
ALTER TABLE transactions ADD COLUMN reversal_of UUID REFERENCES transactions(id);

CREATE UNIQUE INDEX transactions_reversal_of_idx ON transactions(reversal_of);
