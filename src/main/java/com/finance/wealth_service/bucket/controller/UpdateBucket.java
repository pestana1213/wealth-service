package com.finance.wealth_service.bucket.controller;

import com.finance.wealth_service.auth.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.finance.wealth_service.bucket.actions.UpdateBucketAction;
import com.finance.wealth_service.bucket.dto.CreateBucketResponse;
import com.finance.wealth_service.bucket.dto.UpdateBucketRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Buckets", description = "Money allocations")
public class UpdateBucket {
    private final UpdateBucketAction action;

    public UpdateBucket(UpdateBucketAction action) {
        this.action = action;
    }

    @PatchMapping("/buckets/{bucketId}")
    @Operation(summary = "Update a bucket",
            description = "Updates the bucket name, its account and/or corrects its allocated amount. Omitted fields are left unchanged. "
                    + "Correcting the amount records no transaction and leaves account balances untouched. "
                    + "Changing the account is only allowed while the bucket is empty (409 otherwise).")
    public CreateBucketResponse updateBucket(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID bucketId,
                                             @Valid @RequestBody UpdateBucketRequest request) {
        return CreateBucketResponse.from(action.execute(user, bucketId, request));
    }
}
