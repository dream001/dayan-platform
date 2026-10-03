package com.dayan.platform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayan.platform.model.MqttConnection;
import com.dayan.platform.vo.MqttViews.ConnectionView;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface MqttConnectionMapper extends BaseMapper<MqttConnection> {

    @Select("""
            SELECT c.id, c.name, c.client_id, c.broker_url,
                   CASE
                     WHEN c.username_ciphertext IS NULL THEN NULL
                     ELSE 'configured'
                   END AS username_hint,
                   (c.username_ciphertext IS NOT NULL OR c.password_ciphertext IS NOT NULL)
                       AS credential_configured,
                   c.tls_enabled, c.clean_session, c.keep_alive_seconds,
                   c.connection_timeout_seconds, c.enabled, c.status,
                   c.last_check_message, c.last_check_latency_ms, c.last_checked_at,
                   count(s.id) AS subscription_count,
                   c.created_at, c.updated_at
            FROM mqtt_connection c
            LEFT JOIN mqtt_subscription s ON s.connection_id = c.id
            GROUP BY c.id
            ORDER BY c.enabled DESC, c.updated_at DESC, c.id DESC
            """)
    List<ConnectionView> selectViews();

    @Select("""
            SELECT c.id, c.name, c.client_id, c.broker_url,
                   CASE
                     WHEN c.username_ciphertext IS NULL THEN NULL
                     ELSE 'configured'
                   END AS username_hint,
                   (c.username_ciphertext IS NOT NULL OR c.password_ciphertext IS NOT NULL)
                       AS credential_configured,
                   c.tls_enabled, c.clean_session, c.keep_alive_seconds,
                   c.connection_timeout_seconds, c.enabled, c.status,
                   c.last_check_message, c.last_check_latency_ms, c.last_checked_at,
                   count(s.id) AS subscription_count,
                   c.created_at, c.updated_at
            FROM mqtt_connection c
            LEFT JOIN mqtt_subscription s ON s.connection_id = c.id
            WHERE c.id = #{id}
            GROUP BY c.id
            """)
    ConnectionView selectView(@Param("id") long id);

    @Select("""
            SELECT count(*)
            FROM mqtt_connection
            WHERE (name = #{name} OR client_id = #{clientId})
              AND (CAST(#{excludeId} AS BIGINT) IS NULL OR id <> #{excludeId})
            """)
    long countDuplicate(
            @Param("name") String name,
            @Param("clientId") String clientId,
            @Param("excludeId") Long excludeId
    );

    @Update("""
            UPDATE mqtt_connection
            SET status = #{status},
                last_check_message = #{message},
                last_check_latency_ms = #{latencyMs},
                last_checked_at = #{checkedAt},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateCheckResult(
            @Param("id") long id,
            @Param("status") String status,
            @Param("message") String message,
            @Param("latencyMs") long latencyMs,
            @Param("checkedAt") OffsetDateTime checkedAt
    );

    @Update("""
            UPDATE mqtt_connection
            SET status = 'NEVER',
                last_check_message = NULL,
                last_check_latency_ms = NULL,
                last_checked_at = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int resetCheckResult(@Param("id") long id);
}
