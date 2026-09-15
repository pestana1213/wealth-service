package com.finance.wealth_service.account.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @NotBlank
    @Email
    @Size(max = 254)
    @Column(length = 254)
    private String email;

    protected User() {
    }

    public User(String name, String email) {
        this.name = name;
        this.email = java.util.Objects.requireNonNull(email).strip().toLowerCase(java.util.Locale.ROOT);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }
}
