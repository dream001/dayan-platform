INSERT INTO sys_role (name, code, description, enabled, built_in)
VALUES
    ('项目经理', 'MANAGER', '负责所属项目、数据与任务管理；公开项目默认只读', TRUE, TRUE),
    ('采集员', 'COLLECTOR', '负责采集任务执行、数据回传与规范化入库', TRUE, TRUE),
    ('标注员', 'ANNOTATOR', '负责领取标注任务、提交结果与返工修复', TRUE, TRUE),
    ('审核员', 'AUDITOR', '负责审核标注结果、质量判定与返工反馈', TRUE, TRUE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    enabled = TRUE,
    built_in = TRUE,
    updated_at = CURRENT_TIMESTAMP;

UPDATE sys_role
SET description = '内置管理员角色，拥有全部菜单与按钮权限',
    enabled = TRUE,
    built_in = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'SUPER_ADMIN';

INSERT INTO sys_menu_permission
    (id, parent_id, type, name, code, sort_order, visible, enabled)
VALUES
    (1134, 1130, 'BUTTON', '菜单排序', 'system:permission:sort', 40, FALSE, TRUE)
ON CONFLICT (code) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    type = EXCLUDED.type,
    name = EXCLUDED.name,
    sort_order = EXCLUDED.sort_order,
    visible = EXCLUDED.visible,
    enabled = EXCLUDED.enabled,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_menu_permission p
WHERE r.code IN ('MANAGER', 'COLLECTOR', 'ANNOTATOR', 'AUDITOR')
  AND p.code = 'dashboard:view'
ON CONFLICT DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_menu_permission p
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;
