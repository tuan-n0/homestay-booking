package com.homestay.room;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "room_maintenances")
@Getter
@Setter
public class RoomMaintenance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer roomId;

    private LocalDate startDate;

    /** Tính cả ngày này. */
    private LocalDate endDate;

    private String reason;

    private Long createdBy;

    private Instant createdAt = Instant.now();

    private Instant finishedAt;
}
