# 数据库表结构清单

本目录按系统菜单的 `sort_order` 整理 PostgreSQL 表结构，便于查阅、评审和新环境初始化。
应用运行时仍由 `src/main/resources/db/migration` 下的 Flyway 脚本管理版本。

## 文件顺序

| 顺序 | 菜单 | 文件 | 数据表 |
| --- | --- | --- | --- |
| 10 | 工作台 | `10_workspace.sql` | 无独立表，数据来自业务表聚合 |
| 15 | 数据管理 | `15_data_management.sql` | `data_dataset`、`data_dataset_tag`、`data_dataset_tag_rel`、`data_upload_session`、`data_upload_part`、`data_collection_task`、`data_collection_task_assignee`、`data_collection_task_step`、`data_collection_task_dataset`、`data_annotation_task`、`data_annotation_task_dataset`、`data_annotation` |
| 20 | 系统管理 | `20_system_management.sql` | `sys_department`、`sys_user`、`sys_role`、`sys_menu_permission`、`sys_user_role`、`sys_role_permission`、`auth_session` |
| 30 | 文件管理 | `30_file_management.sql` | `file_metadata` |
| 40 | 操作日志 | `40_operation_log.sql` | `operation_log` |
| 50 | 基本配置 | `50_basic_configuration.sql` | `basic_project`、`basic_project_member`、`cloud_storage`、`ai_model`、`ai_agent` |
| 99 | 跨模块约束 | `99_foreign_keys.sql` | 所有跨文件外键 |

数据质检、字典管理、分析图表、技能库、数据导出、可视化、机器人、设备管理、流程管理和运维监控目前没有独立业务表。

## 执行方式

文件名同时表达菜单顺序。由于业务表会引用排序靠后的系统和基本配置表，初始化时应使用：

```bash
psql -v ON_ERROR_STOP=1 -f backend/init/init.sql
```

`init.sql` 会按依赖顺序加载建表文件，最后统一添加跨模块外键。脚本面向空数据库，不应在已经执行 Flyway 的数据库中重复运行。

## 兼容说明

当前 `V9` 与 `V10` 都定义了 `data_dataset`，`V9` 与 `V11` 都定义了
`data_annotation_task`，但字段契约不同。本目录将两组字段合并为单一最终结构，以覆盖当前
Dataset、DataUpload 和 AnnotationTask 代码路径；原始迁移的版本冲突仍应在合并入主分支前单独修正。
