package com.homestay.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.homestay.IntegrationTestBase;

/** S1-04: mỗi vai trò có ít nhất một trường hợp bị từ chối, máy chủ trả 403 chứ không chỉ ẩn nút. */
class AccessControlTest extends IntegrationTestBase {

    @Test
    void receptionistCannotManagePricesOrCatalog() {
        String token = tokenFor("RECEPTIONIST");
        assertThat(post("/api/room-types", Map.of("code", "X1", "name", "X", "standardCapacity", 1,
                "maxCapacity", 1, "bedCount", 1), token).status()).isEqualTo(403);
        assertThat(post("/api/settings/policies", Map.of("checkInTime", "14:00", "checkOutTime", "12:00",
                "lateCheckoutFeePerHour", 0, "extraPersonFee", 0, "tiers", java.util.List.of()), token).status()).isEqualTo(403);
        assertThat(get("/api/users", token).status()).isEqualTo(403);
    }

    @Test
    void housekeepingOnlySeesCleaningList() {
        String token = tokenFor("HOUSEKEEPING");
        assertThat(get("/api/room-types", token).status()).isEqualTo(403);
        assertThat(get("/api/audit-logs", token).status()).isEqualTo(403);
        assertThat(get("/api/settings", token).status()).isEqualTo(403);
    }

    @Test
    void ownerCannotManageAccountsOrReadAuditLog() {
        String token = tokenFor("OWNER");
        assertThat(post("/api/users", Map.of("fullName", "A", "email", "a@x.com", "role", "ADMIN"), token).status())
                .isEqualTo(403);
        assertThat(get("/api/audit-logs", token).status()).isEqualTo(403);
    }

    @Test
    void adminCannotOperateRoomStatus() {
        assertThat(post("/api/rooms/1/status", Map.of("status", "VACANT_DIRTY"), adminToken).status()).isEqualTo(403);
    }

    @Test
    void missingTokenIsRejected() {
        assertThat(get("/api/rooms", null).status()).isEqualTo(401);
        assertThat(get("/api/users", "token-bia").status()).isEqualTo(401);
    }
}
