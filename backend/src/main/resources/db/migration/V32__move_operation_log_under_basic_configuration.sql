UPDATE sys_menu_permission
SET parent_id = (
        SELECT id
        FROM sys_menu_permission
        WHERE code = 'basic:view'
    ),
    sort_order = 90,
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'audit:log:view';
