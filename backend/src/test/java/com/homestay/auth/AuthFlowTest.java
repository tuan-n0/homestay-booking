package com.homestay.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.homestay.IntegrationTestBase;
import com.homestay.user.User;

/** S1-01, S1-02, S1-03. */
class AuthFlowTest extends IntegrationTestBase {

    private User newUser(String email, boolean mustChange) {
        User u = new User();
        u.setFullName("Người dùng " + email);
        u.setEmail(email);
        u.setRole(roles.findByCode("RECEPTIONIST").orElseThrow());
        u.setPasswordHash(encoder.encode(PASSWORD));
        u.setMustChangePassword(mustChange);
        return users.save(u);
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveSameGenericMessage() {
        newUser("generic@test.local", false);
        Resp wrongPw = login("generic@test.local", "sai-mat-khau1");
        Resp unknown = login("khong-ton-tai@test.local", "sai-mat-khau1");
        assertThat(wrongPw.status()).isEqualTo(401);
        assertThat(unknown.status()).isEqualTo(401);
        assertThat(wrongPw.message()).isEqualTo(unknown.message()).isEqualTo("Email hoặc mật khẩu không đúng");
    }

    @Test
    void fiveFailuresLockAccountAndRecordLockTime() {
        User u = newUser("lock@test.local", false);
        for (int i = 0; i < 4; i++) {
            assertThat(login(u.getEmail(), "sai-mat-khau1").status()).isEqualTo(401);
        }
        assertThat(login(u.getEmail(), "sai-mat-khau1").status()).isEqualTo(423);
        // Đúng mật khẩu vẫn bị chặn trong 30 phút
        assertThat(login(u.getEmail(), PASSWORD).status()).isEqualTo(423);
        User reloaded = users.findById(u.getId()).orElseThrow();
        assertThat(reloaded.getLockedAt()).isNotNull();
        assertThat(reloaded.getLockedUntil()).isAfter(reloaded.getLockedAt().plusSeconds(29 * 60));
    }

    @Test
    void loginReturnsAccessAndRefreshTokenAndPasswordIsBcrypt() {
        User u = newUser("tokens@test.local", false);
        Resp r = login(u.getEmail(), PASSWORD);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("expiresIn").asLong()).isEqualTo(30 * 60);
        assertThat(r.body().get("refreshToken").asString()).isNotBlank();
        assertThat(users.findById(u.getId()).orElseThrow().getPasswordHash()).startsWith("$2a$10$");

        Resp refreshed = post("/api/auth/refresh", Map.of("refreshToken", r.body().get("refreshToken").asString()), null);
        assertThat(refreshed.status()).isEqualTo(200);
        // refresh token cũ đã bị xoay vòng, dùng lại bị từ chối
        assertThat(post("/api/auth/refresh", Map.of("refreshToken", r.body().get("refreshToken").asString()), null).status())
                .isEqualTo(401);
    }

    @Test
    void temporaryPasswordMustBeChangedBeforeUsingSystem() {
        User u = newUser("temp@test.local", true);
        String token = login(u.getEmail(), PASSWORD).body().get("accessToken").asString();
        assertThat(get("/api/rooms", token).status()).isEqualTo(403);
        Resp change = call("PUT", "/api/auth/change-password",
                Map.of("oldPassword", PASSWORD, "newPassword", "MatKhauMoi9"), token);
        assertThat(change.status()).isEqualTo(200);
        String fresh = login(u.getEmail(), "MatKhauMoi9").body().get("accessToken").asString();
        assertThat(get("/api/rooms", fresh).status()).isEqualTo(200);
    }

