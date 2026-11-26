package com.habitloop.activity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "habit_pause_periods")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class HabitPausePeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "habit_id", nullable = false)
    private UUID habitId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "resume_date", nullable = false)
    private LocalDate resumeDate;

    @Column(name = "resumed_at")
    private OffsetDateTime resumedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
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

    public UUID getHabitId() {
        return habitId;
    }

    public void setHabitId(UUID habitId) {
        this.habitId = habitId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getResumeDate() {
        return resumeDate;
    }

    public void setResumeDate(LocalDate resumeDate) {
        this.resumeDate = resumeDate;
    }

    public OffsetDateTime getResumedAt() {
        return resumedAt;
    }

    public void setResumedAt(OffsetDateTime resumedAt) {
        this.resumedAt = resumedAt;
    }
}
