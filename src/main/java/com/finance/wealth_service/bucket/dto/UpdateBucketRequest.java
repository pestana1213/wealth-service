package com.finance.wealth_service.bucket.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Omitted fields are left unchanged. {@code currentAmount} corrects a wrong
 * allocation directly; it is a correction, not a movement, so no transaction is
 * recorded and account balances are untouched.
 */
public record UpdateBucketRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") @Size(max = 255) String name,
        @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal currentAmount) {}
