package com.finance.wealth_service.account;

import com.finance.wealth_service.account.entity.Account;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.auth.UserAuthentication;
import com.finance.wealth_service.auth.UserInfoClient;
import com.finance.wealth_service.auth.UserProfile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.Mockito.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class AccountEndpointsTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired ObjectMapper mapper;
    @MockitoBean UserInfoClient userInfo;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    static final String ISSUER = "https://issuer.test";

    static RequestPostProcessor signedIn(String subject) {
        Jwt jwt = Jwt.withTokenValue("token-" + subject).header("alg", "none")
                .issuer(ISSUER).subject(subject).build();
        return authentication(new UserAuthentication(new AuthenticatedUser(ISSUER, subject), jwt));
    }

    // Registration commits its own transaction, as it does in production, where
    // nothing wraps the controller. The rows use random subjects and emails.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void registersSignedInUserWithProfileFromIdentityProvider() throws Exception {
        String subject = UUID.randomUUID().toString();
        String email = subject + "@example.com";
        when(userInfo.fetch("token-" + subject)).thenReturn(new UserProfile("João", email));
        MvcResult result = mvc.perform(post("/users/me").with(signedIn(subject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("João"))
                .andExpect(jsonPath("$.email").value(email))
                .andReturn();
        UUID id = UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        User saved = users.findById(id).orElseThrow();
        assertThat(saved.getIssuer()).isEqualTo(ISSUER);
        assertThat(saved.getSubject()).isEqualTo(subject);
    }

    // Registration commits its own transaction, as it does in production, where
    // nothing wraps the controller. The rows use random subjects and emails.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void registersUserWithoutVerifiedEmail() throws Exception {
        String subject = UUID.randomUUID().toString();
        when(userInfo.fetch("token-" + subject)).thenReturn(new UserProfile("João", null));
        mvc.perform(post("/users/me").with(signedIn(subject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    // Registration commits its own transaction, as it does in production, where
    // nothing wraps the controller. The rows use random subjects and emails.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void registeringTwiceReturnsTheSameUserWithoutAskingTheIdentityProviderAgain() throws Exception {
        String subject = UUID.randomUUID().toString();
        when(userInfo.fetch("token-" + subject)).thenReturn(new UserProfile("João", subject + "@example.com"));
        String first = mvc.perform(post("/users/me").with(signedIn(subject)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long before = users.count();
        mvc.perform(post("/users/me").with(signedIn(subject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mapper.readTree(first).get("id").asText()));
        assertThat(users.count()).isEqualTo(before);
        verify(userInfo, times(1)).fetch("token-" + subject);
    }

    // Registration commits its own transaction, as it does in production, where
    // nothing wraps the controller. The rows use random subjects and emails.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void rejectsEmailAlreadyRegisteredByAnotherIdentity() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();
        when(userInfo.fetch("token-" + first)).thenReturn(new UserProfile("Original", email));
        when(userInfo.fetch("token-" + second)).thenReturn(new UserProfile("Other", email));
        mvc.perform(post("/users/me").with(signedIn(first))).andExpect(status().isOk());
        long before = users.count();
        mvc.perform(post("/users/me").with(signedIn(second))).andExpect(status().isConflict());
        assertThat(users.count()).isEqualTo(before);
    }

    @Test
    void registrationRequiresAToken() throws Exception {
        mvc.perform(post("/users/me")).andExpect(status().isUnauthorized());
        verifyNoInteractions(userInfo);
    }

    @Test
    void createsAndPersistsAccountWithInitialBalance() throws Exception {
        UUID userId = users.saveAndFlush(new User(null, null, "João", UUID.randomUUID() + "@example.com")).getId();
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
        UUID userId = users.saveAndFlush(new User(null, null, "João", UUID.randomUUID() + "@example.com")).getId();
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
    void updatesAccountNameAndType() throws Exception {
        UUID userId = users.saveAndFlush(new User(null, null, "João", UUID.randomUUID() + "@example.com")).getId();
        Account account = accounts.saveAndFlush(new Account(userId, "Bank", new java.math.BigDecimal("10.00"), AccountType.SAVINGS));
        mvc.perform(patch("/accounts/" + account.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" Broker \",\"type\":\"INVESTMENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Broker"))
                .andExpect(jsonPath("$.type").value("INVESTMENT"))
                .andExpect(jsonPath("$.currentAmount").value(10));
        Account updated = accounts.findById(account.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Broker");
        assertThat(updated.getType()).isEqualTo(AccountType.INVESTMENT);
    }

    @Test
    void partialUpdateLeavesOmittedFieldsUnchanged() throws Exception {
        UUID userId = users.saveAndFlush(new User(null, null, "João", UUID.randomUUID() + "@example.com")).getId();
        Account account = accounts.saveAndFlush(new Account(userId, "Bank", java.math.BigDecimal.ZERO, AccountType.SAVINGS));
        mvc.perform(patch("/accounts/" + account.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"INVESTMENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bank"))
                .andExpect(jsonPath("$.type").value("INVESTMENT"));
    }

    @Test
    void rejectsUpdateOfUnknownAccount() throws Exception {
        mvc.perform(patch("/accounts/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bank\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidUpdateRequests() throws Exception {
        UUID userId = users.saveAndFlush(new User(null, null, "João", UUID.randomUUID() + "@example.com")).getId();
        Account account = accounts.saveAndFlush(new Account(userId, "Bank", java.math.BigDecimal.ZERO, AccountType.SAVINGS));
        for (String body : new String[]{
                "{\"name\":\"   \"}",
                "{\"name\":\"" + "a".repeat(256) + "\"}",
                "{\"type\":\"CHECKING\"}"}) {
            mvc.perform(patch("/accounts/" + account.getId()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(accounts.findById(account.getId()).orElseThrow().getName()).isEqualTo("Bank");
    }
}
