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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "achievement_activity_events", uniqueConstraints = @UniqueConstraint(
        name = "uq_achievement_activity_event", columnNames = {"tenant_id", "user_id", "activity_log_id"}))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class AchievementActivityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "activity_log_id", nullable = false)
    private UUID activityLogId;

    @Column(name = "completion_date", nullable = false)
    private LocalDate completionDate;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
