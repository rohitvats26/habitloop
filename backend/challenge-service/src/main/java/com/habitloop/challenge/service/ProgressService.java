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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    public ProgressService(ChallengeParticipantRepository participants, ChallengeRepository challenges,
                           ChallengeProgressEventRepository progressEvents) {
        this.participants = participants;
        this.challenges = challenges;
        this.progressEvents = progressEvents;
    }

    @Transactional
    public void applyActivity(UUID tenantId, UUID userId, UUID activityLogId) {
        List<ChallengeParticipant> parts = participants.findByTenantIdAndUserId(tenantId, userId);
        LocalDate today = LocalDate.now(TimeZones.INDIA);
        for (ChallengeParticipant p : parts) {
            Challenge c = challenges.findByIdAndTenantIdAndDeletedAtIsNull(p.getChallengeId(), tenantId)
                    .orElse(null);
            if (c == null || c.getStatus() != ChallengeStatus.ACTIVE || !c.isWithinWindow(today)) {
                continue;
            }
            if (progressEvents.existsByParticipantIdAndActivityLogId(p.getId(), activityLogId)) {
                continue; // already counted
            }
            try {
                ChallengeProgressEvent ev = new ChallengeProgressEvent();
                ev.setTenantId(tenantId);
                ev.setParticipantId(p.getId());
                ev.setActivityLogId(activityLogId);
                progressEvents.save(ev);
            } catch (DataIntegrityViolationException dup) {
                continue; // concurrent delivery already counted it
            }
            p.setProgress(p.getProgress().add(BigDecimal.ONE));
            if (p.getProgress().compareTo(c.getTarget()) >= 0) {
                p.setCompleted(true);
            }
            participants.save(p);
            log.debug("Applied activity {} to participant {} (progress {})",
                    activityLogId, p.getId(), p.getProgress());
        }
    }
}
