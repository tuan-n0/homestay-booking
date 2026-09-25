package com.homestay.settings;

import java.time.Clock;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.homestay.audit.AuditAction;
import com.homestay.audit.AuditService;
import com.homestay.auth.CurrentUser;
import com.homestay.common.ApiException;
import com.homestay.common.Times;
import com.homestay.user.User;
import com.homestay.user.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** S1-09: thông tin homestay và tham số vận hành có phiên bản. */
@Service
public class SettingsService {

    public static final int MAX_TIERS = 3;

    private final HomestayRepository homestays;
    private final OperatingPolicyRepository policies;
    private final UserRepository users;
    private final AuditService audit;
    private final Clock clock;

    public SettingsService(HomestayRepository homestays, OperatingPolicyRepository policies, UserRepository users,
                           AuditService audit, Clock clock) {
        this.homestays = homestays;
        this.policies = policies;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    /** Phiên bản tham số đang áp dụng tại thời điểm hiện tại. */
    @Transactional(readOnly = true)
    public OperatingPolicy currentPolicy() {
        return policies.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(clock.instant())
                .orElseThrow(() -> new IllegalStateException("Chưa có tham số vận hành"));
    }

    @Transactional(readOnly = true)
    public Homestay homestay() {
        return homestays.findById((short) 1).orElseThrow(() -> new IllegalStateException("Chưa có thông tin homestay"));
    }

    @Transactional(readOnly = true)
    public SettingsResponse get() {
        List<OperatingPolicy> history = policies.findTop20ByOrderByEffectiveFromDescIdDesc();
        Map<Long, User> userMap = users.findAllById(history.stream().map(OperatingPolicy::getCreatedBy).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return new SettingsResponse(toHomestay(homestay()), toPolicy(currentPolicy(), userMap),
                history.stream().map(p -> toPolicy(p, userMap)).toList());
    }

    @Transactional
    public HomestayDto updateHomestay(HomestayDto dto) {
        Homestay h = homestay();
        h.setName(dto.name().trim());
        h.setAddress(dto.address());
        h.setPhone(dto.phone());
        h.setEmail(dto.email());
        h.setUpdatedBy(CurrentUser.get().id());
        h.setUpdatedAt(clock.instant());
        audit.logCurrent(AuditAction.SETTINGS_CHANGED, "HOMESTAY", 1L, "Cập nhật thông tin homestay");
        return toHomestay(h);
    }

    /** Tạo phiên bản tham số mới, áp dụng từ bây giờ cho các booking tạo sau thời điểm này. */
    @Transactional
    public PolicyDto createPolicy(PolicyRequest req) {
        validateTiers(req.tiers());
        Set<Short> weekend = req.weekendDays() == null ? Set.of((short) 5, (short) 6) : new HashSet<>(req.weekendDays());
        if (weekend.stream().anyMatch(d -> d < 1 || d > 7)) {
            throw ApiException.badRequest("Ngày cuối tuần phải từ 1 (thứ Hai) đến 7 (Chủ nhật)");
        }
        OperatingPolicy p = new OperatingPolicy();
        p.setCheckInTime(req.checkInTime());
        p.setCheckOutTime(req.checkOutTime());
        p.setLateCheckoutFeePerHour(req.lateCheckoutFeePerHour());
        p.setExtraPersonFee(req.extraPersonFee());
        p.setExtraBedFee(req.extraBedFee() == null ? 0 : req.extraBedFee());
        p.setLateCheckoutFullNightFrom(req.lateCheckoutFullNightFrom() == null ? LocalTime.of(18, 0) : req.lateCheckoutFullNightFrom());
        p.setWeekendDays(weekend.stream().sorted().toArray(Short[]::new));
        p.setEffectiveFrom(clock.instant());
        p.setCreatedAt(clock.instant());
        p.setCreatedBy(CurrentUser.get().id());
        for (TierDto t : req.tiers()) {
            CancellationTier tier = new CancellationTier();
            tier.setPolicy(p);
            tier.setHoursBefore(t.hoursBefore());
            tier.setRefundPercent(t.refundPercent().shortValue());
            p.getTiers().add(tier);
        }
        policies.save(p);
        audit.logCurrent(AuditAction.SETTINGS_CHANGED, "POLICY", p.getId().longValue(), "Tạo phiên bản tham số mới");
        return toPolicy(p, Map.of());
    }

    /** Tối đa 3 mốc; càng gần ngày nhận phòng thì tỷ lệ hoàn càng thấp; không trùng mốc. */
    static void validateTiers(List<TierDto> tiers) {
        if (tiers.size() > MAX_TIERS) {
            throw ApiException.badRequest("Chính sách huỷ chỉ được khai báo tối đa " + MAX_TIERS + " mốc");
        }
        List<TierDto> sorted = new ArrayList<>(tiers);
        sorted.sort(Comparator.comparingInt(TierDto::hoursBefore).reversed());
        for (int i = 1; i < sorted.size(); i++) {
            TierDto prev = sorted.get(i - 1);
            TierDto cur = sorted.get(i);
            if (prev.hoursBefore().equals(cur.hoursBefore())) {
                throw ApiException.badRequest("Hai mốc huỷ bị trùng nhau ở " + cur.hoursBefore() + " giờ");
            }
            if (cur.refundPercent() >= prev.refundPercent()) {
                throw ApiException.badRequest("Các mốc huỷ phải giảm dần theo thời gian: mốc " + cur.hoursBefore()
                        + " giờ (" + cur.refundPercent() + "%) phải hoàn ít hơn mốc " + prev.hoursBefore()
                        + " giờ (" + prev.refundPercent() + "%)");
            }
        }
    }

    static HomestayDto toHomestay(Homestay h) {
        return new HomestayDto(h.getName(), h.getAddress(), h.getPhone(), h.getEmail());
    }

    static PolicyDto toPolicy(OperatingPolicy p, Map<Long, User> userMap) {
        User by = userMap.get(p.getCreatedBy());
        return new PolicyDto(p.getId(), p.getCheckInTime(), p.getCheckOutTime(), p.getLateCheckoutFeePerHour(),
                p.getExtraPersonFee(), p.getExtraBedFee(), p.getLateCheckoutFullNightFrom(),
                Arrays.asList(p.getWeekendDays()),
                p.getTiers().stream().map(t -> new TierDto(t.getHoursBefore(), (int) t.getRefundPercent())).toList(),
                Times.format(p.getEffectiveFrom()), by == null ? null : by.getFullName());
    }

    public record HomestayDto(@NotBlank(message = "Vui lòng nhập tên homestay") @Size(max = 150) String name,
                              @Size(max = 255) String address, @Size(max = 20) String phone,
                              @Size(max = 255) String email) {}

    public record TierDto(@NotNull @Min(value = 1, message = "Số giờ trước ngày nhận phòng phải lớn hơn 0") Integer hoursBefore,
                          @NotNull @Min(value = 0, message = "Tỷ lệ hoàn từ 0 đến 100%")
                          @Max(value = 100, message = "Tỷ lệ hoàn từ 0 đến 100%") Integer refundPercent) {}

    public record PolicyRequest(
            @NotNull(message = "Vui lòng nhập giờ nhận phòng") LocalTime checkInTime,
            @NotNull(message = "Vui lòng nhập giờ trả phòng") LocalTime checkOutTime,
            @NotNull @Min(value = 0, message = "Phụ thu trả muộn không được âm") Long lateCheckoutFeePerHour,
            @NotNull @Min(value = 0, message = "Phụ thu thêm người không được âm") Long extraPersonFee,
            @Min(value = 0, message = "Phụ thu thêm giường không được âm") Long extraBedFee,
            LocalTime lateCheckoutFullNightFrom,
            List<Short> weekendDays,
            @NotNull List<@Valid TierDto> tiers) {}

    public record PolicyDto(Integer id, LocalTime checkInTime, LocalTime checkOutTime, long lateCheckoutFeePerHour,
                            long extraPersonFee, long extraBedFee, LocalTime lateCheckoutFullNightFrom,
                            List<Short> weekendDays, List<TierDto> tiers, String effectiveFrom, String createdBy) {}

    public record SettingsResponse(HomestayDto homestay, PolicyDto current, List<PolicyDto> history) {}
}
