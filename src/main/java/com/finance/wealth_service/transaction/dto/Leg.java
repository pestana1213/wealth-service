package com.finance.wealth_service.transaction.dto;

import java.util.UUID;

/** One side of a movement: the account the money is in and, optionally, the bucket within it. */
public record Leg(UUID accountId, UUID bucketId) {}
