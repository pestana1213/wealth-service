package com.finance.wealth_service.account;

import com.finance.wealth_service.account.entity.Account;
import com.finance.wealth_service.account.entity.User;
import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.account.repository.UserRepository;
import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.bucket.repository.BucketRepository;
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
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import com.finance.wealth_service.support.TestUsers;
import static com.finance.wealth_service.support.TestUsers.signedInAs;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class BucketGoalEndpointsTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired BucketRepository buckets;
    @Autowired GoalRepository goals;
    @Autowired ObjectMapper mapper;
    MockMvc mvc;
    User user;
    UUID accountId;

    @BeforeEach
    void setup() {
        user = users.saveAndFlush(TestUsers.newUser("João"));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .defaultRequest(get("/").with(signedInAs(user))).build();
        accountId = accounts.saveAndFlush(new Account(user.getId(), "Bank")).getId();
    }

    /** A bucket body for the caller's account, with the remaining fields supplied by the test. */
    String bucketBody(String fields) {
        return "{\"accountId\":\"" + accountId + "\"" + (fields.isEmpty() ? "" : "," + fields) + "}";
    }

    UUID createUser() {
        return user.getId();
    }

    @Test
    void createsBucketWithInitialBalance() throws Exception {
        UUID userId = createUser();
        var result = mvc.perform(post("/buckets").contentType(MediaType.APPLICATION_JSON)
                        .content(bucketBody("\"name\":\" House \",\"currentAmount\":27000.50")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.name").value("House"))
                .andExpect(jsonPath("$.currentAmount").value(27000.50)).andReturn();
        UUID id = UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        buckets.flush();
        assertThat(buckets.findById(id).orElseThrow().getCurrentAmount()).isEqualByComparingTo("27000.50");
    }

    @Test
    void defaultsBucketBalanceToZero() throws Exception {
        for (String amount : new String[]{"", ",\"currentAmount\":null"}) {
            mvc.perform(post("/buckets").contentType(MediaType.APPLICATION_JSON)
                            .content(bucketBody("\"name\":\"Emergency\"" + amount)))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.currentAmount").value(0));
        }
    }

    @Test
    void createsGoalWithOptionalDate() throws Exception {
        UUID bucketId = buckets.saveAndFlush(new Bucket(createUser(), accountId, "House")).getId();
        for (String date : new String[]{"", ",\"goalObjective\":\"2027-12-31\""}) {
            var result = mvc.perform(post("/goals").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"bucketId\":\"" + bucketId + "\",\"goalAmount\":50000.25" + date + "}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.bucketId").value(bucketId.toString()))
                    .andExpect(jsonPath("$.goalAmount").value(50000.25)).andReturn();
            UUID id = UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
            goals.flush();
            var goal = goals.findById(id).orElseThrow();
            assertThat(goal.getGoalAmount()).isEqualByComparingTo("50000.25");
            assertThat(goal.getGoalObjective()).isEqualTo(date.isEmpty() ? null : LocalDate.of(2027, 12, 31));
        }
    }

    @Test
    void rejectsMissingParentsWithoutSaving() throws Exception {
        long bucketCount = buckets.count();
        long goalCount = goals.count();
        mvc.perform(post("/buckets").with(signedInAs(TestUsers.stranger())).contentType(MediaType.APPLICATION_JSON)
                        .content(bucketBody("\"name\":\"House\"")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/goals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bucketId\":\"" + UUID.randomUUID() + "\",\"goalAmount\":100}"))
                .andExpect(status().isNotFound());
        assertThat(buckets.count()).isEqualTo(bucketCount);
        assertThat(goals.count()).isEqualTo(goalCount);
    }

    @Test
    void cannotCreateGoalOnAnotherUsersBucket() throws Exception {
        User other = users.saveAndFlush(TestUsers.newUser("Other"));
        UUID foreignAccount = accounts.saveAndFlush(new Account(other.getId(), "Theirs")).getId();
        UUID foreignBucket = buckets.saveAndFlush(new Bucket(other.getId(), foreignAccount, "Theirs")).getId();
        long before = goals.count();
        mvc.perform(post("/goals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bucketId\":\"" + foreignBucket + "\",\"goalAmount\":100}"))
                .andExpect(status().isNotFound());
        assertThat(goals.count()).isEqualTo(before);
    }

    @Test
    void rejectsInvalidBucketRequests() throws Exception {
        long before = buckets.count();
        for (String body : new String[]{"{}",
                "{\"name\":\"House\"}",
                bucketBody(""),
                bucketBody("\"name\":\" \""),
                bucketBody("\"name\":\"" + "a".repeat(256) + "\""),
                bucketBody("\"name\":\"House\",\"currentAmount\":-1"),
                bucketBody("\"name\":\"House\",\"currentAmount\":1.001"),
                bucketBody("\"name\":\"House\",\"currentAmount\":100000000000000000")}) {
            mvc.perform(post("/buckets").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(buckets.count()).isEqualTo(before);
    }

    @Test
    void rejectsInvalidGoalRequests() throws Exception {
        UUID bucketId = buckets.saveAndFlush(new Bucket(createUser(), accountId, "House")).getId();
        long before = goals.count();
        for (String amount : new String[]{"null", "0", "-1", "1.001", "100000000000000000"}) {
            mvc.perform(post("/goals").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"bucketId\":\"" + bucketId + "\",\"goalAmount\":" + amount + "}"))
                    .andExpect(status().isBadRequest());
        }
        for (String body : new String[]{"{}", "{\"goalAmount\":100}",
                "{\"bucketId\":\"bad\",\"goalAmount\":100}",
                "{\"bucketId\":\"" + bucketId + "\",\"goalAmount\":100,\"goalObjective\":\"invalid\"}"}) {
            mvc.perform(post("/goals").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(goals.count()).isEqualTo(before);
    }

    @Test
    void cannotCreateBucketInAnotherUsersAccount() throws Exception {
        User other = users.saveAndFlush(TestUsers.newUser("Other"));
        UUID foreignAccount = accounts.saveAndFlush(new Account(other.getId(), "Theirs")).getId();
        long before = buckets.count();
        mvc.perform(post("/buckets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + foreignAccount + "\",\"name\":\"House\"}"))
                .andExpect(status().isNotFound());
        assertThat(buckets.count()).isEqualTo(before);
    }
}
