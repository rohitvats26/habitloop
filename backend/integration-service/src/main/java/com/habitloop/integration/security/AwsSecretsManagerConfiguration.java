package com.habitloop.integration.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

@Configuration
@ConditionalOnProperty(name = "habitloop.integration.secret-store", havingValue = "aws")
public class AwsSecretsManagerConfiguration {

    @Bean(destroyMethod = "close")
    public SecretsManagerClient secretsManagerClient() {
        return SecretsManagerClient.create();
    }

    @Bean
    public SecretStore awsSecretsManagerSecretStore(
            SecretsManagerClient client,
            @Value("${habitloop.integration.secret-prefix}") String prefix) {
        return new AwsSecretsManagerSecretStore(client, prefix);
    }
}
