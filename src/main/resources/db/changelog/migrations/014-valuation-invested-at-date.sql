--liquibase formatted sql
--changeset wealth:014

-- invested_money used to be the account balance on the day the valuation was
-- entered, so a valuation backdated to an earlier date got today's balance as its
-- cost basis. Recompute every row as the balance on valued_at: today's balance
-- minus what moved in or out after that date. Balances only change through
-- transactions, so a valuation entered on its own date keeps its stored value,
-- unless a movement was later backdated to before it, which this now counts.
-- Rows from before the column existed are filled too.
UPDATE account_valuations v
SET invested_money = a.current_amount - COALESCE((
    SELECT SUM(CASE WHEN t.destination_account_id = v.account_id THEN t.transaction_amount ELSE 0 END)
         - SUM(CASE WHEN t.provider_account_id = v.account_id THEN t.transaction_amount ELSE 0 END)
    FROM transactions t
    WHERE (t.destination_account_id = v.account_id OR t.provider_account_id = v.account_id)
      AND t.occurred_at > v.valued_at
), 0)
FROM accounts a
WHERE a.id = v.account_id;
