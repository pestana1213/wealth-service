package com.finance.wealth_service.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class WealthMetrics {
    public static final String TRANSACTIONS = "wealth.transactions";
    public static final String TRANSACTION_DURATION = "wealth.transaction.duration";
    public static final String DASHBOARD_DURATION = "wealth.dashboard.duration";
    public static final String DASHBOARD_FAILURES = "wealth.dashboard.failures";
    public static final String RECURRING = "wealth.recurring";

    private final MeterRegistry registry;

    public WealthMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordTransaction(String outcome, long elapsedNanos) {
        Counter.builder(TRANSACTIONS).tag("outcome", outcome).register(registry).increment();
        Timer.builder(TRANSACTION_DURATION).tag("outcome", outcome).publishPercentileHistogram()
                .register(registry).record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    public void recordDashboard(String result, long elapsedNanos) {
        Timer.builder(DASHBOARD_DURATION).tag("result", result).publishPercentileHistogram()
                .register(registry).record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    public void recordDashboardFailure(String reason) {
        Counter.builder(DASHBOARD_FAILURES).tag("reason", reason).register(registry).increment();
    }

    public void recordRecurring(String operation, String result) {
        Counter.builder(RECURRING).tag("operation", operation).tag("result", result)
                .register(registry).increment();
    }
}
