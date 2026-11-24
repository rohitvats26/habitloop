package com.habitloop.integration;

import com.habitloop.common.security.JwtProperties;
import com.habitloop.integration.config.IntegrationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = {"com.habitloop.integration", "com.habitloop.common"})
@EnableConfigurationProperties({JwtProperties.class, IntegrationProperties.class})
public class IntegrationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntegrationServiceApplication.class, args);
    }
}
