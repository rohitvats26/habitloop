package com.habitloop.analytics;

import com.habitloop.analytics.config.AnalyticsProperties;
import com.habitloop.common.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = {"com.habitloop.analytics", "com.habitloop.common"})
@EnableConfigurationProperties({JwtProperties.class, AnalyticsProperties.class})
public class AnalyticsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsServiceApplication.class, args);
    }
}
