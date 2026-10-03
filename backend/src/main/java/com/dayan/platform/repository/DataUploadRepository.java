package com.dayan.platform.repository;

import com.dayan.platform.vo.DataUploadViews.DatasetView;
import com.dayan.platform.vo.DataUploadViews.ProjectOption;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DataUploadRepository {

    private final JdbcTemplate jdbcTemplate;

    public DataUploadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ProjectOption> findAccessibleProjects(long userId) {
        return jdbcTemplate.query("""
                SELECT DISTINCT p.id, p.code, p.name
                FROM basic_project p
                WHERE p.status = 'ACTIVE'
                  AND (
                    p.access_level = 'PUBLIC'
                    OR p.owner_id = ?
                    OR EXISTS (
                      SELECT 1 FROM basic_project_member pm
                      WHERE pm.project_id = p.id
                        AND pm.user_id = ?
                        AND (pm.valid_from IS NULL OR pm.valid_from <= CURRENT_TIMESTAMP)
                        AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
                    )
                    OR EXISTS (
                      SELECT 1 FROM sys_user_role ur
                      JOIN sys_role r ON r.id = ur.role_id
                      WHERE ur.user_id = ? AND r.code = 'SUPER_ADMIN' AND r.enabled = TRUE
                    )
                  )
                ORDER BY p.name, p.id
                """, (rs, rowNum) -> new ProjectOption(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name")
        ), userId, userId, userId);
    }

    public boolean canUploadToProject(long projectId, long userId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM basic_project p
                WHERE p.id = ?
                  AND p.status = 'ACTIVE'
                  AND (
                    p.owner_id = ?
                    OR EXISTS (
                      SELECT 1 FROM basic_project_member pm
                      WHERE pm.project_id = p.id AND pm.user_id = ?
                        AND pm.data_access_level IN ('READ_WRITE', 'FULL')
                        AND (pm.valid_from IS NULL OR pm.valid_from <= CURRENT_TIMESTAMP)
                        AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
                    )
                    OR EXISTS (
                      SELECT 1 FROM sys_user_role ur
                      JOIN sys_role r ON r.id = ur.role_id
                      WHERE ur.user_id = ? AND r.code = 'SUPER_ADMIN' AND r.enabled = TRUE
                    )
                  )
                """, Long.class, projectId, userId, userId, userId);
        return count != null && count > 0;
    }

    public DatasetView findDuplicate(long projectId, String name, String fingerprint) {
        List<DatasetView> rows = jdbcTemplate.query("""
                SELECT d.id, d.project_id, d.name, d.data_type, f.original_name, f.content_type,
                       d.size_bytes, d.metadata_status AS status, d.created_at
                FROM data_dataset d
                JOIN file_metadata f ON f.id = d.file_id
                WHERE d.deleted = FALSE
                  AND d.project_id = ?
                  AND (d.source_fingerprint = ? OR lower(d.name) = lower(?))
                ORDER BY d.id
                LIMIT 1
                """, this::mapDataset, projectId, fingerprint, name);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void createSession(SessionData session) {
        jdbcTemplate.update("""
                INSERT INTO data_upload_session
                    (id, project_id, storage_key, data_type, original_name, content_type,
                     total_size, chunk_size, total_chunks, object_key, source_fingerprint,
                     robot_type, status, uploader_id, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
                """,
                session.id(), session.projectId(), session.storageKey(), session.dataType(),
                session.originalName(), session.contentType(), session.totalSize(),
                session.chunkSize(), session.totalChunks(), session.objectKey(),
                session.sourceFingerprint(), session.robotType(), session.uploaderId(),
                session.expiresAt()
        );
    }

    public SessionData findSession(UUID id, long uploaderId) {
        List<SessionData> rows = jdbcTemplate.query("""
                SELECT * FROM data_upload_session
                WHERE id = ? AND uploader_id = ?
                """, (rs, rowNum) -> mapSession(rs), id, uploaderId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<Integer> findUploadedParts(UUID sessionId) {
        return jdbcTemplate.queryForList("""
                SELECT part_number FROM data_upload_part
                WHERE session_id = ?
                ORDER BY part_number
                """, Integer.class, sessionId);
    }

    public List<PartData> findParts(UUID sessionId) {
        return jdbcTemplate.query("""
                SELECT part_number, object_key, size_bytes
                FROM data_upload_part
                WHERE session_id = ?
                ORDER BY part_number
                """, (rs, rowNum) -> new PartData(
                rs.getInt("part_number"),
                rs.getString("object_key"),
                rs.getLong("size_bytes")
        ), sessionId);
    }

    public void savePart(UUID sessionId, int partNumber, String objectKey, long size, String etag) {
        jdbcTemplate.update("""
                INSERT INTO data_upload_part (session_id, part_number, object_key, size_bytes, etag)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (session_id, part_number) DO UPDATE
                SET object_key = EXCLUDED.object_key,
                    size_bytes = EXCLUDED.size_bytes,
                    etag = EXCLUDED.etag,
                    created_at = CURRENT_TIMESTAMP
                """, sessionId, partNumber, objectKey, size, etag);
        jdbcTemplate.update("""
                UPDATE data_upload_session
                SET status = 'UPLOADING', updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status IN ('PENDING', 'PAUSED', 'UPLOADING')
                """, sessionId);
    }

    public void updateStatus(UUID id, long uploaderId, String status) {
        jdbcTemplate.update("""
                UPDATE data_upload_session
                SET status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND uploader_id = ?
                """, status, id, uploaderId);
    }

    public DatasetView createDataset(SessionData session, String bucketName, String etag, String status) {
        Long fileId = jdbcTemplate.queryForObject("""
                INSERT INTO file_metadata
                    (bucket_name, object_key, original_name, content_type, size_bytes,
                     etag, uploader_id, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'READY')
                RETURNING id
                """, Long.class,
                bucketName, session.objectKey(), session.originalName(), session.contentType(),
                session.totalSize(), etag, session.uploaderId()
        );
        return jdbcTemplate.queryForObject("""
                INSERT INTO data_dataset
                    (project_id, name, file_id, data_type, size_bytes, robot_code,
                     uploader_id, metadata_status, source_fingerprint)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id, project_id, name, data_type,
                          ? AS original_name, ? AS content_type,
                          size_bytes, metadata_status AS status, created_at
                """, this::mapDataset,
                session.projectId(), datasetName(session.originalName()), fileId,
                session.dataType(), session.totalSize(), session.robotType(),
                session.uploaderId(), status, session.sourceFingerprint(),
                session.originalName(), session.contentType()
        );
    }

    public DatasetView createDirectDataset(
            long projectId,
            String storageKey,
            String dataType,
            String originalName,
            String contentType,
            long size,
            String objectKey,
            String fingerprint,
            String robotType,
            String status,
            long uploaderId,
            String bucketName,
            String etag
    ) {
        SessionData session = new SessionData(
                UUID.randomUUID(), projectId, storageKey, dataType, originalName, contentType,
                size, 0, 1, objectKey, fingerprint, robotType, "SUCCESS",
                uploaderId, OffsetDateTime.now()
        );
        return createDataset(session, bucketName, etag, status);
    }

    private DatasetView mapDataset(ResultSet rs, int rowNum) throws SQLException {
        return new DatasetView(
                rs.getLong("id"),
                rs.getLong("project_id"),
                rs.getString("name"),
                rs.getString("data_type"),
                rs.getString("original_name"),
                rs.getString("content_type"),
                rs.getLong("size_bytes"),
                rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    private SessionData mapSession(ResultSet rs) throws SQLException {
        return new SessionData(
                rs.getObject("id", UUID.class),
                rs.getLong("project_id"),
                rs.getString("storage_key"),
                rs.getString("data_type"),
                rs.getString("original_name"),
                rs.getString("content_type"),
                rs.getLong("total_size"),
                rs.getInt("chunk_size"),
                rs.getInt("total_chunks"),
                rs.getString("object_key"),
                rs.getString("source_fingerprint"),
                rs.getString("robot_type"),
                rs.getString("status"),
                rs.getLong("uploader_id"),
                rs.getObject("expires_at", OffsetDateTime.class)
        );
    }

    private String datasetName(String originalName) {
        int separator = originalName.lastIndexOf('.');
        return separator > 0 ? originalName.substring(0, separator) : originalName;
    }

    public record SessionData(
            UUID id,
            long projectId,
            String storageKey,
            String dataType,
            String originalName,
            String contentType,
            long totalSize,
            int chunkSize,
            int totalChunks,
            String objectKey,
            String sourceFingerprint,
            String robotType,
            String status,
            long uploaderId,
            OffsetDateTime expiresAt
    ) {
    }

    public record PartData(int partNumber, String objectKey, long sizeBytes) {
    }
}
