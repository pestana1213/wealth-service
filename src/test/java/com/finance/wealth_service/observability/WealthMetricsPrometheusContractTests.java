package com.finance.wealth_service.observability;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the Prometheus series names and labels that the OpenObserve dashboard in
 * {@code openobserve/} queries. The dashboard is raw PromQL, so a rename in
 * Java code would otherwise leave the panels silently empty.
 */
class WealthMetricsPrometheusContractTests {

    private String scrape(String... outcomes) {
        var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        var metrics = new WealthMetrics(registry);
        for (String outcome : outcomes) {
            metrics.recordTransaction(outcome, 1_000_000);
        }
        metrics.recordDashboard("ok", 1_000_000);
        metrics.recordDashboard("not_found", 1_000_000);
        metrics.recordDashboardFailure("not_found");
        metrics.recordDashboardFailure("error");
        return registry.scrape();
    }

    @Test
    void exposesCountersUnderTheNamesTheDashboardQueries() {
        Set<String> names = seriesNames(scrape("created", "invalid", "conflict"));
        assertThat(names).contains(
                "wealth_transactions_total",
                "wealth_dashboard_failures_total",
                "wealth_transaction_duration_seconds_bucket",
                "wealth_dashboard_duration_seconds_bucket");
    }
    @Test
    void tagsTransactionsByOutcomeSoTheConflictPanelCanSplitOnIt() {
        String scrape = scrape("created", "invalid", "conflict");
        assertThat(scrape).contains("wealth_transactions_total{");
        assertThat(scrape).contains("outcome=\"created\"");
        assertThat(scrape).contains("outcome=\"invalid\"");
        assertThat(scrape).contains("outcome=\"conflict\"");
    }

    @Test
    void tagsDashboardByResultAndFailuresByReason() {
        String scrape = scrape("created");
        assertThat(scrape).contains("result=\"ok\"");
        assertThat(scrape).contains("result=\"not_found\"");
        assertThat(scrape).contains("reason=\"not_found\"");
        assertThat(scrape).contains("reason=\"error\"");
    }

    @Test
    void publishesBucketsSoTheDashboardCanUseHistogramQuantiles() {
        String scrape = scrape("created");
        assertThat(scrape).contains("wealth_transaction_duration_seconds_bucket{");
        assertThat(scrape).contains("wealth_dashboard_duration_seconds_bucket{");
    }

    private Set<String> seriesNames(String scrape) {
        Set<String> names = new TreeSet<>();
        for (String line : scrape.split("\n")) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int brace = line.indexOf('{');
            int space = line.indexOf(' ');
            int end = brace < 0 ? space : (space < 0 ? brace : Math.min(brace, space));
            if (end > 0) {
                names.add(line.substring(0, end));
            }
        }
        return names;
    }
}
