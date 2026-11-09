package com.finance.wealth_service.transaction.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.bucket.port.BucketPort;

import com.finance.wealth_service.common.Leg;
import com.finance.wealth_service.common.Movement;
import com.finance.wealth_service.transaction.entity.Transaction;
import com.finance.wealth_service.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import com.finance.wealth_service.exception.ConflictException;
import com.finance.wealth_service.exception.NotFoundException;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Undoes a transaction by recording its mirror image rather than deleting it, so
 * the history shows both the mistake and the correction. The compensating
 * movement goes through {@link CreateTransactionAction}, which keeps the locking,
 * the ownership checks and the balance arithmetic in one place.
 */
@Service
public class ReverseTransactionAction {
    private final TransactionRepository transactions;
    private final CreateTransactionAction createTransaction;
    private final BucketPort buckets;

    public ReverseTransactionAction(TransactionRepository transactions, CreateTransactionAction createTransaction,
                                    BucketPort buckets) {
        this.transactions = transactions;
        this.createTransaction = createTransaction;
        this.buckets = buckets;
    }

    public Transaction execute(AuthenticatedUser user, UUID transactionId) {
        Transaction original = transactions.findOwnedBy(transactionId, user.subject(), user.issuer())
                .orElseThrow(() -> new NotFoundException("Transaction not found"));
        if (original.getReversalOf() != null) {
            throw new ConflictException(
                    "A reversal cannot be reversed; record a new transaction instead");
        }
        if (transactions.existsByReversalOf(transactionId)) {
            throw new ConflictException("Transaction was already reversed");
        }
        requireBucketUnchanged(user, original.getSourceBucketId(), original.getProviderAccountId());
        requireBucketUnchanged(user, original.getDestinationBucketId(), original.getDestinationAccountId());
        return createTransaction.execute(mirrorOf(user, original));
    }

    /**
     * The mirror must touch the same account and bucket as the original. A bucket
     * archived or moved to another account since then makes that impossible for
     * good, so it is refused here with a reason, rather than by the ledger's
     * concurrency check, which would tell the caller to retry.
     */
    private void requireBucketUnchanged(AuthenticatedUser user, UUID bucketId, UUID accountId) {
        if (bucketId == null) {
            return;
        }
        Bucket bucket = buckets.findOwnedBy(bucketId, user.subject(), user.issuer())
                .orElseThrow(() -> new ConflictException("A bucket in this transaction no longer exists"));
        if (bucket.isArchived()) {
            throw new ConflictException("A bucket in this transaction is archived, so it can no longer be reversed");
        }
        if (!bucket.getAccountId().equals(accountId)) {
            throw new ConflictException(
                    "A bucket in this transaction has moved to another account, so it can no longer be reversed");
        }
    }

    private static Movement mirrorOf(AuthenticatedUser user, Transaction original) {
        return new Movement(user,
                leg(original.getDestinationAccountId(), original.getDestinationBucketId()),
                leg(original.getProviderAccountId(), original.getSourceBucketId()),
                original.getTransactionAmount(),
                original.getCategory(),
                LocalDate.now(),
                "Reversal of " + original.getId(),
                original.getId());
    }

    private static Leg leg(UUID accountId, UUID bucketId) {
        return accountId == null ? null : new Leg(accountId, bucketId);
    }
}
