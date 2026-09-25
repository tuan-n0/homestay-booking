package com.homestay.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.homestay.audit.AuditAction;
import com.homestay.audit.AuditService;
import com.homestay.common.ApiException;
import com.homestay.user.User;
import com.homestay.user.UserRepository;

/**
 * S1-01: đăng nhập bằng email + mật khẩu.
 *  - Sai thông tin → thông báo chung, không nói rõ sai email hay mật khẩu.
 *  - Sai 5 lần trong 15 phút → khoá 30 phút, ghi thời điểm khoá.
 * Bộ đếm được lưu trong giao dịch riêng để không bị huỷ khi ném lỗi đăng nhập sai.
 */
@Service
public class LoginService {

    static final int MAX_FAILED = 5;
    static final Duration FAILED_WINDOW = Duration.ofMinutes(15);
    static final Duration LOCK_DURATION = Duration.ofMinutes(30);
    static final String GENERIC_ERROR = "Email hoặc mật khẩu không đúng";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokenService;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public LoginService(UserRepository users, PasswordEncoder encoder, TokenService tokenService,
                        AuditService audit, TransactionTemplate tx, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.tokenService = tokenService;
        this.audit = audit;
        this.tx = tx;
        this.clock = clock;
    }

    public AuthDtos.TokenResponse login(String email, String password) {
        Instant now = clock.instant();
        LoginOutcome outcome = tx.execute(status -> attempt(email.trim(), password, now));
        switch (outcome.result()) {
            case SUCCESS -> {
                audit.log(AuditAction.LOGIN_SUCCESS, outcome.userId(), null, "USER", outcome.userId(), null);
                return tx.execute(status -> tokenService.issue(users.findById(outcome.userId()).orElseThrow()));
            }
            case LOCKED -> {
                audit.log(AuditAction.LOGIN_FAILED, outcome.userId(), email, "USER", outcome.userId(), "Tài khoản đang bị khoá");
                long minutes = Math.max(1, Duration.between(now, outcome.lockedUntil()).toMinutes() + 1);
                throw new ApiException(HttpStatus.LOCKED,
                        "Tài khoản tạm khoá do đăng nhập sai nhiều lần. Vui lòng thử lại sau " + minutes + " phút");
            }
            case JUST_LOCKED -> {
                audit.log(AuditAction.LOGIN_FAILED, outcome.userId(), email, "USER", outcome.userId(), "Sai mật khẩu");
                audit.log(AuditAction.ACCOUNT_LOCKED, outcome.userId(), email, "USER", outcome.userId(),
                        "Sai " + MAX_FAILED + " lần trong 15 phút, khoá 30 phút");
                throw new ApiException(HttpStatus.LOCKED,
                        "Bạn đã nhập sai " + MAX_FAILED + " lần. Tài khoản tạm khoá 30 phút");
            }
            default -> {
                audit.log(AuditAction.LOGIN_FAILED, outcome.userId(), email, outcome.userId() == null ? null : "USER",
                        outcome.userId(), outcome.userId() == null ? "Email không tồn tại" : "Sai mật khẩu hoặc tài khoản ngừng hoạt động");
                throw ApiException.unauthorized(GENERIC_ERROR);
            }
        }
    }

    private LoginOutcome attempt(String email, String password, Instant now) {
        Optional<User> found = users.findByEmailIgnoreCase(email);
        if (found.isEmpty()) {
            encoder.matches(password, "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BHmFW5.JT1WdQ6Q5cQ4J6D6Z6u8G"); // cân bằng thời gian phản hồi
            return LoginOutcome.failed(null);
        }
        User user = found.get();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            return new LoginOutcome(Result.LOCKED, user.getId(), user.getLockedUntil());
        }
        if (!encoder.matches(password, user.getPasswordHash()) || !user.isActive()) {
            if (!user.isActive()) {
                return LoginOutcome.failed(user.getId());
            }
            if (user.getFirstFailedAt() == null || user.getFirstFailedAt().plus(FAILED_WINDOW).isBefore(now)) {
                user.setFirstFailedAt(now);
                user.setFailedLoginCount(1);
            } else {
                user.setFailedLoginCount(user.getFailedLoginCount() + 1);
            }
            if (user.getFailedLoginCount() >= MAX_FAILED) {
                user.setLockedAt(now);
                user.setLockedUntil(now.plus(LOCK_DURATION));
                user.setFailedLoginCount(0);
                user.setFirstFailedAt(null);
                return new LoginOutcome(Result.JUST_LOCKED, user.getId(), user.getLockedUntil());
            }
            return LoginOutcome.failed(user.getId());
        }
        user.setFailedLoginCount(0);
        user.setFirstFailedAt(null);
        user.setLockedUntil(null);
        return new LoginOutcome(Result.SUCCESS, user.getId(), null);
    }

    enum Result { SUCCESS, FAILED, LOCKED, JUST_LOCKED }

    record LoginOutcome(Result result, Long userId, Instant lockedUntil) {
        static LoginOutcome failed(Long userId) {
            return new LoginOutcome(Result.FAILED, userId, null);
        }
    }
}
