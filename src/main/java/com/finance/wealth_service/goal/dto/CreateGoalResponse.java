package com.finance.wealth_service.goal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateGoalResponse(UUID id, UUID bucketId, BigDecimal goalAmount, LocalDate goalObjective) {}
