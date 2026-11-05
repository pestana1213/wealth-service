package com.finance.wealth_service.bucket.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;

import com.finance.wealth_service.account.port.AccountPort;
import com.finance.wealth_service.bucket.dto.UpdateBucketRequest;
import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.bucket.repository.BucketRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class UpdateBucketAction {
    private final BucketRepository buckets;
    private final AccountPort accounts;

    public UpdateBucketAction(BucketRepository buckets, AccountPort accounts) {
        this.buckets = buckets;
        this.accounts = accounts;
    }

    @Transactional
    public Bucket execute(AuthenticatedUser user, UUID bucketId, UpdateBucketRequest request) {
        Bucket bucket = buckets.findOwnedBy(bucketId, user.subject(), user.issuer())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bucket not found"));
        if (bucket.isArchived()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bucket is archived");
        }
        if (request.accountId() != null && !request.accountId().equals(bucket.getAccountId())) {
            moveToAccount(user, bucket, request.accountId());
        }
        if (request.name() != null) {
            bucket.setName(request.name().strip());
        }
        if (request.currentAmount() != null) {
            bucket.setCurrentAmount(request.currentAmount());
        }
        return buckets.saveAndFlush(bucket);
    }

    /**
     * The bucket's money is a share of its account's balance, so re-pointing a
     * non-empty bucket would move that claim without moving the money: the old
     * account would be under-allocated and the new one over-allocated. Move the
     * allocation out first, as with archiving.
     */
    private void moveToAccount(AuthenticatedUser user, Bucket bucket, UUID accountId) {
        if (!accounts.isOwnedBy(accountId, user.subject(), user.issuer())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }
        if (bucket.getCurrentAmount().compareTo(BigDecimal.ZERO) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bucket still holds money; move the allocation out before changing its account");
        }
        bucket.moveTo(accountId);
    }
}
