package com.homestay.catalog;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "room_types")
@Getter
@Setter
public class RoomType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String code;

    private String name;

    private short standardCapacity;

    private short maxCapacity;

    private short bedCount;

    private String description;

    @Column(name = "is_active")
    private boolean active = true;

    /** S2-01: giá một đêm ngày thường / cuối tuần (VND). Null = chưa khai giá. */
    private Long weekdayPrice;

    private Long weekendPrice;

    @ManyToMany
    @JoinTable(name = "room_type_amenities",
            joinColumns = @JoinColumn(name = "room_type_id"),
            inverseJoinColumns = @JoinColumn(name = "amenity_id"))
    @OrderBy("name")
    private Set<Amenity> amenities = new LinkedHashSet<>();

    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isSellable() {
        return active && weekdayPrice != null && weekendPrice != null;
    }
}
