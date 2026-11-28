package com.habitloop.integration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.common.exception.InvalidRequestException;
import com.habitloop.common.exception.UnauthorizedException;
import com.habitloop.integration.dto.WebhookResponse;
import com.habitloop.integration.entity.ConnectionStatus;
import com.habitloop.integration.entity.Partner;
import com.habitloop.integration.entity.PartnerConnection;
import com.habitloop.integration.entity.RawPartnerEvent;
import com.habitloop.integration.event.PartnerEventPublisher;
import com.habitloop.integration.repository.PartnerConnectionRepository;
import com.habitloop.integration.repository.RawPartnerEventRepository;
import com.habitloop.integration.security.HmacSigner;
import com.habitloop.integration.security.SecretStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock PartnerConnectionRepository connections;
    @Mock RawPartnerEventRepository rawEvents;
    @Mock SecretStore secretStore;
    @Mock PartnerEventPublisher publisher;

    WebhookService service;

    final UUID connectionId = UUID.randomUUID();
    final UUID tenantId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();
    final String secret = "hmac-secret";
    final String body = "{\"externalEventId\":\"evt_1\",\"type\":\"STEPS\",\"value\":8450}";

    @BeforeEach
    void setUp() {
        service = new WebhookService(connections, rawEvents, secretStore, publisher, new ObjectMapper());
    }

    @Test
    void validSignedEvent_isStored_andPublished() {
        stubConnection(ConnectionStatus.CONNECTED);
        when(secretStore.resolve("ref")).thenReturn(secret);
        when(rawEvents.existsByConnectionIdAndExternalEventId(connectionId, "evt_1")).thenReturn(false);
        when(rawEvents.save(any(RawPartnerEvent.class))).thenAnswer(i -> {
            RawPartnerEvent e = i.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        WebhookResponse res = service.ingest(connectionId, HmacSigner.hex(secret, body), body);

        assertThat(res.deduplicated()).isFalse();
        assertThat(res.status()).isEqualTo("ACCEPTED");
        verify(publisher).publish(any());
    }

    @Test
    void invalidSignature_isRejected() {
        stubConnection(ConnectionStatus.CONNECTED);
        when(secretStore.resolve("ref")).thenReturn(secret);

        assertThatThrownBy(() -> service.ingest(connectionId, "deadbeef", body))
                .isInstanceOf(UnauthorizedException.class);
        verify(publisher, never()).publish(any());
    }

    @Test
    void duplicateEvent_isNoOp() {
        stubConnection(ConnectionStatus.CONNECTED);
        when(secretStore.resolve("ref")).thenReturn(secret);
        when(rawEvents.existsByConnectionIdAndExternalEventId(connectionId, "evt_1")).thenReturn(true);

        WebhookResponse res = service.ingest(connectionId, HmacSigner.hex(secret, body), body);

        assertThat(res.deduplicated()).isTrue();
        verify(rawEvents, never()).save(any());
        verify(publisher, never()).publish(any());
    }

    @Test
    void revokedConnection_isRejected() {
        stubConnection(ConnectionStatus.REVOKED);
        assertThatThrownBy(() -> service.ingest(connectionId, "sig", body))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void missingExternalEventId_isBadRequest() {
        stubConnection(ConnectionStatus.CONNECTED);
        when(secretStore.resolve("ref")).thenReturn(secret);
        String noId = "{\"type\":\"STEPS\"}";

        assertThatThrownBy(() -> service.ingest(connectionId, HmacSigner.hex(secret, noId), noId))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void malformedJson_isBadRequest() {
        stubConnection(ConnectionStatus.CONNECTED);
        when(secretStore.resolve("ref")).thenReturn(secret);
        String malformed = "{\"externalEventId\":";

        assertThatThrownBy(() -> service.ingest(connectionId, HmacSigner.hex(secret, malformed), malformed))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Malformed JSON payload");
        verify(publisher, never()).publish(any());
    }

    @Test
    void trailingContent_isRejectedAsMalformedJson() {
        String malformed = "{\"externalEventId\":\"evt_1\"} trailing";
        assertSignedPayloadRejected(malformed);
    }

    @Test
    void nonStringExternalEventId_isBadRequest() {
        String invalidId = "{\"externalEventId\":123}";
        assertSignedPayloadRejected(invalidId);
    }

    @Test
    void oversizedExternalEventId_isBadRequest() {
        String invalidId = "{\"externalEventId\":\"" + "x".repeat(129) + "\"}";
        assertSignedPayloadRejected(invalidId);
    }

    @Test
    void invalidPartnerFields_areRejectedBeforePublishing() {
        assertSignedPayloadRejected("{\"externalEventId\":\"evt\",\"type\":123}");
        assertSignedPayloadRejected("{\"externalEventId\":\"evt\",\"unit\":\"" + "u".repeat(21) + "\"}");
        assertSignedPayloadRejected("{\"externalEventId\":\"evt\",\"value\":\"many\"}");
        assertSignedPayloadRejected("{\"externalEventId\":\"evt\",\"value\":-1}");
        assertSignedPayloadRejected("{\"externalEventId\":\"evt\",\"occurredAt\":\"yesterday\"}");
        assertSignedPayloadRejected("{\"externalEventId\":\"evt\",\"habitId\":\"not-a-uuid\"}");
        String controlCharacterId = new ObjectMapper().createObjectNode()
                .put("externalEventId", "evt" + (char) 0).toString();
        assertSignedPayloadRejected(controlCharacterId);
    }

    @Test
    void oversizedPayload_isRejectedBeforeConnectionLookup() {
        String oversized = "x".repeat(256 * 1024 + 1);

        assertThatThrownBy(() -> service.ingest(connectionId, "signature", oversized))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Webhook payload must not exceed 256 KB");
        verify(connections, never()).findById(connectionId);
    }

    @Test
    void blankPayload_isRejectedBeforeConnectionLookup() {
        assertThatThrownBy(() -> service.ingest(connectionId, "signature", "  "))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Webhook payload is required");
        verify(connections, never()).findById(connectionId);
    }

    private void assertSignedPayloadRejected(String invalidPayload) {
        stubConnection(ConnectionStatus.CONNECTED);
        when(secretStore.resolve("ref")).thenReturn(secret);

        assertThatThrownBy(() -> service.ingest(connectionId, HmacSigner.hex(secret, invalidPayload), invalidPayload))
                .isInstanceOf(InvalidRequestException.class);
        verify(publisher, never()).publish(any());
        verify(rawEvents, never()).save(any());
    }

    private void stubConnection(ConnectionStatus status) {
        PartnerConnection c = new PartnerConnection();
        c.setId(connectionId);
        c.setTenantId(tenantId);
        c.setUserId(userId);
        c.setPartner(Partner.FITBIT);
        c.setHmacSecretRef("ref");
        c.setStatus(status);
        when(connections.findById(connectionId)).thenReturn(Optional.of(c));
        lenient().when(connections.findForUpdateById(connectionId)).thenReturn(Optional.of(c));
        lenient().when(connections.save(any(PartnerConnection.class))).thenAnswer(i -> i.getArgument(0));
    }
}
