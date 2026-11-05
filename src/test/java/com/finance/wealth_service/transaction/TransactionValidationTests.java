package com.finance.wealth_service.transaction;

import com.finance.wealth_service.support.TestUsers;
import static com.finance.wealth_service.support.TestUsers.signedInAs;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import com.finance.wealth_service.account.entity.Account;
import com.finance.wealth_service.account.entity.User;
import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.account.repository.UserRepository;
import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.bucket.repository.BucketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The request contract of {@code POST /transactions}: a malformed payload must be
 * rejected by request validation, before any balance is touched, and must name the
 * offending field. Each case is its own test so that a rejection reaching the
 * service layer cannot contaminate the next assertion's transaction.
 */
@SpringBootTest
@Transactional
class TransactionValidationTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired BucketRepository buckets;
    MockMvc mvc;
    User user;
    UUID userId;
    UUID accountId;

    @BeforeEach
    void setup() {
        user = users.saveAndFlush(TestUsers.newUser("Validation"));
        userId = user.getId();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .defaultRequest(get("/").with(signedInAs(user))).build();
        accountId = accounts.saveAndFlush(new Account(userId, "Bank", new BigDecimal("100.00"))).getId();
        buckets.saveAndFlush(new Bucket(userId, accountId, "House", new BigDecimal("20.00")));
    }

    /** A valid INCOME payload with the amount, date and description fields supplied by the caller. */
    private String income(String fields) {
        return "{\"toAccountId\":\"" + accountId + "\","
                + "\"category\":\"OTHER\"," + fields + "}";
    }

    private void expectFieldError(String body, String field) throws Exception {
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors." + field).exists());
    }

    @Test
    void rejectsMissingAmount() throws Exception {
        expectFieldError(income("\"occurredAt\":\"2026-10-01\""), "transactionAmount");
    }

    @Test
    void rejectsAmountWithMoreThanTwoDecimals() throws Exception {
        expectFieldError(income("\"transactionAmount\":10.555,\"occurredAt\":\"2026-10-01\""), "transactionAmount");
    }

    @Test
    void rejectsZeroAmount() throws Exception {
        expectFieldError(income("\"transactionAmount\":0,\"occurredAt\":\"2026-10-01\""), "transactionAmount");
    }

    @Test
    void rejectsNegativeAmount() throws Exception {
        expectFieldError(income("\"transactionAmount\":-10.00,\"occurredAt\":\"2026-10-01\""), "transactionAmount");
    }

    @Test
    void rejectsMissingOccurredAt() throws Exception {
        expectFieldError(income("\"transactionAmount\":10.00"), "occurredAt");
    }

    @Test
    void rejectsFutureOccurredAt() throws Exception {
        expectFieldError(income("\"transactionAmount\":10.00,\"occurredAt\":\""
                + LocalDate.now().plusDays(1) + "\""), "occurredAt");
    }

    @Test
    void rejectsMissingCategory() throws Exception {
        String body = "{\"toAccountId\":\"" + accountId + "\","
                + "\"transactionAmount\":10.00,\"occurredAt\":\"2026-10-01\"}";
        expectFieldError(body, "category");
    }

    @Test
    void rejectsDescriptionLongerThanTheColumn() throws Exception {
        expectFieldError(income("\"transactionAmount\":10.00,\"occurredAt\":\"2026-10-01\",\"description\":\""
                + "x".repeat(256) + "\""), "description");
    }

    @Test
    void leavesBalancesUntouchedWhenAmountIsInvalid() throws Exception {
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(income("\"occurredAt\":\"2026-10-01\"")))
                .andExpect(status().isBadRequest());
        assertThat(accounts.findById(accountId).orElseThrow().getCurrentAmount()).isEqualByComparingTo("100.00");
    }
}
