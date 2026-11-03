package com.finance.wealth_service.transaction.actions;

import com.finance.wealth_service.transaction.dto.CreateTransactionRequest;
import com.finance.wealth_service.transaction.entity.Transaction;
import com.finance.wealth_service.transaction.entity.TransactionType;
import com.finance.wealth_service.transaction.repository.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

    public Transaction execute(UUID transactionId) {
        Transaction original = transactions.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        if (original.getReversalOf() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A reversal cannot be reversed; record a new transaction instead");
        }
        if (transactions.existsByReversalOf(transactionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transaction was already reversed");
        }
        return createTransaction.execute(mirrorOf(original), transactionId);
    }

    /**
     * Swaps origin and destination on both the account and the bucket side, which
     * turns an income into an expense and a transfer into the opposite transfer.
     * The reversal is dated today: it is a correction made now, not a rewrite of
     * the original movement's date.
     */
    private CreateTransactionRequest mirrorOf(Transaction original) {
        TransactionType mirroredType = switch (original.getType()) {
            case INCOME -> TransactionType.EXPENSE;
            case EXPENSE -> TransactionType.INCOME;
            case TRANSFER -> TransactionType.TRANSFER;
        };
        return new CreateTransactionRequest(
                original.getUserId(),
                original.getDestinationAccountId(),
                original.getProviderAccountId(),
                original.getDestinationBucketId(),
                original.getSourceBucketId(),
                original.getTransactionAmount(),
                mirroredType,
                original.getCategory(),
                LocalDate.now(),
                "Reversal of " + original.getId());
    }
}
