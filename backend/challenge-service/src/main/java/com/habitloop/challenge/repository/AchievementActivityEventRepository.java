package com.habitloop.challenge.repository;

import com.habitloop.challenge.entity.AchievementActivityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AchievementActivityEventRepository extends JpaRepository<AchievementActivityEvent, UUID> {

    long countByTenantIdAndUserId(UUID tenantId, UUID userId);

    @Query("""
            select distinct e.completionDate from AchievementActivityEvent e
            where e.tenantId = :tenantId and e.userId = :userId
            order by e.completionDate desc
            """)
    List<LocalDate> findDistinctDates(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    @Modifying
    @Query(value = """
            INSERT INTO achievement_activity_events
                (id, tenant_id, user_id, activity_log_id, completion_date, created_at)
            VALUES
                (:id, :tenantId, :userId, :activityLogId, :completionDate, CURRENT_TIMESTAMP)
            ON CONFLICT (tenant_id, user_id, activity_log_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
                       @Param("userId") UUID userId, @Param("activityLogId") UUID activityLogId,
                       @Param("completionDate") LocalDate completionDate);
}
