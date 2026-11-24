package com.habitloop.challenge;

import com.habitloop.challenge.client.UserClientProperties;
import com.habitloop.common.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = {"com.habitloop.challenge", "com.habitloop.common"})
@EnableConfigurationProperties({JwtProperties.class, UserClientProperties.class})
public class ChallengeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChallengeServiceApplication.class, args);
    }
}
