package com.finance.wealth_service.bucket.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;

import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.bucket.repository.BucketRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class ArchiveBucketAction {
    private final BucketRepository buckets;

    public ArchiveBucketAction(BucketRepository buckets) {
        this.buckets = buckets;
    }

    /**
     * Archiving is idempotent. The row is kept so that the transaction history can
     * still name the bucket, and money must be moved out first: a non-zero balance
     * would silently vanish from the allocated total.
     */
    @Transactional
    public void execute(AuthenticatedUser user, UUID bucketId) {
        Bucket bucket = buckets.findOwnedBy(bucketId, user.subject(), user.issuer())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bucket not found"));
        if (bucket.getCurrentAmount().compareTo(BigDecimal.ZERO) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bucket still holds money; move the allocation out before archiving it");
        }
        bucket.archive();
        buckets.saveAndFlush(bucket);
    }
}
