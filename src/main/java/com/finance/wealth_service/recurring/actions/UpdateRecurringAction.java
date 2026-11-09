package com.finance.wealth_service.recurring.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.common.Leg;
import com.finance.wealth_service.exception.ConflictException;
import com.finance.wealth_service.exception.InvalidRequestException;
import com.finance.wealth_service.exception.NotFoundException;
import com.finance.wealth_service.exception.WealthException;
import com.finance.wealth_service.observability.WealthMetrics;
import com.finance.wealth_service.recurring.dto.UpdateRecurringRequest;
import com.finance.wealth_service.recurring.entity.Recurring;
import com.finance.wealth_service.recurring.entity.RecurringFrequency;
import com.finance.wealth_service.recurring.repository.RecurringRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Service
public class UpdateRecurringAction {

    private static final Logger log = LoggerFactory.getLogger(UpdateRecurringAction.class);
    private static final String OPERATION = "update";

    private final RecurringRepository recurringRepository;
    private final RecurringLegResolver legs;
    private final TransactionTemplate transactionTemplate;
    private final WealthMetrics metrics;

    public UpdateRecurringAction(RecurringRepository recurringRepository,
                                 RecurringLegResolver legs,
                                 TransactionTemplate transactionTemplate,
                                 WealthMetrics metrics) {
        this.recurringRepository = recurringRepository;
        this.legs = legs;
        this.transactionTemplate = transactionTemplate;
        this.metrics = metrics;
    }

    public Recurring execute(AuthenticatedUser user, UUID recurringId, UpdateRecurringRequest request) {
        try {
            Recurring updated = transactionTemplate.execute(_ -> update(user, recurringId, request));
            metrics.recordRecurring(OPERATION, "updated");
            log.info("Updated recurring {}", recurringId);
            return updated;
        } catch (OptimisticLockingFailureException exception) {
            metrics.recordRecurring(OPERATION, "conflict");
            log.warn("Recurring {} changed while it was being updated", recurringId);
            throw new ConflictException("This recurring changed while it was being updated; refresh to see its current state",
                    exception);
        } catch (NotFoundException exception) {
            metrics.recordRecurring(OPERATION, "not_found");
            log.debug("Recurring {} not found for update", recurringId);
            throw exception;
        } catch (WealthException exception) {
            metrics.recordRecurring(OPERATION, "invalid");
            log.info("Rejected update of recurring {}: {}", recurringId, exception.getMessage());
            throw exception;
        }
    }

    private Recurring update(AuthenticatedUser user, UUID recurringId, UpdateRecurringRequest request) {
        Recurring recurring = recurringRepository
                .findRecurringByIdForUser(user.subject(), user.issuer(), recurringId)
                .orElseThrow(() -> new NotFoundException("Recurring not found"));

        updateLegs(user, recurring, request);
        if (request.name() != null)
            recurring.setName(request.name().strip());
        if (request.transactionAmount() != null)
            recurring.setTransactionAmount(request.transactionAmount());
        if (request.category() != null)
            recurring.setCategory(request.category());
        if (request.mode() != null)
            recurring.setMode(request.mode());
        if (request.changesSchedule())
            reschedule(recurring, request);

        return recurringRepository.saveAndFlush(recurring);
    }

    private void updateLegs(AuthenticatedUser user, Recurring recurring, UpdateRecurringRequest request) {
        Leg from = side(user, leg(recurring.getProviderAccountId(), recurring.getSourceBucketId()),
                request.fromAccountId(), request.fromAccountIdSent(),
                request.fromBucketId(), request.fromBucketIdSent());
        Leg to = side(user, leg(recurring.getDestinationAccountId(), recurring.getDestinationBucketId()),
                request.toAccountId(), request.toAccountIdSent(),
                request.toBucketId(), request.toBucketIdSent());
        if (from == null && to == null)
            throw new InvalidRequestException("A recurring needs an origin or a destination");
        if (Objects.equals(from, to))
            throw new InvalidRequestException("Origin and destination are the same");
        recurring.setAccounts(
                from == null ? null : from.accountId(),
                to == null ? null : to.accountId(),
                from == null ? null : from.bucketId(),
                to == null ? null : to.bucketId());
    }

    private Leg side(AuthenticatedUser user, Leg current,
                     UUID accountId, boolean accountSent, UUID bucketId, boolean bucketSent) {
        if (!accountSent && !bucketSent)
            return current;
        if (accountId != null || bucketId != null)
            return legs.resolve(user, accountId, bucketId);
        if (!accountSent && current != null)
            return new Leg(current.accountId(), null);
        return null;
    }

    private void reschedule(Recurring recurring, UpdateRecurringRequest request) {
        RecurringFrequency frequency = request.frequency() != null ? request.frequency() : recurring.getFrequency();
        int day = request.day() != null ? request.day() : recurring.getDay();
        LocalDate startsOn = request.startsOn() != null ? request.startsOn() : recurring.getStartsOn();
        LocalDate endsOn = request.endsOnSent() ? request.endsOn() : recurring.getEndsOn();

        if (frequency == RecurringFrequency.WEEKLY && day > 7)
            throw new InvalidRequestException("day must be between 1 and 7 for WEEKLY");
        if (endsOn != null && endsOn.isBefore(startsOn))
            throw new InvalidRequestException("endsOn must not be before startsOn");

        recurring.reschedule(frequency, day, startsOn, endsOn, LocalDate.now());
    }

    private static Leg leg(UUID accountId, UUID bucketId) {
        return accountId == null ? null : new Leg(accountId, bucketId);
    }
}
