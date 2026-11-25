package com.habitloop.integration.security;

import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.ResourceExistsException;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

import java.util.Objects;

public class AwsSecretsManagerSecretStore implements SecretStore {

    private final SecretsManagerClient client;
    private final String prefix;

    public AwsSecretsManagerSecretStore(SecretsManagerClient client, String prefix) {
        this.client = Objects.requireNonNull(client);
        if (!StringUtils.hasText(prefix)) {
            throw new IllegalArgumentException("Secrets Manager prefix must not be blank");
        }
        this.prefix = prefix.trim().replaceAll("^/+|/+$", "");
        if (!StringUtils.hasText(this.prefix)) {
            throw new IllegalArgumentException("Secrets Manager prefix must contain a name");
        }
    }

    @Override
    public void store(String ref, String secret) {
        String name = secretName(ref);
        if (!StringUtils.hasText(secret)) {
            throw new IllegalArgumentException("Secret value must not be blank");
        }
        PutSecretValueRequest putRequest = PutSecretValueRequest.builder()
                .secretId(name)
                .secretString(secret)
                .build();

        try {
            client.putSecretValue(putRequest);
        } catch (ResourceNotFoundException missingSecret) {
            try {
                client.createSecret(CreateSecretRequest.builder()
                        .name(name)
                        .secretString(secret)
                        .build());
            } catch (ResourceExistsException concurrentCreate) {
                client.putSecretValue(putRequest);
            }
        }
    }

    @Override
    public String resolve(String ref) {
        try {
            String secret = client.getSecretValue(GetSecretValueRequest.builder()
                    .secretId(secretName(ref))
                    .build())
                    .secretString();
            if (secret == null) {
                throw new IllegalStateException("Secret Manager entry does not contain a string value");
            }
            return secret;
        } catch (ResourceNotFoundException missingSecret) {
            return null;
        }
    }

    private String secretName(String ref) {
        if (!StringUtils.hasText(ref)) {
            throw new IllegalArgumentException("Secret reference must not be blank");
        }
        String normalizedRef = ref.trim().replaceAll("^/+|/+$", "");
        if (!StringUtils.hasText(normalizedRef)) {
            throw new IllegalArgumentException("Secret reference must contain a name");
        }
        return prefix + "/" + normalizedRef;
    }
}
