package com.finance.wealth_service.visualization.dto;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
public record DashboardResponse(UUID userId, BigDecimal totalAmount, BigDecimal allocatedAmount,
                                BigDecimal availableAmount, List<AccountView> accounts,
                                List<BucketView> buckets, List<GoalView> goals) {}
