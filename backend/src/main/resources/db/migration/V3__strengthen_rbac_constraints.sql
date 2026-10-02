ALTER TABLE sys_department
    ADD CONSTRAINT ck_department_code_not_blank CHECK (btrim(code) <> ''),
    ADD CONSTRAINT ck_department_name_not_blank CHECK (btrim(name) <> '');

ALTER TABLE sys_menu_permission
    ADD CONSTRAINT ck_menu_permission_code_not_blank
        CHECK (code IS NULL OR btrim(code) <> '');

ALTER TABLE sys_role
    ADD CONSTRAINT ck_role_name_not_blank CHECK (btrim(name) <> '');

ALTER TABLE sys_user
    ADD CONSTRAINT ck_user_password_hash_not_blank CHECK (btrim(password_hash) <> '');
