package com.dayan.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dayan.platform.config.InitialAdminProperties;
import com.dayan.platform.model.Role;
import com.dayan.platform.model.UserAccount;
import com.dayan.platform.model.UserRole;
import com.dayan.platform.repository.mapper.RoleMapper;
import com.dayan.platform.repository.mapper.UserAccountMapper;
import com.dayan.platform.repository.mapper.UserRoleMapper;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InitialAdminInitializer implements ApplicationRunner {

    static final String ADMIN_ROLE_CODE = "SUPER_ADMIN";
    private static final long INITIALIZATION_LOCK_ID = 7_219_114_816L;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{2,63}");
    private static final Logger log = LoggerFactory.getLogger(InitialAdminInitializer.class);

    private final InitialAdminProperties properties;
    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public InitialAdminInitializer(
            InitialAdminProperties properties,
            UserAccountMapper userAccountMapper,
            RoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate
    ) {
        this.properties = properties;
        this.userAccountMapper = userAccountMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }

        AdminInput input = validateAndNormalize(properties);
        jdbcTemplate.queryForObject(
                "SELECT pg_advisory_xact_lock(?)",
                (resultSet, rowNumber) -> resultSet.getObject(1),
                INITIALIZATION_LOCK_ID
        );

        Long existingUsers = userAccountMapper.selectCount(
                Wrappers.<UserAccount>lambdaQuery().eq(UserAccount::getUsername, input.username())
        );
        if (existingUsers > 0) {
            log.info("Initial administrator already exists; initialization skipped");
            return;
        }

        Role administratorRole = roleMapper.selectOne(
                Wrappers.<Role>lambdaQuery().eq(Role::getCode, ADMIN_ROLE_CODE)
        );
        if (administratorRole == null || !Boolean.TRUE.equals(administratorRole.getEnabled())) {
            throw new IllegalStateException("Enabled built-in administrator role is missing");
        }

        UserAccount user = new UserAccount();
        user.setUsername(input.username());
        user.setPasswordHash(passwordEncoder.encode(input.password()));
        user.setDisplayName(input.displayName());
        user.setEmail(input.email());
        user.setEnabled(true);
        userAccountMapper.insert(user);

        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(administratorRole.getId());
        userRoleMapper.insert(userRole);
        log.info("Initial administrator created and assigned to the built-in administrator role");
    }

    private AdminInput validateAndNormalize(InitialAdminProperties candidate) {
        String username = normalize(candidate.username());
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalStateException(
                    "INITIAL_ADMIN_USERNAME must be 3-64 lowercase letters, digits, '.', '_' or '-'"
            );
        }

        String displayName = trimToNull(candidate.displayName());
        if (displayName == null || displayName.length() > 100) {
            throw new IllegalStateException("INITIAL_ADMIN_DISPLAY_NAME must be 1-100 characters");
        }

        String password = candidate.password();
        if (password == null || password.length() < 12) {
            throw new IllegalStateException("INITIAL_ADMIN_PASSWORD must contain at least 12 characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("INITIAL_ADMIN_PASSWORD must not exceed 72 UTF-8 bytes");
        }

        String email = normalize(candidate.email());
        if (email != null && (email.length() > 254 || !email.contains("@"))) {
            throw new IllegalStateException("INITIAL_ADMIN_EMAIL must be a valid email address");
        }
        return new AdminInput(username, displayName, email, password);
    }

    private String normalize(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record AdminInput(String username, String displayName, String email, String password) {
    }
}
