package com.habitloop.recommendation;

import com.habitloop.common.security.JwtProperties;
import com.habitloop.recommendation.client.AnalyticsClientProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = {"com.habitloop.recommendation", "com.habitloop.common"})
@EnableConfigurationProperties({JwtProperties.class, AnalyticsClientProperties.class})
public class RecommendationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecommendationServiceApplication.class, args);
    }
}
