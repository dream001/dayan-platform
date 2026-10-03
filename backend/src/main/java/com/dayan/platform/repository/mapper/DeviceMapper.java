package com.dayan.platform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayan.platform.dto.DeviceDtos.AgentReportRequest;
import com.dayan.platform.model.Device;
import com.dayan.platform.repository.query.DeviceRows.DeviceRow;
import com.dayan.platform.repository.query.DeviceRows.MetricRow;
import com.dayan.platform.repository.query.DeviceRows.OptionRow;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface DeviceMapper extends BaseMapper<Device> {

    String ACCESS = """
            (#{admin} = TRUE
             OR d.created_by = #{userId}
             OR d.project_id IS NULL
             OR EXISTS (
                 SELECT 1 FROM basic_project_member member
                 WHERE member.project_id = d.project_id
                   AND member.user_id = #{userId}
                   AND (member.valid_from IS NULL OR CURRENT_TIMESTAMP >= member.valid_from)
                   AND (member.valid_until IS NULL OR member.valid_until > CURRENT_TIMESTAMP)
             ))
            """;

    String COLUMNS = """
            d.*, r.name AS robot_name, p.name AS project_name,
            task.name AS collection_task_name,
            report.cpu_usage, report.memory_usage, report.disk_usage,
            report.cpu_temperature, report.memory_used_bytes,
            report.disk_available_bytes, report.active_tcp_connections,
            report.uptime_seconds
            """;

    String JOINS = """
            LEFT JOIN basic_robot r ON r.id = d.robot_id
            LEFT JOIN basic_project p ON p.id = d.project_id
            LEFT JOIN data_collection_task task ON task.id = d.collection_task_id
            LEFT JOIN LATERAL (
                SELECT *
                FROM basic_device_report latest
                WHERE latest.device_id = d.id
                ORDER BY latest.reported_at DESC, latest.id DESC
                LIMIT 1
            ) report ON TRUE
            """;

    @Select("""
            <script>
            SELECT
            """ + COLUMNS + """
            FROM basic_device d
            """ + JOINS + """
            WHERE d.deleted = FALSE AND
            """ + ACCESS + """
            <if test="keyword != null and keyword != ''">
              AND d.device_code ILIKE '%' || #{keyword} || '%'
            </if>
            <if test="projectId != null">AND d.project_id = #{projectId}</if>
            ORDER BY d.updated_at DESC, d.id DESC
            </script>
            """)
    List<DeviceRow> selectDevices(
            @Param("userId") long userId,
            @Param("admin") boolean admin,
            @Param("keyword") String keyword,
            @Param("projectId") Long projectId
    );

    @Select("""
            SELECT
            """ + COLUMNS + """
            FROM basic_device d
            """ + JOINS + """
            WHERE d.agent_id = CAST(#{agentId} AS UUID) AND d.deleted = FALSE AND
            """ + ACCESS)
    DeviceRow selectAccessibleByAgentId(
            @Param("agentId") String agentId,
            @Param("userId") long userId,
            @Param("admin") boolean admin
    );

    @Select("SELECT * FROM basic_device WHERE lower(device_code) = lower(#{code}) LIMIT 1")
    Device selectAnyByCode(@Param("code") String code);

    @Select("""
            SELECT *
            FROM basic_device
            WHERE agent_token = CAST(#{token} AS UUID) AND deleted = FALSE
            """)
    Device selectByAgentToken(@Param("token") String token);

    @Insert("""
            INSERT INTO basic_device (
                agent_id, agent_token, device_code, remark, robot_id,
                project_id, created_by, deleted
            ) VALUES (
                CAST(#{device.agentId} AS UUID),
                CAST(#{device.agentToken} AS UUID),
                #{device.deviceCode},
                #{device.remark},
                #{device.robotId},
                #{device.projectId},
                #{device.createdBy},
                FALSE
            )
            """)
    @org.apache.ibatis.annotations.Options(
            useGeneratedKeys = true,
            keyProperty = "device.id",
            keyColumn = "id"
    )
    int insertDevice(@Param("device") Device device);

    @Select("SELECT * FROM basic_device WHERE id = #{id} AND deleted = FALSE")
    Device selectActiveById(@Param("id") long id);

    @Select("""
            SELECT cpu_usage, memory_usage, disk_usage, cpu_temperature,
                   active_tcp_connections, reported_at
            FROM basic_device_report
            WHERE device_id = #{deviceId} AND reported_at >= #{from}
            ORDER BY reported_at
            LIMIT 2000
            """)
    List<MetricRow> selectMetrics(
            @Param("deviceId") long deviceId,
            @Param("from") OffsetDateTime from
    );

    @Insert("""
            INSERT INTO basic_device_report (
                device_id, cpu_usage, memory_usage, disk_usage, cpu_temperature,
                memory_used_bytes, disk_available_bytes, active_tcp_connections,
                uptime_seconds
            ) VALUES (
                #{deviceId}, #{report.cpuUsage}, #{report.memoryUsage},
                #{report.diskUsage}, #{report.cpuTemperature},
                #{report.memoryUsedBytes}, #{report.diskAvailableBytes},
                #{report.activeTcpConnections}, #{report.uptimeSeconds}
            )
            """)
    int insertReport(
            @Param("deviceId") long deviceId,
            @Param("report") AgentReportRequest report
    );

    @Update("""
            UPDATE basic_device
            SET last_report_at = CURRENT_TIMESTAMP,
                hostname = #{report.hostname},
                operating_system = #{report.operatingSystem},
                platform = #{report.platform},
                kernel_version = #{report.kernelVersion},
                ip_addresses = #{ipAddresses},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{deviceId} AND deleted = FALSE
            """)
    int updateSystemInfo(
            @Param("deviceId") long deviceId,
            @Param("report") AgentReportRequest report,
            @Param("ipAddresses") String ipAddresses
    );

    @Update("""
            UPDATE basic_device
            SET deleted = TRUE, deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND deleted = FALSE
            """)
    int softDelete(@Param("id") long id);

    @Update("""
            UPDATE basic_device
            SET device_code = #{device.deviceCode},
                remark = #{device.remark},
                robot_id = #{device.robotId},
                project_id = #{device.projectId},
                deleted = FALSE,
                deleted_at = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{device.id}
            """)
    int updateRegistration(@Param("device") Device device);

    @Update("""
            UPDATE basic_device
            SET collection_task_id = #{taskId}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND deleted = FALSE
            """)
    int assignTask(@Param("id") long id, @Param("taskId") long taskId);

    @Select("""
            SELECT id, COALESCE(NULLIF(title_zh, ''), NULLIF(title_en, ''), name) AS name
            FROM basic_robot WHERE deleted = FALSE ORDER BY name
            """)
    List<OptionRow> selectRobotOptions();

    @Select("""
            SELECT p.id, p.name
            FROM basic_project p
            WHERE p.status IN ('PLANNING', 'ACTIVE')
              AND (#{admin} = TRUE OR p.owner_id = #{userId} OR EXISTS (
                SELECT 1 FROM basic_project_member member
                WHERE member.project_id = p.id AND member.user_id = #{userId}
                  AND member.data_access_level IN ('READ_WRITE', 'FULL')
              ))
            ORDER BY p.name
            """)
    List<OptionRow> selectProjectOptions(
            @Param("userId") long userId,
            @Param("admin") boolean admin
    );

    @Select("""
            SELECT t.id, t.name
            FROM data_collection_task t
            WHERE t.deleted_at IS NULL
              AND t.status IN ('PENDING', 'WORKING')
              AND (#{admin} = TRUE OR t.created_by = #{userId} OR EXISTS (
                SELECT 1 FROM basic_project_member member
                WHERE member.project_id = t.project_id AND member.user_id = #{userId}
              ))
            ORDER BY t.updated_at DESC, t.id DESC
            """)
    List<OptionRow> selectTaskOptions(
            @Param("userId") long userId,
            @Param("admin") boolean admin
    );
}
