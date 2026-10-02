ALTER TABLE data_dataset
    ADD COLUMN source_fingerprint VARCHAR(128);

ALTER TABLE data_dataset DROP CONSTRAINT ck_data_dataset_type;
ALTER TABLE data_dataset
    ADD CONSTRAINT ck_data_dataset_type CHECK (
        data_type IN ('MCAP', 'BAG', 'VIDEO', 'AUDIO', 'IMAGE', 'HDF5', 'LEROBOT', 'MEITUAN',
                      'LUMOS', 'ZC0TOUCH', 'SENSEXPERIENCE', 'BVH')
    );

DROP INDEX uk_data_dataset_active_name;
CREATE UNIQUE INDEX uk_data_dataset_project_name_active
    ON data_dataset (project_id, lower(name))
    WHERE deleted = FALSE;
CREATE UNIQUE INDEX uk_data_dataset_source_active
    ON data_dataset (source_fingerprint)
    WHERE deleted = FALSE AND source_fingerprint IS NOT NULL;

CREATE TABLE data_upload_session (
    id UUID PRIMARY KEY,
    project_id BIGINT NOT NULL,
    storage_key VARCHAR(64) NOT NULL,
    data_type VARCHAR(32) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    total_size BIGINT NOT NULL,
    chunk_size INTEGER NOT NULL,
    total_chunks INTEGER NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    source_fingerprint VARCHAR(128) NOT NULL,
    robot_type VARCHAR(100),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    uploader_id BIGINT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_data_upload_project FOREIGN KEY (project_id)
        REFERENCES basic_project (id) ON DELETE CASCADE,
    CONSTRAINT fk_data_upload_uploader FOREIGN KEY (uploader_id)
        REFERENCES sys_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_data_upload_size CHECK (total_size > 0),
    CONSTRAINT ck_data_upload_chunk_size CHECK (chunk_size = 10485760),
    CONSTRAINT ck_data_upload_chunks CHECK (total_chunks > 0),
    CONSTRAINT ck_data_upload_status CHECK (
        status IN ('PENDING', 'UPLOADING', 'PAUSED', 'COMPLETING', 'SUCCESS', 'ERROR', 'CANCELLED')
    )
);

CREATE INDEX idx_data_upload_user_status
    ON data_upload_session (uploader_id, status, updated_at DESC);

CREATE TABLE data_upload_part (
    session_id UUID NOT NULL,
    part_number INTEGER NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    size_bytes BIGINT NOT NULL,
    etag VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id, part_number),
    CONSTRAINT fk_data_upload_part_session FOREIGN KEY (session_id)
        REFERENCES data_upload_session (id) ON DELETE CASCADE,
    CONSTRAINT ck_data_upload_part_number CHECK (part_number >= 0),
    CONSTRAINT ck_data_upload_part_size CHECK (size_bytes > 0)
);

INSERT INTO sys_menu_permission
    (id, parent_id, type, name, code, sort_order, visible, enabled)
VALUES
    (1421, 1420, 'BUTTON', '上传数据', 'data:upload:create', 10, FALSE, TRUE)
ON CONFLICT (code) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    name = EXCLUDED.name,
    enabled = TRUE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_menu_permission p
WHERE r.code IN ('SUPER_ADMIN', 'MANAGER', 'COLLECTOR')
  AND p.code IN ('data:view', 'data:upload:view', 'data:upload:create')
ON CONFLICT DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('sys_menu_permission', 'id'),
    (SELECT MAX(id) FROM sys_menu_permission),
    TRUE
);
