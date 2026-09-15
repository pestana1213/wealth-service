package com.finance.wealth_service.bucket.actions;

import com.finance.wealth_service.bucket.dto.CreateBucketRequest;
import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.bucket.repository.BucketRepository;
import com.finance.wealth_service.account.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;

@Service
public class CreateBucketAction {
    private final BucketRepository buckets;
    private final UserRepository users;

    public CreateBucketAction(BucketRepository buckets, UserRepository users) {
        this.buckets = buckets;
        this.users = users;
    }

    @Transactional
    public Bucket execute(CreateBucketRequest request) {
        if (!users.existsById(request.userId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        BigDecimal amount = request.currentAmount() == null ? BigDecimal.ZERO : request.currentAmount();
        return buckets.save(new Bucket(request.userId(), request.name().strip(), amount));
    }
}
