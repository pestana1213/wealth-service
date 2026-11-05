package com.finance.wealth_service.auth;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserInfoClientTests {
    HttpServer server;
    String issuer;
    final AtomicReference<String> userInfo = new AtomicReference<>("{}");
    final AtomicInteger userInfoStatus = new AtomicInteger(200);
    final AtomicReference<String> authorization = new AtomicReference<>();
    final AtomicInteger discoveries = new AtomicInteger();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        issuer = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/.well-known/openid-configuration", exchange -> {
            discoveries.incrementAndGet();
            respond(exchange, 200, "{\"userinfo_endpoint\":\"" + issuer + "/oidc/v1/userinfo\"}");
        });
        server.createContext("/oidc/v1/userinfo", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, userInfoStatus.get(), userInfo.get());
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void readsNameAndVerifiedEmailWithTheCallersToken() {
        userInfo.set("{\"name\":\" João \",\"email\":\" Joao@Example.com \",\"email_verified\":true}");
        UserProfile profile = new UserInfoClient(issuer + "/").fetch("abc");
        assertThat(profile).isEqualTo(new UserProfile("João", "joao@example.com"));
        assertThat(authorization.get()).isEqualTo("Bearer abc");
    }

    @Test
    void dropsUnverifiedEmail() {
        userInfo.set("{\"name\":\"João\",\"email\":\"joao@example.com\",\"email_verified\":false}");
        assertThat(new UserInfoClient(issuer).fetch("abc").email()).isNull();
    }

    @Test
    void fallsBackToPreferredUsername() {
        userInfo.set("{\"preferred_username\":\"joao\"}");
        assertThat(new UserInfoClient(issuer).fetch("abc").name()).isEqualTo("joao");
    }

    @Test
    void looksUpTheEndpointOnce() {
        userInfo.set("{\"name\":\"João\"}");
        UserInfoClient client = new UserInfoClient(issuer);
        client.fetch("abc");
        client.fetch("abc");
        assertThat(discoveries.get()).isEqualTo(1);
    }

    @Test
    void rejectedTokenIsUnauthorized() {
        userInfoStatus.set(401);
        assertThatThrownBy(() -> new UserInfoClient(issuer).fetch("abc"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    void unreachableProviderIsBadGateway() {
        server.stop(0);
        assertThatThrownBy(() -> new UserInfoClient(issuer).fetch("abc"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("502");
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
