package com.finance.wealth_service.auth;

import com.finance.wealth_service.account.AccountType;
import com.finance.wealth_service.account.entity.Account;
import com.finance.wealth_service.account.entity.User;
import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.account.repository.UserRepository;
import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.bucket.repository.BucketRepository;
import com.finance.wealth_service.goal.entity.Goal;
import com.finance.wealth_service.goal.repository.GoalRepository;
import com.finance.wealth_service.support.TestUsers;
import com.finance.wealth_service.transaction.entity.Transaction;
import com.finance.wealth_service.transaction.entity.TransactionCategory;
import com.finance.wealth_service.transaction.entity.TransactionType;
import com.finance.wealth_service.transaction.repository.TransactionRepository;
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

import static com.finance.wealth_service.support.TestUsers.signedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Every endpoint that takes an id must treat another user's resource as if it did
 * not exist: 404, nothing changed. The intruder is signed in for every request.
 */
@SpringBootTest
@Transactional
class OwnershipTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired BucketRepository buckets;
    @Autowired GoalRepository goals;
    @Autowired TransactionRepository transactions;
    MockMvc mvc;
    Account account;
    Bucket bucket;
    Goal goal;
    Transaction transaction;

    @BeforeEach
    void setup() {
        User owner = users.saveAndFlush(TestUsers.newUser("Owner"));
        User intruder = users.saveAndFlush(TestUsers.newUser("Intruder"));
        account = accounts.saveAndFlush(new Account(owner.getId(), "Broker", new BigDecimal("100.00"), AccountType.INVESTMENT));
        bucket = buckets.saveAndFlush(new Bucket(owner.getId(), "House", BigDecimal.ZERO));
        goal = goals.saveAndFlush(new Goal(bucket.getId(), new BigDecimal("1000.00"), null));
        transaction = transactions.saveAndFlush(new Transaction(owner.getId(), null, account.getId(), null, null,
                new BigDecimal("10.00"), TransactionType.INCOME, TransactionCategory.OTHER, LocalDate.now(), "Salary"));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .defaultRequest(get("/").with(signedInAs(intruder))).build();
    }

    @Test
    void cannotTouchAnotherUsersBucket() throws Exception {
        mvc.perform(patch("/buckets/" + bucket.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mine\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/buckets/" + bucket.getId())).andExpect(status().isNotFound());
        Bucket unchanged = buckets.findById(bucket.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("House");
        assertThat(unchanged.isArchived()).isFalse();
    }

    @Test
    void cannotTouchAnotherUsersGoal() throws Exception {
        mvc.perform(patch("/goals/" + goal.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalAmount\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/goals/" + goal.getId())).andExpect(status().isNotFound());
        assertThat(goals.findById(goal.getId()).orElseThrow().getGoalAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void cannotSeeOrValueAnotherUsersAccount() throws Exception {
        mvc.perform(get("/accounts/" + account.getId() + "/valuations")).andExpect(status().isNotFound());
        mvc.perform(post("/accounts/" + account.getId() + "/valuations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":1,\"valuedAt\":\"" + LocalDate.now() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotReverseAnotherUsersTransaction() throws Exception {
        long before = transactions.count();
        mvc.perform(post("/transactions/" + transaction.getId() + "/reversal")).andExpect(status().isNotFound());
        assertThat(transactions.count()).isEqualTo(before);
    }

    @Test
    void seesOnlyTheirOwnDataInListings() throws Exception {
        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts", hasSize(0)))
                .andExpect(jsonPath("$.buckets", hasSize(0)))
                .andExpect(jsonPath("$.goals", hasSize(0)));
        mvc.perform(get("/transactions")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }
}
