package com.habitloop.user;

import com.habitloop.user.dto.AuthResponse;
import com.habitloop.user.dto.LoginRequest;
import com.habitloop.user.dto.RegisterRequest;
import com.habitloop.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test against a real PostgreSQL (Testcontainers). Named *IT so it runs under
 * Failsafe / an explicit integration run, not the default {@code mvn test}. Requires a Docker daemon.
 *
 * Run with: mvn -Dtest=AuthFlowIT -DfailIfNoTests=false test   (with Docker available)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthFlowIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("habitloop_user")
            .withUsername("habitloop")
            .withPassword("habitloop");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("habitloop.jwt.secret", () -> "integration-test-secret-key-at-least-32-bytes-long");
        registry.add("habitloop.seed-demo", () -> "false");
    }

    @Autowired
    TestRestTemplate rest;

    @Test
    void registerThenLogin_thenFetchMe() {
        RegisterRequest register = new RegisterRequest("it@acme.com", "Password123!", "IT User",
                "UTC", "Acme IT", null);
        ResponseEntity<UserResponse> reg = rest.postForEntity("/api/auth/register", register, UserResponse.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reg.getBody()).isNotNull();
        assertThat(reg.getBody().roles()).contains("TENANT_ADMIN");

        ResponseEntity<AuthResponse> login = rest.postForEntity("/api/auth/login",
                new LoginRequest("it@acme.com", "Password123!", null), AuthResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = login.getBody().accessToken();
        assertThat(token).isNotBlank();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<UserResponse> me = rest.exchange("/api/users/me",
                org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), UserResponse.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().email()).isEqualTo("it@acme.com");

        // Unauthenticated access is rejected.
        ResponseEntity<String> noAuth = rest.getForEntity("/api/users/me", String.class);
        assertThat(noAuth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
