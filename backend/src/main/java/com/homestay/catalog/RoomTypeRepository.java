package com.homestay.catalog;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface RoomTypeRepository extends JpaRepository<RoomType, Integer> {

    boolean existsByCodeIgnoreCase(String code);

    List<RoomType> findAllByOrderByNameAsc();

    List<RoomType> findByActiveTrueOrderByNameAsc();

    /** Khoá bi quan loại phòng để các lượt đặt cùng loại phòng xếp hàng tuần tự (S3-02). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RoomType t where t.id = :id")
    Optional<RoomType> lockById(@Param("id") Integer id);
}
