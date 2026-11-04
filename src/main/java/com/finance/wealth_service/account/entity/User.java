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

    private String issuer;

    private String subject;

    public User() {
    }

    public UUID getId() {
        return id;
    }

    public String getIssuer() { return issuer;}

    public String getSubject() {return subject;}
}
