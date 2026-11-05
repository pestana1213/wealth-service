package com.finance.wealth_service.visualization.dto;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
/**
 * @param currentAmount the money contributed to the account
 * @param marketValue   the latest recorded value, or null when none was recorded
 * @param investedMoney what had gone in when that value was recorded, or null with it
 */
public record AccountView(UUID id, String name, BigDecimal currentAmount,
                          BigDecimal allocatedAmount,
                          BigDecimal availableAmount,
                          BigDecimal marketValue, BigDecimal investedMoney,
                          List<BucketView> buckets) {}
