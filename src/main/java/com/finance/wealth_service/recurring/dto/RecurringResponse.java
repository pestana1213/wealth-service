package com.finance.wealth_service.recurring.dto;

import com.finance.wealth_service.recurring.entity.Recurring;
import com.finance.wealth_service.recurring.entity.RecurringFrequency;
import com.finance.wealth_service.recurring.entity.RecurringMode;
import com.finance.wealth_service.transaction.entity.TransactionCategory;
import com.finance.wealth_service.transaction.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Returned by create, list and update. {@code nextOn} is null once the schedule has ended. */
public record RecurringResponse(
        UUID id,
        String name,
        UUID fromAccountId,
        UUID fromBucketId,
        UUID toAccountId,
        UUID toBucketId,
        BigDecimal transactionAmount,
        TransactionType type,
        TransactionCategory category,
        RecurringFrequency frequency,
        int day,
        LocalDate startsOn,
        LocalDate endsOn,
        RecurringMode mode,
        LocalDate nextOn) {

    public static RecurringResponse from(Recurring recurring) {
        return new RecurringResponse(
                recurring.getId(), recurring.getName(),
                recurring.getProviderAccountId(), recurring.getSourceBucketId(),
                recurring.getDestinationAccountId(), recurring.getDestinationBucketId(),
                recurring.getTransactionAmount(), recurring.getType(), recurring.getCategory(),
                recurring.getFrequency(), recurring.getDay(), recurring.getStartsOn(),
                recurring.getEndsOn(), recurring.getMode(), recurring.getNextOn());
    }
}
