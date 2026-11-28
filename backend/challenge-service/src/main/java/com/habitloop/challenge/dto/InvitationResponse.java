package com.habitloop.challenge.dto;

import com.habitloop.challenge.entity.InvitationStatus;

public record InvitationResponse(
        String id,
        String challengeId,
        String challengeTitle,
        String inviterId,
        String inviterName,
        String inviteeId,
        InvitationStatus status
) {
}
