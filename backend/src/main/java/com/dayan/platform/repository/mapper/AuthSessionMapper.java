package com.dayan.platform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayan.platform.model.AuthSession;
import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AuthSessionMapper extends BaseMapper<AuthSession> {

    @Select("""
            SELECT *
            FROM auth_session
            WHERE token_hash = #{tokenHash}
            FOR UPDATE
            """)
    AuthSession selectByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Update("""
            UPDATE auth_session
            SET token_hash = #{newTokenHash},
                expires_at = #{expiresAt},
                last_used_at = #{usedAt},
                ip_address = #{ipAddress},
                user_agent = #{userAgent}
            WHERE id = #{id}
              AND token_hash = #{oldTokenHash}
              AND revoked_at IS NULL
              AND expires_at > #{usedAt}
            """)
    int rotate(
            @Param("id") long id,
            @Param("oldTokenHash") String oldTokenHash,
            @Param("newTokenHash") String newTokenHash,
            @Param("expiresAt") OffsetDateTime expiresAt,
            @Param("usedAt") OffsetDateTime usedAt,
            @Param("ipAddress") String ipAddress,
            @Param("userAgent") String userAgent
    );

    @Update("""
            UPDATE auth_session
            SET revoked_at = #{revokedAt}
            WHERE token_hash = #{tokenHash}
              AND user_id = #{userId}
              AND revoked_at IS NULL
            """)
    int revokeByTokenHash(
            @Param("userId") long userId,
            @Param("tokenHash") String tokenHash,
            @Param("revokedAt") OffsetDateTime revokedAt
    );

    @Update("""
            UPDATE auth_session
            SET revoked_at = #{revokedAt}
            WHERE user_id = #{userId}
              AND id <> #{currentSessionId}
              AND revoked_at IS NULL
            """)
    int revokeOtherSessions(
            @Param("userId") long userId,
            @Param("currentSessionId") long currentSessionId,
            @Param("revokedAt") OffsetDateTime revokedAt
    );

    @Update("""
            UPDATE auth_session
            SET revoked_at = #{revokedAt}
            WHERE user_id = #{userId}
              AND revoked_at IS NULL
            """)
    int revokeAllByUserId(
            @Param("userId") long userId,
            @Param("revokedAt") OffsetDateTime revokedAt
    );
}
