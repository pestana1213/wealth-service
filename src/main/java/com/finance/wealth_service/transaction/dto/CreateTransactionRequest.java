package com.finance.wealth_service.transaction.dto;

import com.finance.wealth_service.transaction.entity.TransactionCategory;
import com.finance.wealth_service.transaction.entity.TransactionType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTransactionRequest(
        UUID fromAccountId,
        UUID fromBucketId,
        UUID toAccountId,
        UUID toBucketId,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal transactionAmount,
        @NotNull TransactionCategory category,
        @NotNull @PastOrPresent LocalDate occurredAt,
        @Size(max = 255) String description) {}
