package com.finance.wealth_service.transaction.dto;

import com.finance.wealth_service.transaction.entity.TransactionCategory;
import com.finance.wealth_service.transaction.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionDTO(
        UUID transactionId,
        MovementAcc source,
        MovementAcc destination,
        BigDecimal transactionAmount,
        TransactionType type,
        TransactionCategory category,
        LocalDate occurredAt,
        String description,
        UUID reversalOf
) {}
