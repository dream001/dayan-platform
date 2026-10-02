package com.dayan.platform.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dayan.platform.support.PostgreSqlIntegrationTestSupport;
import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DatabaseMigrationIntegrationTest extends PostgreSqlIntegrationTestSupport {

    @BeforeAll
    static void migrateDatabase() {
        flyway().migrate();
    }

    @Test
    void migratesSchemaAndSeedDataIdempotently() {
        Flyway flyway = flyway();

        flyway.migrate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(Arrays.stream(flyway.info().applied()).map(MigrationInfo::getVersion))
                .extracting(Object::toString)
                .contains("1", "2", "3", "4", "5", "6", "7", "8", "19", "20", "21");

        JdbcTemplate jdbc = jdbcTemplate();
        assertThat(jdbc.queryForObject(
                """
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name IN (
                    'sys_department', 'sys_user', 'sys_role', 'sys_menu_permission',
                    'sys_user_role', 'sys_role_permission', 'auth_session',
                    'file_metadata', 'operation_log', 'basic_project', 'basic_project_member',
                    'ai_model', 'cloud_storage', 'ai_agent'
                  )
                """,
                Integer.class
        )).isGreaterThanOrEqualTo(14);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM sys_menu_permission WHERE type = 'MENU'",
                Integer.class
        )).isGreaterThanOrEqualTo(27);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM sys_menu_permission WHERE type = 'BUTTON'",
                Integer.class
        )).isGreaterThanOrEqualTo(28);
        assertThat(jdbc.queryForList(
                "SELECT code FROM sys_role WHERE built_in = TRUE ORDER BY code",
                String.class
        )).containsExactly("ANNOTATOR", "AUDITOR", "COLLECTOR", "MANAGER", "SUPER_ADMIN");
        assertThat(jdbc.queryForObject(
                """
                SELECT count(*)
                FROM sys_role_permission rp
                JOIN sys_role r ON r.id = rp.role_id
                WHERE r.code = 'SUPER_ADMIN'
                """,
                Integer.class
        )).isGreaterThanOrEqualTo(55);
        assertThat(jdbc.queryForObject(
                """
                SELECT count(*)
                FROM sys_role_permission rp
                JOIN sys_role r ON r.id = rp.role_id
                JOIN sys_menu_permission p ON p.id = rp.permission_id
                WHERE r.code IN ('MANAGER', 'COLLECTOR', 'ANNOTATOR', 'AUDITOR')
                  AND p.code = 'dashboard:view'
                """,
                Integer.class
        )).isEqualTo(4);
        assertThat(jdbc.queryForList(
                """
                SELECT code
                FROM sys_menu_permission
                WHERE parent_id = (SELECT id FROM sys_menu_permission WHERE code = 'basic:view')
                ORDER BY sort_order, id
                """,
                String.class
        )).containsExactly(
                "basic:project:view",
                "basic:robot:view",
                "basic:device:view",
                "basic:storage:view",
                "basic:workflow:view",
                "basic:model:view",
                "basic:agent:view",
                "basic:operations:view"
        );
        assertThat(jdbc.queryForObject(
                """
                SELECT basic.sort_order > audit.sort_order
                FROM sys_menu_permission basic
                JOIN sys_menu_permission audit ON audit.code = 'audit:log:view'
                WHERE basic.code = 'basic:view'
                """,
                Boolean.class
        )).isTrue();
        assertThat(jdbc.queryForObject(
                """
                SELECT count(*)
                FROM sys_role_permission rp
                JOIN sys_role r ON r.id = rp.role_id
                JOIN sys_menu_permission p ON p.id = rp.permission_id
                WHERE r.code = 'MANAGER'
                  AND p.code = 'basic:project:view'
                """,
                Integer.class
        )).isOne();
    }

    @Test
    void enforcesUniqueCheckAndForeignKeyConstraints() {
        JdbcTemplate jdbc = jdbcTemplate();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO sys_role (name, code) VALUES (?, ?)",
                "Duplicate administrator",
                "SUPER_ADMIN"
        )).hasMessageContaining("uk_role_code");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO sys_menu_permission (type, name, code) VALUES (?, ?, ?)",
                "UNKNOWN",
                "Invalid permission",
                "invalid:permission"
        )).hasMessageContaining("ck_menu_permission_type");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO sys_role_permission (role_id, permission_id) VALUES (?, ?)",
                999_999L,
                1000L
        )).hasMessageContaining("fk_role_permission_role");
    }

    private JdbcTemplate jdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRESQL.getJdbcUrl(),
                POSTGRESQL.getUsername(),
                POSTGRESQL.getPassword()
        );
        return new JdbcTemplate(dataSource);
    }

    private static Flyway flyway() {
        return Flyway.configure()
                .dataSource(
                        POSTGRESQL.getJdbcUrl(),
                        POSTGRESQL.getUsername(),
                        POSTGRESQL.getPassword()
                )
                .locations("classpath:db/migration")
                .load();
    }
}
