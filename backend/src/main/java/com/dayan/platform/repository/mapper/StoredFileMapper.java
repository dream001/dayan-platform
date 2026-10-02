package com.dayan.platform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayan.platform.model.StoredFile;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface StoredFileMapper extends BaseMapper<StoredFile> {

    @Select("""
            <script>
            SELECT count(*)
            FROM file_metadata
            WHERE status = 'READY'
            <if test="keyword != null and keyword != ''">
              AND original_name ILIKE CONCAT('%', #{keyword}, '%')
            </if>
            </script>
            """)
    long countReady(@Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT *
            FROM file_metadata
            WHERE status = 'READY'
            <if test="keyword != null and keyword != ''">
              AND original_name ILIKE CONCAT('%', #{keyword}, '%')
            </if>
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<StoredFile> selectReadyPage(
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Select("SELECT * FROM file_metadata WHERE id = #{id} FOR UPDATE")
    StoredFile selectByIdForUpdate(@Param("id") long id);

    @Update("""
            UPDATE file_metadata
            SET status = 'DELETING',
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND status IN ('READY', 'FAILED')
            """)
    int markDeleting(@Param("id") long id);

    @Update("""
            UPDATE file_metadata
            SET status = 'FAILED',
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND status = 'DELETING'
            """)
    int markDeleteFailed(@Param("id") long id);

    @Delete("DELETE FROM file_metadata WHERE id = #{id} AND status = 'DELETING'")
    int deleteDeleting(@Param("id") long id);
}
