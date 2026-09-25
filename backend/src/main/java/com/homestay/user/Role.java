package com.homestay.user;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "roles")
@Getter
@Setter
public class Role {

    public static final String RECEPTIONIST = "RECEPTIONIST";
    public static final String HOUSEKEEPING = "HOUSEKEEPING";
    public static final String OWNER = "OWNER";
    public static final String ADMIN = "ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    private String code;

    private String name;
}
