package com.homestay.settings;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Một phiên bản tham số vận hành. Mỗi lần sửa tạo phiên bản mới; booking giữ phiên bản lúc tạo (S1-09). */
@Entity
@Table(name = "operating_policies")
@Getter
@Setter
public class OperatingPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private LocalTime checkInTime = LocalTime.of(14, 0);

    private LocalTime checkOutTime = LocalTime.of(12, 0);

    private long lateCheckoutFeePerHour;

    private long extraPersonFee;

    private long extraBedFee;

    private LocalTime lateCheckoutFullNightFrom = LocalTime.of(18, 0);

    /** Đêm được tính là cuối tuần theo ISO: 5 = thứ Sáu, 6 = thứ Bảy (S2-01). */
    private Short[] weekendDays = {5, 6};

    private Instant effectiveFrom = Instant.now();

    private Long createdBy;

    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("hoursBefore DESC")
    private List<CancellationTier> tiers = new ArrayList<>();

    /** Tỷ lệ hoàn cọc khi huỷ trước giờ nhận phòng `hoursBefore` giờ (S3-05). */
    public int refundPercentFor(long hoursBefore) {
        return tiers.stream()
                .sorted(Comparator.comparingInt(CancellationTier::getHoursBefore).reversed())
                .filter(t -> hoursBefore >= t.getHoursBefore())
                .findFirst()
                .map(t -> (int) t.getRefundPercent())
                .orElse(0);
    }

    public boolean isWeekend(java.time.LocalDate night) {
        int dow = night.getDayOfWeek().getValue();
        for (Short d : weekendDays) {
            if (d != null && d == dow) {
                return true;
            }
        }
        return false;
    }
}
