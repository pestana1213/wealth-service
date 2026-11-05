package com.finance.wealth_service.bucket.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;

import com.finance.wealth_service.bucket.dto.UpdateBucketRequest;
import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.bucket.repository.BucketRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class UpdateBucketAction {
    private final BucketRepository buckets;

    public UpdateBucketAction(BucketRepository buckets) {
        this.buckets = buckets;
    }

    @Transactional
    public Bucket execute(AuthenticatedUser user, UUID bucketId, UpdateBucketRequest request) {
        Bucket bucket = buckets.findOwnedBy(bucketId, user.subject(), user.issuer())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bucket not found"));
        if (bucket.isArchived()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bucket is archived");
        }
        if (request.name() != null) {
            bucket.setName(request.name().strip());
        }
        if (request.currentAmount() != null) {
            bucket.setCurrentAmount(request.currentAmount());
        }
        return buckets.saveAndFlush(bucket);
    }
}
