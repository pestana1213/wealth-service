package com.finance.wealth_service.visualization.dto;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** @param goals every goal set on this bucket, soonest objective first; they all read the same balance */
public record BucketView(UUID id, String name, BigDecimal currentAmount, List<GoalView> goals) {}