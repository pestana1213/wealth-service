package com.finance.wealth_service.recurring.dto;

import com.finance.wealth_service.recurring.entity.RecurringFrequency;
import com.finance.wealth_service.recurring.entity.RecurringMode;
import com.finance.wealth_service.transaction.entity.TransactionCategory;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Origin and destination follow the same rules as {@code POST /transactions}: no origin
 * is an income, no destination is an expense, both is a transfer.
 *
 * <p>{@code day} is checked here only against 1–31. The range that depends on the
 * frequency (1–7 for WEEKLY) and {@code endsOn >= startsOn} are checked by the action,
 * which also has to check them on a partial update against the stored values.
 */
public record CreateRecurringRequest(
        @NotBlank @Size(max = 255) String name,
        UUID fromAccountId,
        UUID fromBucketId,
        UUID toAccountId,
        UUID toBucketId,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal transactionAmount,
        @NotNull TransactionCategory category,
        @NotNull RecurringFrequency frequency,
        @NotNull @Min(1) @Max(31) Integer day,
        @NotNull LocalDate startsOn,
        LocalDate endsOn,
        @NotNull RecurringMode mode) {}
