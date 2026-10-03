UPDATE sys_menu_permission
SET visible = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'file:view';
