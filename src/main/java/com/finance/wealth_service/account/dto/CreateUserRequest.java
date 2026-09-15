package com.finance.wealth_service.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Email @Size(max = 254) String email) {
    public CreateUserRequest {
        if (email != null) {
            email = email.strip().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
