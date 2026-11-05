package com.finance.wealth_service.bucket.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;
import java.util.UUID;

import com.finance.wealth_service.bucket.dto.CreateBucketRequest;
import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.bucket.repository.BucketRepository;
import com.finance.wealth_service.account.port.AccountPort;
import com.finance.wealth_service.account.port.UserPort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;

@Service
public class CreateBucketAction {
    private final BucketRepository buckets;
    private final UserPort users;
    private final AccountPort accounts;

    public CreateBucketAction(BucketRepository buckets, UserPort users, AccountPort accounts) {
        this.buckets = buckets;
        this.users = users;
        this.accounts = accounts;
    }

    @Transactional
    public Bucket execute(AuthenticatedUser user, CreateBucketRequest request) {
        UUID userId = users.requireUserId(user);
        if (!accounts.isOwnedBy(request.accountId(), user.subject(), user.issuer())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }
        BigDecimal amount = request.currentAmount() == null ? BigDecimal.ZERO : request.currentAmount();
        return buckets.save(new Bucket(userId, request.accountId(), request.name().strip(), amount));
    }
}
