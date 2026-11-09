package com.finance.wealth_service.transaction;

import com.finance.wealth_service.support.TestUsers;
import static com.finance.wealth_service.support.TestUsers.signedInAs;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Undoing a transaction records a compensating one instead of deleting the
 * original: the balances return to where they were and the history keeps both
 * rows.
 */
@SpringBootTest
@Transactional
class TransactionReversalTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired BucketRepository buckets;
    @Autowired ObjectMapper mapper;
    MockMvc mvc;
    User user;
    UUID userId;
    UUID bankId;
    UUID brokerId;
    UUID houseId;
    UUID etfId;

    @BeforeEach
    void setup() {
        user = users.saveAndFlush(TestUsers.newUser("Reversal"));
        userId = user.getId();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .defaultRequest(get("/").with(signedInAs(user))).build();
        bankId = accounts.saveAndFlush(new Account(userId, "Bank", new BigDecimal("100.00"))).getId();
        brokerId = accounts.saveAndFlush(new Account(userId, "Broker", BigDecimal.ZERO)).getId();
        houseId = buckets.saveAndFlush(new Bucket(userId, bankId, "House", new BigDecimal("20.00"))).getId();
        etfId = buckets.saveAndFlush(new Bucket(userId, brokerId, "ETF", BigDecimal.ZERO)).getId();
    }

    private UUID create(String fields) throws Exception {
        String body = "{\"category\":\"OTHER\",\"transactionAmount\":10.00,"
                + "\"occurredAt\":\"2026-10-01\"," + fields + "}";
        MvcResult result = mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID income() throws Exception {
        return create("\"toBucketId\":\"" + houseId + "\"");
    }

    private void assertBalances(String bank, String broker, String house, String etf) {
        assertThat(accounts.findById(bankId).orElseThrow().getCurrentAmount()).isEqualByComparingTo(bank);
        assertThat(accounts.findById(brokerId).orElseThrow().getCurrentAmount()).isEqualByComparingTo(broker);
        assertThat(buckets.findById(houseId).orElseThrow().getCurrentAmount()).isEqualByComparingTo(house);
        assertThat(buckets.findById(etfId).orElseThrow().getCurrentAmount()).isEqualByComparingTo(etf);
    }

    @Test
    void reversingIncomeDebitsTheAccountAndBucketBack() throws Exception {
        UUID original = income();
        assertBalances("110.00", "0.00", "30.00", "0.00");
        mvc.perform(post("/transactions/" + original + "/reversal"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andExpect(jsonPath("$.transactionAmount").value(10.00));
        assertBalances("100.00", "0.00", "20.00", "0.00");
    }

    @Test
    void reversingExpenseCreditsTheAccountAndBucketBack() throws Exception {
        UUID original = create("\"fromBucketId\":\"" + houseId + "\"");
        assertBalances("90.00", "0.00", "10.00", "0.00");
        mvc.perform(post("/transactions/" + original + "/reversal"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("INCOME"));
        assertBalances("100.00", "0.00", "20.00", "0.00");
    }

    @Test
    void reversingTransferSwapsBothSides() throws Exception {
        UUID original = create("\"fromBucketId\":\"" + houseId + "\",\"toBucketId\":\"" + etfId + "\"");
        assertBalances("90.00", "10.00", "10.00", "10.00");
        mvc.perform(post("/transactions/" + original + "/reversal"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.providerAccountId").value(brokerId.toString()))
                .andExpect(jsonPath("$.destinationAccountId").value(bankId.toString()));
        assertBalances("100.00", "0.00", "20.00", "0.00");
    }

    @Test
    void reversalLinksToTheOriginal() throws Exception {
        UUID original = income();
        mvc.perform(post("/transactions/" + original + "/reversal"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reversalOf").value(original.toString()));
    }

    @Test
    void historyKeepsBothTheOriginalAndTheReversal() throws Exception {
        UUID original = income();
        mvc.perform(post("/transactions/" + original + "/reversal")).andExpect(status().isCreated());
        mvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void historyTellsTheReversalApartFromTheOriginal() throws Exception {
        UUID original = income();
        mvc.perform(post("/transactions/" + original + "/reversal")).andExpect(status().isCreated());
        mvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reversalOf").value(original.toString()))
                .andExpect(jsonPath("$.content[1].transactionId").value(original.toString()))
                .andExpect(jsonPath("$.content[1].reversalOf").value((Object) null));
    }

    @Test
    void refusesToReverseTheSameTransactionTwice() throws Exception {
        UUID original = income();
        mvc.perform(post("/transactions/" + original + "/reversal")).andExpect(status().isCreated());
        mvc.perform(post("/transactions/" + original + "/reversal")).andExpect(status().isConflict());
        assertBalances("100.00", "0.00", "20.00", "0.00");
    }

    @Test
    void refusesToReverseAReversal() throws Exception {
        UUID original = income();
        MvcResult result = mvc.perform(post("/transactions/" + original + "/reversal"))
                .andExpect(status().isCreated()).andReturn();
        UUID reversal = UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        mvc.perform(post("/transactions/" + reversal + "/reversal")).andExpect(status().isConflict());
    }

    @Test
    void returnsNotFoundForUnknownTransaction() throws Exception {
        mvc.perform(post("/transactions/" + UUID.randomUUID() + "/reversal")).andExpect(status().isNotFound());
    }

    /**
     * The original credited ETF while it lived in the broker account. Once ETF is
     * emptied and moved to the bank, the mirror would debit the broker for a bucket
     * that is no longer there, so it is refused and nothing moves.
     */
    @Test
    void refusesToReverseWhenTheBucketHasMovedToAnotherAccount() throws Exception {
        UUID original = create("\"toBucketId\":\"" + etfId + "\"");
        create("\"fromBucketId\":\"" + etfId + "\",\"toAccountId\":\"" + brokerId + "\"");
        mvc.perform(patch("/buckets/" + etfId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + bankId + "\"}"))
                .andExpect(status().isOk());
        assertBalances("100.00", "10.00", "20.00", "0.00");

        mvc.perform(post("/transactions/" + original + "/reversal")).andExpect(status().isConflict());
        assertBalances("100.00", "10.00", "20.00", "0.00");
    }
}
