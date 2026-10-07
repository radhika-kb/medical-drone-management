package com.medicaldrone.model;

import jakarta.persistence.*;

@Entity
@Table(name = "roles")
public class Role {

    public static final String ADMIN = "ROLE_ADMIN";
    public static final String REQUESTING_HOSPITAL = "ROLE_REQUESTING_HOSPITAL";
    public static final String SUPPLYING_HOSPITAL = "ROLE_SUPPLYING_HOSPITAL";
    public static final String CENTRAL_HUB = "ROLE_CENTRAL_HUB";
    public static final String OPERATOR = "ROLE_OPERATOR";
    public static final String MEDICAL_CENTER_USER = "ROLE_MEDICAL_CENTER_USER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    public Role() {}

    public Role(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
