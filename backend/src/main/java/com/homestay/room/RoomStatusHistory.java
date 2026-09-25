package com.homestay.room;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "room_status_history")
@Getter
@Setter
public class RoomStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer roomId;

    @Enumerated(EnumType.STRING)
    private RoomStatus fromStatus;

    @Enumerated(EnumType.STRING)
    private RoomStatus toStatus;

    private Long maintenanceId;

    private String note;

    private Long changedBy;

    private Instant changedAt = Instant.now();
}
