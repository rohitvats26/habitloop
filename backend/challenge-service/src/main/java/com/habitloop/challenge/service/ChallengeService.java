package com.habitloop.challenge.service;

import com.habitloop.challenge.dto.ChallengeDetailResponse;
import com.habitloop.challenge.dto.ChallengeSummary;
import com.habitloop.challenge.dto.CreateChallengeRequest;
import com.habitloop.challenge.entity.Challenge;
import com.habitloop.challenge.entity.ChallengeParticipant;
import com.habitloop.challenge.entity.ChallengeStatus;
import com.habitloop.challenge.entity.Visibility;
import com.habitloop.challenge.mapper.ChallengeMapper;
import com.habitloop.challenge.repository.ChallengeParticipantRepository;
import com.habitloop.challenge.repository.ChallengeRepository;
import com.habitloop.common.exception.BusinessException;
import com.habitloop.common.exception.CrossTenantAccessException;
import com.habitloop.common.exception.ResourceNotFoundException;
import com.habitloop.common.security.SecurityUtils;
import com.habitloop.common.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ChallengeService {

    private final ChallengeRepository challenges;
    private final ChallengeParticipantRepository participants;
    private final ChallengeMapper mapper;

    public ChallengeService(ChallengeRepository challenges, ChallengeParticipantRepository participants,
                            ChallengeMapper mapper) {
        this.challenges = challenges;
        this.participants = participants;
        this.mapper = mapper;
    }

    @Transactional
    public ChallengeSummary create(CreateChallengeRequest req) {
        if (req.endsOn().isBefore(req.startsOn())) {
            throw new BusinessException("endsOn must not be before startsOn");
        }
        Challenge c = new Challenge();
        c.setTenantId(tenantId());
        c.setCreatorId(userId());
        c.setTitle(req.title().trim());
        c.setDescription(req.description());
        c.setVisibility(req.visibility());
        c.setMetric(req.metric().trim());
        c.setTarget(req.target());
        c.setStartsOn(req.startsOn());
        c.setEndsOn(req.endsOn());
        c.setStatus(ChallengeStatus.ACTIVE);
        c = challenges.save(c);

        addParticipant(c, userId()); // creator auto-joins
        return mapper.toSummary(c);
    }

    @Transactional(readOnly = true)
    public PageResponse<ChallengeSummary> feed(Pageable pageable) {
        var page = challenges.feed(tenantId(), userId(), Visibility.PUBLIC, pageable)
                .map(mapper::toSummary);
        return PageResponse.of(page);
    }

    @Transactional(readOnly = true)
    public ChallengeDetailResponse details(UUID challengeId) {
        Challenge c = requireChallenge(challengeId);
        ChallengeParticipant me = participants.findByChallengeIdAndUserId(challengeId, userId()).orElse(null);
        boolean isCreator = c.getCreatorId().equals(userId());
        if (c.getVisibility() == Visibility.PRIVATE && me == null && !isCreator) {
            throw new ResourceNotFoundException("Challenge", challengeId); // hide private challenges
        }
        var leaderboard = participants.findByChallengeIdAndTenantIdOrderByProgressDesc(challengeId, tenantId());
        return mapper.toDetail(c, me, leaderboard);
    }

    @Transactional
    public ChallengeDetailResponse join(UUID challengeId) {
        Challenge c = requireChallenge(challengeId);
        if (c.getVisibility() == Visibility.PRIVATE) {
            throw new CrossTenantAccessException("Private challenge requires an invitation");
        }
        if (!participants.existsByChallengeIdAndUserId(challengeId, userId())) {
            addParticipant(c, userId());
        }
        return details(challengeId);
    }

    ChallengeParticipant addParticipant(Challenge c, UUID user) {
        ChallengeParticipant p = new ChallengeParticipant();
        p.setTenantId(c.getTenantId());
        p.setChallengeId(c.getId());
        p.setUserId(user);
        return participants.save(p);
    }

    private Challenge requireChallenge(UUID challengeId) {
        return challenges.findByIdAndTenantIdAndDeletedAtIsNull(challengeId, tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Challenge", challengeId));
    }

    private UUID tenantId() {
        return UUID.fromString(SecurityUtils.currentTenantId());
    }

    private UUID userId() {
        return UUID.fromString(SecurityUtils.currentUserId());
    }
}
