package com.habitloop.user.controller;

import com.habitloop.user.dto.UserResponse;
import com.habitloop.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Service-to-service endpoints (e.g. challenge-service verifying an invitee). Same-tenant scoped via the
 * propagated JWT. In AWS these are reachable only within the VPC; still authenticated (defense in depth).
 */
@RestController
@RequestMapping("/internal/users")
@Tag(name = "Internal")
public class InternalUserController {

    private final UserService userService;

    public InternalUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Internal: verify a user exists in the caller's tenant")
    public UserResponse getUser(@PathVariable UUID id) {
        return userService.getUserInTenant(id);
    }
}
