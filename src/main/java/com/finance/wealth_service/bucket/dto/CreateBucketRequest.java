package com.finance.wealth_service.bucket.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateBucketRequest(
        @NotBlank @Size(max = 255) String name,
        @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal currentAmount) {}
