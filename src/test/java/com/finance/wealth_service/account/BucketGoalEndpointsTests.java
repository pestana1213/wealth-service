package com.finance.wealth_service.account;

import com.finance.wealth_service.account.entity.User;
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
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class BucketGoalEndpointsTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired BucketRepository buckets;
    @Autowired GoalRepository goals;
    @Autowired ObjectMapper mapper;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    UUID createUser() {
        return users.saveAndFlush(new User("João", UUID.randomUUID() + "@example.com")).getId();
    }

    @Test
    void createsBucketWithInitialBalance() throws Exception {
        UUID userId = createUser();
        var result = mvc.perform(post("/buckets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"name\":\" House \",\"currentAmount\":27000.50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.name").value("House"))
                .andExpect(jsonPath("$.currentAmount").value(27000.50)).andReturn();
        UUID id = UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        buckets.flush();
        assertThat(buckets.findById(id).orElseThrow().getCurrentAmount()).isEqualByComparingTo("27000.50");
    }

    @Test
    void defaultsBucketBalanceToZero() throws Exception {
        UUID userId = createUser();
        for (String amount : new String[]{"", ",\"currentAmount\":null"}) {
            mvc.perform(post("/buckets").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\":\"" + userId + "\",\"name\":\"Emergency\"" + amount + "}"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.currentAmount").value(0));
        }
    }

    @Test
    void createsGoalWithOptionalDate() throws Exception {
        UUID bucketId = buckets.saveAndFlush(new Bucket(createUser(), "House")).getId();
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
        mvc.perform(post("/buckets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + UUID.randomUUID() + "\",\"name\":\"House\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/goals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bucketId\":\"" + UUID.randomUUID() + "\",\"goalAmount\":100}"))
                .andExpect(status().isNotFound());
        assertThat(buckets.count()).isEqualTo(bucketCount);
        assertThat(goals.count()).isEqualTo(goalCount);
    }

    @Test
    void rejectsInvalidBucketRequests() throws Exception {
        String userId = createUser().toString();
        long before = buckets.count();
        for (String body : new String[]{"{}", "{\"userId\":\"bad\",\"name\":\"House\"}",
                "{\"userId\":\"" + userId + "\",\"name\":\" \"}",
                "{\"userId\":\"" + userId + "\",\"name\":\"" + "a".repeat(256) + "\"}",
                "{\"userId\":\"" + userId + "\",\"name\":\"House\",\"currentAmount\":-1}",
                "{\"userId\":\"" + userId + "\",\"name\":\"House\",\"currentAmount\":1.001}",
                "{\"userId\":\"" + userId + "\",\"name\":\"House\",\"currentAmount\":100000000000000000}"}) {
            mvc.perform(post("/buckets").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(buckets.count()).isEqualTo(before);
    }

    @Test
    void rejectsInvalidGoalRequests() throws Exception {
        UUID bucketId = buckets.saveAndFlush(new Bucket(createUser(), "House")).getId();
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
}
