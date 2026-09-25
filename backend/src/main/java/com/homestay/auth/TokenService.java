package com.homestay.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.homestay.common.ApiException;
import com.homestay.common.Tokens;
import com.homestay.config.AppProperties;
import com.homestay.user.PermissionService;
import com.homestay.user.User;
import com.homestay.user.UserRepository;

/** S1-01: access token 30 phút + refresh token (lưu dạng băm, xoay vòng mỗi lần làm mới). */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final PermissionService permissionService;
    private final AppProperties props;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, RefreshTokenRepository refreshTokens, UserRepository users,
                        PermissionService permissionService, AppProperties props, Clock clock) {
        this.encoder = encoder;
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.permissionService = permissionService;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public AuthDtos.TokenResponse issue(User user) {
        Instant now = clock.instant();
        Duration ttl = Duration.ofMinutes(props.jwt().accessTokenMinutes());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("homestay")
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim(JwtAuthConverter.CLAIM_VERSION, user.getTokenVersion())
                .claim("role", user.getRole().getCode())
                .build();
        String access = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        String rawRefresh = Tokens.randomUrlSafe(32);
        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setTokenHash(Tokens.sha256(rawRefresh));
        rt.setExpiresAt(now.plus(Duration.ofDays(props.jwt().refreshTokenDays())));
        refreshTokens.save(rt);

        return new AuthDtos.TokenResponse(access, rawRefresh, ttl.toSeconds(), me(user));
    }

    @Transactional
    public AuthDtos.TokenResponse refresh(String rawRefresh) {
        Instant now = clock.instant();
        RefreshToken rt = refreshTokens.findByTokenHash(Tokens.sha256(rawRefresh))
                .filter(t -> t.getRevokedAt() == null && t.getExpiresAt().isAfter(now))
                .orElseThrow(() -> ApiException.unauthorized("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại"));
        User user = users.findById(rt.getUserId())
                .filter(User::isActive)
                .orElseThrow(() -> ApiException.unauthorized("Tài khoản đã bị vô hiệu hoá"));
        rt.setRevokedAt(now);
        return issue(user);
    }

    @Transactional
    public void revoke(String rawRefresh) {
        refreshTokens.findByTokenHash(Tokens.sha256(rawRefresh))
                .filter(t -> t.getRevokedAt() == null)
                .ifPresent(t -> t.setRevokedAt(clock.instant()));
    }

    /** Huỷ mọi phiên của tài khoản (đổi mật khẩu, vô hiệu hoá tài khoản). */
    @Transactional
    public void revokeAllSessions(User user) {
        user.revokeAllSessions();
        refreshTokens.revokeAllOfUser(user.getId(), clock.instant());
    }

    public AuthDtos.MeResponse me(User user) {
        return new AuthDtos.MeResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getRole().getCode(), user.getRole().getName(),
                user.isMustChangePassword() ? java.util.Set.of() : permissionService.permissionsOf(user.getRole().getId()),
                user.isMustChangePassword());
    }
}
