package com.finance.wealth_service.bucket.controller;

import com.finance.wealth_service.auth.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.finance.wealth_service.bucket.actions.ArchiveBucketAction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Buckets", description = "Money allocations")
public class ArchiveBucket {
    private final ArchiveBucketAction action;

    public ArchiveBucket(ArchiveBucketAction action) {
        this.action = action;
    }

    @DeleteMapping("/buckets/{bucketId}")
    @Operation(summary = "Archive a bucket",
            description = "Hides the bucket from the dashboard and refuses it in new transactions, keeping the row so the "
                    + "history still names it. The bucket must hold no money: a non-zero balance returns 409.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveBucket(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID bucketId) {
        action.execute(user, bucketId);
    }
}
