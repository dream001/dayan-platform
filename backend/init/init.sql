\set ON_ERROR_STOP on

BEGIN;

-- 先创建系统基础表，再创建有外键依赖的菜单业务表。
\ir 20_system_management.sql
\ir 50_basic_configuration.sql
\ir 30_file_management.sql
\ir 15_data_management.sql
\ir 40_operation_log.sql
\ir 10_workspace.sql
\ir 99_foreign_keys.sql

COMMIT;
