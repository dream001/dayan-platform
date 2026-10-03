ALTER TABLE data_upload_session
    ADD COLUMN duration_seconds NUMERIC(12, 3);

ALTER TABLE data_upload_session
    ADD CONSTRAINT ck_data_upload_duration
        CHECK (duration_seconds IS NULL OR duration_seconds > 0);
