package com.habitloop.challenge.mapper;

import com.habitloop.challenge.dto.ChallengeDetailResponse;
import com.habitloop.challenge.dto.ChallengeSummary;
import com.habitloop.challenge.dto.InvitationResponse;
import com.habitloop.challenge.dto.LeaderboardEntry;
import com.habitloop.challenge.entity.Challenge;
import com.habitloop.challenge.entity.ChallengeInvitation;
import com.habitloop.challenge.entity.ChallengeParticipant;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ChallengeMapper {

    public ChallengeSummary toSummary(Challenge c) {
        return new ChallengeSummary(
                c.getId().toString(), c.getTitle(), c.getDescription(),
                c.getVisibility(), c.getStatus(), c.getTarget(), c.getStartsOn(), c.getEndsOn());
    }

    public ChallengeDetailResponse toDetail(Challenge c, ChallengeParticipant me,
                                            List<ChallengeParticipant> participants) {
        List<LeaderboardEntry> leaderboard = participants.stream()
                .map(p -> new LeaderboardEntry(p.getUserId().toString(), p.getProgress(), p.isCompleted()))
                .toList();
        BigDecimal myProgress = me == null ? null : me.getProgress();
        boolean completed = me != null && me.isCompleted();
        return new ChallengeDetailResponse(
                c.getId().toString(), c.getTitle(), c.getDescription(), c.getVisibility(), c.getStatus(),
                c.getTarget(), c.getStartsOn(), c.getEndsOn(), myProgress, completed, leaderboard);
    }

    public InvitationResponse toInvitationResponse(ChallengeInvitation inv) {
        return new InvitationResponse(
                inv.getId().toString(), inv.getChallengeId().toString(),
                inv.getInviterId().toString(), inv.getInviteeId().toString(), inv.getStatus());
    }
}
