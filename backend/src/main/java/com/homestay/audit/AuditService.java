package com.homestay.audit;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.homestay.auth.CurrentUser;
import com.homestay.common.PageResponse;
import com.homestay.common.RequestUtils;
import com.homestay.common.Times;
import com.homestay.user.User;
import com.homestay.user.UserRepository;

import jakarta.persistence.criteria.Predicate;

@Service
public class AuditService {

    public static final int PAGE_SIZE = 50;

    private final AuditLogRepository repo;
    private final UserRepository users;

    public AuditService(AuditLogRepository repo, UserRepository users) {
        this.repo = repo;
        this.users = users;
    }

    /** Ghi trong giao dịch riêng để nhật ký vẫn còn khi thao tác chính bị lỗi (vd đăng nhập sai). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, Long userId, String emailAttempt, String targetType, Long targetId, String detail) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setUserId(userId);
        log.setEmailAttempt(emailAttempt);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        log.setIpAddress(RequestUtils.currentIp());
        repo.save(log);
    }

    /** Ghi hành động của người đang đăng nhập. */
    public void logCurrent(String action, String targetType, Long targetId, String detail) {
        log(action, CurrentUser.idOrNull(), null, targetType, targetId, detail);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(LocalDate from, LocalDate to, Long userId, String action, int page) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), Times.startOfDay(from)));
            }
            if (to != null) {
                ps.add(cb.lessThan(root.get("createdAt"), Times.startOfDay(to.plusDays(1))));
            }
            if (userId != null) {
                ps.add(cb.equal(root.get("userId"), userId));
            }
            if (action != null && !action.isBlank()) {
                ps.add(cb.equal(root.get("action"), action));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<AuditLog> result = repo.findAll(spec,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        Map<Long, User> userMap = users.findAllById(result.getContent().stream()
                        .map(AuditLog::getUserId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return PageResponse.of(result, l -> {
            User u = l.getUserId() == null ? null : userMap.get(l.getUserId());
            return new AuditLogResponse(l.getId(), Times.format(l.getCreatedAt()), l.getCreatedAt().toString(),
                    l.getUserId(), u != null ? u.getEmail() : l.getEmailAttempt(), u != null ? u.getFullName() : null,
                    l.getIpAddress(), l.getAction(), l.getTargetType(), l.getTargetId(), l.getDetail());
        });
    }

    public record AuditLogResponse(Long id, String time, String timestamp, Long userId, String email, String fullName,
                                   String ipAddress, String action, String targetType, Long targetId, String detail) {}
}
