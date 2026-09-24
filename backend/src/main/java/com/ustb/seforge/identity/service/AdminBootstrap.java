package com.ustb.seforge.identity.service;

import com.ustb.seforge.identity.api.CreateUserRequest;
import com.ustb.seforge.identity.domain.AccountType;
import com.ustb.seforge.identity.domain.GlobalRole;
import com.ustb.seforge.identity.repository.UserRoleRepository;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final IdentityService identityService;
    private final UserRoleRepository userRoleRepository;
    private final String email;
    private final String username;
    private final String password;
    private final String displayName;

    public AdminBootstrap(
            IdentityService identityService,
            UserRoleRepository userRoleRepository,
            @Value("${seforge.bootstrap-admin.email:}") String email,
            @Value("${seforge.bootstrap-admin.username:admin}") String username,
            @Value("${seforge.bootstrap-admin.password:}") String password,
            @Value("${seforge.bootstrap-admin.display-name:SEForge Administrator}") String displayName) {
        this.identityService = identityService;
        this.userRoleRepository = userRoleRepository;
        this.email = email;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRoleRepository.existsAnyByRoleCode(GlobalRole.ADMIN.name())) return;
        if (email.isBlank() || password.isBlank()) {
            log.warn("No administrator exists. Set SEFORGE_BOOTSTRAP_ADMIN_EMAIL and "
                    + "SEFORGE_BOOTSTRAP_ADMIN_PASSWORD once to create the initial administrator.");
            return;
        }
        if (password.length() < 10 || password.length() > 72) {
            // Degrade to a warning: an invalid bootstrap password must not crash-loop the whole API.
            log.warn("Ignoring the bootstrap administrator: password must contain 10 to 72 characters. "
                    + "Fix SEFORGE_BOOTSTRAP_ADMIN_PASSWORD and restart.");
            return;
        }
        try {
            identityService.createUser(new CreateUserRequest(
                    email, username, password, displayName, AccountType.PLATFORM,
                    Set.of(GlobalRole.ADMIN, GlobalRole.USER)));
            log.info("Created the initial SEForge administrator account");
        } catch (RuntimeException failure) {
            // A conflicting username/email (or any store failure) must not crash-loop the API.
            // Resolve the conflict or clear the bootstrap variables, then restart.
            log.warn("Could not create the bootstrap administrator: {}. Resolve the conflict or clear "
                    + "SEFORGE_BOOTSTRAP_ADMIN_EMAIL/SEFORGE_BOOTSTRAP_ADMIN_PASSWORD.", failure.getMessage());
        }
    }
}
