package com.habitloop.user.mapper;

import com.habitloop.user.dto.TenantResponse;
import com.habitloop.user.dto.UserResponse;
import com.habitloop.user.entity.Role;
import com.habitloop.user.entity.Tenant;
import com.habitloop.user.entity.User;
import com.habitloop.user.entity.UserProfile;
import org.springframework.stereotype.Component;

import java.util.List;

/** Maps entities to API DTOs. Entities are never exposed directly (implementation standard). */
@Component
public class UserMapper {

    public UserResponse toUserResponse(User user, UserProfile profile) {
        return new UserResponse(
                user.getId().toString(),
                user.getTenantId().toString(),
                user.getEmail(),
                profile == null ? null : profile.getDisplayName(),
                profile == null ? null : profile.getTimezone(),
                profile == null ? null : profile.getAvatarUrl(),
                roleNames(user)
        );
    }

    public List<String> roleNames(User user) {
        return user.getRoles().stream().map(Role::getName).sorted().toList();
    }

    public TenantResponse toTenantResponse(Tenant tenant) {
        return new TenantResponse(
                tenant.getId().toString(),
                tenant.getName(),
                tenant.getJoinCode(),
                tenant.getStatus().name()
        );
    }
}
