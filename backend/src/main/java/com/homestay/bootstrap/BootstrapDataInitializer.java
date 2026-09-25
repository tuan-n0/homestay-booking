package com.homestay.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.homestay.config.AppProperties;
import com.homestay.settings.CancellationTier;
import com.homestay.settings.Homestay;
import com.homestay.settings.HomestayRepository;
import com.homestay.settings.OperatingPolicy;
import com.homestay.settings.OperatingPolicyRepository;
import com.homestay.user.Role;
import com.homestay.user.RoleRepository;
import com.homestay.user.User;
import com.homestay.user.UserRepository;

/**
 * Lần chạy đầu tiên trên database trống: tạo tài khoản Quản trị, thông tin homestay
 * và phiên bản tham số vận hành mặc định (giờ nhận 14:00, trả 12:00).
 */
@Component
@Order(1)
public class BootstrapDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapDataInitializer.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final HomestayRepository homestays;
    private final OperatingPolicyRepository policies;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public BootstrapDataInitializer(UserRepository users, RoleRepository roles, HomestayRepository homestays,
                                    OperatingPolicyRepository policies, PasswordEncoder encoder, AppProperties props) {
        this.users = users;
        this.roles = roles;
        this.homestays = homestays;
        this.policies = policies;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() == 0) {
            User admin = new User();
            admin.setFullName("Quản trị hệ thống");
            admin.setEmail(props.bootstrapAdmin().email());
            admin.setPasswordHash(encoder.encode(props.bootstrapAdmin().password()));
            admin.setRole(roles.findByCode(Role.ADMIN).orElseThrow());
            admin.setMustChangePassword(true);
            users.save(admin);
            log.info("Đã tạo tài khoản quản trị đầu tiên: {} (bắt buộc đổi mật khẩu khi đăng nhập)", admin.getEmail());
        }
        if (homestays.count() == 0) {
            Homestay h = new Homestay();
            h.setName("Homestay của tôi");
            homestays.save(h);
        }
        if (policies.count() == 0) {
            OperatingPolicy p = new OperatingPolicy();
            p.setCreatedBy(users.findAll().get(0).getId());
            p.setLateCheckoutFeePerHour(50_000);
            p.setExtraPersonFee(100_000);
            p.setExtraBedFee(150_000);
            addTier(p, 72, 100);
            addTier(p, 24, 50);
            policies.save(p);
        }
    }

    private static void addTier(OperatingPolicy p, int hours, int percent) {
        CancellationTier t = new CancellationTier();
        t.setPolicy(p);
        t.setHoursBefore(hours);
        t.setRefundPercent((short) percent);
        p.getTiers().add(t);
    }
}
