-- 跨菜单、跨文件外键统一在所有表创建后添加。

ALTER TABLE sys_department
    ADD CONSTRAINT fk_department_parent FOREIGN KEY (parent_id)
        REFERENCES sys_department (id) ON DELETE RESTRICT;

ALTER TABLE sys_user
    ADD CONSTRAINT fk_user_department FOREIGN KEY (department_id)
        REFERENCES sys_department (id) ON DELETE RESTRICT;

ALTER TABLE sys_menu_permission
    ADD CONSTRAINT fk_menu_permission_parent FOREIGN KEY (parent_id)
        REFERENCES sys_menu_permission (id) ON DELETE RESTRICT;

ALTER TABLE sys_user_role
    ADD CONSTRAINT fk_user_role_user FOREIGN KEY (user_id)
        REFERENCES sys_user (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_user_role_role FOREIGN KEY (role_id)
        REFERENCES sys_role (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_user_role_assigner FOREIGN KEY (assigned_by)
        REFERENCES sys_user (id) ON DELETE SET NULL;

ALTER TABLE sys_role_permission
    ADD CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id)
        REFERENCES sys_role (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id)
        REFERENCES sys_menu_permission (id) ON DELETE CASCADE;

ALTER TABLE auth_session
    ADD CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id)
        REFERENCES sys_user (id) ON DELETE CASCADE;

ALTER TABLE basic_project
    ADD CONSTRAINT fk_basic_project_owner FOREIGN KEY (owner_id)
        REFERENCES sys_user (id) ON DELETE RESTRICT;

ALTER TABLE basic_project_member
    ADD CONSTRAINT fk_basic_project_member_project FOREIGN KEY (project_id)
        REFERENCES basic_project (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_basic_project_member_user FOREIGN KEY (user_id)
        REFERENCES sys_user (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_basic_project_member_assigner FOREIGN KEY (assigned_by)
        REFERENCES sys_user (id) ON DELETE SET NULL;

ALTER TABLE cloud_storage
    ADD CONSTRAINT fk_cloud_storage_creator FOREIGN KEY (created_by)
        REFERENCES sys_user (id) ON DELETE SET NULL;

ALTER TABLE ai_agent
    ADD CONSTRAINT fk_ai_agent_model FOREIGN KEY (model_id)
        REFERENCES ai_model (id) ON DELETE RESTRICT;

ALTER TABLE file_metadata
    ADD CONSTRAINT fk_file_uploader FOREIGN KEY (uploader_id)
        REFERENCES sys_user (id) ON DELETE SET NULL;

ALTER TABLE data_dataset
    ADD CONSTRAINT fk_data_dataset_file FOREIGN KEY (file_id)
        REFERENCES file_metadata (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_data_dataset_project FOREIGN KEY (project_id)
        REFERENCES basic_project (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_data_dataset_task FOREIGN KEY (task_id)
        REFERENCES data_annotation_task (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_data_dataset_collector FOREIGN KEY (collector_id)
        REFERENCES sys_user (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_data_dataset_uploader FOREIGN KEY (uploader_id)
        REFERENCES sys_user (id) ON DELETE RESTRICT;

ALTER TABLE data_dataset_tag_rel
    ADD CONSTRAINT fk_data_dataset_tag_rel_dataset FOREIGN KEY (dataset_id)
        REFERENCES data_dataset (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_data_dataset_tag_rel_tag FOREIGN KEY (tag_id)
        REFERENCES data_dataset_tag (id) ON DELETE CASCADE;

ALTER TABLE data_annotation
    ADD CONSTRAINT fk_data_annotation_dataset FOREIGN KEY (dataset_id)
        REFERENCES data_dataset (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_data_annotation_task FOREIGN KEY (task_id)
        REFERENCES data_annotation_task (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_data_annotation_annotator FOREIGN KEY (annotator_id)
        REFERENCES sys_user (id) ON DELETE SET NULL;

ALTER TABLE data_upload_session
    ADD CONSTRAINT fk_data_upload_project FOREIGN KEY (project_id)
        REFERENCES basic_project (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_data_upload_uploader FOREIGN KEY (uploader_id)
        REFERENCES sys_user (id) ON DELETE CASCADE;

ALTER TABLE data_upload_part
    ADD CONSTRAINT fk_data_upload_part_session FOREIGN KEY (session_id)
        REFERENCES data_upload_session (id) ON DELETE CASCADE;

ALTER TABLE data_collection_task
    ADD CONSTRAINT fk_collection_task_project FOREIGN KEY (project_id)
        REFERENCES basic_project (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_collection_task_creator FOREIGN KEY (created_by)
        REFERENCES sys_user (id) ON DELETE RESTRICT;

ALTER TABLE data_collection_task_assignee
    ADD CONSTRAINT fk_collection_assignee_task FOREIGN KEY (task_id)
        REFERENCES data_collection_task (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_collection_assignee_user FOREIGN KEY (user_id)
        REFERENCES sys_user (id) ON DELETE RESTRICT;

ALTER TABLE data_collection_task_step
    ADD CONSTRAINT fk_collection_step_task FOREIGN KEY (task_id)
        REFERENCES data_collection_task (id) ON DELETE CASCADE;

ALTER TABLE data_collection_task_dataset
    ADD CONSTRAINT fk_collection_dataset_task FOREIGN KEY (task_id)
        REFERENCES data_collection_task (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_collection_dataset_data FOREIGN KEY (dataset_id)
        REFERENCES data_dataset (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_collection_dataset_linker FOREIGN KEY (linked_by)
        REFERENCES sys_user (id) ON DELETE SET NULL;

ALTER TABLE data_annotation_task
    ADD CONSTRAINT fk_annotation_task_project FOREIGN KEY (project_id)
        REFERENCES basic_project (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_annotation_task_annotator FOREIGN KEY (annotator_id)
        REFERENCES sys_user (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_annotation_task_reviewer FOREIGN KEY (reviewer_id)
        REFERENCES sys_user (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_annotation_task_creator FOREIGN KEY (creator_id)
        REFERENCES sys_user (id) ON DELETE RESTRICT;

ALTER TABLE data_annotation_task_dataset
    ADD CONSTRAINT fk_annotation_dataset_task FOREIGN KEY (task_id)
        REFERENCES data_annotation_task (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_annotation_dataset_data FOREIGN KEY (dataset_id)
        REFERENCES data_dataset (id) ON DELETE RESTRICT;
