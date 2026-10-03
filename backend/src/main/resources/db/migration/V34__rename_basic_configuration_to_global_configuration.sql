UPDATE sys_menu_permission
SET name = '全局配置',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'basic:view';
