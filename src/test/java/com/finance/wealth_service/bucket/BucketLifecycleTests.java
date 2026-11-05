package com.finance.wealth_service.bucket;

import com.finance.wealth_service.support.TestUsers;
import static com.finance.wealth_service.support.TestUsers.signedInAs;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import com.finance.wealth_service.account.entity.Account;
import com.finance.wealth_service.account.entity.User;
import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.account.repository.UserRepository;
import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.bucket.repository.BucketRepository;
import com.finance.wealth_service.goal.entity.Goal;
import com.finance.wealth_service.goal.repository.GoalRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Correcting and retiring a bucket. Deleting a bucket archives it: the money it
 * held must have been moved out first, the dashboard stops showing it, new
 * transactions cannot use it, and the existing history keeps naming it.
 */
@SpringBootTest
@Transactional
class BucketLifecycleTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired BucketRepository buckets;
    @Autowired GoalRepository goals;
    MockMvc mvc;
    User user;
    UUID userId;
    UUID accountId;
    UUID bucketId;

    @BeforeEach
    void setup() {
        user = users.saveAndFlush(TestUsers.newUser("Lifecycle"));
        userId = user.getId();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .defaultRequest(get("/").with(signedInAs(user))).build();
        accountId = accounts.saveAndFlush(new Account(userId, "Bank", new BigDecimal("100.00"))).getId();
        bucketId = buckets.saveAndFlush(new Bucket(userId, "House", new BigDecimal("20.00"))).getId();
    }

    private UUID emptyBucket() {
        return buckets.saveAndFlush(new Bucket(userId, "Empty", BigDecimal.ZERO)).getId();
    }

    @Test
    void renamesBucketLeavingTheBalanceAlone() throws Exception {
        mvc.perform(patch("/buckets/" + bucketId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Casa  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Casa"))
                .andExpect(jsonPath("$.currentAmount").value(20.00));
    }

    @Test
    void correctsBucketBalanceLeavingTheNameAlone() throws Exception {
        mvc.perform(patch("/buckets/" + bucketId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentAmount\":27000.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("House"))
                .andExpect(jsonPath("$.currentAmount").value(27000.50));
        assertThat(buckets.findById(bucketId).orElseThrow().getCurrentAmount()).isEqualByComparingTo("27000.50");
    }

    @Test
    void rejectsInvalidBucketPatches() throws Exception {
        for (String body : new String[]{"{\"currentAmount\":-1}", "{\"currentAmount\":1.001}", "{\"name\":\"   \"}"}) {
            mvc.perform(patch("/buckets/" + bucketId).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(buckets.findById(bucketId).orElseThrow().getName()).isEqualTo("House");
    }

    @Test
    void returnsNotFoundWhenPatchingUnknownBucket() throws Exception {
        mvc.perform(patch("/buckets/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Casa\"}")).andExpect(status().isNotFound());
    }

    @Test
    void archivesEmptyBucketWithoutDeletingTheRow() throws Exception {
        UUID empty = emptyBucket();
        mvc.perform(delete("/buckets/" + empty)).andExpect(status().isNoContent());
        assertThat(buckets.findById(empty)).isPresent();
    }

    @Test
    void refusesToArchiveBucketThatStillHoldsMoney() throws Exception {
        mvc.perform(delete("/buckets/" + bucketId)).andExpect(status().isConflict());
    }

    @Test
    void returnsNotFoundWhenArchivingUnknownBucket() throws Exception {
        mvc.perform(delete("/buckets/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void archivedBucketLeavesTheDashboard() throws Exception {
        UUID empty = emptyBucket();
        mvc.perform(delete("/buckets/" + empty)).andExpect(status().isNoContent());
        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets[?(@.id=='" + empty + "')]").isEmpty())
                .andExpect(jsonPath("$.buckets[?(@.id=='" + bucketId + "')]").isNotEmpty())
                .andExpect(jsonPath("$.allocatedAmount").value(20.00));
    }

    @Test
    void archivedBucketTakesItsGoalsOffTheDashboard() throws Exception {
        UUID empty = emptyBucket();
        goals.saveAndFlush(new Goal(empty, new BigDecimal("500.00"), LocalDate.of(2027, 12, 31)));
        mvc.perform(delete("/buckets/" + empty)).andExpect(status().isNoContent());
        mvc.perform(get("/goals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.bucketId=='" + empty + "')]").isEmpty());
    }

    @Test
    void archivedBucketIsRefusedByNewTransactions() throws Exception {
        UUID empty = emptyBucket();
        mvc.perform(delete("/buckets/" + empty)).andExpect(status().isNoContent());
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationAccountId\":\"" + accountId + "\","
                                + "\"destinationBucketId\":\"" + empty + "\",\"type\":\"INCOME\","
                                + "\"category\":\"OTHER\",\"transactionAmount\":10.00,\"occurredAt\":\"2026-10-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void historyStillNamesAnArchivedBucket() throws Exception {
        String income = "{\"destinationAccountId\":\"" + accountId + "\","
                + "\"destinationBucketId\":\"" + bucketId + "\",\"type\":\"INCOME\","
                + "\"category\":\"HOUSE\",\"transactionAmount\":10.00,\"occurredAt\":\"2026-10-01\"}";
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(income))
                .andExpect(status().isCreated());
        mvc.perform(patch("/buckets/" + bucketId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentAmount\":0}")).andExpect(status().isOk());
        mvc.perform(delete("/buckets/" + bucketId)).andExpect(status().isNoContent());

        mvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].destination.name").value("House"));
    }
}
