package com.habitloop.integration.service;

import com.habitloop.common.exception.DuplicateResourceException;
import com.habitloop.common.security.AuthUser;
import com.habitloop.integration.config.IntegrationProperties;
import com.habitloop.integration.dto.ConnectionResponse;
import com.habitloop.integration.dto.CreateConnectionRequest;
import com.habitloop.integration.entity.Partner;
import com.habitloop.integration.entity.PartnerConnection;
import com.habitloop.integration.mapper.ConnectionMapper;
import com.habitloop.integration.repository.PartnerConnectionRepository;
import com.habitloop.integration.security.SecretStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectionServiceTest {

    @Mock PartnerConnectionRepository connections;
    @Mock PasswordEncoder passwordEncoder;
    @Mock SecretStore secretStore;

    ConnectionService service;

    final UUID tenantId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ConnectionService(connections, new ConnectionMapper(), passwordEncoder, secretStore,
                new IntegrationProperties());
        var principal = new AuthUser(userId.toString(), tenantId.toString(), List.of("USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_returnsApiKeyOnce_storesSecret_buildsWebhookUrl() {
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(connections.save(any(PartnerConnection.class))).thenAnswer(i -> {
            PartnerConnection c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        ConnectionResponse res = service.create(new CreateConnectionRequest(Partner.FITBIT));

        assertThat(res.apiKey()).startsWith("hl_live_");
        assertThat(res.hmacSecret()).isNotBlank();
        assertThat(res.webhookUrl()).contains("/api/webhooks/" + res.connectionId());
        verify(secretStore).store(anyString(), anyString());
        verify(passwordEncoder).encode(anyString()); // api key is hashed, not stored raw
    }

    @Test
    void create_duplicatePartner_throwsConflict() {
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(connections.save(any(PartnerConnection.class)))
                .thenThrow(new DataIntegrityViolationException("uq_connection"));

        assertThatThrownBy(() -> service.create(new CreateConnectionRequest(Partner.FITBIT)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void list_mapsWithoutApiKey() {
        PartnerConnection c = new PartnerConnection();
        c.setId(UUID.randomUUID());
        c.setTenantId(tenantId);
        c.setUserId(userId);
        c.setPartner(Partner.GARMIN);
        when(connections.findByUserIdAndTenantId(userId, tenantId)).thenReturn(List.of(c));

        List<ConnectionResponse> res = service.list();

        assertThat(res).hasSize(1);
        assertThat(res.get(0).apiKey()).isNull();
        verify(connections).findByUserIdAndTenantId(eq(userId), eq(tenantId));
    }
}
