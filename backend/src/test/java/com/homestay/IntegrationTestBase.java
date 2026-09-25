package com.homestay;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.homestay.user.RoleRepository;
import com.homestay.user.User;
import com.homestay.user.UserRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Chạy ứng dụng thật trên cổng ngẫu nhiên với PostgreSQL 15 trong Docker (Testcontainers),
 * để kiểm thử cả ràng buộc ở tầng dữ liệu (chống đặt trùng, trigger chỉ ghi thêm...).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:15-alpine");

    static {
        POSTGRES.start();   // dùng chung 1 container cho mọi lớp test
    }

    public static final String PASSWORD = "Test12345";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    protected int port;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected UserRepository users;

    @Autowired
    protected RoleRepository roles;

    @Autowired
    protected PasswordEncoder encoder;

    protected String adminToken;

    @BeforeEach
    void setUpAdmin() {
        adminToken = tokenFor("ADMIN");
    }

    /** Tạo (nếu chưa có) tài khoản test của một vai trò đã đổi mật khẩu, trả về access token. */
    protected String tokenFor(String roleCode) {
        String email = roleCode.toLowerCase() + "@test.local";
        User u = users.findByEmailIgnoreCase(email).orElseGet(() -> {
            User nu = new User();
            nu.setFullName("Test " + roleCode);
            nu.setEmail(email);
            nu.setRole(roles.findByCode(roleCode).orElseThrow());
            nu.setPasswordHash(encoder.encode(PASSWORD));
            nu.setMustChangePassword(false);
            return users.save(nu);
        });
        return login(u.getEmail(), PASSWORD).body().get("accessToken").asString();
    }

    protected Resp login(String email, String password) {
        return call("POST", "/api/auth/login", Map.of("email", email, "password", password), null);
    }

    protected Resp call(String method, String path, Object body, String token) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Content-Type", "application/json");
            if (token != null) {
                b.header("Authorization", "Bearer " + token);
            }
            HttpRequest.BodyPublisher pub = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
            HttpResponse<String> res = HTTP.send(b.method(method, pub).build(), HttpResponse.BodyHandlers.ofString());
            JsonNode node = res.body() == null || res.body().isBlank() ? null : json.readTree(res.body());
            return new Resp(res.statusCode(), node);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected Resp get(String path, String token) {
        return call("GET", path, null, token);
    }

    protected Resp post(String path, Object body, String token) {
        return call("POST", path, body, token);
    }

    public record Resp(int status, JsonNode body) {
        public String message() {
            return body == null || body.get("message") == null ? null : body.get("message").asString();
        }
    }
}
