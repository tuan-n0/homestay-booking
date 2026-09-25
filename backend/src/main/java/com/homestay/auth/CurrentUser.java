package com.homestay.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.homestay.common.ApiException;

public final class CurrentUser {

    private CurrentUser() {}

    public static AuthUser get() {
        AuthUser user = getOrNull();
        if (user == null) {
            throw ApiException.unauthorized("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại");
        }
        return user;
    }

    public static AuthUser getOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return user;
        }
        return null;
    }

    public static Long idOrNull() {
        AuthUser u = getOrNull();
        return u == null ? null : u.id();
    }
}
