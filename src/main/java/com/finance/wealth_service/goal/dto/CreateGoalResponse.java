package com.finance.wealth_service.goal.dto;

import com.finance.wealth_service.goal.entity.Goal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateGoalResponse(UUID id, UUID bucketId, BigDecimal goalAmount, LocalDate goalObjective) {
    public static CreateGoalResponse from(Goal goal) {
        return new CreateGoalResponse(goal.getId(), goal.getBucketId(), goal.getGoalAmount(), goal.getGoalObjective());
    }
}
