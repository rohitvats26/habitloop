package com.habitloop.user.service;

import com.habitloop.common.exception.BusinessException;
import com.habitloop.common.exception.DuplicateResourceException;
import com.habitloop.common.exception.UnauthorizedException;
import com.habitloop.common.security.JwtService;
import com.habitloop.user.dto.AuthResponse;
import com.habitloop.user.dto.LoginRequest;
import com.habitloop.user.dto.RegisterRequest;
import com.habitloop.user.dto.UserResponse;
import com.habitloop.user.entity.Role;
import com.habitloop.user.entity.Tenant;
import com.habitloop.user.entity.User;
import com.habitloop.user.mapper.UserMapper;
import com.habitloop.user.repository.RoleRepository;
import com.habitloop.user.repository.TenantRepository;
import com.habitloop.user.repository.UserProfileRepository;
import com.habitloop.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository users;
    @Mock TenantRepository tenants;
    @Mock RoleRepository roles;
    @Mock UserProfileRepository profiles;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(users, tenants, roles, profiles, passwordEncoder, jwtService, new UserMapper());
    }

    @Test
    void register_newTenant_makesUserTenantAdmin() {
        RegisterRequest req = new RegisterRequest("Ria@Acme.com", "Password123!", "Ria",
                "Asia/Kolkata", "Acme Wellness", null);

        when(tenants.existsByJoinCode(anyString())).thenReturn(false);
        when(tenants.save(any(Tenant.class))).thenAnswer(i -> {
            Tenant t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(users.existsByTenantIdAndEmail(any(), anyString())).thenReturn(false);
        when(roles.findByName(Role.TENANT_ADMIN)).thenReturn(Optional.of(new Role(Role.TENANT_ADMIN)));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(users.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        UserResponse res = authService.register(req);

        assertThat(res.email()).isEqualTo("ria@acme.com");
        assertThat(res.roles()).containsExactly(Role.TENANT_ADMIN);
    }

    @Test
    void register_requiresExactlyOneOfTenantNameOrJoinCode() {
        RegisterRequest both = new RegisterRequest("a@b.com", "Password123!", "A", "UTC", "T", "CODE");
        assertThatThrownBy(() -> authService.register(both)).isInstanceOf(BusinessException.class);
    }

    @Test
    void register_duplicateEmail_inTenant_throwsConflict() {
        RegisterRequest req = new RegisterRequest("dup@acme.com", "Password123!", "Dup", "UTC", null, "ACME2026");
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setJoinCode("ACME2026");
        when(tenants.findByJoinCode("ACME2026")).thenReturn(Optional.of(tenant));
        when(users.existsByTenantIdAndEmail(tenant.getId(), "dup@acme.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req)).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void login_validCredentials_returnsTokens() {
        User user = buildUser();
        when(users.findByEmail("ria@acme.com")).thenReturn(List.of(user));
        when(passwordEncoder.matches("Password123!", "hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(anyString(), anyString(), any())).thenReturn("access-tok");
        when(jwtService.generateRefreshToken(anyString(), anyString(), any())).thenReturn("refresh-tok");
        lenient().when(jwtService.getAccessTokenSeconds()).thenReturn(3600L);

        AuthResponse res = authService.login(new LoginRequest("ria@acme.com", "Password123!", null));

        assertThat(res.accessToken()).isEqualTo("access-tok");
        assertThat(res.refreshToken()).isEqualTo("refresh-tok");
        assertThat(res.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        User user = buildUser();
        when(users.findByEmail("ria@acme.com")).thenReturn(List.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ria@acme.com", "nope", null)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void login_ambiguousAcrossTenants_requiresTenantCode() {
        when(users.findByEmail("x@y.com")).thenReturn(List.of(buildUser(), buildUser()));
        assertThatThrownBy(() -> authService.login(new LoginRequest("x@y.com", "p", null)))
                .isInstanceOf(BusinessException.class);
    }

    private User buildUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setTenantId(UUID.randomUUID());
        user.setEmail("ria@acme.com");
        user.setPasswordHash("hashed");
        user.setRoles(Set.of(new Role(Role.USER)));
        return user;
    }
}
