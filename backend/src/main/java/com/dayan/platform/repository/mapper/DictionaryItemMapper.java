package com.dayan.platform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayan.platform.model.DictionaryItem;
import com.dayan.platform.repository.query.DictionaryRows.DictionaryItemRow;
import com.dayan.platform.repository.query.DictionaryRows.DictionaryTypeCountRow;
import com.dayan.platform.vo.DictionaryViews.DictionaryProjectOption;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface DictionaryItemMapper extends BaseMapper<DictionaryItem> {

    String ACCESS_PREDICATE = """
            (#{platformAdmin} = TRUE
             OR d.scope IN ('GLOBAL', 'SHARED')
             OR EXISTS (
                 SELECT 1
                 FROM basic_project_member pm
                 WHERE pm.project_id = d.project_id
                   AND pm.user_id = #{userId}
                   AND (pm.valid_from IS NULL OR CURRENT_TIMESTAMP >= pm.valid_from)
                   AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
             ))
            AND (d.dictionary_type != 'TAG_CATEGORY' OR #{platformAdmin} = TRUE)
            """;

    String ITEM_SELECT = """
            SELECT d.*, p.name AS project_name, creator.display_name AS creator_name
            FROM data_dictionary_entry d
            LEFT JOIN basic_project p ON p.id = d.project_id
            JOIN sys_user creator ON creator.id = d.creator_id
            """;

    @Select("""
            <script>
            """ + ITEM_SELECT + """
            WHERE """ + ACCESS_PREDICATE + """
              AND d.dictionary_type = #{dictionaryType}
              <if test="keyword != null and keyword != ''">
                AND (
                  d.english_text ILIKE '%' || #{keyword} || '%'
                  OR d.chinese_text ILIKE '%' || #{keyword} || '%'
                  OR d.japanese_text ILIKE '%' || #{keyword} || '%'
                )
              </if>
              <if test="scope != null and scope != ''">AND d.scope = #{scope}</if>
              <if test="projectId != null">AND d.project_id = #{projectId}</if>
            ORDER BY ${orderBy} ${direction}, d.id ${direction}
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<DictionaryItemRow> selectPage(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin,
            @Param("dictionaryType") String dictionaryType,
            @Param("keyword") String keyword,
            @Param("scope") String scope,
            @Param("projectId") Long projectId,
            @Param("orderBy") String orderBy,
            @Param("direction") String direction,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    @Select("""
            <script>
            SELECT count(*)
            FROM data_dictionary_entry d
            WHERE """ + ACCESS_PREDICATE + """
              AND d.dictionary_type = #{dictionaryType}
              <if test="keyword != null and keyword != ''">
                AND (
                  d.english_text ILIKE '%' || #{keyword} || '%'
                  OR d.chinese_text ILIKE '%' || #{keyword} || '%'
                  OR d.japanese_text ILIKE '%' || #{keyword} || '%'
                )
              </if>
              <if test="scope != null and scope != ''">AND d.scope = #{scope}</if>
              <if test="projectId != null">AND d.project_id = #{projectId}</if>
            </script>
            """)
    long countPage(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin,
            @Param("dictionaryType") String dictionaryType,
            @Param("keyword") String keyword,
            @Param("scope") String scope,
            @Param("projectId") Long projectId
    );

    @Select("""
            SELECT d.dictionary_type, count(*) AS count
            FROM data_dictionary_entry d
            WHERE """ + ACCESS_PREDICATE + """
            GROUP BY d.dictionary_type
            """)
    List<DictionaryTypeCountRow> selectTypeCounts(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin
    );

    @Select("""
            """ + ITEM_SELECT + """
            WHERE d.id = #{id}
              AND """ + ACCESS_PREDICATE + """
            """)
    DictionaryItemRow selectAccessibleById(
            @Param("id") long id,
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin
    );

    @Select("""
            """ + ITEM_SELECT + """
            WHERE d.id = #{id}
            """)
    DictionaryItemRow selectRowById(@Param("id") long id);

    @Select("""
            <script>
            SELECT count(*)
            FROM data_dictionary_entry
            WHERE scope = 'GLOBAL'
              AND dictionary_type = #{dictionaryType}
              AND lower(english_text) = lower(#{englishText})
              <if test="excludedId != null">AND id != #{excludedId}</if>
            </script>
            """)
    long countGlobalDuplicate(
            @Param("dictionaryType") String dictionaryType,
            @Param("englishText") String englishText,
            @Param("excludedId") Long excludedId
    );

    @Select("""
            <script>
            SELECT count(*)
            FROM data_dictionary_entry
            WHERE scope != 'GLOBAL'
              AND dictionary_type = #{dictionaryType}
              AND lower(english_text) = lower(#{englishText})
              AND creator_id = #{creatorId}
              <if test="excludedId != null">AND id != #{excludedId}</if>
            </script>
            """)
    long countCreatorDuplicate(
            @Param("dictionaryType") String dictionaryType,
            @Param("englishText") String englishText,
            @Param("creatorId") long creatorId,
            @Param("excludedId") Long excludedId
    );

    @Select("""
            SELECT count(*)
            FROM basic_project_member
            WHERE project_id = #{projectId}
              AND user_id = #{userId}
              AND (valid_from IS NULL OR CURRENT_TIMESTAMP >= valid_from)
              AND (valid_until IS NULL OR valid_until > CURRENT_TIMESTAMP)
            """)
    long countActiveProjectMembership(
            @Param("projectId") long projectId,
            @Param("userId") long userId
    );

    @Select("""
            SELECT count(*)
            FROM basic_project_member
            WHERE project_id = #{projectId}
              AND user_id = #{userId}
              AND role IN ('PROJECT_ADMIN', 'PROJECT_MANAGER')
              AND (valid_from IS NULL OR CURRENT_TIMESTAMP >= valid_from)
              AND (valid_until IS NULL OR valid_until > CURRENT_TIMESTAMP)
            """)
    long countProjectManagementMembership(
            @Param("projectId") long projectId,
            @Param("userId") long userId
    );

    @Select("""
            SELECT p.id, p.name
            FROM basic_project p
            WHERE p.status != 'ARCHIVED'
              AND (
                #{platformAdmin} = TRUE
                OR EXISTS (
                    SELECT 1
                    FROM basic_project_member pm
                    WHERE pm.project_id = p.id
                      AND pm.user_id = #{userId}
                      AND (pm.valid_from IS NULL OR CURRENT_TIMESTAMP >= pm.valid_from)
                      AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
                )
              )
            ORDER BY p.name, p.id
            """)
    List<DictionaryProjectOption> selectProjectOptions(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin
    );

    @Select("""
            """ + ITEM_SELECT + """
            WHERE """ + ACCESS_PREDICATE + """
              AND d.dictionary_type = #{dictionaryType}
            ORDER BY lower(d.english_text), d.id
            """)
    List<DictionaryItemRow> selectForExport(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin,
            @Param("dictionaryType") String dictionaryType
    );
}
