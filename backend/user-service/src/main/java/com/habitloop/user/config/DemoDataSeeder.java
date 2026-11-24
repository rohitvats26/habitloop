package com.habitloop.user.config;

import com.habitloop.user.entity.Role;
import com.habitloop.user.entity.Tenant;
import com.habitloop.user.entity.User;
import com.habitloop.user.entity.UserProfile;
import com.habitloop.user.repository.RoleRepository;
import com.habitloop.user.repository.TenantRepository;
import com.habitloop.user.repository.UserProfileRepository;
import com.habitloop.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Seeds a demo tenant and users on startup when {@code habitloop.seed-demo=true} (local/dev only).
 * Passwords are hashed with the real {@link PasswordEncoder}, so no bcrypt hash is hardcoded in SQL.
 */
@Component
@ConditionalOnProperty(name = "habitloop.seed-demo", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String DEMO_JOIN_CODE = "ACME2026";
    private static final String DEMO_PASSWORD = "Password123!";

    private final TenantRepository tenants;
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserProfileRepository profiles;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(TenantRepository tenants, UserRepository users, RoleRepository roles,
                          UserProfileRepository profiles, PasswordEncoder passwordEncoder) {
        this.tenants = tenants;
        this.users = users;
        this.roles = roles;
        this.profiles = profiles;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (tenants.findByJoinCode(DEMO_JOIN_CODE).isPresent()) {
            log.info("Demo data already present; skipping seed");
            return;
        }
        Tenant tenant = new Tenant();
        tenant.setName("Acme Wellness");
        tenant.setJoinCode(DEMO_JOIN_CODE);
        tenant = tenants.save(tenant);

        createUser(tenant, "ria@acme.com", "Ria", "Asia/Kolkata", Role.TENANT_ADMIN);
        createUser(tenant, "sam@acme.com", "Sam", "Asia/Kolkata", Role.USER);

        log.info("Seeded demo tenant '{}' (join code {}) with 2 users. Password: {}",
                tenant.getName(), DEMO_JOIN_CODE, DEMO_PASSWORD);
    }

    private void createUser(Tenant tenant, String email, String displayName, String tz, String roleName) {
        Role role = roles.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + roleName));
        User user = new User();
        user.setTenantId(tenant.getId());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        user.setRoles(Set.of(role));
        user = users.save(user);

        UserProfile profile = new UserProfile();
        profile.setUserId(user.getId());
        profile.setTenantId(tenant.getId());
        profile.setDisplayName(displayName);
        profile.setTimezone(tz);
        profiles.save(profile);
    }
}
