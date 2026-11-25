package com.habitloop.integration.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dev/local {@link SecretStore} backed by an in-memory map. NOT for production — secrets do not survive a
 * restart and are not shared across instances. Select the AWS Secrets Manager implementation for deployed
 * environments with {@code SECRET_STORE_BACKEND=aws}.
 */
@Component
@ConditionalOnProperty(name = "habitloop.integration.secret-store", havingValue = "memory", matchIfMissing = true)
public class InMemorySecretStore implements SecretStore {

    private final Map<String, String> secrets = new ConcurrentHashMap<>();

    @Override
    public void store(String ref, String secret) {
        secrets.put(ref, secret);
    }

    @Override
    public String resolve(String ref) {
        return secrets.get(ref);
    }
}
