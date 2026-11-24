package com.habitloop.challenge.controller;

import com.habitloop.challenge.dto.InvitationResponse;
import com.habitloop.challenge.service.InvitationService;
import com.habitloop.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/invitations")
@Tag(name = "Invitations")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @GetMapping
    @Operation(summary = "List the caller's pending invitations")
    public PageResponse<InvitationResponse> pending(@PageableDefault(size = 20) Pageable pageable) {
        return invitationService.myPendingInvitations(pageable);
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Accept an invitation and join the challenge")
    public InvitationResponse accept(@PathVariable UUID id) {
        return invitationService.accept(id);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject an invitation")
    public InvitationResponse reject(@PathVariable UUID id) {
        return invitationService.reject(id);
    }
}
