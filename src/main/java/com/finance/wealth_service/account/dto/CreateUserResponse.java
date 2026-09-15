package com.finance.wealth_service.account.dto;

import java.util.UUID;

public record CreateUserResponse(UUID id, String name, String email) {}
