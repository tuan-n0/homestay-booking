package com.homestay.common;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Mọi mốc thời gian lưu UTC; hiển thị và tính ngày theo giờ Việt Nam. */
public final class Times {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private Times() {}

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }

    public static LocalDateTime nowLocal(Clock clock) {
        return LocalDateTime.now(clock.withZone(ZONE));
    }

    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }

    public static Instant at(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(ZONE).toInstant();
    }

    public static String format(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(instant.atZone(ZONE));
    }

    public static String format(LocalDate date) {
        return date == null ? "" : DATE.format(date);
    }
}
