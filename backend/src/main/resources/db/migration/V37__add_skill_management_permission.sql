INSERT INTO sys_menu_permission
    (id, parent_id, type, name, code, sort_order, visible, enabled)
VALUES
    (1481, 1480, 'BUTTON', '管理技能资产', 'data:skill:manage', 10, FALSE, TRUE)
ON CONFLICT (code) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    type = EXCLUDED.type,
    name = EXCLUDED.name,
    sort_order = EXCLUDED.sort_order,
    visible = EXCLUDED.visible,
    enabled = EXCLUDED.enabled,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
CROSS JOIN sys_menu_permission permission
WHERE role.code IN ('SUPER_ADMIN', 'MANAGER')
  AND permission.code = 'data:skill:manage'
ON CONFLICT DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('sys_menu_permission', 'id'),
    (SELECT MAX(id) FROM sys_menu_permission),
    TRUE
);
