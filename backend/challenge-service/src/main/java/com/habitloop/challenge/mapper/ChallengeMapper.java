package com.habitloop.challenge.mapper;

import com.habitloop.challenge.dto.ChallengeDetailResponse;
import com.habitloop.challenge.dto.ChallengeSummary;
import com.habitloop.challenge.dto.InvitationResponse;
import com.habitloop.challenge.dto.LeaderboardEntry;
import com.habitloop.challenge.entity.Challenge;
import com.habitloop.challenge.entity.ChallengeInvitation;
import com.habitloop.challenge.entity.ChallengeParticipant;
import com.habitloop.challenge.entity.ChallengeStatus;
import com.habitloop.common.time.TimeZones;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class ChallengeMapper {

    public ChallengeSummary toSummary(Challenge c, boolean joined) {
        return new ChallengeSummary(
                c.getId().toString(), c.getTitle(), c.getDescription(),
                c.getVisibility(), c.statusOn(LocalDate.now(TimeZones.INDIA)),
                c.getTarget(), c.getStartsOn(), c.getEndsOn(), joined);
    }

    public ChallengeDetailResponse toDetail(Challenge c, ChallengeParticipant me,
                                            List<ChallengeParticipant> participants, Map<String, String> names,
                                            boolean viewerIsCreator) {
        List<LeaderboardEntry> leaderboard = participants.stream()
                .map(p -> new LeaderboardEntry(p.getUserId().toString(), names.get(p.getUserId().toString()),
                        p.getProgress(), p.isCompleted()))
                .toList();
        BigDecimal myProgress = me == null ? null : me.getProgress();
        boolean completed = me != null && me.isCompleted();
        ChallengeStatus status = c.statusOn(LocalDate.now(TimeZones.INDIA));
        return new ChallengeDetailResponse(
                c.getId().toString(), c.getTitle(), c.getDescription(), c.getVisibility(), status,
                c.getTarget(), c.getStartsOn(), c.getEndsOn(), myProgress, completed,
                viewerIsCreator && status == ChallengeStatus.ACTIVE, leaderboard);
    }

    public InvitationResponse toInvitationResponse(ChallengeInvitation inv) {
        return toInvitationResponse(inv, null, null);
    }

    public InvitationResponse toInvitationResponse(ChallengeInvitation inv, String challengeTitle,
                                                   String inviterName) {
        return new InvitationResponse(
                inv.getId().toString(), inv.getChallengeId().toString(), challengeTitle,
                inv.getInviterId().toString(), inviterName, inv.getInviteeId().toString(), inv.getStatus());
    }
}
