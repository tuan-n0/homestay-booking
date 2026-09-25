package com.homestay.user;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Đọc ma trận phân quyền từ bảng role_permissions (S1-04).
 * Thêm vai trò hoặc đổi quyền chỉ cần sửa dữ liệu, không phải sửa từng màn hình.
 */
@Service
public class PermissionService {

    private final JdbcTemplate jdbc;
    private final Map<Short, Set<String>> cache = new ConcurrentHashMap<>();

    public PermissionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Set<String> permissionsOf(Short roleId) {
        return cache.computeIfAbsent(roleId, id -> Set.copyOf(jdbc.queryForList("""
                SELECT p.code FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                WHERE rp.role_id = ?
                """, String.class, id)));
    }

    public List<Map<String, Object>> matrix() {
        return jdbc.queryForList("""
                SELECT r.code AS role, p.code AS permission
                FROM role_permissions rp
                JOIN roles r ON r.id = rp.role_id
                JOIN permissions p ON p.id = rp.permission_id
                ORDER BY r.id, p.id
                """);
    }

    public void clearCache() {
        cache.clear();
    }
}
