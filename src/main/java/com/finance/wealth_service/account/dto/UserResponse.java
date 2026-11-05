package com.finance.wealth_service.account.dto;

import java.util.UUID;

public record UserResponse(UUID id, String name, String email) {}
