package com.finance.wealth_service.visualization.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
public record GoalView(UUID id, UUID bucketId, BigDecimal goalAmount, LocalDate goalObjective, BigDecimal currentAmount) {}
