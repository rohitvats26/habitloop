package com.habitloop.integration.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AwsSecretsManagerSecretStoreTest {

    @Mock
    SecretsManagerClient client;

    private AwsSecretsManagerSecretStore store;

    @BeforeEach
    void setUp() {
        store = new AwsSecretsManagerSecretStore(client, "habitloop-dev");
    }

    @Test
    void store_createsSecretWhenReferenceDoesNotExist() {
        doThrow(ResourceNotFoundException.builder().message("missing").build())
                .when(client).putSecretValue(any(PutSecretValueRequest.class));

        store.store("hl/hmac/123", "hmac-secret");

        ArgumentCaptor<CreateSecretRequest> request = ArgumentCaptor.forClass(CreateSecretRequest.class);
        verify(client).createSecret(request.capture());
        assertThat(request.getValue().name()).isEqualTo("habitloop-dev/hl/hmac/123");
        assertThat(request.getValue().secretString()).isEqualTo("hmac-secret");
    }

    @Test
    void storeAddsVersionToExistingSecret() {
        store.store("hl/hmac/123", "rotated-secret");

        ArgumentCaptor<PutSecretValueRequest> request = ArgumentCaptor.forClass(PutSecretValueRequest.class);
        verify(client).putSecretValue(request.capture());
        assertThat(request.getValue().secretId()).isEqualTo("habitloop-dev/hl/hmac/123");
        assertThat(request.getValue().secretString()).isEqualTo("rotated-secret");
    }

    @Test
    void resolveReturnsSecretAndMapsLogicalReferenceToPrefixedName() {
        when(client.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder().secretString("hmac-secret").build());

        assertThat(store.resolve("hl/hmac/123")).isEqualTo("hmac-secret");

        ArgumentCaptor<GetSecretValueRequest> request = ArgumentCaptor.forClass(GetSecretValueRequest.class);
        verify(client).getSecretValue(request.capture());
        assertThat(request.getValue().secretId()).isEqualTo("habitloop-dev/hl/hmac/123");
    }

    @Test
    void resolveReturnsNullWhenReferenceIsMissing() {
        doThrow(ResourceNotFoundException.builder().message("missing").build())
                .when(client).getSecretValue(any(GetSecretValueRequest.class));

        assertThat(store.resolve("hl/hmac/missing")).isNull();
    }
}
