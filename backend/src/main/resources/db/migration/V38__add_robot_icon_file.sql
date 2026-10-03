ALTER TABLE basic_robot
    ADD COLUMN icon_file_id BIGINT;

ALTER TABLE basic_robot
    ADD CONSTRAINT fk_basic_robot_icon_file
        FOREIGN KEY (icon_file_id)
        REFERENCES file_metadata (id)
        ON DELETE SET NULL;

CREATE INDEX idx_basic_robot_icon_file
    ON basic_robot (icon_file_id)
    WHERE icon_file_id IS NOT NULL;
