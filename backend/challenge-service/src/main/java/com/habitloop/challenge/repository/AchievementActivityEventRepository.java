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
            where e.tenantId = :tenantId and e.userId = :userId and e.habitId = :habitId
            order by e.completionDate desc
            """)
    List<LocalDate> findDistinctDates(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId,
                                      @Param("habitId") UUID habitId);

    @Modifying
    @Query(value = """
            INSERT INTO achievement_activity_users (tenant_id, user_id) VALUES (:tenantId, :userId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int ensureUser(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    @Query(value = """
            SELECT user_id FROM achievement_activity_users
            WHERE tenant_id = :tenantId AND user_id = :userId FOR UPDATE
            """, nativeQuery = true)
    UUID lockUser(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    @Modifying
    @Query(value = """
            INSERT INTO achievement_activity_events
                (id, tenant_id, user_id, activity_log_id, habit_id, completion_date, created_at)
            VALUES
                (:id, :tenantId, :userId, :activityLogId, :habitId, :completionDate, CURRENT_TIMESTAMP)
            ON CONFLICT (tenant_id, user_id, activity_log_id) DO UPDATE
                SET habit_id = EXCLUDED.habit_id
                WHERE achievement_activity_events.habit_id IS NULL
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
                       @Param("userId") UUID userId, @Param("activityLogId") UUID activityLogId,
                       @Param("habitId") UUID habitId,
                       @Param("completionDate") LocalDate completionDate);

    @Modifying
    @Query(value = """
            INSERT INTO achievement_activity_events
                (id, tenant_id, user_id, activity_log_id, completion_date, created_at)
            VALUES
                (:id, :tenantId, :userId, :activityLogId, :completionDate, CURRENT_TIMESTAMP)
            ON CONFLICT (tenant_id, user_id, activity_log_id) DO NOTHING
            """, nativeQuery = true)
    int insertWithoutHabitIfAbsent(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
                                   @Param("userId") UUID userId, @Param("activityLogId") UUID activityLogId,
                                   @Param("completionDate") LocalDate completionDate);
}
