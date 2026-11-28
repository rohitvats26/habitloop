package com.habitloop.activity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "habit_schedule_versions")
public class HabitScheduleVersion {
    @Id
    private UUID id;

    @Column(name = "habit_id", nullable = false)
    private UUID habitId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Frequency frequency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String schedule;

    @Column(name = "target_per_period", nullable = false)
    private int targetPerPeriod;

    public HabitScheduleVersion() {
    }

    public HabitScheduleVersion(UUID habitId, UUID tenantId, LocalDate effectiveDate,
                                Frequency frequency, String schedule, int targetPerPeriod) {
        this.id = UUID.randomUUID();
        this.habitId = habitId;
        this.tenantId = tenantId;
        this.effectiveDate = effectiveDate;
        this.frequency = frequency;
        this.schedule = schedule;
        this.targetPerPeriod = targetPerPeriod;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public String getSchedule() {
        return schedule;
    }

    public int getTargetPerPeriod() {
        return targetPerPeriod;
    }
}
