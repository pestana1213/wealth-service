package com.finance.wealth_service.transaction.dto;

import com.finance.wealth_service.transaction.entity.Transaction;
import com.finance.wealth_service.transaction.entity.TransactionCategory;
import com.finance.wealth_service.transaction.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTransactionResponse(
        UUID id,
        UUID userId,
        UUID providerAccountId,
        UUID destinationAccountId,
        UUID sourceBucketId,
        UUID destinationBucketId,
        BigDecimal transactionAmount,
        TransactionType type,
        TransactionCategory category,
        LocalDate occurredAt,
        String description,
        UUID reversalOf) {
    public static CreateTransactionResponse from(Transaction transaction) {
        return new CreateTransactionResponse(
                transaction.getId(), transaction.getUserId(), transaction.getProviderAccountId(),
                transaction.getDestinationAccountId(), transaction.getSourceBucketId(),
                transaction.getDestinationBucketId(), transaction.getTransactionAmount(),
                transaction.getType(), transaction.getCategory(), transaction.getOccurredAt(),
                transaction.getDescription(), transaction.getReversalOf());
    }
}
