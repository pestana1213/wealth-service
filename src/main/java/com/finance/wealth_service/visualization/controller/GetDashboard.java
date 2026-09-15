package com.finance.wealth_service.visualization.controller;

import com.finance.wealth_service.visualization.actions.GetDashboardAction;
import com.finance.wealth_service.visualization.dto.DashboardResponse;
import com.finance.wealth_service.visualization.dto.AccountView;
import com.finance.wealth_service.visualization.dto.BucketView;
import com.finance.wealth_service.visualization.dto.GoalView;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/users/{userId}")
@Tag(name = "Visualization", description = "Read-only balances and dashboard views")
public class GetDashboard {
    private final GetDashboardAction action;
    public GetDashboard(GetDashboardAction action) { this.action = action; }

    @GetMapping("/dashboard")
    @Operation(summary = "Get dashboard", description = "Returns totals, available cash, accounts, buckets and goals.")
    public DashboardResponse get(@PathVariable UUID userId) { return action.execute(userId); }

    @GetMapping("/accounts")
    @Operation(summary = "List user accounts")
    public List<AccountView> accounts(@PathVariable UUID userId) { return action.execute(userId).accounts(); }

    @GetMapping("/buckets")
    @Operation(summary = "List user buckets")
    public List<BucketView> buckets(@PathVariable UUID userId) { return action.execute(userId).buckets(); }

    @GetMapping("/goals")
    @Operation(summary = "List user goals", description = "Includes current bucket amount for goal progress.")
    public List<GoalView> goals(@PathVariable UUID userId) { return action.execute(userId).goals(); }
}
