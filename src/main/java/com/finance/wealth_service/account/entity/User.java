package com.finance.wealth_service.account.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String issuer;

    private String subject;

    @Column(nullable = false)
    private String name;

    private String email;

    protected User() {
    }

    public User(String issuer, String subject, String name, String email) {
        this.issuer = issuer;
        this.subject = subject;
        this.name = name;
        this.email = email;
    }

    public UUID getId() { return id; }

    public String getIssuer() { return issuer; }

    public String getSubject() { return subject; }

    public String getName() { return name; }

    public String getEmail() { return email; }
}
