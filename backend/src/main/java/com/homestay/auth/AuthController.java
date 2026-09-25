package com.homestay.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.homestay.audit.AuditAction;
import com.homestay.audit.AuditService;
import com.homestay.auth.AuthDtos.ChangePasswordRequest;
import com.homestay.auth.AuthDtos.ForgotPasswordRequest;
import com.homestay.auth.AuthDtos.LoginRequest;
import com.homestay.auth.AuthDtos.MeResponse;
import com.homestay.auth.AuthDtos.MessageResponse;
import com.homestay.auth.AuthDtos.RefreshRequest;
import com.homestay.auth.AuthDtos.ResetPasswordRequest;
import com.homestay.auth.AuthDtos.TokenResponse;
import com.homestay.user.UserRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginService loginService;
    private final TokenService tokenService;
    private final PasswordService passwordService;
    private final UserRepository users;
    private final AuditService audit;

    public AuthController(LoginService loginService, TokenService tokenService, PasswordService passwordService,
                          UserRepository users, AuditService audit) {
        this.loginService = loginService;
        this.tokenService = tokenService;
        this.passwordService = passwordService;
        this.users = users;
        this.audit = audit;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return loginService.login(req.email(), req.password());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return tokenService.refresh(req.refreshToken());
    }

    @PostMapping("/logout")
    public MessageResponse logout(@RequestBody(required = false) RefreshRequest req) {
        if (req != null && req.refreshToken() != null) {
            tokenService.revoke(req.refreshToken());
        }
        audit.logCurrent(AuditAction.LOGOUT, "USER", CurrentUser.idOrNull(), null);
        return new MessageResponse("Đã đăng xuất");
    }

    @GetMapping("/me")
    public MeResponse me() {
        return tokenService.me(users.findById(CurrentUser.get().id()).orElseThrow());
    }

    @PutMapping("/change-password")
    public MessageResponse changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        passwordService.changePassword(CurrentUser.get().id(), req.oldPassword(), req.newPassword());
        return new MessageResponse("Đổi mật khẩu thành công. Vui lòng đăng nhập lại bằng mật khẩu mới");
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        passwordService.requestReset(req.email());
        return new MessageResponse("Nếu email tồn tại trong hệ thống, chúng tôi đã gửi liên kết đặt lại mật khẩu. "
                + "Vui lòng kiểm tra hộp thư (liên kết có hiệu lực 30 phút)");
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        passwordService.resetPassword(req.token(), req.newPassword());
        return new MessageResponse("Đặt lại mật khẩu thành công. Bạn có thể đăng nhập bằng mật khẩu mới");
    }
}
