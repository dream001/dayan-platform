ALTER TABLE data_qc_rule
    ADD COLUMN data_type VARCHAR(32);

UPDATE data_qc_rule
SET data_type = 'MCAP'
WHERE data_type IS NULL;

ALTER TABLE data_qc_rule
    ALTER COLUMN data_type SET NOT NULL,
    ALTER COLUMN data_type SET DEFAULT 'MCAP';

ALTER TABLE data_qc_rule
    DROP CONSTRAINT ck_qc_rule_algorithm;

ALTER TABLE data_qc_rule
    ADD CONSTRAINT ck_qc_rule_algorithm
        CHECK (algorithm_code IN ('MCAP_STRUCTURAL', 'DATASET_INTEGRITY')),
    ADD CONSTRAINT ck_qc_rule_data_type
        CHECK (data_type IN (
            'MCAP', 'BAG', 'VIDEO', 'AUDIO', 'IMAGE', 'HDF5',
            'LEROBOT', 'MEITUAN', 'LUMOS', 'ZC0TOUCH',
            'SENSEXPERIENCE', 'BVH'
        ));

CREATE INDEX idx_qc_rule_data_type
    ON data_qc_rule (data_type, enabled, priority, id);
