package com.finance.wealth_service.transaction.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.transaction.dto.CreateTransactionRequest;

import java.util.UUID;

/**
 * A movement waiting to be posted: who is moving the money, the client's request
 * and the internal link that only a reversal carries. The owner comes from the
 * token and the link is not part of the request body, so a client can forge
 * neither by posting to {@code /transactions}.
 */
public record NewTransaction(AuthenticatedUser owner, CreateTransactionRequest request, UUID reversalOf) {

    public NewTransaction(AuthenticatedUser owner, CreateTransactionRequest request) {
        this(owner, request, null);
    }
}
