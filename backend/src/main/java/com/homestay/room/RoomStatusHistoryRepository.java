package com.homestay.room;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomStatusHistoryRepository extends JpaRepository<RoomStatusHistory, Long> {

    List<RoomStatusHistory> findTop50ByRoomIdOrderByChangedAtDesc(Integer roomId);
}
