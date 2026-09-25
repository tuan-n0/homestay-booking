package com.homestay.booking;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.homestay.catalog.RoomType;
import com.homestay.room.Room;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "bookings")
@Getter
@Setter
public class Booking {

    public enum Source { ONLINE, WALK_IN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;

    @Enumerated(EnumType.STRING)
    private Source source = Source.ONLINE;

    @Enumerated(EnumType.STRING)
    private BookingStatus status = BookingStatus.PENDING;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "room_type_id")
    private RoomType roomType;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "room_id")
    private Room room;

    private LocalDate checkInDate;

    private LocalDate checkOutDate;

    private short guestCount;

    private String customerName;

    private String customerPhone;

    private String customerEmail;

    private String customerNote;

    private String internalNote;

    private Integer policyId;

    private long roomAmount;

    private long extraGuestAmount;

    private long totalAmount;

    private boolean policyAccepted;

    private Instant holdExpiresAt;

    private String createdIp;

    private Long createdBy;

    private Instant confirmedAt;

    private Long confirmedBy;

    private Instant checkedInAt;

    private Long checkedInBy;

    private Instant checkedOutAt;

    private Long checkedOutBy;

    private String earlyCheckoutNote;

    private Instant cancelledAt;

    private Long cancelledBy;

    private String cancelReason;

    private Short refundPercent;

    private Long refundAmount;

    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public int nights() {
        return (int) ChronoUnit.DAYS.between(checkInDate, checkOutDate);
    }
}
