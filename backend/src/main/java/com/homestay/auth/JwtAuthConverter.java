package com.homestay.auth;

import java.util.List;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.homestay.user.PermissionService;
import com.homestay.user.User;
import com.homestay.user.UserRepository;

/**
 * Mỗi request đọc lại tài khoản từ DB:
 *  - tài khoản bị vô hiệu hoá → token bị từ chối ngay (S1-02, yêu cầu trong vòng 1 phút)
 *  - token_version trong token khác DB → phiên đã bị huỷ do đổi mật khẩu (S1-03)
 *  - còn phải đổi mật khẩu tạm → không có quyền nghiệp vụ nào (S1-02)
 */
@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String CLAIM_VERSION = "ver";

    private final UserRepository users;
    private final PermissionService permissionService;

    public JwtAuthConverter(UserRepository users, PermissionService permissionService) {
        this.users = users;
        this.permissionService = permissionService;
    }

    @Override
    @Transactional(readOnly = true)
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        User user = users.findById(userId)
                .orElseThrow(() -> new InvalidBearerTokenException("Tài khoản không tồn tại"));
        if (!user.isActive()) {
            throw new InvalidBearerTokenException("Tài khoản đã bị vô hiệu hoá");
        }
        Number version = jwt.getClaim(CLAIM_VERSION);
        if (version == null || version.intValue() != user.getTokenVersion()) {
            throw new InvalidBearerTokenException("Phiên đăng nhập đã bị huỷ");
        }
        Set<String> permissions = user.isMustChangePassword()
                ? Set.of()
                : permissionService.permissionsOf(user.getRole().getId());
        AuthUser principal = new AuthUser(user.getId(), user.getEmail(), user.getFullName(),
                user.getRole().getCode(), permissions, user.isMustChangePassword());
        List<SimpleGrantedAuthority> authorities = permissions.stream().map(SimpleGrantedAuthority::new).toList();
        return new UsernamePasswordAuthenticationToken(principal, jwt, authorities);
    }
}
