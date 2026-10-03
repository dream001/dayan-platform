DROP INDEX uk_data_dataset_source_active;

CREATE UNIQUE INDEX uk_data_dataset_project_source_active
    ON data_dataset (project_id, source_fingerprint)
    WHERE deleted = FALSE AND source_fingerprint IS NOT NULL;
