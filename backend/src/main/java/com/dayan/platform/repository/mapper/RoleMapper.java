package com.dayan.platform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayan.platform.model.Role;
import com.dayan.platform.repository.query.RoleSummaryRow;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface RoleMapper extends BaseMapper<Role> {

    @Select("""
            <script>
            SELECT r.id, r.name, r.code, r.description, r.enabled, r.built_in,
                   count(ur.user_id) AS user_count, r.created_at
            FROM sys_role r
            LEFT JOIN sys_user_role ur ON ur.role_id = r.id
            <where>
              <if test="keyword != null and keyword != ''">
                AND (r.name ILIKE '%' || #{keyword} || '%' OR r.code ILIKE '%' || #{keyword} || '%')
              </if>
              <if test="enabled != null">AND r.enabled = #{enabled}</if>
            </where>
            GROUP BY r.id
            ORDER BY r.built_in DESC, r.created_at DESC, r.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<RoleSummaryRow> selectSummaryPage(
            @Param("keyword") String keyword,
            @Param("enabled") Boolean enabled,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    @Select("""
            <script>
            SELECT count(*) FROM sys_role r
            <where>
              <if test="keyword != null and keyword != ''">
                AND (r.name ILIKE '%' || #{keyword} || '%' OR r.code ILIKE '%' || #{keyword} || '%')
              </if>
              <if test="enabled != null">AND r.enabled = #{enabled}</if>
            </where>
            </script>
            """)
    long countSummaries(@Param("keyword") String keyword, @Param("enabled") Boolean enabled);

    @Select("""
            SELECT r.id, r.name, r.code, r.description, r.enabled, r.built_in,
                   count(ur.user_id) AS user_count, r.created_at
            FROM sys_role r
            LEFT JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE r.id = #{id}
            GROUP BY r.id
            """)
    RoleSummaryRow selectSummaryById(@Param("id") long id);

    @Select("""
            <script>
            SELECT count(*) FROM sys_role
            WHERE id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    long countByIds(@Param("ids") Collection<Long> ids);
}
