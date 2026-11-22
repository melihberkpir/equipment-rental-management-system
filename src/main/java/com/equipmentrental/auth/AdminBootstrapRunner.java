package com.equipmentrental.auth;

import com.equipmentrental.domain.Role;
import com.equipmentrental.domain.RoleName;
import com.equipmentrental.domain.User;
import com.equipmentrental.repository.RoleRepository;
import com.equipmentrental.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the first administrator from configuration so no credentials live in migrations. */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminBootstrapProperties properties;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapRunner(AdminBootstrapProperties properties, UserRepository users, RoleRepository roles,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isConfigured()) {
            return;
        }
        String email = EmailNormalizer.normalize(properties.email());
        if (users.existsByEmail(email)) {
            return;
        }
        Role adminRole = roles.findByName(RoleName.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role is missing"));
        users.save(new User(adminRole, properties.fullName(), email, passwordEncoder.encode(properties.password())));
        log.info("Created initial administrator account");
    }
}
