package com.homestay.settings;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "homestay")
@Getter
@Setter
public class Homestay {

    @Id
    private Short id = 1;

    private String name;

    private String address;

    private String phone;

    private String email;

    private Long updatedBy;

    private Instant updatedAt = Instant.now();
}
