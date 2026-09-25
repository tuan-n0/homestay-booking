package com.homestay.auth;

import java.util.Set;

/** Người dùng đang đăng nhập, dựng lại từ access token + dữ liệu mới nhất trong DB. */
public record AuthUser(Long id, String email, String fullName, String role, Set<String> permissions,
                       boolean mustChangePassword) {

    public boolean has(String permission) {
        return permissions.contains(permission);
    }
}
