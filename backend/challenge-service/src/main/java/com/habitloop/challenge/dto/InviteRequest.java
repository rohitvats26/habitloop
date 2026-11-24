package com.habitloop.challenge.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InviteRequest(@NotNull UUID userId) {
}
