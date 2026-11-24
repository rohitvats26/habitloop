package com.habitloop.integration.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dev/local {@link SecretStore} backed by an in-memory map. NOT for production — secrets do not survive a
 * restart and are not shared across instances. An AWS Secrets Manager implementation replaces this bean
 * in deployed environments (e.g. via a profile-guarded @Configuration).
 */
@Component
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
