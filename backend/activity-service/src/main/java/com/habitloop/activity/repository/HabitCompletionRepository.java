package com.habitloop.activity.repository;

import com.habitloop.activity.entity.HabitCompletion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface HabitCompletionRepository extends JpaRepository<HabitCompletion, UUID> {

    boolean existsByHabitIdAndCompletionDate(UUID habitId, LocalDate completionDate);

    Page<HabitCompletion> findByHabitIdAndTenantId(UUID habitId, UUID tenantId, Pageable pageable);

    int countByHabitIdAndTenantIdAndCompletionDateBetween(UUID habitId, UUID tenantId,
                                                           LocalDate from, LocalDate to);

    @Query("select c.completionDate from HabitCompletion c where c.habitId = :habitId and c.tenantId = :tenantId order by c.completionDate")
    List<LocalDate> findDates(@Param("habitId") UUID habitId, @Param("tenantId") UUID tenantId);

    @Query("select c.completionDate from HabitCompletion c where c.habitId = :habitId and c.tenantId = :tenantId and c.completionDate >= :from order by c.completionDate")
    List<LocalDate> findDatesFrom(@Param("habitId") UUID habitId, @Param("tenantId") UUID tenantId,
                                  @Param("from") LocalDate from);
}
