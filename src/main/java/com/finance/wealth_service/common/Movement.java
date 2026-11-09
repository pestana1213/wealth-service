package com.finance.wealth_service.common;

import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.transaction.entity.TransactionCategory;
import com.finance.wealth_service.transaction.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A movement waiting to be posted, with both sides already resolved to an account
 * and an optional bucket. The owner comes from the token, {@code reversalOf} is only
 * set by a reversal and {@code occurrence} only by confirming a recurring, so a client
 * can forge none of them by posting to {@code /transactions}.
 */
public record Movement(AuthenticatedUser owner, Leg from, Leg to, BigDecimal amount,
                       TransactionCategory category, LocalDate occurredAt,
                       String description, UUID reversalOf, Occurrence occurrence) {

    public Movement(AuthenticatedUser owner, Leg from, Leg to, BigDecimal amount,
                    TransactionCategory category, LocalDate occurredAt,
                    String description, UUID reversalOf) {
        this(owner, from, to, amount, category, occurredAt, description, reversalOf, null);
    }

    /** No origin is an income, no destination is an expense, both is a transfer. */
    public TransactionType type() {
        if (from == null) return TransactionType.INCOME;
        if (to == null)   return TransactionType.EXPENSE;
        return TransactionType.TRANSFER;
    }
}
