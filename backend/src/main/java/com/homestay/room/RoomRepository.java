package com.homestay.room;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, Integer> {

    boolean existsByRoomNumberIgnoreCase(String roomNumber);

    long countByRoomTypeId(Integer roomTypeId);

    List<Room> findByRoomTypeIdAndActiveTrueOrderByRoomNumberAsc(Integer roomTypeId);

    List<Room> findByActiveTrueOrderByFloorAscRoomNumberAsc();

    List<Room> findByStatusAndActiveTrueOrderByFloorAscRoomNumberAsc(RoomStatus status);

    @Query("""
            select r from Room r
            where (:roomTypeId is null or r.roomType.id = :roomTypeId)
              and (:floor is null or r.floor = :floor)
              and (:status is null or r.status = :status)
            order by r.floor, r.roomNumber
            """)
    List<Room> filter(@Param("roomTypeId") Integer roomTypeId, @Param("floor") Short floor,
                      @Param("status") RoomStatus status);
}
