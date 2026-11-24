package com.habitloop.integration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A user's connection to a wearable/partner. Stores only the API-key <em>hash</em> and a
 * <em>reference</em> to the HMAC secret (resolved from Secrets Manager / local secret store) — never the
 * secret itself (docs/08, no secrets in RDS).
 */
@Entity
@Table(name = "partner_connections", uniqueConstraints = @UniqueConstraint(
        name = "uq_connection", columnNames = {"tenant_id", "user_id", "partner"}))
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = UUID.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class PartnerConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Partner partner;

    @Column(name = "api_key_hash", nullable = false, length = 100)
    private String apiKeyHash;

    @Column(name = "hmac_secret_ref", nullable = false, length = 200)
    private String hmacSecretRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ConnectionStatus status = ConnectionStatus.CONNECTED;

    @Column(name = "connected_at", nullable = false)
    private OffsetDateTime connectedAt;

    @Column(name = "last_event_at")
    private OffsetDateTime lastEventAt;

    @PrePersist
    void onCreate() {
        this.connectedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Partner getPartner() {
        return partner;
    }

    public void setPartner(Partner partner) {
        this.partner = partner;
    }

    public String getApiKeyHash() {
        return apiKeyHash;
    }

    public void setApiKeyHash(String apiKeyHash) {
        this.apiKeyHash = apiKeyHash;
    }

    public String getHmacSecretRef() {
        return hmacSecretRef;
    }

    public void setHmacSecretRef(String hmacSecretRef) {
        this.hmacSecretRef = hmacSecretRef;
    }

    public ConnectionStatus getStatus() {
        return status;
    }

    public void setStatus(ConnectionStatus status) {
        this.status = status;
    }

    public OffsetDateTime getConnectedAt() {
        return connectedAt;
    }

    public OffsetDateTime getLastEventAt() {
        return lastEventAt;
    }

    public void setLastEventAt(OffsetDateTime lastEventAt) {
        this.lastEventAt = lastEventAt;
    }
}
