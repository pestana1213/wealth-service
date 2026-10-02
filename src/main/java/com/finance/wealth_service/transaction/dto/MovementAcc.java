package com.finance.wealth_service.transaction.dto;

import java.util.UUID;

public record MovementAcc (
    UUID id,
    String name,
    MovementSource type
) {}
