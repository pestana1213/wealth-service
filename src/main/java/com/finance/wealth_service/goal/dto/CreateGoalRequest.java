package com.finance.wealth_service.goal.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateGoalRequest(
        @NotNull UUID bucketId,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal goalAmount,
        LocalDate goalObjective) {}
