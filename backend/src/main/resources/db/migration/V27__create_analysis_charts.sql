ALTER TABLE data_annotation
    ADD COLUMN skill_name VARCHAR(128),
    ADD COLUMN skill_name_zh VARCHAR(128),
    ADD COLUMN object_a_name VARCHAR(128),
    ADD COLUMN object_a_name_zh VARCHAR(128),
    ADD COLUMN object_b_name VARCHAR(128),
    ADD COLUMN object_b_name_zh VARCHAR(128),
    ADD COLUMN action_name VARCHAR(255),
    ADD COLUMN start_offset_seconds NUMERIC(12, 3),
    ADD COLUMN end_offset_seconds NUMERIC(12, 3),
    ADD CONSTRAINT ck_data_annotation_start_offset
        CHECK (start_offset_seconds IS NULL OR start_offset_seconds >= 0),
    ADD CONSTRAINT ck_data_annotation_end_offset
        CHECK (
            end_offset_seconds IS NULL
            OR (
                end_offset_seconds >= 0
                AND (start_offset_seconds IS NULL OR end_offset_seconds >= start_offset_seconds)
            )
        );

CREATE INDEX idx_data_annotation_chart_sequence
    ON data_annotation (dataset_id, start_offset_seconds, id)
    WHERE is_valid = TRUE;
CREATE INDEX idx_data_annotation_chart_calendar
    ON data_annotation (created_at)
    WHERE is_valid = TRUE;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_menu_permission p
WHERE r.code IN ('SUPER_ADMIN', 'MANAGER', 'AUDITOR', 'ANNOTATOR')
  AND p.code IN ('data:view', 'data:chart:view', 'basic:project:view')
ON CONFLICT DO NOTHING;
