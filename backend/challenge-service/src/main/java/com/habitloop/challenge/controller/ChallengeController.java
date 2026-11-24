package com.habitloop.challenge.controller;

import com.habitloop.challenge.dto.ChallengeDetailResponse;
import com.habitloop.challenge.dto.ChallengeSummary;
import com.habitloop.challenge.dto.CreateChallengeRequest;
import com.habitloop.challenge.dto.InvitationResponse;
import com.habitloop.challenge.dto.InviteRequest;
import com.habitloop.challenge.service.ChallengeService;
import com.habitloop.challenge.service.InvitationService;
import com.habitloop.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/challenges")
@Tag(name = "Challenges")
public class ChallengeController {

    private final ChallengeService challengeService;
    private final InvitationService invitationService;

    public ChallengeController(ChallengeService challengeService, InvitationService invitationService) {
        this.challengeService = challengeService;
        this.invitationService = invitationService;
    }

    @PostMapping
    @Operation(summary = "Create a public or private challenge")
    public ResponseEntity<ChallengeSummary> create(@Valid @RequestBody CreateChallengeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(challengeService.create(request));
    }

    @GetMapping
    @Operation(summary = "Challenge feed: public challenges in the tenant plus the caller's own")
    public PageResponse<ChallengeSummary> feed(@PageableDefault(size = 20) Pageable pageable) {
        return challengeService.feed(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Challenge details with the caller's progress and the leaderboard")
    public ChallengeDetailResponse details(@PathVariable UUID id) {
        return challengeService.details(id);
    }

    @PostMapping("/{id}/join")
    @Operation(summary = "Join a public challenge")
    public ChallengeDetailResponse join(@PathVariable UUID id) {
        return challengeService.join(id);
    }

    @PostMapping("/{id}/invites")
    @Operation(summary = "Invite a user (same tenant) to a challenge")
    public ResponseEntity<InvitationResponse> invite(@PathVariable UUID id,
                                                     @Valid @RequestBody InviteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invitationService.invite(id, request.userId()));
    }
}
