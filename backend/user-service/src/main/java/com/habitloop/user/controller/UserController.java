package com.habitloop.user.controller;

import com.habitloop.user.dto.UpdateProfileRequest;
import com.habitloop.user.dto.UserResponse;
import com.habitloop.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current user's profile")
    public UserResponse me() {
        return userService.getCurrentUser();
    }

    @PutMapping("/me")
    @Operation(summary = "Update the current user's profile")
    public UserResponse updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateCurrentProfile(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Admin: get a user in the current tenant")
    public UserResponse getUser(@PathVariable UUID id) {
        return userService.getUserInTenant(id);
    }
}
