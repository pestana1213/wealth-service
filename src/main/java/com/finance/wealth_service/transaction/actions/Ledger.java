package com.finance.wealth_service.transaction.actions;

import com.finance.wealth_service.account.entity.Account;
import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.bucket.repository.BucketRepository;
import com.finance.wealth_service.transaction.dto.CreateTransactionRequest;
import com.finance.wealth_service.transaction.entity.Transaction;
import com.finance.wealth_service.transaction.repository.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The only place that moves money. Everything here runs in one database
 * transaction: the balances it touches and the history row it writes commit
 * together or not at all.
 *
 * <p>An account may be overdrawn: a debit balance or a credit card is a real
 * account state, so an expense is allowed to take one below zero. A bucket may
 * not, because it is a share of money that exists in the accounts; a movement
 * that would take one below zero is refused with a 400.
 *
 * <p>Every balance change is written with an explicit {@code saveAndFlush}, so the
 * {@code UPDATE} is issued at the line that changes the balance instead of being
 * left to dirty checking at commit. Flushing is not committing: a later failure
 * still rolls the whole movement back.
 *
 * <p>Rows are locked with {@code FOR UPDATE NOWAIT}, accounts before buckets and
 * each group in UUID order, so two concurrent movements cannot hold the locks the
 * other needs. A row already locked fails the movement immediately instead of
 * queueing behind it; the caller turns that into a 409.
 */
@Service
public class Ledger {
    private final AccountRepository accounts;
    private final BucketRepository buckets;
    private final TransactionRepository transactions;
    private final TransactionTemplate transactionTemplate;

    public Ledger(AccountRepository accounts, BucketRepository buckets,
                  TransactionRepository transactions, TransactionTemplate transactionTemplate) {
        this.accounts = accounts;
        this.buckets = buckets;
        this.transactions = transactions;
        this.transactionTemplate = transactionTemplate;
    }

    public Transaction post(NewTransaction movement) {
        CreateTransactionRequest request = movement.request();
        return transactionTemplate.execute(_ -> {
            Map<UUID, Account> lockedAccounts = lockAccounts(request);
            Map<UUID, Bucket> lockedBuckets = lockBuckets(request);
            applyToAccounts(request, lockedAccounts);
            applyToBuckets(request, lockedBuckets);
            return transactions.saveAndFlush(new Transaction(
                    request.userId(), request.providerAccountId(), request.destinationAccountId(),
                    request.sourceBucketId(), request.destinationBucketId(), request.transactionAmount(),
                    request.type(), request.category(), request.occurredAt(), request.description(),
                    movement.reversalOf()));
        });
    }

    private Map<UUID, Account> lockAccounts(CreateTransactionRequest request) {
        Map<UUID, Account> locked = new HashMap<>();
        Stream.of(request.providerAccountId(), request.destinationAccountId())
                .filter(Objects::nonNull).distinct().sorted()
                .forEach(id -> locked.put(id, accounts.findByIdAndUserIdForUpdate(id, request.userId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account not found"))));
        return locked;
    }

    private Map<UUID, Bucket> lockBuckets(CreateTransactionRequest request) {
        Map<UUID, Bucket> locked = new HashMap<>();
        Stream.of(request.sourceBucketId(), request.destinationBucketId())
                .filter(Objects::nonNull).distinct().sorted()
                .forEach(id -> locked.put(id, buckets.findByIdAndUserIdForUpdate(id, request.userId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bucket not found"))));
        return locked;
    }

    private void applyToAccounts(CreateTransactionRequest request, Map<UUID, Account> locked) {
        if (Objects.equals(request.providerAccountId(), request.destinationAccountId())) {
            return;
        }
        Account source = locked.get(request.providerAccountId());
        Account destination = locked.get(request.destinationAccountId());
        if (source != null) {
            source.setCurrentAmount(source.getCurrentAmount().subtract(request.transactionAmount()));
            accounts.saveAndFlush(source);
        }
        if (destination != null) {
            destination.setCurrentAmount(destination.getCurrentAmount().add(request.transactionAmount()));
            accounts.saveAndFlush(destination);
        }
    }

    private void applyToBuckets(CreateTransactionRequest request, Map<UUID, Bucket> locked) {
        if (Objects.equals(request.sourceBucketId(), request.destinationBucketId())) {
            return;
        }
        Bucket source = locked.get(request.sourceBucketId());
        Bucket destination = locked.get(request.destinationBucketId());
        if (source != null) {
            BigDecimal left = source.getCurrentAmount().subtract(request.transactionAmount());
            if (left.compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Final Value from source bucket can't be less then 0");
            }
            source.setCurrentAmount(left);
            buckets.saveAndFlush(source);
        }
        if (destination != null) {
            destination.setCurrentAmount(destination.getCurrentAmount().add(request.transactionAmount()));
            buckets.saveAndFlush(destination);
        }
    }

}
