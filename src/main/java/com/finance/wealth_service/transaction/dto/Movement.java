package com.finance.wealth_service.transaction.dto;

import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.transaction.entity.TransactionCategory;
import com.finance.wealth_service.transaction.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A movement waiting to be posted, with both sides already resolved to an account
 * and an optional bucket. The owner comes from the token and {@code reversalOf}
 * is only set by a reversal, so a client can forge neither by posting to
 * {@code /transactions}.
 */
public record Movement(AuthenticatedUser owner, Leg from, Leg to, BigDecimal amount,
                       TransactionCategory category, LocalDate occurredAt,
                       String description, UUID reversalOf) {

    /** No origin is an income, no destination is an expense, both is a transfer. */
    public TransactionType type() {
        if (from == null) return TransactionType.INCOME;
        if (to == null)   return TransactionType.EXPENSE;
        return TransactionType.TRANSFER;
    }
}
