# OpenObserve dashboard for wealth-service

`wealth-service-dashboard.json` is an OpenObserve dashboard covering HTTP
traffic, the transaction write path, and the JVM/pool signals that explain them.

It is the counterpart to `load-tests/openobserve/` in `control-hub`, and it is
**not** a direct port. `control-hub` is a Kubernetes reconciler, so its
`Saturation` tab is built entirely on meters that do not exist here — queue
depth, worker retries, informer sync, enqueue outcomes. Those panels were
dropped rather than copied empty. What replaces them is the contention path
that this service actually has: `FOR UPDATE NOWAIT` on accounts and buckets.

## Import

In OpenObserve: **Dashboards -> Import** and select the JSON file.

| Tab | What it answers |
| --- | --- |
| Traffic | Is the read path being hit hard, and is it keeping up? |
| Transactions | Are writes landing, and how much contention is being lost? |
| JVM, pool and process | Is the process itself running out of anything? |

There are no template variables. This dashboard is scoped to a single service
instance, so there is nothing to filter on.

## Panel layout is a 192-column grid

Each panel's `layout` (`x`, `y`, `w`, `h`) is handed straight to GridStack with
no rescaling, and OpenObserve initialises that grid with `column: 192`,
`cellHeight: 17px`. A layout authored against a 96-unit grid — two panels of
`w: 48` at `x: 0` and `x: 48` — renders in the **left half of the viewport** with
the right half empty, which looks like a rendering bug but is just coordinates
that never reach column 192.

These panels therefore use `w: 96` at `x: 0` and `x: 96`, two per row. OpenObserve
enforces `minW: 12` and `minH: 8` for chart panels, so anything smaller will not
survive a drag-resize. The generator that produced the file checks both.

## Metric names were verified, not guessed

Every series this dashboard queries was read off a **live scrape** of
`/actuator/prometheus` from a running instance, driven through a user, an
account, a bucket, a successful income, an allocation, an invalid transaction,
a 404, and eight concurrent transactions that produced seven `409`s. The
`fields.stream` of each panel, and every `{{label}}` in a legend, was then
checked against that scrape programmatically; the check is what caught two of
the problems below.

The result is pinned by
`src/test/java/com/finance/wealth_service/observability/WealthMetricsPrometheusContractTests.java`,
which registers the meters against a `PrometheusMeterRegistry` and asserts the
names and label values the dashboard depends on. A rename in Java code now fails
the build instead of quietly emptying a panel.

Meters used, and where they come from:

| Series | Type | Labels |
| --- | --- | --- |
| `wealth_transactions_total` | counter | `outcome` = `created`, `invalid`, `conflict` |
| `wealth_transaction_duration_seconds` | histogram | `outcome`, `le` |
| `wealth_dashboard_duration_seconds` | histogram | `result` = `ok`, `not_found`, `error`, `le` |
| `wealth_dashboard_failures_total` | counter | `reason` = `not_found`, `error` |
| `http_server_requests_seconds` | histogram | `uri`, `status`, `method`, `outcome`, `exception`, `error` |
| `jvm_threads_live_threads`, `jvm_threads_daemon_threads` | gauge | — |
| `jvm_memory_used_bytes`, `jvm_memory_max_bytes` | gauge | `area` |
| `jvm_gc_pause_seconds`, `jvm_gc_memory_allocated_bytes_total` | histogram / counter | — |
| `process_cpu_usage`, `process_files_open_files`, `process_uptime_seconds` | gauge | — |
| `hikaricp_connections_{active,max,pending,timeout_total,acquire_seconds}` | gauge / counter / timer | — |

The four `wealth_*` domain meters are new. They are registered by
`observability/WealthMetrics.java` and recorded by `CreateTransactionAction` and
`GetDashboardAction`.

## Five things to know before you trust it

### `wealth_http_requests_total` never records a 5xx

`observability/RequestMetricsFilter` reads `response.getStatus()` in a `finally`
block. For a request that ends in an unhandled exception, that block runs during
the initial dispatch, *before* the container's error dispatch sets the status.
The label is therefore the status at the end of the original dispatch, which is
still `200`.

Measured directly: two `POST /transactions` that each returned `500` were
recorded as

```
wealth_http_requests_total{route="/transactions",status="200"} 2.0
http_server_requests_seconds_count{uri="/transactions",status="500",exception="ConstraintViolationException"} 2
```

