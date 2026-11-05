package com.finance.wealth_service.bucket.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;
import java.util.UUID;

import com.finance.wealth_service.bucket.dto.CreateBucketRequest;
import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.bucket.repository.BucketRepository;
import com.finance.wealth_service.account.port.UserPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
public class CreateBucketAction {
    private final BucketRepository buckets;
    private final UserPort users;

    public CreateBucketAction(BucketRepository buckets, UserPort users) {
        this.buckets = buckets;
        this.users = users;
    }

    @Transactional
    public Bucket execute(AuthenticatedUser user, CreateBucketRequest request) {
        UUID userId = users.requireUserId(user);
        BigDecimal amount = request.currentAmount() == null ? BigDecimal.ZERO : request.currentAmount();
        return buckets.save(new Bucket(userId, request.name().strip(), amount));
    }
}
