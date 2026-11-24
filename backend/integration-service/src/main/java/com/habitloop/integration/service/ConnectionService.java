package com.habitloop.integration.service;

import com.habitloop.common.exception.DuplicateResourceException;
import com.habitloop.common.exception.ResourceNotFoundException;
import com.habitloop.common.security.SecurityUtils;
import com.habitloop.integration.config.IntegrationProperties;
import com.habitloop.integration.dto.ConnectionResponse;
import com.habitloop.integration.dto.CreateConnectionRequest;
import com.habitloop.integration.entity.ConnectionStatus;
import com.habitloop.integration.entity.PartnerConnection;
import com.habitloop.integration.mapper.ConnectionMapper;
import com.habitloop.integration.repository.PartnerConnectionRepository;
import com.habitloop.integration.security.SecretStore;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class ConnectionService {

    private static final String API_KEY_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final SecureRandom random = new SecureRandom();

    private final PartnerConnectionRepository connections;
    private final ConnectionMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final SecretStore secretStore;
    private final IntegrationProperties props;

    public ConnectionService(PartnerConnectionRepository connections, ConnectionMapper mapper,
                             PasswordEncoder passwordEncoder, SecretStore secretStore,
                             IntegrationProperties props) {
        this.connections = connections;
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.secretStore = secretStore;
        this.props = props;
    }

    @Transactional
    public ConnectionResponse create(CreateConnectionRequest req) {
        UUID tenantId = tenantId();
        UUID userId = userId();

        String apiKey = "hl_live_" + randomString(24);
        String hmacSecret = randomSecret();
        String secretRef = "hl/hmac/" + UUID.randomUUID();
        secretStore.store(secretRef, hmacSecret);

        PartnerConnection c = new PartnerConnection();
        c.setTenantId(tenantId);
        c.setUserId(userId);
        c.setPartner(req.partner());
        c.setApiKeyHash(passwordEncoder.encode(apiKey));
        c.setHmacSecretRef(secretRef);
        c.setStatus(ConnectionStatus.CONNECTED);

        try {
            c = connections.save(c);
        } catch (DataIntegrityViolationException dup) {
            throw new DuplicateResourceException("A connection for this partner already exists");
        }
        // apiKey and the HMAC secret are returned once (never stored in plaintext; the secret lives
        // only in the SecretStore / Secrets Manager).
        return mapper.toResponse(c, apiKey, hmacSecret, props.getWebhookBaseUrl());
    }

    @Transactional(readOnly = true)
    public List<ConnectionResponse> list() {
        return connections.findByUserIdAndTenantId(userId(), tenantId()).stream()
                .map(c -> mapper.toResponse(c, null, null, props.getWebhookBaseUrl()))
                .toList();
    }

    @Transactional
    public void revoke(UUID connectionId) {
        PartnerConnection c = connections.findByIdAndUserIdAndTenantId(connectionId, userId(), tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Connection", connectionId));
        c.setStatus(ConnectionStatus.REVOKED);
        connections.save(c);
    }

    private String randomString(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(API_KEY_ALPHABET.charAt(random.nextInt(API_KEY_ALPHABET.length())));
        }
        return sb.toString();
    }

    private String randomSecret() {
        byte[] buf = new byte[32];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private UUID tenantId() {
        return UUID.fromString(SecurityUtils.currentTenantId());
    }

    private UUID userId() {
        return UUID.fromString(SecurityUtils.currentUserId());
    }
}
