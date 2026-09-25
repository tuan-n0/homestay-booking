package com.homestay.auth;

import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class AuthDtos {

    /** S1-03: mật khẩu mới tối thiểu 8 ký tự, có cả chữ và số. */
    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,100}$";
    public static final String PASSWORD_MESSAGE = "Mật khẩu mới tối thiểu 8 ký tự, gồm cả chữ và số";

    private AuthDtos() {}

    public record LoginRequest(
            @NotBlank(message = "Vui lòng nhập email") String email,
            @NotBlank(message = "Vui lòng nhập mật khẩu") String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record TokenResponse(String accessToken, String refreshToken, long expiresIn, MeResponse user) {}

    public record MeResponse(Long id, String email, String fullName, String role, String roleName,
                             Set<String> permissions, boolean mustChangePassword) {}

    public record ChangePasswordRequest(
            @NotBlank(message = "Vui lòng nhập mật khẩu cũ") String oldPassword,
            @NotBlank(message = PASSWORD_MESSAGE) @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String newPassword) {}

    public record ForgotPasswordRequest(
            @NotBlank(message = "Vui lòng nhập email") @Email(message = "Email không đúng định dạng") String email) {}

    public record ResetPasswordRequest(
            @NotBlank(message = "Liên kết không hợp lệ") String token,
            @NotBlank(message = PASSWORD_MESSAGE) @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String newPassword) {}

    public record MessageResponse(String message) {}
}
