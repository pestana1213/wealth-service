package com.finance.wealth_service.recurring.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Both fields are optional. Without {@code transactionAmount} the recurring's amount is
 * used; without {@code occurredAt} the transaction is dated on the occurrence day.
 * The body itself may be omitted.
 */
public record ConfirmOccurrenceRequest(
        @Positive @Digits(integer = 17, fraction = 2) BigDecimal transactionAmount,
        @PastOrPresent LocalDate occurredAt) {}
