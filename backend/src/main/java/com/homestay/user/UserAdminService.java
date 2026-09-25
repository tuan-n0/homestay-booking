package com.homestay.user;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.homestay.audit.AuditAction;
import com.homestay.audit.AuditService;
import com.homestay.auth.CurrentUser;
import com.homestay.auth.TokenService;
import com.homestay.common.ApiException;
import com.homestay.common.PageResponse;
import com.homestay.common.Times;
import com.homestay.common.Tokens;
import com.homestay.config.AppProperties;
import com.homestay.notification.MailService;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** S1-02: quản trị tạo và quản lý tài khoản nhân viên. */
@Service
public class UserAdminService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final TokenService tokenService;
    private final MailService mail;
    private final AuditService audit;
    private final AppProperties props;

    public UserAdminService(UserRepository users, RoleRepository roles, PasswordEncoder encoder,
                            TokenService tokenService, MailService mail, AuditService audit, AppProperties props) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.tokenService = tokenService;
        this.mail = mail;
        this.audit = audit;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String keyword, int page) {
        return PageResponse.of(users.search(keyword == null ? "" : keyword.trim(), PageRequest.of(Math.max(page, 0), 20)),
                UserAdminService::toResponse);
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> roles() {
        return roles.findAll().stream().map(r -> new RoleResponse(r.getCode(), r.getName())).toList();
    }

    @Transactional
    public UserResponse create(UserRequest req) {
        String email = req.email().trim();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Email " + email + " đã được dùng cho một tài khoản khác");
        }
        String tempPassword = Tokens.temporaryPassword();
        User user = new User();
        user.setFullName(req.fullName().trim());
        user.setEmail(email);
        user.setPhone(blankToNull(req.phone()));
        user.setRole(findRole(req.role()));
        user.setActive(req.active() == null || req.active());
        user.setPasswordHash(encoder.encode(tempPassword));
        user.setMustChangePassword(true);
        users.save(user);
        audit.logCurrent(AuditAction.USER_CREATED, "USER", user.getId(), "Vai trò " + user.getRole().getCode());
        mail.send(user.getEmail(), "Tài khoản hệ thống Homestay của bạn",
                "Xin chào " + user.getFullName() + ",\n\n"
                        + "Tài khoản của bạn đã được tạo với vai trò " + user.getRole().getName() + ".\n"
                        + "Email đăng nhập: " + user.getEmail() + "\n"
                        + "Mật khẩu tạm: " + tempPassword + "\n\n"
                        + "Đăng nhập tại " + props.frontendUrl() + "/login — bạn sẽ được yêu cầu đổi mật khẩu ở lần đăng nhập đầu tiên.");
        return toResponse(user);
    }

    @Transactional
    public UserResponse update(Long id, UserRequest req) {
        User user = users.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
        String email = req.email().trim();
        if (!user.getEmail().equalsIgnoreCase(email) && users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Email " + email + " đã được dùng cho một tài khoản khác");
        }
        boolean isSelf = id.equals(CurrentUser.get().id());
        Role newRole = findRole(req.role());
        boolean newActive = req.active() == null || req.active();
        if (isSelf && (!newActive || !newRole.getCode().equals(user.getRole().getCode()))) {
            throw ApiException.badRequest("Bạn không thể tự đổi vai trò hoặc vô hiệu hoá tài khoản của chính mình");
        }
        user.setFullName(req.fullName().trim());
        user.setEmail(email);
        user.setPhone(blankToNull(req.phone()));

        if (!newRole.getId().equals(user.getRole().getId())) {
            String detail = user.getRole().getCode() + " → " + newRole.getCode();
            user.setRole(newRole);
            tokenService.revokeAllSessions(user);   // quyền thay đổi → đăng nhập lại
            audit.logCurrent(AuditAction.ROLE_CHANGED, "USER", user.getId(), detail);
        }
        if (user.isActive() != newActive) {
            user.setActive(newActive);
            if (!newActive) {
                tokenService.revokeAllSessions(user);   // phiên hiện tại hết hiệu lực ngay ở request kế tiếp
                audit.logCurrent(AuditAction.ACCOUNT_DEACTIVATED, "USER", user.getId(), user.getEmail());
            } else {
                audit.logCurrent(AuditAction.ACCOUNT_ACTIVATED, "USER", user.getId(), user.getEmail());
            }
        }
        return toResponse(user);
    }

    private Role findRole(String code) {
        return roles.findByCode(code)
                .orElseThrow(() -> ApiException.badRequest("Vai trò không hợp lệ: " + code));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole().getCode(),
                u.getRole().getName(), u.isActive(), u.isMustChangePassword(),
                u.getLockedUntil() != null && u.getLockedUntil().isAfter(java.time.Instant.now()),
                Times.format(u.getCreatedAt()));
    }

    public record UserRequest(
            @NotBlank(message = "Vui lòng nhập họ tên") @Size(max = 100, message = "Họ tên tối đa 100 ký tự") String fullName,
            @NotBlank(message = "Vui lòng nhập email") @Email(message = "Email không đúng định dạng") String email,
            @Pattern(regexp = "^$|^0[0-9]{9}$", message = "Số điện thoại gồm 10 chữ số, bắt đầu bằng 0") String phone,
            @NotNull(message = "Vui lòng chọn vai trò") String role,
            Boolean active) {}

    public record UserResponse(Long id, String fullName, String email, String phone, String role, String roleName,
                               boolean active, boolean mustChangePassword, boolean locked, String createdAt) {}

    public record RoleResponse(String code, String name) {}
}
