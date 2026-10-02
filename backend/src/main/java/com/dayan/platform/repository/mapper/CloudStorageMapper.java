package com.dayan.platform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayan.platform.model.CloudStorage;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface CloudStorageMapper extends BaseMapper<CloudStorage> {

    @Select("""
            SELECT *
            FROM cloud_storage
            ORDER BY default_storage DESC, enabled DESC, updated_at DESC, id DESC
            """)
    List<CloudStorage> selectAll();

    @Select("""
            SELECT count(*)
            FROM cloud_storage
            WHERE storage_key = #{storageKey}
              AND (CAST(#{excludeId} AS BIGINT) IS NULL OR id <> #{excludeId})
            """)
    long countByStorageKey(
            @Param("storageKey") String storageKey,
            @Param("excludeId") Long excludeId
    );

    @Select("""
            SELECT count(*)
            FROM cloud_storage
            WHERE name = #{name}
              AND (CAST(#{excludeId} AS BIGINT) IS NULL OR id <> #{excludeId})
            """)
    long countByName(@Param("name") String name, @Param("excludeId") Long excludeId);

    @Update("""
            UPDATE cloud_storage
            SET default_storage = FALSE,
                updated_at = CURRENT_TIMESTAMP
            WHERE default_storage = TRUE
              AND id <> #{id}
            """)
    int clearOtherDefaults(@Param("id") long id);

    @Update("""
            UPDATE cloud_storage
            SET default_storage = TRUE,
                enabled = TRUE,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int markDefault(@Param("id") long id);

    @Update("""
            UPDATE cloud_storage
            SET status = #{status},
                last_check_message = #{message},
                last_check_latency_ms = #{latencyMs},
                usage_bytes = #{usageBytes},
                object_count = #{objectCount},
                last_checked_at = #{checkedAt},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateCheckResult(
            @Param("id") long id,
            @Param("status") String status,
            @Param("message") String message,
            @Param("latencyMs") long latencyMs,
            @Param("usageBytes") long usageBytes,
            @Param("objectCount") long objectCount,
            @Param("checkedAt") OffsetDateTime checkedAt
    );
}
