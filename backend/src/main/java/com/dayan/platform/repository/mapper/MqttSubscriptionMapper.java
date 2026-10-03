package com.dayan.platform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayan.platform.model.MqttSubscription;
import com.dayan.platform.vo.MqttViews.SubscriptionView;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface MqttSubscriptionMapper extends BaseMapper<MqttSubscription> {

    @Select("""
            <script>
            SELECT s.id, s.connection_id, c.name AS connection_name,
                   s.topic_filter, s.qos, s.description, s.enabled,
                   s.created_at, s.updated_at
            FROM mqtt_subscription s
            JOIN mqtt_connection c ON c.id = s.connection_id
            WHERE 1 = 1
              <if test="connectionId != null">AND s.connection_id = #{connectionId}</if>
            ORDER BY s.enabled DESC, s.updated_at DESC, s.id DESC
            </script>
            """)
    List<SubscriptionView> selectViews(@Param("connectionId") Long connectionId);

    @Select("""
            SELECT s.id, s.connection_id, c.name AS connection_name,
                   s.topic_filter, s.qos, s.description, s.enabled,
                   s.created_at, s.updated_at
            FROM mqtt_subscription s
            JOIN mqtt_connection c ON c.id = s.connection_id
            WHERE s.id = #{id}
            """)
    SubscriptionView selectView(@Param("id") long id);

    @Select("""
            SELECT count(*)
            FROM mqtt_subscription
            WHERE connection_id = #{connectionId}
              AND topic_filter = #{topicFilter}
              AND (CAST(#{excludeId} AS BIGINT) IS NULL OR id <> #{excludeId})
            """)
    long countDuplicate(
            @Param("connectionId") long connectionId,
            @Param("topicFilter") String topicFilter,
            @Param("excludeId") Long excludeId
    );
}
