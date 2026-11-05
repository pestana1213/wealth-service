package com.finance.wealth_service.goal;

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
 * Revising and dropping a goal. Nothing references a goal, so deleting one
 * removes the row.
 */
@SpringBootTest
@Transactional
class GoalLifecycleTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired BucketRepository buckets;
    @Autowired GoalRepository goals;
    MockMvc mvc;
    User user;
    UUID userId;
    UUID goalId;

    @BeforeEach
    void setup() {
        user = users.saveAndFlush(TestUsers.newUser("Goals"));
        userId = user.getId();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .defaultRequest(get("/").with(signedInAs(user))).build();
        UUID accountId = accounts.saveAndFlush(new Account(userId, "Bank", new BigDecimal("100.00"))).getId();
        UUID bucketId = buckets.saveAndFlush(new Bucket(userId, accountId, "House", new BigDecimal("20.00"))).getId();
        goalId = goals.saveAndFlush(new Goal(bucketId, new BigDecimal("50000.00"), LocalDate.of(2027, 12, 31))).getId();
    }

    @Test
    void revisesGoalAmountLeavingTheDateAlone() throws Exception {
        mvc.perform(patch("/goals/" + goalId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalAmount\":60000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalAmount").value(60000.00))
                .andExpect(jsonPath("$.goalObjective").value("2027-12-31"));
        assertThat(goals.findById(goalId).orElseThrow().getGoalAmount()).isEqualByComparingTo("60000.00");
    }

    @Test
    void revisesGoalDateLeavingTheAmountAlone() throws Exception {
        mvc.perform(patch("/goals/" + goalId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalObjective\":\"2030-01-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalAmount").value(50000.00))
                .andExpect(jsonPath("$.goalObjective").value("2030-01-01"));
    }

    @Test
    void rejectsInvalidGoalPatches() throws Exception {
        for (String body : new String[]{"{\"goalAmount\":0}", "{\"goalAmount\":-5}", "{\"goalAmount\":1.001}"}) {
            mvc.perform(patch("/goals/" + goalId).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(goals.findById(goalId).orElseThrow().getGoalAmount()).isEqualByComparingTo("50000.00");
    }

    @Test
    void returnsNotFoundWhenPatchingUnknownGoal() throws Exception {
        mvc.perform(patch("/goals/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"goalAmount\":10.00}")).andExpect(status().isNotFound());
    }

    @Test
    void deletesGoalRow() throws Exception {
        mvc.perform(delete("/goals/" + goalId)).andExpect(status().isNoContent());
        assertThat(goals.findById(goalId)).isEmpty();
    }

    @Test
    void returnsNotFoundWhenDeletingUnknownGoal() throws Exception {
        mvc.perform(delete("/goals/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void deletedGoalLeavesTheDashboard() throws Exception {
        mvc.perform(delete("/goals/" + goalId)).andExpect(status().isNoContent());
        // The bucket stays; only its goal is gone.
        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts[0].buckets[0].name").value("House"))
                .andExpect(jsonPath("$.accounts[0].buckets[0].goals").isEmpty());
    }

    @Test
    void showsTheGoalInsideItsBucket() throws Exception {
        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts[0].buckets[0].currentAmount").value(20.00))
                .andExpect(jsonPath("$.accounts[0].buckets[0].goals[0].id").value(goalId.toString()))
                .andExpect(jsonPath("$.accounts[0].buckets[0].goals[0].goalAmount").value(50000.00))
                .andExpect(jsonPath("$.accounts[0].buckets[0].goals[0].goalObjective").value("2027-12-31"));
    }

    /** Several goals on one bucket are milestones over the same money, soonest first. */
    @Test
    void showsEveryGoalOfABucketSoonestFirst() throws Exception {
        UUID bucketId = goals.findById(goalId).orElseThrow().getBucketId();
        UUID sooner = goals.saveAndFlush(new Goal(bucketId, new BigDecimal("10000.00"), LocalDate.of(2026, 12, 31))).getId();
        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts[0].buckets[0].goals.length()").value(2))
                .andExpect(jsonPath("$.accounts[0].buckets[0].goals[0].id").value(sooner.toString()))
                .andExpect(jsonPath("$.accounts[0].buckets[0].goals[1].id").value(goalId.toString()));
    }
}
