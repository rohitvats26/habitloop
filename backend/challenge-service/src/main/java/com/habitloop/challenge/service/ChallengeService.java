package com.habitloop.challenge.service;

import com.habitloop.challenge.client.UserClient;
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
import com.habitloop.common.time.TimeZones;
import com.habitloop.common.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ChallengeService {

    private final ChallengeRepository challenges;
    private final ChallengeParticipantRepository participants;
    private final ChallengeMapper mapper;
    private final AchievementService achievements;
    private final UserClient userClient;

    public ChallengeService(ChallengeRepository challenges, ChallengeParticipantRepository participants,
                            ChallengeMapper mapper, AchievementService achievements, UserClient userClient) {
        this.challenges = challenges;
        this.participants = participants;
        this.mapper = mapper;
        this.achievements = achievements;
        this.userClient = userClient;
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
        return mapper.toSummary(c, true);
    }

    @Transactional(readOnly = true)
    public PageResponse<ChallengeSummary> feed(Pageable pageable) {
        UUID tenant = tenantId();
        UUID user = userId();
        var page = challenges.feed(tenant, user, Visibility.PUBLIC, pageable);
        var ids = page.getContent().stream().map(Challenge::getId).toList();
        Set<UUID> joined = ids.isEmpty() ? Set.of()
                : participants.findByTenantIdAndUserIdAndChallengeIdIn(tenant, user, ids).stream()
                        .map(ChallengeParticipant::getChallengeId).collect(Collectors.toSet());
        return PageResponse.of(page.map(c -> mapper.toSummary(c, joined.contains(c.getId()))));
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
        var names = userClient.displayNames(leaderboard.stream().map(ChallengeParticipant::getUserId).toList());
        return mapper.toDetail(c, me, leaderboard, names, isCreator);
    }

    @Transactional
    public ChallengeDetailResponse join(UUID challengeId) {
        Challenge c = requireChallenge(challengeId);
        if (c.getVisibility() == Visibility.PRIVATE) {
            throw new CrossTenantAccessException("Private challenge requires an invitation");
        }
        if (!participants.existsByChallengeIdAndUserId(challengeId, userId())) {
            requireOpen(c);
            addParticipant(c, userId());
        }
        return details(challengeId);
    }

    void requireOpen(Challenge c) {
        if (c.statusOn(LocalDate.now(TimeZones.INDIA)) != ChallengeStatus.ACTIVE) {
            throw new BusinessException("This challenge is not open to join");
        }
    }

    ChallengeParticipant addParticipant(Challenge c, UUID user) {
        ChallengeParticipant p = new ChallengeParticipant();
        p.setTenantId(c.getTenantId());
        p.setChallengeId(c.getId());
        p.setUserId(user);
        ChallengeParticipant saved = participants.save(p);
        achievements.awardChallengeParticipation(c.getTenantId(), user, c.getId());
        return saved;
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
