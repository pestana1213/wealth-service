package com.finance.wealth_service.goal.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Omitted fields are left unchanged. A goal's bucket is fixed: pointing a goal at
 * another bucket means a different goal.
 */
public record UpdateGoalRequest(
        @Positive @Digits(integer = 17, fraction = 2) BigDecimal goalAmount,
        LocalDate goalObjective) {}
