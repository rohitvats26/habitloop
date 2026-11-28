package com.habitloop.activity.repository;

import com.habitloop.activity.entity.HabitScheduleVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface HabitScheduleVersionRepository extends JpaRepository<HabitScheduleVersion, UUID> {
    List<HabitScheduleVersion> findByHabitIdAndTenantIdOrderByEffectiveDate(UUID habitId, UUID tenantId);

    void deleteByHabitIdAndTenantIdAndEffectiveDateAfter(UUID habitId, UUID tenantId, LocalDate date);
}