    @Test
    void changePasswordValidatesAndRevokesAllOldSessions() {
        User u = newUser("change@test.local", false);
        String sessionA = login(u.getEmail(), PASSWORD).body().get("accessToken").asString();
        String sessionB = login(u.getEmail(), PASSWORD).body().get("accessToken").asString();

        assertThat(call("PUT", "/api/auth/change-password", Map.of("oldPassword", "sai", "newPassword", "Abcdefg12"), sessionA).message())
                .isEqualTo("Mật khẩu cũ không đúng");
        assertThat(call("PUT", "/api/auth/change-password", Map.of("oldPassword", PASSWORD, "newPassword", "chichucai"), sessionA).status())
                .isEqualTo(400);
        assertThat(call("PUT", "/api/auth/change-password", Map.of("oldPassword", PASSWORD, "newPassword", "1234567"), sessionA).status())
                .isEqualTo(400);

        assertThat(call("PUT", "/api/auth/change-password", Map.of("oldPassword", PASSWORD, "newPassword", "Abcdefg12"), sessionA).status())
                .isEqualTo(200);
        assertThat(get("/api/auth/me", sessionA).status()).isEqualTo(401);
        assertThat(get("/api/auth/me", sessionB).status()).isEqualTo(401);
    }

    @Test
    void forgotPasswordLimitedToThreePerHourAndLinkIsSingleUse() {
        User u = newUser("forgot@test.local", false);
        for (int i = 0; i < 3; i++) {
            assertThat(post("/api/auth/forgot-password", Map.of("email", u.getEmail()), null).status()).isEqualTo(200);
        }
        assertThat(post("/api/auth/forgot-password", Map.of("email", u.getEmail()), null).status()).isEqualTo(429);
        // Email không tồn tại vẫn trả như thường
        assertThat(post("/api/auth/forgot-password", Map.of("email", "nobody@test.local"), null).status()).isEqualTo(200);

        // Tạo một token biết trước để thử dùng 2 lần và hết hạn
        String raw = "known-token-value";
        jdbc.update("INSERT INTO password_reset_tokens (user_id, token_hash, expires_at) VALUES (?, ?, now() + interval '30 minutes')",
                u.getId(), com.homestay.common.Tokens.sha256(raw));
        assertThat(post("/api/auth/reset-password", Map.of("token", raw, "newPassword", "DatLai12345"), null).status()).isEqualTo(200);
        assertThat(post("/api/auth/reset-password", Map.of("token", raw, "newPassword", "DatLai12345"), null).message())
                .contains("đã được sử dụng");
        assertThat(login(u.getEmail(), "DatLai12345").status()).isEqualTo(200);

        String expired = "expired-token-value";
        jdbc.update("INSERT INTO password_reset_tokens (user_id, token_hash, expires_at, created_at) VALUES (?, ?, now() - interval '1 minute', now() - interval '2 hours')",
                u.getId(), com.homestay.common.Tokens.sha256(expired));
        assertThat(post("/api/auth/reset-password", Map.of("token", expired, "newPassword", "DatLai12345"), null).message())
                .contains("hết hạn");
    }

    @Test
    void deactivatedAccountLosesSessionImmediatelyAndDuplicateEmailRejected() {
        Resp created = post("/api/users", Map.of("fullName", "Buồng phòng B", "email", "hk-b@test.local", "role", "HOUSEKEEPING"), adminToken);
        assertThat(created.status()).isEqualTo(201);
        assertThat(post("/api/users", Map.of("fullName", "Trùng", "email", "HK-B@test.local", "role", "RECEPTIONIST"), adminToken).message())
                .contains("HK-B@test.local").contains("đã được dùng");

        long id = created.body().get("id").asLong();
        User u = users.findById(id).orElseThrow();
        u.setPasswordHash(encoder.encode(PASSWORD));
        u.setMustChangePassword(false);
        users.save(u);
        String session = login("hk-b@test.local", PASSWORD).body().get("accessToken").asString();
        assertThat(get("/api/auth/me", session).status()).isEqualTo(200);

        Resp deactivated = call("PUT", "/api/users/" + id,
                Map.of("fullName", "Buồng phòng B", "email", "hk-b@test.local", "role", "HOUSEKEEPING", "active", false), adminToken);
        assertThat(deactivated.status()).isEqualTo(200);
        assertThat(get("/api/auth/me", session).status()).isEqualTo(401);
        assertThat(login("hk-b@test.local", PASSWORD).status()).isEqualTo(401);
    }
}
