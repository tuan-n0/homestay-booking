package com.homestay.room;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomMaintenanceRepository extends JpaRepository<RoomMaintenance, Long> {

    Optional<RoomMaintenance> findFirstByRoomIdAndFinishedAtIsNullOrderByCreatedAtDesc(Integer roomId);

    /** Các đợt bảo trì còn hiệu lực giao với khoảng đêm [from, to). */
    @Query("""
            select m from RoomMaintenance m
            where m.finishedAt is null and m.startDate < :to and m.endDate >= :from
            """)
    List<RoomMaintenance> findOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
