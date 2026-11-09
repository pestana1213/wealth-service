package com.finance.wealth_service.recurring.dto;

import com.finance.wealth_service.recurring.entity.Recurring;
import com.finance.wealth_service.transaction.entity.TransactionCategory;
import com.finance.wealth_service.transaction.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One occurrence waiting to be confirmed or skipped, for the "A confirmar" card. */
public record DueOccurrenceResponse(
        UUID recurringId,
        String name,
        LocalDate dueOn,
        BigDecimal transactionAmount,
        UUID fromAccountId,
        UUID fromBucketId,
        UUID toAccountId,
        UUID toBucketId,
        TransactionType type,
        TransactionCategory category) {

    public static DueOccurrenceResponse from(Recurring recurring, LocalDate dueOn) {
        return new DueOccurrenceResponse(
                recurring.getId(), recurring.getName(), dueOn, recurring.getTransactionAmount(),
                recurring.getProviderAccountId(), recurring.getSourceBucketId(),
                recurring.getDestinationAccountId(), recurring.getDestinationBucketId(),
                recurring.getType(), recurring.getCategory());
    }
}
