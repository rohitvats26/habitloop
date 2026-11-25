package com.habitloop.user.service;

import com.habitloop.common.exception.BusinessException;
import com.habitloop.common.exception.DuplicateResourceException;
import com.habitloop.common.exception.ResourceNotFoundException;
import com.habitloop.common.exception.UnauthorizedException;
import com.habitloop.common.security.JwtService;
import com.habitloop.user.dto.AuthResponse;
import com.habitloop.user.dto.LoginRequest;
import com.habitloop.user.dto.RegisterRequest;
import com.habitloop.user.dto.UserResponse;
import com.habitloop.user.entity.Role;
import com.habitloop.user.entity.Tenant;
import com.habitloop.user.entity.User;
import com.habitloop.user.entity.UserProfile;
import com.habitloop.user.mapper.UserMapper;
import com.habitloop.user.repository.RoleRepository;
import com.habitloop.user.repository.TenantRepository;
import com.habitloop.user.repository.UserProfileRepository;
import com.habitloop.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.List;
import java.util.Set;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();

    private final UserRepository users;
    private final TenantRepository tenants;
    private final RoleRepository roles;
    private final UserProfileRepository profiles;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper mapper;

    public AuthService(UserRepository users, TenantRepository tenants, RoleRepository roles,
                       UserProfileRepository profiles, PasswordEncoder passwordEncoder,
                       JwtService jwtService, UserMapper mapper) {
        this.users = users;
        this.tenants = tenants;
        this.roles = roles;
        this.profiles = profiles;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mapper = mapper;
    }

    @Transactional
    public UserResponse register(RegisterRequest req) {
        boolean joining = StringUtils.hasText(req.joinCode());
        boolean creating = StringUtils.hasText(req.tenantName());
        if (joining == creating) {
            throw new BusinessException("Provide exactly one of tenantName (create) or joinCode (join)");
        }

        Tenant tenant;
        String roleName;
        if (joining) {
            tenant = tenants.findByJoinCode(req.joinCode().trim().toUpperCase())
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant for join code not found"));
            roleName = Role.USER;
        } else {
            tenant = new Tenant();
            tenant.setName(req.tenantName().trim());
            tenant.setJoinCode(uniqueJoinCode());
            tenant = tenants.save(tenant);
            roleName = Role.TENANT_ADMIN;
        }

        String email = req.email().trim().toLowerCase();
        if (users.existsByTenantIdAndEmail(tenant.getId(), email)) {
            throw new DuplicateResourceException("Email already registered in this tenant");
        }

        Role role = roles.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + roleName));

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRoles(Set.of(role));
        user = users.save(user);

        UserProfile profile = new UserProfile();
        profile.setUserId(user.getId());
        profile.setTenantId(tenant.getId());
        profile.setDisplayName(req.displayName().trim());
        profile.setTimezone(StringUtils.hasText(req.timezone()) ? req.timezone().trim() : UserProfile.TIMEZONE);
        profiles.save(profile);

        log.info("Registered user {} in tenant {} as {}", user.getId(), tenant.getId(), roleName);
        return mapper.toUserResponse(user, profile);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        List<User> candidates = users.findByEmail(email);

        if (StringUtils.hasText(req.tenantCode())) {
            Tenant t = tenants.findByJoinCode(req.tenantCode().trim().toUpperCase())
                    .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
            candidates = candidates.stream().filter(u -> u.getTenantId().equals(t.getId())).toList();
        }

        if (candidates.isEmpty()) {
            throw new UnauthorizedException("Invalid credentials");
        }
        if (candidates.size() > 1) {
            throw new BusinessException("AMBIGUOUS_TENANT",
                    "Email exists in multiple tenants; supply tenantCode");
        }

        User user = candidates.get(0);
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        try {
            Claims claims = jwtService.parse(refreshToken);
            if (!JwtService.TYPE_REFRESH.equals(claims.get(JwtService.CLAIM_TYPE, String.class))) {
                throw new UnauthorizedException("Not a refresh token");
            }
            User user = users.findById(java.util.UUID.fromString(claims.getSubject()))
                    .orElseThrow(() -> new UnauthorizedException("Invalid token"));
            return issueTokens(user);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }
    }

    private AuthResponse issueTokens(User user) {
        List<String> roleNames = mapper.roleNames(user);
        String id = user.getId().toString();
        String tenantId = user.getTenantId().toString();
        String access = jwtService.generateAccessToken(id, tenantId, roleNames);
        String refresh = jwtService.generateRefreshToken(id, tenantId, roleNames);
        return AuthResponse.bearer(access, refresh, jwtService.getAccessTokenSeconds(), id, tenantId);
    }

    private String uniqueJoinCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (!tenants.existsByJoinCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique join code");
    }
}
