package com.finance.wealth_service.visualization.controller;

import com.finance.wealth_service.auth.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.finance.wealth_service.visualization.actions.GetDashboardAction;
import com.finance.wealth_service.visualization.dto.DashboardResponse;
import com.finance.wealth_service.visualization.dto.AccountView;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Visualization", description = "Read-only balances and dashboard views")
public class GetDashboard {
    private final GetDashboardAction action;
    public GetDashboard(GetDashboardAction action) { this.action = action; }

    @GetMapping("/dashboard")
    @Operation(summary = "Get dashboard",
            description = "Returns the totals and every account, each with its active buckets and their goals.")
    public DashboardResponse get(@AuthenticationPrincipal AuthenticatedUser user) { return action.execute(user); }

    @GetMapping("/accounts")
    @Operation(summary = "List user accounts", description = "Each account with its active buckets and their goals.")
    public List<AccountView> accounts(@AuthenticationPrincipal AuthenticatedUser user) { return action.accounts(user); }
}
