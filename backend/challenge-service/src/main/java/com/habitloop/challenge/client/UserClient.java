package com.habitloop.challenge.client;

import com.habitloop.common.exception.BusinessException;
import com.habitloop.common.exception.ResourceNotFoundException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-only, same-tenant verification call to user-service with a timeout and a Resilience4j circuit
 * breaker (docs/02 §7, docs/07). A 404 (invitee not in the caller's tenant) is a business outcome and is
 * configured to NOT trip the breaker; transport failures do, and the fallback fails closed with a clear
 * error rather than letting an unverified invite through.
 */
@Component
public class UserClient {

    private static final Logger log = LoggerFactory.getLogger(UserClient.class);

    private final RestClient restClient;

    public UserClient(UserClientProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getConnectTimeoutMs());
        factory.setReadTimeout(props.getReadTimeoutMs());
        this.restClient = RestClient.builder()
                .baseUrl(props.getUrl())
                .requestFactory(factory)
                .build();
    }

    @CircuitBreaker(name = "userService", fallbackMethod = "verifyFallback")
    public void verifyUserInTenant(UUID userId) {
        restClient.get()
                .uri("/internal/users/{id}", userId)
                .header(HttpHeaders.AUTHORIZATION, currentAuthorization())
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, res) -> {
                    throw new ResourceNotFoundException("User", userId);
                })
                .toBodilessEntity();
    }

    @SuppressWarnings("unused")
    private void verifyFallback(UUID userId, Throwable t) {
        if (t instanceof ResourceNotFoundException rnfe) {
            throw rnfe; // not a failure of the dependency — surface as 404
        }
        log.warn("user-service verification unavailable for {}: {}", userId, t.toString());
        throw new BusinessException("USER_VERIFICATION_UNAVAILABLE",
                "Could not verify the invited user right now; please try again");
    }

    /** Best-effort same-tenant display names; callers fall back to anonymous member labels. */
    @CircuitBreaker(name = "userService", fallbackMethod = "displayNamesFallback")
    public Map<String, String> displayNames(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<UserDisplayName> names = restClient.post()
                .uri("/internal/users/display-names")
                .header(HttpHeaders.AUTHORIZATION, currentAuthorization())
                .body(List.copyOf(userIds))
                .retrieve()
                .body(new ParameterizedTypeReference<List<UserDisplayName>>() { });
        if (names == null) {
            return Map.of();
        }
        return names.stream()
                .filter(name -> name.id() != null && name.displayName() != null)
                .collect(Collectors.toMap(UserDisplayName::id, UserDisplayName::displayName, (first, second) -> first));
    }

    @SuppressWarnings("unused")
    private Map<String, String> displayNamesFallback(Collection<UUID> userIds, Throwable t) {
        log.warn("user-service display names unavailable: {}", t.toString());
        return Map.of();
    }

    private String currentAuthorization() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String header = attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null) {
                return header;
            }
        }
        return "";
    }
}
