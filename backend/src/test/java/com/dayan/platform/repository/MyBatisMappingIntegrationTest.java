package com.dayan.platform.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dayan.platform.model.AuthSession;
import com.dayan.platform.model.Department;
import com.dayan.platform.model.MenuPermission;
import com.dayan.platform.model.OperationLog;
import com.dayan.platform.model.Role;
import com.dayan.platform.model.StoredFile;
import com.dayan.platform.model.UserAccount;
import com.dayan.platform.repository.mapper.AuthSessionMapper;
import com.dayan.platform.repository.mapper.DepartmentMapper;
import com.dayan.platform.repository.mapper.MenuPermissionMapper;
import com.dayan.platform.repository.mapper.OperationLogMapper;
import com.dayan.platform.repository.mapper.RoleMapper;
import com.dayan.platform.repository.mapper.StoredFileMapper;
import com.dayan.platform.repository.mapper.UserAccountMapper;
import com.dayan.platform.repository.mapper.UserRoleMapper;
import com.dayan.platform.support.PostgreSqlIntegrationTestSupport;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MyBatisMappingIntegrationTest extends PostgreSqlIntegrationTestSupport {

    @Autowired
    private DepartmentMapper departmentMapper;
    @Autowired
    private UserAccountMapper userAccountMapper;
    @Autowired
    private RoleMapper roleMapper;
    @Autowired
    private MenuPermissionMapper menuPermissionMapper;
    @Autowired
    private UserRoleMapper userRoleMapper;
    @Autowired
    private AuthSessionMapper authSessionMapper;
    @Autowired
    private StoredFileMapper storedFileMapper;
    @Autowired
    private OperationLogMapper operationLogMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void mapsCoreAndRelationshipEntities() {
        UserAccount administrator = userAccountMapper.selectOne(
                Wrappers.<UserAccount>lambdaQuery()
                        .eq(UserAccount::getUsername, "integration-admin")
        );
        assertThat(administrator).isNotNull();
        assertThat(administrator.getPasswordHash()).startsWith("$2");
        assertThat(administrator.getPasswordHash()).doesNotContain(INITIAL_ADMIN_PASSWORD);
        assertThat(passwordEncoder.matches(INITIAL_ADMIN_PASSWORD, administrator.getPasswordHash())).isTrue();

        Role administratorRole = roleMapper.selectOne(
                Wrappers.<Role>lambdaQuery().eq(Role::getCode, "SUPER_ADMIN")
        );
        assertThat(administratorRole.getBuiltIn()).isTrue();
        assertThat(userRoleMapper.countByUserAndRole(
                administrator.getId(),
                administratorRole.getId()
        )).isOne();
        assertThat(menuPermissionMapper.selectCount(
                Wrappers.<MenuPermission>lambdaQuery().eq(MenuPermission::getType, "BUTTON")
        )).isGreaterThanOrEqualTo(28);

        String suffix = UUID.randomUUID().toString().replace("-", "");
        Department department = new Department();
        department.setName("Mapping Test");
        department.setCode("mapping-" + suffix);
        department.setSortOrder(1);
        department.setEnabled(true);
        assertThat(departmentMapper.insert(department)).isOne();
        assertThat(department.getId()).isPositive();

        UserAccount user = new UserAccount();
        user.setDepartmentId(department.getId());
        user.setUsername("mapping-" + suffix);
        user.setPasswordHash(passwordEncoder.encode("Mapping-Test-Password"));
        user.setDisplayName("Mapping Test User");
        user.setEnabled(true);
        assertThat(userAccountMapper.insert(user)).isOne();

        UserAccount reloadedUser = userAccountMapper.selectById(user.getId());
        assertThat(reloadedUser.getDepartmentId()).isEqualTo(department.getId());
        assertThat(reloadedUser.getCreatedAt()).isNotNull();

        AuthSession session = new AuthSession();
        session.setUserId(user.getId());
        session.setTokenHash("sha256:" + suffix);
        session.setExpiresAt(OffsetDateTime.now().plusDays(1));
        assertThat(authSessionMapper.insert(session)).isOne();
        assertThat(authSessionMapper.selectById(session.getId()).getTokenHash())
                .isEqualTo(session.getTokenHash());

        StoredFile file = new StoredFile();
        file.setBucketName("dayan-test");
        file.setObjectKey("mapping/" + suffix);
        file.setOriginalName("mapping.txt");
        file.setContentType("text/plain");
        file.setSizeBytes(7L);
        file.setUploaderId(user.getId());
        file.setStatus("READY");
        assertThat(storedFileMapper.insert(file)).isOne();
        assertThat(storedFileMapper.selectById(file.getId()).getOriginalName())
                .isEqualTo("mapping.txt");

        OperationLog log = new OperationLog();
        log.setOperatorId(user.getId());
        log.setOperatorName(user.getUsername());
        log.setModule("MAPPING_TEST");
        log.setAction("INSERT");
        log.setResult("SUCCESS");
        log.setRequestId(suffix);
        log.setDurationMs(1L);
        assertThat(operationLogMapper.insert(log)).isOne();
        assertThat(operationLogMapper.selectById(log.getId()).getOccurredAt()).isNotNull();
    }
}
