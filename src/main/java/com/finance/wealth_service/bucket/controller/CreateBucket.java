package com.finance.wealth_service.bucket.controller;

import com.finance.wealth_service.auth.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.finance.wealth_service.bucket.actions.CreateBucketAction;
import com.finance.wealth_service.bucket.dto.CreateBucketRequest;
import com.finance.wealth_service.bucket.dto.CreateBucketResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Buckets", description = "Money allocations")
public class CreateBucket {
    private final CreateBucketAction action;

    public CreateBucket(CreateBucketAction action) {
        this.action = action;
    }

    @PostMapping("/buckets")
    @Operation(summary = "Create a bucket", description = "Creates a bucket with an optional initial allocation.")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateBucketResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                       @Valid @RequestBody CreateBucketRequest request) {
        return CreateBucketResponse.from(action.execute(user, request));
    }
}
