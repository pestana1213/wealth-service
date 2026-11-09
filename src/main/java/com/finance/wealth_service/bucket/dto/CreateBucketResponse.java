package com.finance.wealth_service.bucket.dto;

import com.finance.wealth_service.bucket.entity.Bucket;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateBucketResponse(UUID id, UUID userId, UUID accountId, String name, BigDecimal currentAmount) {
    public static CreateBucketResponse from(Bucket bucket) {
        return new CreateBucketResponse(bucket.getId(), bucket.getUserId(), bucket.getAccountId(), bucket.getName(),
                bucket.getCurrentAmount());
    }
}
