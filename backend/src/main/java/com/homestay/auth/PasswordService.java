package com.homestay.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.homestay.audit.AuditAction;
import com.homestay.audit.AuditService;
import com.homestay.common.ApiException;
import com.homestay.common.Tokens;
import com.homestay.config.AppProperties;
import com.homestay.notification.MailService;
import com.homestay.user.User;
import com.homestay.user.UserRepository;

/** S1-03: đổi mật khẩu, quên mật khẩu, đặt lại mật khẩu. */
@Service
public class PasswordService {

    static final Duration RESET_TTL = Duration.ofMinutes(30);
    static final int MAX_RESET_PER_HOUR = 3;

    private final UserRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder encoder;
    private final TokenService tokenService;
    private final MailService mail;
    private final AuditService audit;
    private final AppProperties props;
    private final Clock clock;

    public PasswordService(UserRepository users, PasswordResetTokenRepository resetTokens, PasswordEncoder encoder,
                           TokenService tokenService, MailService mail, AuditService audit, AppProperties props,
                           Clock clock) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.encoder = encoder;
        this.tokenService = tokenService;
        this.mail = mail;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = users.findById(userId).orElseThrow();
        if (!encoder.matches(oldPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("Mật khẩu cũ không đúng");
        }
        if (encoder.matches(newPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("Mật khẩu mới phải khác mật khẩu cũ");
        }
        applyNewPassword(user, newPassword);
        audit.log(AuditAction.PASSWORD_CHANGED, user.getId(), null, "USER", user.getId(), null);
    }

    /** Luôn trả về như nhau dù email có tồn tại hay không, để không lộ danh sách tài khoản. */
    @Transactional
    public void requestReset(String email) {
        Instant now = clock.instant();
        users.findByEmailIgnoreCase(email.trim()).filter(User::isActive).ifPresent(user -> {
            long recent = resetTokens.countByUserIdAndCreatedAtAfter(user.getId(), now.minus(Duration.ofHours(1)));
            if (recent >= MAX_RESET_PER_HOUR) {
                throw ApiException.tooManyRequests(
                        "Bạn đã yêu cầu đặt lại mật khẩu quá 3 lần trong 1 giờ. Vui lòng thử lại sau");
            }
            String raw = Tokens.randomUrlSafe(32);
            PasswordResetToken token = new PasswordResetToken();
            token.setUserId(user.getId());
            token.setTokenHash(Tokens.sha256(raw));
            token.setExpiresAt(now.plus(RESET_TTL));
            token.setCreatedAt(now);
            resetTokens.save(token);
            String link = props.frontendUrl() + "/reset-password?token=" + raw;
            mail.send(user.getEmail(), "Đặt lại mật khẩu tài khoản Homestay",
                    "Xin chào " + user.getFullName() + ",\n\n"
                            + "Bạn (hoặc ai đó) vừa yêu cầu đặt lại mật khẩu. Nhấn vào liên kết dưới đây để đặt mật khẩu mới:\n"
                            + link + "\n\n"
                            + "Liên kết hết hạn sau 30 phút và chỉ dùng được một lần.\n"
                            + "Nếu bạn không yêu cầu, hãy bỏ qua email này.");
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken token = resetTokens.findByTokenHash(Tokens.sha256(rawToken))
                .orElseThrow(() -> ApiException.badRequest("Liên kết đặt lại mật khẩu không hợp lệ"));
        if (token.getUsedAt() != null) {
            throw ApiException.badRequest("Liên kết đã được sử dụng. Vui lòng yêu cầu liên kết mới");
        }
        if (token.getExpiresAt().isBefore(now)) {
            throw ApiException.badRequest("Liên kết đã hết hạn. Vui lòng yêu cầu liên kết mới");
        }
        token.setUsedAt(now);
        User user = users.findById(token.getUserId()).orElseThrow();
        applyNewPassword(user, newPassword);
        user.setFailedLoginCount(0);
        user.setFirstFailedAt(null);
        user.setLockedUntil(null);
        audit.log(AuditAction.PASSWORD_RESET, user.getId(), null, "USER", user.getId(), null);
    }

    private void applyNewPassword(User user, String newPassword) {
        user.setPasswordHash(encoder.encode(newPassword));
        user.setMustChangePassword(false);
        tokenService.revokeAllSessions(user);   // mọi phiên cũ bị huỷ
    }
}
