package com.habitloop.challenge.repository;

import com.habitloop.challenge.entity.AchievementAward;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface AchievementAwardRepository extends JpaRepository<AchievementAward, UUID> {

    Page<AchievementAward> findByTenantIdAndUserIdOrderByAwardedAtDesc(
            UUID tenantId, UUID userId, Pageable pageable);

    @Modifying
    @Query(value = """
            INSERT INTO achievement_awards
                (id, tenant_id, user_id, code, source_key, title, description, awarded_at)
            VALUES
                (:id, :tenantId, :userId, :code, :sourceKey, :title, :description, :awardedAt)
            ON CONFLICT (tenant_id, user_id, code, source_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
                       @Param("userId") UUID userId, @Param("code") String code,
                       @Param("sourceKey") String sourceKey, @Param("title") String title,
                       @Param("description") String description, @Param("awardedAt") OffsetDateTime awardedAt);
}
