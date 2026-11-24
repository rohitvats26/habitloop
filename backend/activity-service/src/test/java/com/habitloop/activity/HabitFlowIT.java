package com.habitloop.activity;

import com.habitloop.activity.dto.CompletionResponse;
import com.habitloop.activity.dto.HabitResponse;
import com.habitloop.activity.entity.ActivityLog;
import com.habitloop.activity.entity.ActivitySource;
import com.habitloop.activity.repository.ActivityLogRepository;
import com.habitloop.common.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end flow against real PostgreSQL (Testcontainers). Mints a JWT with the same signing secret the
 * app is configured with (user-service is not part of this module). Named *IT so it is not run by the
 * default {@code mvn test}. Requires a Docker daemon.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class HabitFlowIT {

    static final String SECRET = "integration-test-secret-key-at-least-32-bytes-long";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("habitloop_activity")
            .withUsername("habitloop")
            .withPassword("habitloop");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("habitloop.jwt.secret", () -> SECRET);
        registry.add("habitloop.kafka.enabled", () -> "false");
    }

    @Autowired TestRestTemplate rest;
    @Autowired JwtService jwtService;
    @Autowired ActivityLogRepository activityLogs;

    @Test
    void createHabit_complete_streakAdvances() {
        String token = jwtService.generateAccessToken(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), List.of("USER"));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> habitBody = Map.of(
                "name", "Morning run", "description", "5k", "frequency", "DAILY",
                "schedule", Map.of("perWeek", 3), "targetPerPeriod", 1);
        ResponseEntity<HabitResponse> created = rest.postForEntity("/api/habits",
                new HttpEntity<>(habitBody, headers), HabitResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String habitId = created.getBody().id();

        ResponseEntity<CompletionResponse> done = rest.postForEntity("/api/habits/" + habitId + "/completions",
                new HttpEntity<>(Map.of("date", "2026-11-23", "note", "great"), headers), CompletionResponse.class);
        assertThat(done.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(done.getBody().streak().current()).isEqualTo(1);

        // Duplicate same-day completion is rejected (idempotency).
        ResponseEntity<String> dup = rest.postForEntity("/api/habits/" + habitId + "/completions",
                new HttpEntity<>(Map.of("date", "2026-11-23"), headers), String.class);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // No token -> 401.
        ResponseEntity<String> noAuth = rest.getForEntity("/api/habits", String.class);
        assertThat(noAuth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void activitySearchAppliesOptionalFiltersAndKeepsTenantAndUserIsolation() {
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        ActivityLog early = saveActivity(tenant, user, "RUN", "2026-01-01T00:00:00Z");
        ActivityLog boundary = saveActivity(tenant, user, "RUN", "2026-01-02T00:00:00Z");
        ActivityLog late = saveActivity(tenant, user, "WALK", "2026-01-03T00:00:00Z");
        saveActivity(tenant, UUID.randomUUID(), "RUN", "2026-01-02T00:00:00Z");
        saveActivity(UUID.randomUUID(), user, "RUN", "2026-01-02T00:00:00Z");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtService.generateAccessToken(
                user.toString(), tenant.toString(), List.of("USER")));

        assertActivitySearch(headers, "", early, boundary, late);
        assertActivitySearch(headers, "?from=2026-01-02T00:00:00Z", boundary, late);
        assertActivitySearch(headers, "?to=2026-01-02T00:00:00Z", early, boundary);
        assertActivitySearch(headers, "?type=RUN&from=2026-01-02T00:00:00Z&to=2026-01-02T00:00:00Z",
                boundary);
        assertActivitySearch(headers, "?type=WALK", late);

        ResponseEntity<JsonNode> page = rest.exchange("/api/activity?size=1", HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody().path("content").size()).isEqualTo(1);
        assertThat(page.getBody().path("totalElements").asInt()).isEqualTo(3);
    }

    private ActivityLog saveActivity(UUID tenant, UUID user, String type, String occurredAt) {
        ActivityLog activity = new ActivityLog();
        activity.setTenantId(tenant);
        activity.setUserId(user);
        activity.setActivityType(type);
        activity.setOccurredAt(OffsetDateTime.parse(occurredAt));
        activity.setSource(ActivitySource.MANUAL);
        return activityLogs.save(activity);
    }

    private void assertActivitySearch(HttpHeaders headers, String query, ActivityLog... expected) {
        ResponseEntity<JsonNode> response = rest.exchange("/api/activity" + query, HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> ids = StreamSupport.stream(response.getBody().path("content").spliterator(), false)
                .map(item -> item.path("id").asText())
                .toList();
        assertThat(ids).containsExactlyInAnyOrder(
                java.util.Arrays.stream(expected).map(activity -> activity.getId().toString()).toArray(String[]::new));
    }
}
