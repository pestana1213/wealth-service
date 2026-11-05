package com.finance.wealth_service.bucket.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateBucketResponse(UUID id, UUID userId, UUID accountId, String name, BigDecimal currentAmount) {}
