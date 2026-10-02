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
                .containsExactly("1", "2", "3", "4");

        JdbcTemplate jdbc = jdbcTemplate();
        assertThat(jdbc.queryForObject(
                """
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name IN (
                    'sys_department', 'sys_user', 'sys_role', 'sys_menu_permission',
                    'sys_user_role', 'sys_role_permission', 'auth_session',
                    'file_metadata', 'operation_log'
                  )
                """,
                Integer.class
        )).isEqualTo(9);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM sys_menu_permission WHERE type = 'MENU'",
                Integer.class
        )).isEqualTo(8);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM sys_menu_permission WHERE type = 'BUTTON'",
                Integer.class
        )).isEqualTo(21);
        assertThat(jdbc.queryForObject(
                """
                SELECT count(*)
                FROM sys_role_permission rp
                JOIN sys_role r ON r.id = rp.role_id
                WHERE r.code = 'SUPER_ADMIN'
                """,
                Integer.class
        )).isEqualTo(29);
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
