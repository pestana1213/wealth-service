package com.finance.wealth_service.account;

import com.finance.wealth_service.account.entity.User;
import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.account.repository.UserRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class AccountEndpointsTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired ObjectMapper mapper;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void createsAndPersistsUser() throws Exception {
        var result = mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  João  \",\"email\":\" Joao@Example.com \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("João"))
                .andExpect(jsonPath("$.email").value("joao@example.com"))
                .andReturn();
        UUID id = UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        users.flush();
        assertThat(users.findById(id).orElseThrow().getName()).isEqualTo("João");
        assertThat(users.findById(id).orElseThrow().getEmail()).isEqualTo("joao@example.com");
    }

    @Test
    void createsAndPersistsAccountWithInitialBalance() throws Exception {
        UUID userId = users.saveAndFlush(new User("João", UUID.randomUUID() + "@example.com")).getId();
        var result = mvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"name\":\" Bank \",\"currentAmount\":55000.25}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.name").value("Bank"))
                .andExpect(jsonPath("$.currentAmount").value(55000.25)).andReturn();
        UUID id = UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        accounts.flush();
        assertThat(accounts.findById(id).orElseThrow().getCurrentAmount()).isEqualByComparingTo("55000.25");
    }

    @Test
    void defaultsInitialBalanceToZero() throws Exception {
        UUID userId = users.saveAndFlush(new User("João", UUID.randomUUID() + "@example.com")).getId();
        mvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"name\":\"Bank\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.currentAmount").value(0));
    }

    @Test
    void rejectsUnknownUserWithoutCreatingAccount() throws Exception {
        long before = accounts.count();
        mvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + UUID.randomUUID() + "\",\"name\":\"Bank\"}"))
                .andExpect(status().isNotFound());
        assertThat(accounts.count()).isEqualTo(before);
    }

    @Test
    void rejectsInvalidRequests() throws Exception {
        long usersBefore = users.count();
        long accountsBefore = accounts.count();
        for (String body : new String[]{"{}", "{\"name\":\"   \"}", "{\"name\":\"" + "a".repeat(256) + "\"}"}) {
            mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        String userId = UUID.randomUUID().toString();
        for (String body : new String[]{
                "{\"name\":\"Bank\"}",
                "{\"userId\":\"invalid\",\"name\":\"Bank\"}",
                "{\"userId\":\"" + userId + "\",\"name\":\" \"}",
                "{\"userId\":\"" + userId + "\",\"name\":\"Bank\",\"currentAmount\":1.001}",
                "{\"userId\":\"" + userId + "\",\"name\":\"Bank\",\"currentAmount\":100000000000000000}"}) {
            mvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(users.count()).isEqualTo(usersBefore);
        assertThat(accounts.count()).isEqualTo(accountsBefore);
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        users.saveAndFlush(new User("Original", email));
        long before = users.count();
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Other\",\"email\":\"" + email.toUpperCase(java.util.Locale.ROOT) + "\"}"))
                .andExpect(status().isConflict());
        assertThat(users.count()).isEqualTo(before);
    }

    @Test
    void rejectsMissingOrInvalidEmail() throws Exception {
        long before = users.count();
        for (String email : new String[]{"null", "\"\"", "\"   \"", "\"invalid\"", "\"" + "a".repeat(250) + "@example.com\""}) {
            mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"João\",\"email\":" + email + "}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"João\"}"))
                .andExpect(status().isBadRequest());
        assertThat(users.count()).isEqualTo(before);
    }
}
