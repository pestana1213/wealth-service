package com.finance.wealth_service.transaction.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;

import com.finance.wealth_service.account.entity.Account;
import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.bucket.repository.BucketRepository;
import com.finance.wealth_service.common.Leg;
import com.finance.wealth_service.common.Movement;
import com.finance.wealth_service.transaction.entity.Transaction;
import com.finance.wealth_service.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import com.finance.wealth_service.exception.ConflictException;
import com.finance.wealth_service.exception.InvalidRequestException;

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

    public Transaction post(Movement movement) {
        return transactionTemplate.execute(_ -> {
            Map<UUID, Account> lockedAccounts = lockAccounts(movement);
            Map<UUID, Bucket> lockedBuckets = lockBuckets(movement);
            requireBucketInAccount(movement.from(), lockedBuckets);
            requireBucketInAccount(movement.to(), lockedBuckets);
            applyToAccounts(movement, lockedAccounts);
            applyToBuckets(movement, lockedBuckets);
            Transaction transaction = new Transaction(
                    ownerOf(lockedAccounts),
                    accountOf(movement.from()), accountOf(movement.to()),
                    bucketOf(movement.from()),  bucketOf(movement.to()),
                    movement.amount(), movement.type(), movement.category(),
                    movement.occurredAt(), movement.description(), movement.reversalOf());
            if (movement.occurrence() != null) {
                transaction.linkToRecurring(movement.occurrence().recurringId(), movement.occurrence().on());
            }
            return transactions.saveAndFlush(transaction);
        });
    }

    private static UUID ownerOf(Map<UUID, Account> lockedAccounts) {
        return lockedAccounts.values().stream().findFirst()
                .map(Account::getUserId)
                .orElseThrow(() -> new InvalidRequestException("A movement needs an account"));
    }

    private Map<UUID, Account> lockAccounts(Movement movement) {
        AuthenticatedUser owner = movement.owner();
        Map<UUID, Account> locked = new HashMap<>();
        Stream.of(accountOf(movement.from()), accountOf(movement.to()))
                .filter(Objects::nonNull).distinct().sorted()
                .forEach(id -> locked.put(id, accounts.findOwnedByForUpdate(id, owner.subject(), owner.issuer())
                        .orElseThrow(() -> new InvalidRequestException("Account not found"))));
        return locked;
    }

    private Map<UUID, Bucket> lockBuckets(Movement movement) {
        AuthenticatedUser owner = movement.owner();
        Map<UUID, Bucket> locked = new HashMap<>();
        Stream.of(bucketOf( movement.from()), bucketOf(movement.to()))
                .filter(Objects::nonNull).distinct().sorted()
                .forEach(id -> locked.put(id, buckets.findOwnedByForUpdate(id, owner.subject(), owner.issuer())
                        .orElseThrow(() -> new InvalidRequestException("Bucket not found"))));
        return locked;
    }

    private void applyToAccounts(Movement movement, Map<UUID, Account> locked) {
        UUID sourceId = accountOf(movement.from());
        UUID destinationId = accountOf(movement.to());
        if (Objects.equals(sourceId, destinationId)) {
            return;
        }
        Account source = locked.get(sourceId);
        Account destination = locked.get(destinationId);
        if (source != null) {
            source.setCurrentAmount(source.getCurrentAmount().subtract(movement.amount()));
            accounts.saveAndFlush(source);
        }
        if (destination != null) {
            destination.setCurrentAmount(destination.getCurrentAmount().add(movement.amount()));
            accounts.saveAndFlush(destination);
        }
    }

    private void applyToBuckets(Movement movement, Map<UUID, Bucket> locked) {
        UUID sourceId = bucketOf(movement.from());
        UUID destinationId = bucketOf(movement.to());
        if (Objects.equals(sourceId, destinationId)) {
            return;
        }
        Bucket source = locked.get(sourceId);
        Bucket destination = locked.get(destinationId);
        if (source != null) {
            BigDecimal left = source.getCurrentAmount().subtract(movement.amount());
            if (left.compareTo(BigDecimal.ZERO) < 0) {
                throw new InvalidRequestException(
                        "Final Value from source bucket can't be less than 0");
            }
            source.setCurrentAmount(left);
            buckets.saveAndFlush(source);
        }
        if (destination != null) {
            destination.setCurrentAmount(destination.getCurrentAmount().add(movement.amount()));
            buckets.saveAndFlush(destination);
        }
    }

    private static UUID accountOf(Leg leg) { return leg == null ? null : leg.accountId(); }

    private static UUID bucketOf(Leg leg)  { return leg == null ? null : leg.bucketId(); }

    private static void requireBucketInAccount(Leg leg, Map<UUID, Bucket> locked) {
        if (leg == null || leg.bucketId() == null) return;
        Bucket bucket = locked.get(leg.bucketId());
        if (!bucket.getAccountId().equals(leg.accountId())) {
            throw new ConflictException(
                    "Bucket is no longer in that account; retry");
        }
    }

}
