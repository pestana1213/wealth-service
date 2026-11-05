package com.finance.wealth_service.valuation;

import com.finance.wealth_service.account.AccountType;
import com.finance.wealth_service.account.entity.Account;
import com.finance.wealth_service.account.entity.User;
import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.account.repository.UserRepository;
import com.finance.wealth_service.valuation.repository.AccountValuationRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class ValuationEndpointsTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired AccountValuationRepository valuations;
    MockMvc mvc;
    UUID investmentId;
    UUID savingsId;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        UUID userId = users.saveAndFlush(new User(null, null, "João", UUID.randomUUID() + "@example.com")).getId();
        investmentId = accounts.saveAndFlush(new Account(userId, "Broker", new BigDecimal("1000.00"), AccountType.INVESTMENT)).getId();
        savingsId = accounts.saveAndFlush(new Account(userId, "Bank", BigDecimal.ZERO, AccountType.SAVINGS)).getId();
    }

    private String body(String value, String date) {
        return "{\"value\":" + value + ",\"valuedAt\":\"" + date + "\"}";
    }

    @Test
    void recordsValuationWithoutChangingBalance() throws Exception {
        mvc.perform(post("/accounts/" + investmentId + "/valuations").contentType(MediaType.APPLICATION_JSON)
                        .content(body("1250.50", "2026-10-01")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(investmentId.toString()))
                .andExpect(jsonPath("$.value").value(1250.50))
                .andExpect(jsonPath("$.valuedAt").value("2026-10-01"));
        assertThat(valuations.findByAccountIdOrderByValuedAtDesc(investmentId)).hasSize(1);
        assertThat(accounts.findById(investmentId).orElseThrow().getCurrentAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void listsValuationsNewestFirst() throws Exception {
        for (String date : new String[]{"2026-08-01", "2026-10-01", "2026-09-01"}) {
            mvc.perform(post("/accounts/" + investmentId + "/valuations").contentType(MediaType.APPLICATION_JSON)
                    .content(body("100", date))).andExpect(status().isCreated());
        }
        mvc.perform(get("/accounts/" + investmentId + "/valuations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].valuedAt").value("2026-10-01"))
                .andExpect(jsonPath("$[2].valuedAt").value("2026-08-01"));
    }

    @Test
    void rejectsSecondValuationForSameDate() throws Exception {
        mvc.perform(post("/accounts/" + investmentId + "/valuations").contentType(MediaType.APPLICATION_JSON)
                .content(body("100", "2026-10-01"))).andExpect(status().isCreated());
        mvc.perform(post("/accounts/" + investmentId + "/valuations").contentType(MediaType.APPLICATION_JSON)
                        .content(body("200", "2026-10-01")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsValuationOfSavingsAccount() throws Exception {
        mvc.perform(post("/accounts/" + savingsId + "/valuations").contentType(MediaType.APPLICATION_JSON)
                        .content(body("100", "2026-10-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Valuations can only be recorded for investment accounts"));
    }

    @Test
    void returnsNotFoundForUnknownAccount() throws Exception {
        UUID unknown = UUID.randomUUID();
        mvc.perform(post("/accounts/" + unknown + "/valuations").contentType(MediaType.APPLICATION_JSON)
                .content(body("100", "2026-10-01"))).andExpect(status().isNotFound());
        mvc.perform(get("/accounts/" + unknown + "/valuations")).andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidValuations() throws Exception {
        String tomorrow = LocalDate.now().plusDays(1).toString();
        for (String content : new String[]{
                "{}",
                body("-1", "2026-10-01"),
                body("1.001", "2026-10-01"),
                body("100", tomorrow),
                "{\"value\":100}"}) {
            mvc.perform(post("/accounts/" + investmentId + "/valuations").contentType(MediaType.APPLICATION_JSON).content(content))
                    .andExpect(status().isBadRequest());
        }
        assertThat(valuations.findByAccountIdOrderByValuedAtDesc(investmentId)).isEmpty();
    }
}
