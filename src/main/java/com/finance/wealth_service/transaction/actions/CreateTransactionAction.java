package com.finance.wealth_service.transaction.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;

import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.bucket.port.BucketPort;
import com.finance.wealth_service.observability.WealthMetrics;
import com.finance.wealth_service.transaction.dto.CreateTransactionRequest;
import com.finance.wealth_service.transaction.dto.Leg;
import com.finance.wealth_service.transaction.dto.Movement;
import com.finance.wealth_service.transaction.entity.Transaction;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import com.finance.wealth_service.exception.ConflictException;
import com.finance.wealth_service.exception.InvalidRequestException;
import com.finance.wealth_service.exception.WealthException;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Guards the request, hands the movement to the {@link Ledger} and turns what
 * comes back into an HTTP outcome. The database transaction lives in the ledger,
 * so the translation below runs after it has already rolled back.
 */
@Service
public class CreateTransactionAction {
    private final Ledger ledger;
    private final BucketPort bucketPort;
    private final WealthMetrics metrics;

    public CreateTransactionAction(Ledger ledger, WealthMetrics metrics, BucketPort bucketPort) {
        this.ledger = ledger;
        this.bucketPort = bucketPort;
        this.metrics = metrics;
    }

    public Transaction execute(AuthenticatedUser user, CreateTransactionRequest request) {
        return record(() -> {
            Leg from = resolve(user, request.fromAccountId(), request.fromBucketId());
            Leg to   = resolve(user, request.toAccountId(),   request.toBucketId());
            return post(new Movement(user, from, to, request.transactionAmount(), request.category(),
                    request.occurredAt(), request.description(), null));
        });
    }

    public Transaction execute(Movement movement) {
        return record(() -> post(movement));
    }

    private Transaction post(Movement movement) {
        if (movement.from() == null && movement.to() == null)
            throw new InvalidRequestException("A movement needs an origin or a destination");
        if (Objects.equals(movement.from(), movement.to()))
            throw new InvalidRequestException("Origin and destination are the same");
        return ledger.post(movement);
    }

    /** Runs the whole attempt, validation included, so every outcome lands in the metrics. */
    private Transaction record(Supplier<Transaction> attempt) {
        long started = System.nanoTime();
        try {
            Transaction created = attempt.get();
            metrics.recordTransaction("created", System.nanoTime() - started);
            return created;
        } catch (PessimisticLockingFailureException exception) {
            metrics.recordTransaction("conflict", System.nanoTime() - started);
            throw new ConflictException(
                    "Account or bucket is busy; retry the transaction", exception);
        } catch (OptimisticLockingFailureException exception) {
            metrics.recordTransaction("conflict", System.nanoTime() - started);
            throw new ConflictException(
                    "Balance changed concurrently; retry the transaction", exception);
        } catch (WealthException exception) {
            metrics.recordTransaction("invalid", System.nanoTime() - started);
            throw exception;
        }
    }

    private Leg resolve(AuthenticatedUser user, UUID accountId, UUID bucketId) {
        if (accountId != null && bucketId != null)
            throw new InvalidRequestException("Give an account or a bucket, not both");
        if (bucketId != null) {
            Bucket bucket = bucketPort.findOwnedBy(bucketId, user.subject(), user.issuer())
                    .orElseThrow(() -> new InvalidRequestException("Bucket not found"));
            return new Leg(bucket.getAccountId(), bucketId);
        }
        return accountId == null ? null : new Leg(accountId, null);
    }

}
