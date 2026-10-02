INSERT INTO sys_menu_permission
    (id, parent_id, type, name, code, path, component, icon, sort_order, visible, enabled)
VALUES
    (1400, NULL, 'MENU', '数据管理', 'data:view', '/data', 'Layout', 'database', 15, TRUE, TRUE),
    (1410, 1400, 'MENU', '数据管理', 'data:manage:view', '/data/manage', 'data/manage/index', 'files', 10, TRUE, TRUE),
    (1420, 1400, 'MENU', '数据上传', 'data:upload:view', '/data/upload', 'data/upload/index', 'upload', 20, TRUE, TRUE),
    (1430, 1400, 'MENU', '采集任务', 'data:collect:task:view', '/data/collect-tasks', 'data/collect-task/index', 'collect', 30, TRUE, TRUE),
    (1440, 1400, 'MENU', '标注任务', 'data:annotate:task:view', '/data/annotate-tasks', 'data/annotate-task/index', 'annotate', 40, TRUE, TRUE),
    (1450, 1400, 'MENU', '数据质检', 'data:qc:view', '/data/quality-check', 'data/quality-check/index', 'qc', 50, TRUE, TRUE),
    (1460, 1400, 'MENU', '字典管理', 'data:dict:view', '/data/dictionary', 'data/dictionary/index', 'dict', 60, TRUE, TRUE),
    (1470, 1400, 'MENU', '分析图表', 'data:chart:view', '/data/charts', 'data/chart/index', 'chart', 70, TRUE, TRUE),
    (1480, 1400, 'MENU', '技能库', 'data:skill:view', '/data/skills', 'data/skill/index', 'skill', 80, TRUE, TRUE),
    (1490, 1400, 'MENU', '数据导出', 'data:export:view', '/data/export', 'data/export/index', 'export', 90, TRUE, TRUE),
    (1500, 1400, 'MENU', '可视化', 'data:visual:view', '/data/visualization', 'data/visualization/index', 'visual', 100, TRUE, TRUE)
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
WHERE r.code IN ('SUPER_ADMIN', 'MANAGER')
  AND p.code IN (
      'data:view',
      'data:manage:view',
      'data:upload:view',
      'data:collect:task:view',
      'data:annotate:task:view',
      'data:qc:view',
      'data:dict:view',
      'data:chart:view',
      'data:skill:view',
      'data:export:view',
      'data:visual:view'
  )
ON CONFLICT DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('sys_menu_permission', 'id'),
    (SELECT MAX(id) FROM sys_menu_permission),
    TRUE
);
