package com.finance.wealth_service.transaction.actions;

import com.finance.wealth_service.account.entity.Account;
import com.finance.wealth_service.account.entity.User;
import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.account.repository.UserRepository;
import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.bucket.repository.BucketRepository;
import com.finance.wealth_service.transaction.dto.CreateTransactionRequest;
import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.support.TestUsers;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.finance.wealth_service.transaction.entity.Transaction;
import com.finance.wealth_service.transaction.entity.TransactionCategory;
import com.finance.wealth_service.transaction.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransactionActionIntegrationTests {
    @Autowired CreateTransactionAction action;
    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired BucketRepository buckets;
    @Autowired TransactionRepository transactions;
    @Autowired TransactionTemplate template;
    @Autowired EntityManager entityManager;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired tools.jackson.databind.ObjectMapper mapper;
    @org.springframework.beans.factory.annotation.Value("${local.server.port}") int port;
    // The HTTP tests go through the real security filter chain, so the decoder
    // stands in for Zitadel and turns the test token into the caller's identity.
    static final String TOKEN = "test-token";
    @MockitoBean JwtDecoder jwtDecoder;
    UUID userId;
    AuthenticatedUser caller;
    UUID accountId;
    UUID bucketId;
    UUID otherAccountId;
    UUID otherBucketId;
    UUID carBucketId;

    @BeforeEach
    void setup() {
        User user = users.saveAndFlush(TestUsers.newUser("Income test"));
        userId = user.getId();
        caller = TestUsers.identityOf(user);
        when(jwtDecoder.decode(TOKEN)).thenReturn(Jwt.withTokenValue(TOKEN).header("alg", "none")
                .issuer(caller.issuer()).subject(caller.subject()).build());
        accountId = accounts.saveAndFlush(new Account(userId, "Bank", new BigDecimal("100.00"))).getId();
        otherAccountId = accounts.saveAndFlush(new Account(userId, "Broker")).getId();
        // House and Car live in the bank, ETF in the broker.
        otherBucketId = buckets.saveAndFlush(new Bucket(userId, otherAccountId, "ETF")).getId();
        bucketId = buckets.saveAndFlush(new Bucket(userId, accountId, "House", new BigDecimal("20.00"))).getId();
        carBucketId = buckets.saveAndFlush(new Bucket(userId, accountId, "Car")).getId();
    }

    @AfterEach
    void cleanup() {
        template.executeWithoutResult(status -> {
            entityManager.createQuery("delete from Transaction t where t.userId = :userId").setParameter("userId", userId).executeUpdate();
            buckets.deleteById(otherBucketId);
            accounts.deleteById(otherAccountId);
            buckets.deleteById(carBucketId);
            buckets.deleteById(bucketId);
            accounts.deleteById(accountId);
            users.deleteById(userId);
        });
    }

    /** An income into the bank, or straight into one of its buckets. */
    CreateTransactionRequest request(UUID targetBucket, TransactionCategory category) {
        return targetBucket == null
                ? movement(null, null, accountId, null, category)
                : movement(null, null, null, targetBucket, category);
    }

    long transactionCount() {
        return template.execute(status -> entityManager.createQuery(
                "select count(t) from Transaction t where t.userId = :userId", Long.class)
                .setParameter("userId", userId).getSingleResult());
    }

    @Test
    void commitsAccountBucketAndHistoryTogether() {
        Transaction transaction = action.execute(caller, request(bucketId, TransactionCategory.HOUSE));
        assertThat(transactions.findById(transaction.getId())).isPresent();
        assertThat(accounts.findById(accountId).orElseThrow().getCurrentAmount()).isEqualByComparingTo("130.25");
        assertThat(buckets.findById(bucketId).orElseThrow().getCurrentAmount()).isEqualByComparingTo("50.25");
        assertThat(transactionCount()).isEqualTo(1);
    }

    @Test
    void incomeWithoutBucketOnlyChangesAccount() {
        action.execute(caller, request(null, TransactionCategory.OTHER));
        assertThat(accounts.findById(accountId).orElseThrow().getCurrentAmount()).isEqualByComparingTo("130.25");
        assertThat(buckets.findById(bucketId).orElseThrow().getCurrentAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    void failedHistoryInsertRollsBackBothBalances() {
        assertThatThrownBy(() -> action.execute(caller, request(bucketId, null))).isInstanceOf(RuntimeException.class);
        assertThat(accounts.findById(accountId).orElseThrow().getCurrentAmount()).isEqualByComparingTo("100.00");
        assertThat(buckets.findById(bucketId).orElseThrow().getCurrentAmount()).isEqualByComparingTo("20.00");
        assertThat(transactionCount()).isZero();
    }

    @Test
    void anotherCallerCannotMoveTheseAccounts() {
        assertThatThrownBy(() -> action.execute(TestUsers.stranger(), request(bucketId, TransactionCategory.HOUSE)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(accounts.findById(accountId).orElseThrow().getCurrentAmount()).isEqualByComparingTo("100.00");
        assertThat(buckets.findById(bucketId).orElseThrow().getCurrentAmount()).isEqualByComparingTo("20.00");
        assertThat(transactionCount()).isZero();
    }

    @Test
    void recordsTheTransactionUnderTheAccountOwner() {
        Transaction transaction = action.execute(caller, request(null, TransactionCategory.OTHER));
        assertThat(transaction.getUserId()).isEqualTo(userId);
    }

    @Test
    void foreignBucketDoesNotChangeBalance() {
        User other = users.saveAndFlush(TestUsers.newUser("Other"));
        Account foreignAccount = accounts.saveAndFlush(new Account(other.getId(), "Other"));
        Bucket foreign = buckets.saveAndFlush(new Bucket(other.getId(), foreignAccount.getId(), "Other"));
        try {
            assertThatThrownBy(() -> action.execute(caller, request(foreign.getId(), TransactionCategory.OTHER)))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            assertThat(accounts.findById(accountId).orElseThrow().getCurrentAmount()).isEqualByComparingTo("100.00");
            assertThat(transactionCount()).isZero();
        } finally {
            buckets.deleteById(foreign.getId());
            accounts.deleteById(foreignAccount.getId());
            users.deleteById(other.getId());
        }
    }

    @Test
    void rejectsStaleAccountAndBucketUpdates() {
        Account staleAccount = accounts.findById(accountId).orElseThrow();
        Bucket staleBucket = buckets.findById(bucketId).orElseThrow();
        action.execute(caller, request(bucketId, TransactionCategory.HOUSE));
        staleAccount.setCurrentAmount(new BigDecimal("999.00"));
        staleBucket.setCurrentAmount(new BigDecimal("999.00"));
        assertThatThrownBy(() -> accounts.saveAndFlush(staleAccount)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThatThrownBy(() -> buckets.saveAndFlush(staleBucket)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(accounts.findById(accountId).orElseThrow().getCurrentAmount()).isEqualByComparingTo("130.25");
        assertThat(buckets.findById(bucketId).orElseThrow().getCurrentAmount()).isEqualByComparingTo("50.25");
    }

    /** Each side is an account or a bucket, never both; the ledger derives the bucket's account. */
    CreateTransactionRequest movement(UUID fromAccount, UUID fromBucket, UUID toAccount, UUID toBucket,
                                      TransactionCategory category) {
        return new CreateTransactionRequest(fromAccount, fromBucket, toAccount, toBucket,
                new BigDecimal("30.25"), category, LocalDate.now(), "Movement test");
    }

    void assertBalances(String bank, String broker, String house, String etf) {
        assertThat(accounts.findById(accountId).orElseThrow().getCurrentAmount()).isEqualByComparingTo(bank);
        assertThat(accounts.findById(otherAccountId).orElseThrow().getCurrentAmount()).isEqualByComparingTo(broker);
        assertThat(buckets.findById(bucketId).orElseThrow().getCurrentAmount()).isEqualByComparingTo(house);
        assertThat(buckets.findById(otherBucketId).orElseThrow().getCurrentAmount()).isEqualByComparingTo(etf);
    }

    @Test
    void expenseDebitsBothTheAccountAndTheBucket() {
        action.execute(caller, request(bucketId, TransactionCategory.HOUSE));
        Transaction transaction = action.execute(caller, movement(null, bucketId, null, null,
                TransactionCategory.HOUSE));
        assertThat(transactions.findById(transaction.getId())).isPresent();
        assertBalances("100.00", "0", "20", "0");
        assertThat(transactionCount()).isEqualTo(2);
    }

    /** An account can be overdrawn: a credit card or a debit balance is a real account. */
    @Test
    void expenseDrivesTheAccountNegativeWithoutCheckingSufficientFunds() {
        for (int movements = 0; movements < 4; movements++) {
            action.execute(caller, movement(accountId, null, null, null, TransactionCategory.OTHER));
        }
        assertBalances("-21.00", "0", "20", "0");
        assertThat(transactionCount()).isEqualTo(4);
    }

    /** A bucket is a share of money that exists, so it cannot go below zero. */
    @Test
    void expenseRefusesToDriveTheBucketNegative() {
        assertThatThrownBy(() -> action.execute(caller, movement(null, bucketId, null, null,
                TransactionCategory.HOUSE)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertBalances("100", "0", "20", "0");
        assertThat(transactionCount()).isZero();
    }

    @Test
    void transferMovesMoneyBetweenAccountsAndBuckets() {
        action.execute(caller, request(bucketId, TransactionCategory.HOUSE));
        Transaction transaction = action.execute(caller, movement(null, bucketId, null, otherBucketId,
                TransactionCategory.INVESTMENT));
        assertThat(transactions.findById(transaction.getId())).isPresent();
        assertBalances("100.00", "30.25", "20", "30.25");
        assertThat(transactionCount()).isEqualTo(2);
    }

    @Test
    void transferRefusesToDriveTheSourceBucketNegative() {
        assertThatThrownBy(() -> action.execute(caller, movement(null, bucketId, null, otherBucketId,
                TransactionCategory.INVESTMENT)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertBalances("100", "0", "20", "0");
        assertThat(transactionCount()).isZero();
    }

    /** Money that is not allocated in one account can go straight into a bucket of another. */
    @Test
    void transferFromAnAccountIntoABucketOfAnotherAccount() {
        action.execute(caller, movement(accountId, null, null, otherBucketId, TransactionCategory.INVESTMENT));
        assertBalances("69.75", "30.25", "20", "30.25");
    }

    @Test
    void transferFromABucketIntoAnotherAccount() {
        action.execute(caller, request(bucketId, TransactionCategory.HOUSE));
        action.execute(caller, movement(null, bucketId, otherAccountId, null, TransactionCategory.OTHER));
        assertBalances("100.00", "30.25", "20", "0");
    }

    @Test
    void transferBetweenAccountsWithoutBuckets() {
        action.execute(caller, movement(accountId, null, otherAccountId, null, TransactionCategory.OTHER));
        assertBalances("69.75", "30.25", "20", "0");
    }

    @Test
    void allocationsReallocationsAndReleasesPreserveAccountBalance() {
        action.execute(caller, movement(accountId, null, null, bucketId, TransactionCategory.HOUSE));
        assertBalances("100", "0", "50.25", "0");
        action.execute(caller, movement(null, bucketId, null, carBucketId, TransactionCategory.CAR));
        assertBalances("100", "0", "20", "0");
        assertThat(buckets.findById(carBucketId).orElseThrow().getCurrentAmount()).isEqualByComparingTo("30.25");
        action.execute(caller, movement(null, carBucketId, accountId, null, TransactionCategory.OTHER));
        assertBalances("100", "0", "20", "0");
        assertThat(buckets.findById(carBucketId).orElseThrow().getCurrentAmount()).isEqualByComparingTo("0");
        assertThat(transactionCount()).isEqualTo(3);
    }

    @Test
    void expenseAndTransferRollBackWhenHistoryInsertFails() {
        assertThatThrownBy(() -> action.execute(caller, movement(null, bucketId, null, null, null)))
                .isInstanceOf(RuntimeException.class);
        assertBalances("100", "0", "20", "0");
        assertThatThrownBy(() -> action.execute(caller, movement(null, bucketId, null, otherBucketId, null)))
                .isInstanceOf(RuntimeException.class);
        assertBalances("100", "0", "20", "0");
        assertThat(transactionCount()).isZero();
    }

    @Test
    void rejectsForeignAndMissingReferencesOnEitherSide() {
        User other = users.saveAndFlush(TestUsers.newUser("Foreign"));
        Account foreignAccount = accounts.saveAndFlush(new Account(other.getId(), "Foreign"));
        Bucket foreignBucket = buckets.saveAndFlush(new Bucket(other.getId(), foreignAccount.getId(), "Foreign"));
        try {
            for (UUID invalid : new UUID[]{UUID.randomUUID(), foreignAccount.getId()}) {
                for (CreateTransactionRequest request : new CreateTransactionRequest[]{
                        movement(invalid, null, null, null, TransactionCategory.OTHER),
                        movement(invalid, null, otherAccountId, null, TransactionCategory.OTHER),
                        movement(accountId, null, invalid, null, TransactionCategory.OTHER)}) {
                    assertThatThrownBy(() -> action.execute(caller, request)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
                }
            }
            for (UUID invalid : new UUID[]{UUID.randomUUID(), foreignBucket.getId()}) {
                for (CreateTransactionRequest request : new CreateTransactionRequest[]{
                        movement(null, invalid, null, null, TransactionCategory.OTHER),
                        movement(null, invalid, null, otherBucketId, TransactionCategory.OTHER),
                        movement(null, bucketId, null, invalid, TransactionCategory.OTHER)}) {
                    assertThatThrownBy(() -> action.execute(caller, request)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
                }
            }
            assertBalances("100", "0", "20", "0");
            assertThat(transactionCount()).isZero();
        } finally {
            buckets.deleteById(foreignBucket.getId());
            accounts.deleteById(foreignAccount.getId());
            users.deleteById(other.getId());
        }
    }

    @Test
    void httpRequestsCommitBalancesToDatabase() throws Exception {
        try (var client = java.net.http.HttpClient.newHttpClient()) {
            record Step(CreateTransactionRequest request, String bank, String house) {}
            for (Step step : new Step[]{
                    new Step(request(bucketId, TransactionCategory.HOUSE), "130.25", "50.25"),
                    new Step(movement(accountId, null, null, null, TransactionCategory.HOUSE), "100.00", "50.25"),
                    new Step(movement(null, bucketId, null, otherBucketId, TransactionCategory.OTHER), "69.75", "20.00")}) {
                var response = client.send(java.net.http.HttpRequest.newBuilder(
                                java.net.URI.create("http://localhost:" + port + "/transactions"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + TOKEN)
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(step.request())))
                        .build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(201);
                // Read through JDBC after the HTTP response, bypassing the JPA persistence context.
                assertThat(jdbc.queryForObject("select current_amount from accounts where id = ?", BigDecimal.class, accountId))
                        .isEqualByComparingTo(step.bank());
                assertThat(jdbc.queryForObject("select current_amount from buckets where id = ?", BigDecimal.class, bucketId))
                        .isEqualByComparingTo(step.house());
            }
            assertThat(jdbc.queryForObject("select current_amount from accounts where id = ?", BigDecimal.class, otherAccountId))
                    .isEqualByComparingTo("30.25");
            assertThat(jdbc.queryForObject("select current_amount from buckets where id = ?", BigDecimal.class, otherBucketId))
                    .isEqualByComparingTo("30.25");
            assertThat(transactionCount()).isEqualTo(3);
        }
    }

    @Test
    void lockedAccountOrBucketReturnsConflictWithoutPartialUpdates() throws Exception {
        try (var client = java.net.http.HttpClient.newHttpClient()) {
            for (String table : new String[]{"accounts", "buckets"}) {
                UUID lockedId = table.equals("accounts") ? accountId : bucketId;
                try (var connection = java.util.Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
                    connection.setAutoCommit(false);
                    try (var statement = connection.prepareStatement("select id from " + table + " where id = ? for update")) {
                        statement.setObject(1, lockedId);
                        try (var rows = statement.executeQuery()) {
                            assertThat(rows.next()).isTrue();
                        }
                    }
                    var response = client.send(java.net.http.HttpRequest.newBuilder(
                                    java.net.URI.create("http://localhost:" + port + "/transactions"))
                            .timeout(java.time.Duration.ofSeconds(5))
                            .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + TOKEN)
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                                    mapper.writeValueAsString(request(bucketId, TransactionCategory.HOUSE))))
                            .build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                    assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(409);
                    assertBalances("100", "0", "20", "0");
                    assertThat(transactionCount()).isZero();
                    // A bucket lock failure must also release any account locks acquired earlier.
                    if (table.equals("buckets")) {
                        template.executeWithoutResult(status -> {
                            assertThat(accounts.findOwnedByForUpdate(accountId, caller.subject(), caller.issuer())).isPresent();
                        });
                    }
                    connection.rollback();
                }
            }
        }
        action.execute(caller, request(bucketId, TransactionCategory.HOUSE));
        assertBalances("130.25", "0", "50.25", "0");
        assertThat(transactionCount()).isEqualTo(1);
    }
}
