package com.dayan.platform.repository.mapper;

import com.dayan.platform.repository.query.DashboardStatsRow;
import org.apache.ibatis.annotations.Select;

public interface DashboardMapper {

    @Select("""
            SELECT
                (SELECT count(*) FROM sys_user) AS total_users,
                (SELECT count(*) FROM sys_user WHERE enabled = TRUE) AS enabled_users,
                (SELECT count(*) FROM file_metadata WHERE status = 'READY') AS total_files,
                (
                    SELECT COALESCE(sum(size_bytes), 0)
                    FROM file_metadata
                    WHERE status = 'READY'
                ) AS total_file_size_bytes
            """)
    DashboardStatsRow selectStatistics();
}
