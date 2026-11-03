package com.finance.wealth_service.transaction.actions;

import com.finance.wealth_service.transaction.dto.CreateTransactionRequest;

import java.util.UUID;

/**
 * A movement waiting to be posted: the client's request plus the internal link
 * that only a reversal carries. The link is not part of the request body, so a
 * client cannot forge it by posting to {@code /transactions}.
 */
public record NewTransaction(CreateTransactionRequest request, UUID reversalOf) {

    public NewTransaction(CreateTransactionRequest request) {
        this(request, null);
    }
}
