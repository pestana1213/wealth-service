package com.finance.wealth_service.visualization;

import com.finance.wealth_service.account.AccountType;
import com.finance.wealth_service.account.entity.Account;
import com.finance.wealth_service.account.entity.User;
import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.account.repository.UserRepository;
import com.finance.wealth_service.valuation.entity.AccountValuation;
import com.finance.wealth_service.valuation.repository.AccountValuationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Net worth is what the money is worth now, not what was put in. An investment
 * account counts at its latest valuation; everything else counts at its balance.
 */
@SpringBootTest
@Transactional
class DashboardNetWorthTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired AccountValuationRepository valuations;
    MockMvc mvc;
    UUID userId;
    UUID bankId;
    UUID brokerId;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        userId = users.saveAndFlush(new User(null, null, "Net worth", UUID.randomUUID() + "@example.com")).getId();
        bankId = accounts.saveAndFlush(new Account(userId, "Bank", new BigDecimal("100.00"))).getId();
        brokerId = accounts.saveAndFlush(
                new Account(userId, "Broker", new BigDecimal("1000.00"), AccountType.INVESTMENT)).getId();
    }

    private void record(String value, String invested, String date) {
        valuations.saveAndFlush(new AccountValuation(brokerId, new BigDecimal(value),
                new BigDecimal(invested), LocalDate.parse(date)));
    }

    @Test
    void countsAnInvestmentAccountAtItsLatestValuation() throws Exception {
        record("1100.00", "1000.00", "2026-09-01");
        record("1250.50", "1000.00", "2026-10-01");
        mvc.perform(get("/users/" + userId + "/dashboard"))
                .andExpect(status().isOk())
                // Contributed money is unchanged: 100 + 1000.
                .andExpect(jsonPath("$.totalAmount").value(1100.00))
                // Market value: the bank at its balance, the broker at 1250.50.
                .andExpect(jsonPath("$.marketAmount").value(1350.50))
                .andExpect(jsonPath("$.marketGain").value(250.50));
    }

    @Test
    void countsAnAccountWithoutValuationAtItsBalance() throws Exception {
        mvc.perform(get("/users/" + userId + "/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketAmount").value(1100.00))
                .andExpect(jsonPath("$.marketGain").value(0));
    }

    @Test
    void reportsALossWhenTheMarketIsBelowWhatWentIn() throws Exception {
        record("800.00", "1000.00", "2026-10-01");
        mvc.perform(get("/users/" + userId + "/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketAmount").value(900.00))
                .andExpect(jsonPath("$.marketGain").value(-200.00));
    }

    @Test
    void showsTheLatestValueAndInvestedAmountOnTheAccountItself() throws Exception {
        record("1250.50", "1000.00", "2026-10-01");
        mvc.perform(get("/users/" + userId + "/dashboard"))
                .andExpect(status().isOk())
                // Ordered by name: Bank, then Broker.
                .andExpect(jsonPath("$.accounts[0].id").value(bankId.toString()))
                .andExpect(jsonPath("$.accounts[0].marketValue").value((Object) null))
                .andExpect(jsonPath("$.accounts[1].id").value(brokerId.toString()))
                .andExpect(jsonPath("$.accounts[1].marketValue").value(1250.50))
                .andExpect(jsonPath("$.accounts[1].investedMoney").value(1000.00));
    }
}
