package com.finance.wealth_service.recurring.actions;

import com.finance.wealth_service.account.port.AccountPort;
import com.finance.wealth_service.account.port.UserPort;
import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.bucket.port.BucketPort;
import com.finance.wealth_service.common.Leg;
import com.finance.wealth_service.exception.InvalidRequestException;
import com.finance.wealth_service.exception.NotFoundException;
import com.finance.wealth_service.observability.WealthMetrics;
import com.finance.wealth_service.recurring.dto.CreateRecurringRequest;
import com.finance.wealth_service.recurring.entity.Recurring;
import com.finance.wealth_service.recurring.entity.RecurringFrequency;
import com.finance.wealth_service.recurring.repository.RecurringRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class CreateRecurringAction {

    private final RecurringRepository recurringRepository;

    private final UserPort userPort;

    private final BucketPort bucketPort;

    private final AccountPort accountPort;

    private final WealthMetrics metrics;

    private static final Logger log = LoggerFactory.getLogger(CreateRecurringAction.class);

    public CreateRecurringAction(RecurringRepository recurringRepository, UserPort userPort,
                                 BucketPort bucketPort, AccountPort accountPort, WealthMetrics metrics) {
        this.recurringRepository = recurringRepository;
        this.userPort = userPort;
        this.bucketPort = bucketPort;
        this.accountPort = accountPort;
        this.metrics = metrics;
    }

    public Recurring execute(AuthenticatedUser user, CreateRecurringRequest request) {
        UUID userId = userPort.findUserId(user).orElseThrow(() -> {
            metrics.recordRecurring("create", "not_found");
            return new NotFoundException("User not found");
        });

        Leg from = resolve(user, request.fromAccountId(), request.fromBucketId());
        Leg to = resolve(user, request.toAccountId(), request.toBucketId());
        if (from == null && to == null)
            throw new InvalidRequestException("A recurring needs an origin or a destination");
        if (Objects.equals(from, to))
            throw new InvalidRequestException("Origin and destination are the same");
        if (request.frequency() == RecurringFrequency.WEEKLY && request.day() > 7)
            throw new InvalidRequestException("day must be between 1 and 7 for WEEKLY");
        if (request.endsOn() != null && request.endsOn().isBefore(request.startsOn()))
            throw new InvalidRequestException("endsOn must not be before startsOn");

        Recurring created = recurringRepository.save(new Recurring(
                userId,
                request.name(),
                from == null ? null : from.accountId(),
                to == null ? null : to.accountId(),
                from == null ? null : from.bucketId(),
                to == null ? null : to.bucketId(),
                request.transactionAmount(),
                request.category(),
                request.frequency(),
                request.day(),
                request.startsOn(),
                request.endsOn(),
                request.mode()));
        metrics.recordRecurring("create", "created");
        return created;
    }

    private Leg resolve(AuthenticatedUser user, UUID accountId, UUID bucketId) {
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
