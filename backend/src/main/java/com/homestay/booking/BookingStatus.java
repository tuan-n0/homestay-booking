package com.homestay.booking;

import java.util.EnumSet;
import java.util.Set;

public enum BookingStatus {
    PENDING("Chờ xác nhận"),
    CONFIRMED("Đã xác nhận"),
    CHECKED_IN("Đang lưu trú"),
    CLOSED("Đã đóng"),
    CANCELLED("Đã huỷ"),
    EXPIRED("Hết hạn giữ chỗ");

    /** Các trạng thái còn chiếm phòng (khớp điều kiện của ràng buộc ex_bookings_no_overlap). */
    public static final Set<BookingStatus> OCCUPYING = EnumSet.of(PENDING, CONFIRMED, CHECKED_IN);

    private final String label;

    BookingStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