**Every status panel here uses `http_server_requests_seconds_count`**, which is
recorded by the Spring interceptor after error handling and is accurate.
`wealth_http_requests_total` is still useful as a route-normalised counter, but
not for status. Its `route` label is the more valuable of the two: the filter
collapses `/users/{uuid}/...` to `/users/{userId}/...` before tagging, which
`http_server_requests` also does via its own `uri` template.

### p95 panels need a config line that was missing

`http_server_requests_seconds` is only a **histogram** because
`management.metrics.distribution.percentiles-histogram.http.server.requests=true`
is set in `application.properties`. That line was **not present** before this
work. Without it the timer is a summary, publishing only `_count`, `_sum` and
`_max`, and both `histogram_quantile` panels are permanently empty while
looking perfectly valid. This is the same trap documented in control-hub's
dashboard README, where `v1/applications p95` was empty for the same reason.

`wealth_http_request_duration_seconds`, the pre-existing filter timer, is still
a **summary** and has no buckets. It is not queried here. The three new domain
timers call `publishPercentileHistogram()`, so their quantile panels work.

### `jvm_threads_live` does not exist in Spring Boot 4

The correct names are **`jvm_threads_live_threads`** and
**`jvm_threads_daemon_threads`**. Micrometer 1.17 renamed them. The `JVM, pool
and process` tab in `control-hub` queries the old spellings, so its thread panel
is empty for the same reason PromQL is correct. Worth knowing if you ever diff
the two dashboards.

### There is no reconciliation or saturation tab, and cannot be one

The single most interesting thing about this service is that
`CreateTransactionAction` locks accounts and buckets with `FOR UPDATE NOWAIT`
and maps any lock collision to `409`. Under eight concurrent transactions
against one account the split is `201 x1, 409 x7`. Contention is therefore not
a rare error path here, it is the expected outcome of two writers racing, and
the client is expected to retry. The `Conflict ratio` panel is the one to
watch; the rest of the `Transactions` tab exists to tell apart a genuinely stuck
writer from ordinary contention.

There is no queue, no worker, no informer and no lease, so none of the
`control-hub` `Saturation` panels have any counterpart to query.

### A missing `category` returns 500, not 400

Found while generating this dashboard, unrelated to it, and not fixed here.
`Transaction.category` is `@NotNull` on the entity but the
`CreateTransactionRequest` DTO does not require it. Posting a transaction
without a `category` passes bean validation on the request, then fails
Hibernate's pre-insert validation with an unhandled
`ConstraintViolationException`, surfacing as a `500` rather than a client error.

Worth fixing separately: it is a client mistake being reported as a server
fault, and the `Transactions -> Transaction responses by status` panel will
show a 500 line for a caller who simply forgot a field.

## How to read it during a run

**Request rate up, latency flat** — the read path is absorbing more load. This
is the healthy case.

**Conflict ratio climbing while p95 stays flat** — ordinary contention. The
`FOR UPDATE NOWAIT` lock is doing its job: losing writers fail fast instead of
queueing. Nothing to fix unless the ratio approaches the request rate.

**Conflict ratio near 1** — a single hot account or bucket. Every writer is
losing. Look at whether one `uri` dominates, which points at one account
absorbing all traffic.

**Latency up, pool pending at max** — the pool is the bottleneck. `db_pool` is
arranged so `in use` and `pending` are read against `pool max` on the same
axis; `pending` pinned at max is the signal, and `pool acquire` rises before
`in use` does.

**Heap climbing steadily with no GC relief** — a retention problem rather than
an allocation-rate problem. `GetDashboardAction` runs three repository calls
per request and calls `buckets.findByUserIdOrderByName` twice with identical
arguments (`GetDashboardAction.java:31` and `:33`); the second is used to build
the goal-amount map. It is a redundant query on the hottest read path, and would
show up here first as allocation pressure under sustained dashboard traffic.

## Local verification

The dashboard can be checked without OpenObserve:

```sh
docker compose up -d
./mvnw spring-boot:run
curl -s localhost:8080/actuator/prometheus | grep -E '^wealth_'
```

Every `wealth_*` series above should appear. Note that `wealth_transactions_total`
only has an `outcome` series once a transaction has been attempted, and
`wealth_dashboard_failures_total` only after a 404, because Micrometer does not
pre-register tag combinations.
