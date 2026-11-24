package com.habitloop.challenge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Filter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Records that a given activity log has already been applied to a participant's progress. The unique
 * (participant_id, activity_log_id) constraint makes progress updates idempotent, so a replayed
 * {@code activity.logged} never double-counts (docs/07).
 */
@Entity
@Table(name = "challenge_progress_events", uniqueConstraints = @UniqueConstraint(
        name = "uq_progress_event", columnNames = {"participant_id", "activity_log_id"}))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class ChallengeProgressEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "participant_id", nullable = false)
    private UUID participantId;

    @Column(name = "activity_log_id", nullable = false)
    private UUID activityLogId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
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

    public UUID getParticipantId() {
        return participantId;
    }

    public void setParticipantId(UUID participantId) {
        this.participantId = participantId;
    }

    public UUID getActivityLogId() {
        return activityLogId;
    }

    public void setActivityLogId(UUID activityLogId) {
        this.activityLogId = activityLogId;
    }
}
