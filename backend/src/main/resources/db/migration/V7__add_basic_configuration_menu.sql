INSERT INTO sys_menu_permission
    (id, parent_id, type, name, code, path, component, icon, sort_order, visible, enabled)
VALUES
    (1600, NULL, 'MENU', '基本配置', 'basic:view', '/basic', 'Layout', 'settings', 50, TRUE, TRUE),
    (1610, 1600, 'MENU', '项目管理', 'basic:project:view', '/basic/projects', 'basic/project/index', 'folder', 10, TRUE, TRUE),
    (1620, 1600, 'MENU', '机器人', 'basic:robot:view', '/basic/robots', 'basic/robot/index', 'skill', 20, TRUE, TRUE),
    (1630, 1600, 'MENU', '设备管理', 'basic:device:view', '/basic/devices', 'basic/device/index', 'monitor', 30, TRUE, TRUE),
    (1640, 1600, 'MENU', '存储管理', 'basic:storage:view', '/basic/storage', 'basic/storage/index', 'database', 40, TRUE, TRUE),
    (1650, 1600, 'MENU', '流程管理', 'basic:workflow:view', '/basic/workflows', 'basic/workflow/index', 'collect', 50, TRUE, TRUE),
    (1660, 1600, 'MENU', '模型管理', 'basic:model:view', '/basic/models', 'basic/model/index', 'visual', 60, TRUE, TRUE),
    (1670, 1600, 'MENU', '运维监控', 'basic:operations:view', '/basic/operations', 'basic/operations/index', 'chart', 70, TRUE, TRUE)
ON CONFLICT (code) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    type = EXCLUDED.type,
    name = EXCLUDED.name,
    path = EXCLUDED.path,
    component = EXCLUDED.component,
    icon = EXCLUDED.icon,
    sort_order = EXCLUDED.sort_order,
    visible = EXCLUDED.visible,
    enabled = EXCLUDED.enabled,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_menu_permission p
WHERE r.code = 'SUPER_ADMIN'
  AND p.code IN (
      'basic:view',
      'basic:project:view',
      'basic:robot:view',
      'basic:device:view',
      'basic:storage:view',
      'basic:workflow:view',
      'basic:model:view',
      'basic:operations:view'
  )
ON CONFLICT DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_menu_permission p
WHERE r.code = 'MANAGER'
  AND p.code = 'basic:project:view'
ON CONFLICT DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('sys_menu_permission', 'id'),
    (SELECT MAX(id) FROM sys_menu_permission),
    TRUE
);
