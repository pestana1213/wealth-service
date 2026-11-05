package com.finance.wealth_service.transaction.actions;

import com.finance.wealth_service.transaction.dto.CreateTransactionRequest;

import java.util.UUID;

/**
 * A movement waiting to be posted: whose money it moves, the client's request
 * and the internal link that only a reversal carries. The owner comes from the
 * token and the link is not part of the request body, so a client can forge
 * neither by posting to {@code /transactions}.
 */
public record NewTransaction(UUID userId, CreateTransactionRequest request, UUID reversalOf) {

    public NewTransaction(UUID userId, CreateTransactionRequest request) {
        this(userId, request, null);
    }
}
