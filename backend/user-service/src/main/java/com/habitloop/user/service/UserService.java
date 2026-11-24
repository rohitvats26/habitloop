package com.habitloop.user.service;

import com.habitloop.common.exception.ResourceNotFoundException;
import com.habitloop.common.security.SecurityUtils;
import com.habitloop.user.dto.UpdateProfileRequest;
import com.habitloop.user.dto.UserResponse;
import com.habitloop.user.entity.User;
import com.habitloop.user.entity.UserProfile;
import com.habitloop.user.mapper.UserMapper;
import com.habitloop.user.repository.UserProfileRepository;
import com.habitloop.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final UserMapper mapper;

    public UserService(UserRepository users, UserProfileRepository profiles, UserMapper mapper) {
        this.users = users;
        this.profiles = profiles;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        UUID tenantId = UUID.fromString(SecurityUtils.currentTenantId());
        UUID userId = UUID.fromString(SecurityUtils.currentUserId());
        return load(userId, tenantId);
    }

    @Transactional
    public UserResponse updateCurrentProfile(UpdateProfileRequest req) {
        UUID tenantId = UUID.fromString(SecurityUtils.currentTenantId());
        UUID userId = UUID.fromString(SecurityUtils.currentUserId());
        UserProfile profile = profiles.findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", userId));
        if (req.displayName() != null) {
            profile.setDisplayName(req.displayName().trim());
        }
        if (StringUtils.hasText(req.timezone())) {
            profile.setTimezone(req.timezone().trim());
        }
        if (req.avatarUrl() != null) {
            profile.setAvatarUrl(req.avatarUrl().trim());
        }
        profiles.save(profile);
        return load(userId, tenantId);
    }

    /** Admin fetch of a tenant user. Cross-tenant ids return 404 (existence hidden). */
    @Transactional(readOnly = true)
    public UserResponse getUserInTenant(UUID userId) {
        UUID tenantId = UUID.fromString(SecurityUtils.currentTenantId());
        return load(userId, tenantId);
    }

    private UserResponse load(UUID userId, UUID tenantId) {
        User user = users.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        UserProfile profile = profiles.findByUserIdAndTenantId(userId, tenantId).orElse(null);
        return mapper.toUserResponse(user, profile);
    }
}
