package com.finance.wealth_service.valuation.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateValuationRequest(
        @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal value,
        @NotNull @PastOrPresent LocalDate valuedAt) {}
