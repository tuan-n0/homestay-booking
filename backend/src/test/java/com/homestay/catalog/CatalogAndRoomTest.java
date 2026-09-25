package com.homestay.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.homestay.IntegrationTestBase;

/** S1-06, S1-07, S1-08, S1-09, S1-10. */
class CatalogAndRoomTest extends IntegrationTestBase {

    private long createType(String code, String token) {
        Resp r = post("/api/room-types", Map.of("code", code, "name", "Loại " + code, "standardCapacity", 2,
                "maxCapacity", 3, "bedCount", 1), token);
        assertThat(r.status()).isEqualTo(201);
        return r.body().get("id").asLong();
    }

    @Test
    void roomTypeRulesAndCannotDeleteWhileRoomsAttached() {
        String owner = tokenFor("OWNER");
        Resp bad = post("/api/room-types", Map.of("code", "BAD", "name", "Sai", "standardCapacity", 3,
                "maxCapacity", 2, "bedCount", 1), owner);
        assertThat(bad.status()).isEqualTo(400);
        assertThat(bad.message()).contains("không được nhỏ hơn");

        long typeId = createType("RT1", owner);
        assertThat(post("/api/room-types", Map.of("code", "rt1", "name", "Trùng", "standardCapacity", 2,
                "maxCapacity", 2, "bedCount", 1), owner).status()).isEqualTo(409);

        assertThat(post("/api/rooms", Map.of("roomNumber", "101", "floor", 1, "roomTypeId", typeId), owner).status()).isEqualTo(201);
        assertThat(post("/api/rooms", Map.of("roomNumber", "101", "floor", 1, "roomTypeId", typeId), owner).status()).isEqualTo(409);
        assertThat(call("DELETE", "/api/room-types/" + typeId, null, owner).status()).isEqualTo(409);
        assertThat(call("PATCH", "/api/room-types/" + typeId + "/active?value=false", null, owner).body().get("active").asBoolean())
                .isFalse();
    }

    @Test
    void amenitiesCannotBeAttachedTwiceOrDeletedWhileUsed() {
        String owner = tokenFor("OWNER");
        long typeId = createType("RT2", owner);
        long amenityId = post("/api/amenities", Map.of("code", "WIFI2", "name", "Wifi", "icon", "wifi"), owner)
                .body().get("id").asLong();
        assertThat(post("/api/room-types/" + typeId + "/amenities/" + amenityId, null, owner).status()).isEqualTo(200);
        assertThat(post("/api/room-types/" + typeId + "/amenities/" + amenityId, null, owner).status()).isEqualTo(409);
        assertThat(call("DELETE", "/api/amenities/" + amenityId, null, owner).status()).isEqualTo(409);

        call("PATCH", "/api/amenities/" + amenityId + "/active?value=false", null, owner);
        assertThat(get("/api/room-types/" + typeId, owner).body().get("amenities").size()).isZero();
    }

    @Test
    void changingRoomTypeWithFutureBookingsRequiresConfirmation() {
        String owner = tokenFor("OWNER");
        long typeA = createType("RTA", owner);
        long typeB = createType("RTB", owner);
        long roomId = post("/api/rooms", Map.of("roomNumber", "301", "floor", 3, "roomTypeId", typeA), owner)
                .body().get("id").asLong();
        Integer policyId = jdbc.queryForObject("SELECT max(id) FROM operating_policies", Integer.class);
        LocalDate in = LocalDate.now().plusDays(10);
        jdbc.update("""
                INSERT INTO bookings (code, room_type_id, room_id, check_in_date, check_out_date, guest_count,
                                      customer_name, customer_phone, policy_id)
                VALUES ('FUTURE01', ?, ?, ?, ?, 2, 'Khách tương lai', '0912345678', ?)
                """, typeA, roomId, in, in.plusDays(2), policyId);

        Map<String, Object> body = Map.of("roomNumber", "301", "floor", 3, "roomTypeId", typeB);
        Resp warn = call("PUT", "/api/rooms/" + roomId, body, owner);
        assertThat(warn.status()).isEqualTo(409);
        assertThat(warn.body().get("code").asString()).isEqualTo("FUTURE_BOOKINGS");
        assertThat(warn.body().get("bookings").get(0).get("code").asString()).isEqualTo("FUTURE01");

        assertThat(call("PUT", "/api/rooms/" + roomId + "?confirm=true", body, owner).status()).isEqualTo(200);
    }

    @Test
    void cancellationTiersMustDecreaseAndChangesAreVersioned() {
        String owner = tokenFor("OWNER");
        Map<String, Object> base = new HashMap<>(Map.of("checkInTime", "14:00", "checkOutTime", "12:00",
                "lateCheckoutFeePerHour", 50000, "extraPersonFee", 100000));
        base.put("tiers", java.util.List.of(Map.of("hoursBefore", 72, "refundPercent", 50),
                Map.of("hoursBefore", 24, "refundPercent", 60)));
        assertThat(post("/api/settings/policies", base, owner).status()).isEqualTo(400);

        base.put("tiers", java.util.List.of(Map.of("hoursBefore", 72, "refundPercent", 100),
                Map.of("hoursBefore", 48, "refundPercent", 70), Map.of("hoursBefore", 24, "refundPercent", 40),
                Map.of("hoursBefore", 6, "refundPercent", 10)));
        assertThat(post("/api/settings/policies", base, owner).message()).contains("tối đa 3");

        int before = jdbc.queryForObject("SELECT count(*) FROM operating_policies", Integer.class);
        base.put("tiers", java.util.List.of(Map.of("hoursBefore", 72, "refundPercent", 100),
                Map.of("hoursBefore", 24, "refundPercent", 50)));
        assertThat(post("/api/settings/policies", base, owner).status()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operating_policies", Integer.class)).isEqualTo(before + 1);
        assertThat(get("/api/settings", owner).body().get("history").get(0).get("createdBy").asString()).isEqualTo("Test OWNER");
    }

    @Test
    void roomStatusRules() {
        String owner = tokenFor("OWNER");
        String rec = tokenFor("RECEPTIONIST");
        long typeId = createType("RTS", owner);
        long roomId = post("/api/rooms", Map.of("roomNumber", "401", "floor", 4, "roomTypeId", typeId), owner)
                .body().get("id").asLong();

        assertThat(post("/api/rooms/" + roomId + "/status", Map.of("status", "MAINTENANCE"), rec).message())
                .contains("lý do");
        String today = LocalDate.now().toString();
        assertThat(post("/api/rooms/" + roomId + "/status", Map.of("status", "MAINTENANCE", "reason", "Hỏng điều hoà",
                "maintenanceFrom", today, "maintenanceTo", today), rec).status()).isEqualTo(200);
        assertThat(post("/api/rooms/" + roomId + "/status", Map.of("status", "OCCUPIED"), rec).status()).isEqualTo(400);

        jdbc.update("UPDATE rooms SET status = 'OCCUPIED' WHERE id = ?", roomId);
        assertThat(post("/api/rooms/" + roomId + "/status", Map.of("status", "MAINTENANCE", "reason", "x",
                "maintenanceFrom", today, "maintenanceTo", today), rec).message()).contains("trả phòng trước");

        Resp history = get("/api/rooms/" + roomId + "/status-history", rec);
        assertThat(history.body().get(0).get("changedBy").asString()).isEqualTo("Test RECEPTIONIST");
    }
}
