package com.finance.wealth_service.visualization.dto;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
/**
 * @param totalAmount  the money contributed, summed over the accounts
 * @param marketAmount what that money is worth now: each account at its latest
 *                     valuation, or at its balance when it has none
 * @param marketGain   market value minus what went in, over the valued accounts only
 */
public record DashboardResponse(UUID userId, BigDecimal totalAmount, BigDecimal allocatedAmount,
                                BigDecimal availableAmount, BigDecimal marketAmount, BigDecimal marketGain,
                                List<AccountView> accounts,
                                List<BucketView> buckets, List<GoalView> goals) {}
