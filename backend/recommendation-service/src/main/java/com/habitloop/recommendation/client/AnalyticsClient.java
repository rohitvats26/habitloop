package com.habitloop.recommendation.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Reads aggregates from analytics-service with a timeout and a Resilience4j circuit breaker. If analytics
 * is unavailable the fallbacks return empty data, so recommendations degrade gracefully instead of failing
 * (docs/02 §7, docs/07).
 */
@Component
public class AnalyticsClient {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsClient.class);

    private final RestClient restClient;

    public AnalyticsClient(AnalyticsClientProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getConnectTimeoutMs());
        factory.setReadTimeout(props.getReadTimeoutMs());
        this.restClient = RestClient.builder().baseUrl(props.getUrl()).requestFactory(factory).build();
    }

    @CircuitBreaker(name = "analyticsService", fallbackMethod = "patternFallback")
    public PatternDto getPattern() {
        return restClient.get().uri("/api/analytics/pattern")
                .header(HttpHeaders.AUTHORIZATION, currentAuthorization())
                .retrieve().body(PatternDto.class);
    }

    @CircuitBreaker(name = "analyticsService", fallbackMethod = "summaryFallback")
    public SummaryDto getSummary() {
        return restClient.get().uri("/api/analytics/summary")
                .header(HttpHeaders.AUTHORIZATION, currentAuthorization())
                .retrieve().body(SummaryDto.class);
    }

    @SuppressWarnings("unused")
    private PatternDto patternFallback(Throwable t) {
        log.warn("analytics-service pattern unavailable: {}", t.toString());
        return PatternDto.empty();
    }

    @SuppressWarnings("unused")
    private SummaryDto summaryFallback(Throwable t) {
        log.warn("analytics-service summary unavailable: {}", t.toString());
        return SummaryDto.empty();
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
