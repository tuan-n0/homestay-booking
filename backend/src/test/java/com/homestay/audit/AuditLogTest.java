package com.homestay.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.homestay.IntegrationTestBase;

/** S1-05: nhật ký có IP, lọc được, 50 dòng/trang, không sửa xoá được. */
class AuditLogTest extends IntegrationTestBase {

    @Test
    void failedLoginIsLoggedWithIpAndLogIsAppendOnly() {
        login("admin@test.local", "sai-mat-khau1");
        Resp page = get("/api/audit-logs?action=LOGIN_FAILED", adminToken);
        assertThat(page.status()).isEqualTo(200);
        assertThat(page.body().get("size").asInt()).isEqualTo(50);
        assertThat(page.body().get("content").get(0).get("ipAddress").asString()).isNotBlank();
        assertThat(page.body().get("content").get(0).get("time").asString()).matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}");

        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_logs")).hasMessageContaining("chỉ cho phép thêm mới");
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_logs SET action = 'X'")).hasMessageContaining("chỉ cho phép thêm mới");
    }
}
