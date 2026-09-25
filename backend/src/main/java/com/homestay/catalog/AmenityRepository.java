package com.homestay.catalog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AmenityRepository extends JpaRepository<Amenity, Integer> {

    boolean existsByCodeIgnoreCase(String code);

    List<Amenity> findAllByOrderByNameAsc();

    @Query(value = "SELECT count(*) FROM room_type_amenities WHERE amenity_id = :id", nativeQuery = true)
    long countUsage(@Param("id") Integer id);
}
