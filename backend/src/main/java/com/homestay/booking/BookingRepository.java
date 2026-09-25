package com.homestay.booking;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    Optional<Booking> findByCode(String code);

    boolean existsByCode(String code);

    /** S1-07: booking còn hiệu lực trong tương lai của một phòng. */
    @Query("""
            select b from Booking b
            where b.room.id = :roomId and b.status in :statuses and b.checkOutDate > :today
            order by b.checkInDate
            """)
    List<Booking> findUpcomingByRoom(@Param("roomId") Integer roomId, @Param("today") LocalDate today,
                                     @Param("statuses") Collection<BookingStatus> statuses);

    /** Các booking còn chiếm phòng giao với khoảng đêm [from, to). */
    @Query("""
            select b from Booking b
            where b.status in :statuses and b.checkInDate < :to and b.checkOutDate > :from
            """)
    List<Booking> findOccupying(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                @Param("statuses") Collection<BookingStatus> statuses);

    @Query("""
            select b from Booking b
            where b.room.id = :roomId and b.status in :statuses and b.checkInDate < :to and b.checkOutDate > :from
              and (:excludeId is null or b.id <> :excludeId)
            """)
    List<Booking> findConflicts(@Param("roomId") Integer roomId, @Param("from") LocalDate from,
                                @Param("to") LocalDate to, @Param("statuses") Collection<BookingStatus> statuses,
                                @Param("excludeId") Long excludeId);
}
