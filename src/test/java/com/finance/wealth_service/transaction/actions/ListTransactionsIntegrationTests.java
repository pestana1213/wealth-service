package com.finance.wealth_service.transaction.actions;

import com.finance.wealth_service.account.entity.Account;
import com.finance.wealth_service.account.entity.User;
import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.account.repository.UserRepository;
import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.bucket.repository.BucketRepository;
import com.finance.wealth_service.transaction.dto.MovementAcc;
import com.finance.wealth_service.transaction.dto.TransactionDTO;
import com.finance.wealth_service.transaction.dto.CreateTransactionRequest;import com.finance.wealth_service.transaction.entity.TransactionCategory;
import com.finance.wealth_service.transaction.entity.TransactionType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static com.finance.wealth_service.transaction.dto.MovementSource.ACCOUNT;
import static com.finance.wealth_service.transaction.dto.MovementSource.BUCKET;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ListTransactionsIntegrationTests {
    @Autowired ListTransactions listTransactions;
    @Autowired CreateTransactionAction createTransaction;
    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired BucketRepository buckets;
    @Autowired TransactionTemplate template;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    UUID userId;
    UUID accountId;
    UUID otherAccountId;
    UUID bucketId;
    UUID otherBucketId;

    @BeforeEach
    void setup() {
        userId = users.saveAndFlush(new User(null, null, "History", UUID.randomUUID() + "@example.com")).getId();
        accountId = accounts.saveAndFlush(new Account(userId, "Moey", new BigDecimal("1000.00"))).getId();
        otherAccountId = accounts.saveAndFlush(new Account(userId, "XTB", new BigDecimal("1000.00"))).getId();
        bucketId = buckets.saveAndFlush(new Bucket(userId, "Casa", new BigDecimal("100.00"))).getId();
        otherBucketId = buckets.saveAndFlush(new Bucket(userId, "Carro", new BigDecimal("100.00"))).getId();
    }

    @AfterEach
    void cleanup() {
        template.executeWithoutResult(status -> {
            entityManager.createQuery("delete from Transaction t where t.userId = :userId")
                    .setParameter("userId", userId).executeUpdate();
            buckets.deleteById(otherBucketId);
            buckets.deleteById(bucketId);
            accounts.deleteById(otherAccountId);
            accounts.deleteById(accountId);
            users.deleteById(userId);
        });
    }

    CreateTransactionRequest request(TransactionType type, UUID sourceAccount, UUID destinationAccount,
                                     UUID sourceBucket, UUID destinationBucket) {
        return new CreateTransactionRequest(userId, sourceAccount, destinationAccount, sourceBucket, destinationBucket,
                new BigDecimal("10.00"), type, TransactionCategory.OTHER, LocalDate.now(), "History test");
    }

    void setCreatedAt(UUID transactionId, Instant createdAt) {
        jdbc.update("update transactions set created_at = ? where id = ?",
                java.sql.Timestamp.from(createdAt), transactionId);
    }

    @Test
    void namesTheBucketsWhenATransferStaysInsideOneAccount() {
        createTransaction.execute(request(TransactionType.TRANSFER, accountId, accountId, bucketId, otherBucketId));

        List<TransactionDTO> history = listTransactions.listTransactions(userId);

        assertThat(history).hasSize(1);
        MovementAcc from = history.getFirst().source();
        MovementAcc to = history.getFirst().destination();
        assertThat(from.name()).isEqualTo("Casa");
        assertThat(from.type()).isEqualTo(BUCKET);
        assertThat(to.name()).isEqualTo("Carro");
        assertThat(to.type()).isEqualTo(BUCKET);
        assertThat(from.id()).isNotEqualTo(to.id());
    }

    @Test
    void namesTheAccountsWhenMoneyMovesBetweenThem() {
        createTransaction.execute(request(TransactionType.TRANSFER, accountId, otherAccountId, null, null));

        List<TransactionDTO> history = listTransactions.listTransactions(userId);

        assertThat(history).hasSize(1);
        assertThat(history.getFirst().source().name()).isEqualTo("Moey");
        assertThat(history.getFirst().source().type()).isEqualTo(ACCOUNT);
        assertThat(history.getFirst().destination().name()).isEqualTo("XTB");
    }

    @Test
    void namesTheAccountsWhenBothSidesShareTheSameBucket() {
        createTransaction.execute(
                request(TransactionType.TRANSFER, accountId, otherAccountId, bucketId, bucketId));

        List<TransactionDTO> history = listTransactions.listTransactions(userId);

        assertThat(history).hasSize(1);
        assertThat(history.getFirst().source().name()).isEqualTo("Moey");
        assertThat(history.getFirst().source().type()).isEqualTo(ACCOUNT);
        assertThat(history.getFirst().destination().name()).isEqualTo("XTB");
        assertThat(history.getFirst().destination().type()).isEqualTo(ACCOUNT);
    }

    @Test
    void namesTheAccountAndTheBucketWhenMoneyIsAllocated() {
        createTransaction.execute(
                request(TransactionType.TRANSFER, accountId, accountId, null, otherBucketId));

        List<TransactionDTO> history = listTransactions.listTransactions(userId);

        assertThat(history).hasSize(1);
        assertThat(history.getFirst().source().name()).isEqualTo("Moey");
        assertThat(history.getFirst().source().type()).isEqualTo(ACCOUNT);
        assertThat(history.getFirst().destination().name()).isEqualTo("Carro");
        assertThat(history.getFirst().destination().type()).isEqualTo(BUCKET);
    }

    @Test
    void ordersSameDayMovementsNewestInsertedFirst() {
        var older = createTransaction.execute(request(TransactionType.EXPENSE, accountId, null, null, null));
        var newer = createTransaction.execute(request(TransactionType.EXPENSE, accountId, null, null, null));
        setCreatedAt(older.getId(), Instant.now().minusSeconds(3600));
        setCreatedAt(newer.getId(), Instant.now());

        List<TransactionDTO> history = listTransactions.listTransactions(userId);

        assertThat(history).extracting(TransactionDTO::transactionId)
                .containsExactly(newer.getId(), older.getId());
    }

    @Test
    void ordersByMovementDateBeforeInsertionOrder() {
        var today = createTransaction.execute(request(TransactionType.EXPENSE, accountId, null, null, null));
        var yesterday = createTransaction.execute(new CreateTransactionRequest(userId, accountId, null, null, null,
                new BigDecimal("10.00"), TransactionType.EXPENSE, TransactionCategory.OTHER,
                LocalDate.now().minusDays(1), "Yesterday"));
        setCreatedAt(yesterday.getId(), Instant.now());

        List<TransactionDTO> history = listTransactions.listTransactions(userId);

        assertThat(history).extracting(TransactionDTO::transactionId)
                .containsExactly(today.getId(), yesterday.getId());
    }

    @Test
    void rejectsUnknownUserWithNotFound() {
        assertThatThrownBy(() -> listTransactions.listTransactions(UUID.randomUUID()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void leavesIncomeWithoutSourceNull() {
        createTransaction.execute(request(TransactionType.INCOME, null, accountId, null, null));

        List<TransactionDTO> history = listTransactions.listTransactions(userId);

        assertThat(history).hasSize(1);
        assertThat(history.getFirst().source()).isNull();
        assertThat(history.getFirst().destination().name()).isEqualTo("Moey");
    }
}
