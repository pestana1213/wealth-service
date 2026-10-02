package com.finance.wealth_service.bucket.port;

import com.finance.wealth_service.bucket.entity.Bucket;

import java.util.List;
import java.util.UUID;

public interface BucketPort {
    List<Bucket> getBucketsFromIds(List<UUID> ids);

}
