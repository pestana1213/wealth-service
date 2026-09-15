--liquibase formatted sql
--changeset wealth:002
ALTER TABLE accounts ADD COLUMN current_amount NUMERIC(19, 2) NOT NULL DEFAULT 0;
ALTER TABLE buckets ADD COLUMN current_amount NUMERIC(19, 2) NOT NULL DEFAULT 0;

-- Preserve balances if transactions were recorded before this migration.
UPDATE accounts a
SET current_amount = (
    SELECT COALESCE(SUM(
        CASE WHEN t.destination_account_id = a.id THEN t.transaction_amount ELSE 0 END
        - CASE WHEN t.provider_account_id = a.id THEN t.transaction_amount ELSE 0 END
    ), 0)
    FROM transactions t
    WHERE t.destination_account_id = a.id OR t.provider_account_id = a.id
);

UPDATE buckets b
SET current_amount = (
    SELECT COALESCE(SUM(
        CASE WHEN t.destination_bucket_id = b.id THEN t.transaction_amount ELSE 0 END
        - CASE WHEN t.source_bucket_id = b.id THEN t.transaction_amount ELSE 0 END
    ), 0)
    FROM transactions t
    WHERE t.destination_bucket_id = b.id OR t.source_bucket_id = b.id
);
