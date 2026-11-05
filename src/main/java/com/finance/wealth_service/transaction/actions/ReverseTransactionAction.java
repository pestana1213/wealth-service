package com.finance.wealth_service.transaction.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;

import com.finance.wealth_service.transaction.dto.Leg;
import com.finance.wealth_service.transaction.dto.Movement;
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

    public ReverseTransactionAction(TransactionRepository transactions, CreateTransactionAction createTransaction) {
        this.transactions = transactions;
        this.createTransaction = createTransaction;
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
        return createTransaction.execute(mirrorOf(user, original));
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
