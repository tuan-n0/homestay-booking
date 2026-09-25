package com.homestay.audit;

/** Các hành động được ghi nhật ký (S1-05 và yêu cầu bảo vệ dữ liệu cá nhân). */
public final class AuditAction {

    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String LOGOUT = "LOGOUT";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_UPDATED = "USER_UPDATED";
    public static final String ROLE_CHANGED = "ROLE_CHANGED";
    public static final String ACCOUNT_DEACTIVATED = "ACCOUNT_DEACTIVATED";
    public static final String ACCOUNT_ACTIVATED = "ACCOUNT_ACTIVATED";
    public static final String GUEST_ID_VIEWED = "GUEST_ID_VIEWED";
    public static final String SETTINGS_CHANGED = "SETTINGS_CHANGED";
    public static final String BOOKING_CONFIRMED = "BOOKING_CONFIRMED";
    public static final String BOOKING_UPDATED = "BOOKING_UPDATED";
    public static final String BOOKING_CANCELLED = "BOOKING_CANCELLED";
    public static final String PAYMENT_RECORDED = "PAYMENT_RECORDED";

    private AuditAction() {}
}
