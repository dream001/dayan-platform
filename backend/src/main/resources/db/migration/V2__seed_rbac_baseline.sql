INSERT INTO sys_role (id, name, code, description, enabled, built_in)
VALUES (1, '超级管理员', 'SUPER_ADMIN', '内置角色，拥有全部菜单与按钮权限', TRUE, TRUE);

INSERT INTO sys_menu_permission
    (id, parent_id, type, name, code, path, component, icon, sort_order, visible)
VALUES
    (1000, NULL, 'MENU', '工作台', 'dashboard:view', '/dashboard', 'dashboard/index', 'dashboard', 10, TRUE),
    (1100, NULL, 'MENU', '系统管理', 'system:view', '/system', 'Layout', 'settings', 20, TRUE),
    (1110, 1100, 'MENU', '用户管理', 'system:user:view', '/system/users', 'system/user/index', 'users', 10, TRUE),
    (1120, 1100, 'MENU', '角色管理', 'system:role:view', '/system/roles', 'system/role/index', 'shield', 20, TRUE),
    (1130, 1100, 'MENU', '菜单权限', 'system:permission:view', '/system/permissions', 'system/permission/index', 'menu', 30, TRUE),
    (1140, 1100, 'MENU', '部门管理', 'system:department:view', '/system/departments', 'system/department/index', 'organization', 40, TRUE),
    (1200, NULL, 'MENU', '文件管理', 'file:view', '/files', 'file/index', 'folder', 30, TRUE),
    (1300, NULL, 'MENU', '操作日志', 'audit:log:view', '/audit/logs', 'audit/log/index', 'history', 40, TRUE);

INSERT INTO sys_menu_permission
    (id, parent_id, type, name, code, sort_order, visible)
VALUES
    (1111, 1110, 'BUTTON', '新增用户', 'system:user:create', 10, FALSE),
    (1112, 1110, 'BUTTON', '编辑用户', 'system:user:update', 20, FALSE),
    (1113, 1110, 'BUTTON', '启停用户', 'system:user:change-status', 30, FALSE),
    (1114, 1110, 'BUTTON', '重置密码', 'system:user:reset-password', 40, FALSE),
    (1115, 1110, 'BUTTON', '分配角色', 'system:user:assign-role', 50, FALSE),
    (1116, 1110, 'BUTTON', '删除用户', 'system:user:delete', 60, FALSE),
    (1121, 1120, 'BUTTON', '新增角色', 'system:role:create', 10, FALSE),
    (1122, 1120, 'BUTTON', '编辑角色', 'system:role:update', 20, FALSE),
    (1123, 1120, 'BUTTON', '删除角色', 'system:role:delete', 30, FALSE),
    (1124, 1120, 'BUTTON', '角色授权', 'system:role:grant', 40, FALSE),
    (1131, 1130, 'BUTTON', '新增权限', 'system:permission:create', 10, FALSE),
    (1132, 1130, 'BUTTON', '编辑权限', 'system:permission:update', 20, FALSE),
    (1133, 1130, 'BUTTON', '删除权限', 'system:permission:delete', 30, FALSE),
    (1141, 1140, 'BUTTON', '新增部门', 'system:department:create', 10, FALSE),
    (1142, 1140, 'BUTTON', '编辑部门', 'system:department:update', 20, FALSE),
    (1143, 1140, 'BUTTON', '删除部门', 'system:department:delete', 30, FALSE),
    (1201, 1200, 'BUTTON', '上传文件', 'file:upload', 10, FALSE),
    (1202, 1200, 'BUTTON', '下载文件', 'file:download', 20, FALSE),
    (1203, 1200, 'BUTTON', '预览文件', 'file:preview', 30, FALSE),
    (1204, 1200, 'BUTTON', '删除文件', 'file:delete', 40, FALSE),
    (1301, 1300, 'BUTTON', '查看日志详情', 'audit:log:detail', 10, FALSE);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id
FROM sys_menu_permission;

SELECT setval(
    pg_get_serial_sequence('sys_role', 'id'),
    (SELECT MAX(id) FROM sys_role),
    TRUE
);
SELECT setval(
    pg_get_serial_sequence('sys_menu_permission', 'id'),
    (SELECT MAX(id) FROM sys_menu_permission),
    TRUE
);
