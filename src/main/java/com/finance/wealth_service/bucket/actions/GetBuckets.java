package com.finance.wealth_service.bucket.actions;

import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.bucket.port.BucketPort;
import com.finance.wealth_service.bucket.repository.BucketRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class GetBuckets implements BucketPort {

    private final BucketRepository bucketRepo;

    public GetBuckets(BucketRepository bucketRepo) {
        this.bucketRepo = bucketRepo;
    }

    public List<Bucket> getBucketsFromIds(List<UUID> ids) {
        return bucketRepo.findAllByIds(ids);
    }
}
