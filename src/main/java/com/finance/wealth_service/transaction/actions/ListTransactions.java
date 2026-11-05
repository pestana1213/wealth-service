package com.finance.wealth_service.transaction.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;

import com.finance.wealth_service.account.port.AccountPort;
import com.finance.wealth_service.bucket.port.BucketPort;
import com.finance.wealth_service.transaction.dto.MovementAcc;
import com.finance.wealth_service.transaction.dto.MovementSource;
import com.finance.wealth_service.transaction.dto.TransactionDTO;
import com.finance.wealth_service.transaction.entity.Transaction;
import com.finance.wealth_service.transaction.entity.TransactionType;
import com.finance.wealth_service.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.finance.wealth_service.transaction.dto.MovementSource.ACCOUNT;
import static com.finance.wealth_service.transaction.dto.MovementSource.BUCKET;

@Service
public class ListTransactions {

    private static final Logger log = LoggerFactory.getLogger(ListTransactions.class);

    private static final String REMOVED_ACCOUNT = "Conta removida";
    private static final String REMOVED_BUCKET = "Finalidade removida";

    private final TransactionRepository transactionRepo;


    private final AccountPort accountPort;

    private final BucketPort bucketPort;

    public ListTransactions(TransactionRepository transactionRepo,
                            AccountPort accountPort,
                            BucketPort bucketPort) {
        this.transactionRepo = transactionRepo;
        this.accountPort = accountPort;
        this.bucketPort = bucketPort;
    }

    @Transactional(readOnly = true)
    public List<TransactionDTO> listTransactions(AuthenticatedUser user) {
        List<Transaction> transactions = transactionRepo.findHistory(user.subject(), user.issuer());
        Map<UUID, MovementAcc> accounts = accounts(transactions);
        Map<UUID, MovementAcc> buckets = buckets(transactions);

        return transactions.stream()
                .map(t -> toDto(t, accounts, buckets))
                .toList();
    }

    private TransactionDTO toDto(Transaction t, Map<UUID, MovementAcc> accounts, Map<UUID, MovementAcc> buckets) {
        boolean betweenBuckets = t.getType() != TransactionType.TRANSFER
                || !Objects.equals(t.getSourceBucketId(), t.getDestinationBucketId());
        return new TransactionDTO(
                t.getId(),
                endpoint(t.getSourceBucketId(), buckets, t.getProviderAccountId(), accounts, betweenBuckets),
                endpoint(t.getDestinationBucketId(), buckets, t.getDestinationAccountId(), accounts, betweenBuckets),
                t.getTransactionAmount(),
                t.getType(),
                t.getCategory(),
                t.getOccurredAt(),
                t.getDescription(),
                t.getReversalOf()
        );
    }

    private Map<UUID, MovementAcc> accounts(List<Transaction> transactions) {
        List<UUID> ids = transactions.stream()
                .flatMap(t -> Stream.of(t.getProviderAccountId(), t.getDestinationAccountId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return accountPort.getAccountsFromIds(ids).stream()
                .map(a -> new MovementAcc(a.getId(), a.getName(), ACCOUNT))
                .collect(Collectors.toMap(MovementAcc::id, Function.identity()));
    }

    private Map<UUID, MovementAcc> buckets(List<Transaction> transactions) {
        List<UUID> ids = transactions.stream()
                .flatMap(t -> Stream.of(t.getSourceBucketId(), t.getDestinationBucketId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return bucketPort.getBucketsFromIds(ids).stream()
                .map(b -> new MovementAcc(b.getId(), b.getName(), BUCKET))
                .collect(Collectors.toMap(MovementAcc::id, Function.identity()));
    }

    private MovementAcc endpoint(UUID bucketId, Map<UUID, MovementAcc> buckets,
                                 UUID accountId, Map<UUID, MovementAcc> accounts,
                                 boolean preferBucket) {
        if (preferBucket && bucketId != null) {
            return resolve(buckets, bucketId, BUCKET);
        }
        return accountId == null ? null : resolve(accounts, accountId, ACCOUNT);
    }

    private MovementAcc resolve(Map<UUID, MovementAcc> accs, UUID id, MovementSource kind) {
        MovementAcc acc = accs.get(id);
        if (acc != null) {
            return acc;
        }
        log.warn("Movement endpoint {} of kind {} no longer exists; rendering it as removed", id, kind);
        return new MovementAcc(id, kind == BUCKET ? REMOVED_BUCKET : REMOVED_ACCOUNT, kind);
    }
}
