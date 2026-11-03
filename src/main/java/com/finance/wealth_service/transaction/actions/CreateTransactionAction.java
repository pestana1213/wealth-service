package com.finance.wealth_service.transaction.actions;

import com.finance.wealth_service.account.entity.Account;
import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.observability.WealthMetrics;
import com.finance.wealth_service.transaction.repository.TransactionRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import com.finance.wealth_service.transaction.dto.CreateTransactionRequest;
import com.finance.wealth_service.transaction.entity.Transaction;
import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.account.repository.UserRepository;
import com.finance.wealth_service.bucket.repository.BucketRepository;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CreateTransactionAction {
    private final UserRepository users;
    private final AccountRepository accounts;
    private final BucketRepository buckets;
    private final TransactionTemplate transactionTemplate;
    private final TransactionRepository transactions;
    private final WealthMetrics metrics;

    public CreateTransactionAction(UserRepository users, AccountRepository accounts, BucketRepository buckets, TransactionTemplate transactionTemplate, TransactionRepository transactions, WealthMetrics metrics) {
        this.users = users;
        this.accounts = accounts;
        this.buckets = buckets;
        this.transactionTemplate = transactionTemplate;
        this.transactions = transactions;
        this.metrics = metrics;
    }

    public Transaction execute(CreateTransactionRequest request) {
        return execute(request, null);
    }

    /**
     * @param reversalOf the transaction this one compensates, or {@code null} for an
     *                   ordinary movement. Not part of the request body: a client
     *                   cannot forge the link by posting to {@code /transactions}.
     */
    public Transaction execute(CreateTransactionRequest request, UUID reversalOf) {
        long started = System.nanoTime();
        if (!validate(request)) {
            metrics.recordTransaction("invalid", System.nanoTime() - started);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid transaction origin, destination or ownership");
        }

        try {
            Transaction created = transactionTemplate.execute(_ -> {
                Map<UUID, Account> lockedAccounts = new HashMap<>();
                Stream.of(request.providerAccountId(), request.destinationAccountId())
                        .filter(Objects::nonNull).distinct().sorted()
                        .forEach(id -> lockedAccounts.put(id, findAccount(id, request.userId())));
                Map<UUID, Bucket> lockedBuckets = new HashMap<>();
                Stream.of(request.sourceBucketId(), request.destinationBucketId())
                        .filter(Objects::nonNull).distinct().sorted()
                        .forEach(id -> lockedBuckets.put(id, findBucket(id, request.userId())));

                Account sourceAccount = lockedAccounts.get(request.providerAccountId());
                Account destinationAccount = lockedAccounts.get(request.destinationAccountId());
                Bucket sourceBucket = lockedBuckets.get(request.sourceBucketId());
                Bucket destinationBucket = lockedBuckets.get(request.destinationBucketId());

                if (!Objects.equals(request.providerAccountId(), request.destinationAccountId())) {
                    if (sourceAccount != null) {
                        BigDecimal finalValue = sourceAccount.getCurrentAmount().subtract(request.transactionAmount());
                        if (finalValue.compareTo(BigDecimal.ZERO) < 0)
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Final Value from source account can't be less then 0");

                        sourceAccount.setCurrentAmount(sourceAccount.getCurrentAmount().subtract(request.transactionAmount()));
                    }
                    if (destinationAccount != null) {
                        destinationAccount.setCurrentAmount(destinationAccount.getCurrentAmount().add(request.transactionAmount()));
                    }
                }
                if (!Objects.equals(request.sourceBucketId(), request.destinationBucketId())) {
                    if (sourceBucket != null) {
                        BigDecimal finalValue = sourceBucket.getCurrentAmount().subtract(request.transactionAmount());
                        if (finalValue.compareTo(BigDecimal.ZERO) < 0)
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Final Value from source bucket can't be less then 0");

                        sourceBucket.setCurrentAmount(sourceBucket.getCurrentAmount().subtract(request.transactionAmount()));
                    }
                    if (destinationBucket != null) {
                        destinationBucket.setCurrentAmount(destinationBucket.getCurrentAmount().add(request.transactionAmount()));
                    }
                }

                return transactions.saveAndFlush(new Transaction(
                        request.userId(), request.providerAccountId(), request.destinationAccountId(),
                        request.sourceBucketId(), request.destinationBucketId(), request.transactionAmount(),
                        request.type(), request.category(), request.occurredAt(), request.description(), reversalOf));
            });
            metrics.recordTransaction("created", System.nanoTime() - started);
            return created;
        } catch (PessimisticLockingFailureException exception) {
            metrics.recordTransaction("conflict", System.nanoTime() - started);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Account or bucket is busy; retry the transaction", exception);
        } catch (OptimisticLockingFailureException exception) {
            metrics.recordTransaction("conflict", System.nanoTime() - started);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Balance changed concurrently; retry the transaction", exception);
        } catch (ResponseStatusException exception) {
            metrics.recordTransaction("invalid", System.nanoTime() - started);
            throw exception;
        }
    }

    private Account findAccount(UUID accountId, UUID userId) {
        if (accountId == null) {
            return null;
        }
        return accounts.findByIdAndUserIdForUpdate(accountId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account not found"));
    }

    private Bucket findBucket(UUID bucketId, UUID userId) {
        if (bucketId == null) {
            return null;
        }
        return buckets.findByIdAndUserIdForUpdate(bucketId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bucket not found"));
    }

    private boolean validate(CreateTransactionRequest request) {
        if (request == null || request.userId() == null || request.type() == null) {
            return false;
        }

        boolean validRoute = switch (request.type()) {
            case INCOME -> request.providerAccountId() == null
                    && request.sourceBucketId() == null
                    && request.destinationAccountId() != null;
            case EXPENSE -> request.providerAccountId() != null
                    && request.destinationAccountId() == null
                    && request.destinationBucketId() == null;
            case TRANSFER -> request.providerAccountId() != null
                    && request.destinationAccountId() != null
                    && (!request.providerAccountId().equals(request.destinationAccountId())
                        || !Objects.equals(request.sourceBucketId(), request.destinationBucketId()));
        };

        return validRoute
                && users.existsById(request.userId());
    }

}
