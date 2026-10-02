package com.dayan.platform.service.impl;

import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.config.ApplicationProperties;
import com.dayan.platform.dto.AuthDtos.LoginRequest;
import com.dayan.platform.dto.AuthDtos.PasswordChangeRequest;
import com.dayan.platform.dto.AuthDtos.ProfileUpdateRequest;
import com.dayan.platform.model.AuthSession;
import com.dayan.platform.model.UserAccount;
import com.dayan.platform.repository.mapper.AuthSessionMapper;
import com.dayan.platform.repository.mapper.UserAccountMapper;
import com.dayan.platform.security.PlatformUserPrincipal;
import com.dayan.platform.service.AuthService;
import com.dayan.platform.vo.AuthViews.TokenView;
import com.dayan.platform.vo.AuthViews.UserView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthServiceImpl implements AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final ApplicationProperties properties;
    private final UserAccountMapper userAccountMapper;
    private final AuthSessionMapper authSessionMapper;

    public AuthServiceImpl(
            AuthenticationManager authenticationManager,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            ApplicationProperties properties,
            UserAccountMapper userAccountMapper,
            AuthSessionMapper authSessionMapper
    ) {
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.userAccountMapper = userAccountMapper;
        this.authSessionMapper = authSessionMapper;
    }

    @Override
    @Transactional
    public TokenView login(LoginRequest request, ClientMetadata client) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        PlatformUserPrincipal principal;
        try {
            principal = (PlatformUserPrincipal) authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.username().trim().toLowerCase(Locale.ROOT),
                            request.password()
                    )
            ).getPrincipal();
        } catch (AuthenticationException exception) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String refreshToken = randomRefreshToken();
        AuthSession session = new AuthSession();
        session.setUserId(principal.id());
        session.setTokenHash(hashToken(refreshToken));
        session.setExpiresAt(now.plus(properties.security().refreshTokenTtl()));
        session.setIpAddress(truncate(client.ipAddress(), 45));
        session.setUserAgent(truncate(client.userAgent(), 512));
        authSessionMapper.insert(session);

        UserAccount user = userAccountMapper.selectById(principal.id());
        user.setLastLoginAt(now);
        userAccountMapper.updateById(user);
        return tokenView(user, session.getId(), refreshToken);
    }

    @Override
    @Transactional
    public TokenView refresh(String refreshToken, ClientMetadata client) {
        String oldHash = hashToken(refreshToken);
        AuthSession session = authSessionMapper.selectByTokenHashForUpdate(oldHash);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (session == null || session.getRevokedAt() != null || !session.getExpiresAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        UserAccount user = userAccountMapper.selectById(session.getUserId());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            session.setRevokedAt(now);
            authSessionMapper.updateById(session);
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        String newRefreshToken = randomRefreshToken();
        int updated = authSessionMapper.rotate(
                session.getId(),
                oldHash,
                hashToken(newRefreshToken),
                now.plus(properties.security().refreshTokenTtl()),
                now,
                truncate(client.ipAddress(), 45),
                truncate(client.userAgent(), 512)
        );
        if (updated != 1) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        return tokenView(user, session.getId(), newRefreshToken);
    }

    @Override
    @Transactional
    public void logout(long userId, String refreshToken) {
        authSessionMapper.revokeByTokenHash(
                userId,
                hashToken(refreshToken),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public UserView currentUser(long userId) {
        return userView(requireEnabledUser(userId));
    }

    @Override
    @Transactional
    public UserView updateProfile(long userId, ProfileUpdateRequest request) {
        UserAccount user = requireEnabledUser(userId);
        user.setDisplayName(request.displayName().trim());
        user.setEmail(normalizeNullable(request.email(), true));
        user.setPhone(normalizeNullable(request.phone(), false));
        try {
            userAccountMapper.updateById(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "Email address is already in use");
        }
        return userView(user);
    }

    @Override
    @Transactional
    public void changePassword(long userId, long currentSessionId, PasswordChangeRequest request) {
        UserAccount user = requireEnabledUser(userId);
        if (request.currentPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_INVALID);
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_INVALID);
        }
        if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "New password exceeds 72 UTF-8 bytes");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(now);
        userAccountMapper.updateById(user);
        authSessionMapper.revokeOtherSessions(userId, currentSessionId, now);
    }

    private TokenView tokenView(UserAccount user, long sessionId, String refreshToken) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.security().accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.security().issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("sid", sessionId)
                .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims
        )).getTokenValue();
        return new TokenView(
                "Bearer",
                accessToken,
                properties.security().accessTokenTtl().toSeconds(),
                refreshToken,
                userView(user)
        );
    }

    private UserAccount requireEnabledUser(long userId) {
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    private UserView userView(UserAccount user) {
        List<String> permissions = userAccountMapper.selectPermissionCodes(user.getId());
        return new UserView(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPhone(),
                user.getDepartmentId(),
                permissions
        );
    }

    private String randomRefreshToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalizeNullable(String value, boolean lowerCase) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return lowerCase ? normalized.toLowerCase(Locale.ROOT) : normalized;
    }

    private String truncate(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength);
    }
}
