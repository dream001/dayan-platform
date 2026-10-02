ALTER TABLE operation_log
    DROP CONSTRAINT fk_operation_log_operator;

COMMENT ON COLUMN operation_log.operator_id IS
    'Historical operator identifier; intentionally not constrained so audit writes remain independent';
