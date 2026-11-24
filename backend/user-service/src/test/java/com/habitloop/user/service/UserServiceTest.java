package com.habitloop.user.service;

import com.habitloop.common.exception.ResourceNotFoundException;
import com.habitloop.common.security.AuthUser;
import com.habitloop.user.dto.UpdateProfileRequest;
import com.habitloop.user.dto.UserResponse;
import com.habitloop.user.entity.Role;
import com.habitloop.user.entity.User;
import com.habitloop.user.entity.UserProfile;
import com.habitloop.user.mapper.UserMapper;
import com.habitloop.user.repository.UserProfileRepository;
import com.habitloop.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository users;
    @Mock UserProfileRepository profiles;

    UserService userService;

    final UUID tenantId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userService = new UserService(users, profiles, new UserMapper());
        var principal = new AuthUser(userId.toString(), tenantId.toString(), List.of("USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUser_returnsProfileData() {
        when(users.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(buildUser()));
        when(profiles.findByUserIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(buildProfile()));

        UserResponse res = userService.getCurrentUser();

        assertThat(res.id()).isEqualTo(userId.toString());
        assertThat(res.tenantId()).isEqualTo(tenantId.toString());
        assertThat(res.displayName()).isEqualTo("Ria");
        assertThat(res.roles()).containsExactly("USER");
    }

    @Test
    void getUserInTenant_crossTenant_returnsNotFound() {
        UUID otherUser = UUID.randomUUID();
        when(users.findByIdAndTenantId(otherUser, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserInTenant(otherUser))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateProfile_updatesTimezone() {
        UserProfile profile = buildProfile();
        when(profiles.findByUserIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(profile));
        when(users.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(buildUser()));
        when(profiles.save(profile)).thenReturn(profile);

        userService.updateCurrentProfile(new UpdateProfileRequest(null, "America/New_York", null));

        assertThat(profile.getTimezone()).isEqualTo("America/New_York");
    }

    private User buildUser() {
        User user = new User();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setEmail("ria@acme.com");
        user.setRoles(Set.of(new Role(Role.USER)));
        return user;
    }

    private UserProfile buildProfile() {
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setTenantId(tenantId);
        profile.setDisplayName("Ria");
        profile.setTimezone("Asia/Kolkata");
        return profile;
    }
}
