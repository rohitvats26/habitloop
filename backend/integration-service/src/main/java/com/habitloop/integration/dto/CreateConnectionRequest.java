package com.habitloop.integration.dto;

import com.habitloop.integration.entity.Partner;
import jakarta.validation.constraints.NotNull;

public record CreateConnectionRequest(@NotNull Partner partner) {
}
