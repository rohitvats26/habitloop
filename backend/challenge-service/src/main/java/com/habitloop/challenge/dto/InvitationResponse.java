package com.habitloop.challenge.dto;

import com.habitloop.challenge.entity.InvitationStatus;

public record InvitationResponse(
        String id,
        String challengeId,
        String inviterId,
        String inviteeId,
        InvitationStatus status
) {
}
