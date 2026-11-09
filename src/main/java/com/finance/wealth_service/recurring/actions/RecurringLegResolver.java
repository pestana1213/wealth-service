package com.finance.wealth_service.recurring.actions;

import com.finance.wealth_service.account.port.AccountPort;
import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.bucket.port.BucketPort;
import com.finance.wealth_service.common.Leg;
import com.finance.wealth_service.exception.InvalidRequestException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RecurringLegResolver {

    private final BucketPort bucketPort;
    private final AccountPort accountPort;

    public RecurringLegResolver(BucketPort bucketPort, AccountPort accountPort) {
        this.bucketPort = bucketPort;
        this.accountPort = accountPort;
    }

    public Leg resolve(AuthenticatedUser user, UUID accountId, UUID bucketId) {
        if (accountId != null && bucketId != null)
            throw new InvalidRequestException("Give an account or a bucket, not both");
        if (bucketId != null) {
            Bucket bucket = bucketPort.findOwnedBy(bucketId, user.subject(), user.issuer())
                    .filter(found -> !found.isArchived())
                    .orElseThrow(() -> new InvalidRequestException("Bucket not found"));
            return new Leg(bucket.getAccountId(), bucketId);
        }
        if (accountId == null) return null;
        if (!accountPort.isOwnedBy(accountId, user.subject(), user.issuer()))
            throw new InvalidRequestException("Account not found");
        return new Leg(accountId, null);
    }
}
