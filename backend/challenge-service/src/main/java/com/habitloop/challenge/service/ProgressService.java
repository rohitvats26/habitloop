package com.habitloop.challenge.service;

import com.habitloop.challenge.entity.Challenge;
import com.habitloop.challenge.entity.ChallengeParticipant;
import com.habitloop.challenge.entity.ChallengeProgressEvent;
import com.habitloop.challenge.entity.ChallengeStatus;
import com.habitloop.challenge.repository.ChallengeParticipantRepository;
import com.habitloop.challenge.repository.ChallengeProgressEventRepository;
import com.habitloop.challenge.repository.ChallengeRepository;
import com.habitloop.common.time.TimeZones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Applies an {@code activity.logged} event to every active challenge the user participates in. Each
 * (participant, activityLogId) pair is applied at most once via a unique progress-event row, so a
 * replayed event never double-counts progress (NFR-2, docs/07). Runs off the Kafka consumer thread, so
 * the tenant id comes from the event, not a request.
 */
@Service
public class ProgressService {

    private static final Logger log = LoggerFactory.getLogger(ProgressService.class);

    private final ChallengeParticipantRepository participants;
    private final ChallengeRepository challenges;
    private final ChallengeProgressEventRepository progressEvents;
    private final AchievementService achievements;

    public ProgressService(ChallengeParticipantRepository participants, ChallengeRepository challenges,
                           ChallengeProgressEventRepository progressEvents, AchievementService achievements) {
        this.participants = participants;
        this.challenges = challenges;
        this.progressEvents = progressEvents;
        this.achievements = achievements;
    }

    @Transactional
    public void applyActivity(UUID tenantId, UUID userId, UUID activityLogId, OffsetDateTime occurredAt) {
        List<ChallengeParticipant> parts = participants.findByTenantIdAndUserIdOrderByIdAsc(tenantId, userId);
        LocalDate activityDate = occurredAt.atZoneSameInstant(TimeZones.INDIA).toLocalDate();
        for (ChallengeParticipant p : parts) {
            Challenge c = challenges.findByIdAndTenantIdAndDeletedAtIsNull(p.getChallengeId(), tenantId)
                    .orElse(null);
            LocalDate joinedDate = p.getJoinedAt().atZoneSameInstant(TimeZones.INDIA).toLocalDate();
            if (c == null || c.getStatus() != ChallengeStatus.ACTIVE || !c.isWithinWindow(activityDate)
                    || activityDate.isBefore(joinedDate)) {
                continue;
            }
            if (progressEvents.existsByParticipantIdAndActivityLogId(p.getId(), activityLogId)) {
                continue; // already counted
            }
            ChallengeProgressEvent ev = new ChallengeProgressEvent();
            ev.setTenantId(tenantId);
            ev.setParticipantId(p.getId());
            ev.setActivityLogId(activityLogId);
            progressEvents.save(ev);
            p.setProgress(p.getProgress().add(BigDecimal.ONE));
            boolean newlyCompleted = !p.isCompleted() && p.getProgress().compareTo(c.getTarget()) >= 0;
            if (newlyCompleted) {
                p.setCompleted(true);
                achievements.awardChallengeCompletion(tenantId, userId, c.getId());
            }
            participants.save(p);
            log.debug("Applied activity {} to participant {} (progress {})",
                    activityLogId, p.getId(), p.getProgress());
        }
    }
}
