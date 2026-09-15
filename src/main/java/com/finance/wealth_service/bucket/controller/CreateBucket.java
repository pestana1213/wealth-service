package com.finance.wealth_service.bucket.controller;

import com.finance.wealth_service.bucket.actions.CreateBucketAction;
import com.finance.wealth_service.bucket.dto.CreateBucketRequest;
import com.finance.wealth_service.bucket.dto.CreateBucketResponse;
import com.finance.wealth_service.bucket.entity.Bucket;
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
    public CreateBucketResponse create(@Valid @RequestBody CreateBucketRequest request) {
        Bucket bucket = action.execute(request);
        return new CreateBucketResponse(bucket.getId(), bucket.getUserId(), bucket.getName(), bucket.getCurrentAmount());
    }
}
