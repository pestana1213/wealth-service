# k6 load tests

Load test for `POST /transactions`, the hot path in `wealth-service`.

`setup()` onboards the test data over HTTP (`POST /users` -> `POST /accounts`
-> `POST /buckets`) so the measured scenarios only exercise the write path.
The write mix follows the transaction conventions in the root `README.md`:
`INCOME`, `EXPENSE`, `TRANSFER` between accounts, and `TRANSFER` that allocates
into and releases from a bucket.

## Running

```bash
docker compose up -d                       # Postgres on 127.0.0.1:5432
./mvnw spring-boot:run                     # app on :8080

docker run --rm -i --network host \
  -v "$PWD:/scripts:ro" -w /scripts grafana/k6:latest run wealth-service.js
```

Add `-e` flags between the image and `run` to configure the test. With k6
installed natively, drop the `docker run` wrapper and just use `k6 run`.

## What the numbers mean

The service locks accounts and buckets with `FOR UPDATE NOWAIT` and returns
`409` instead of waiting. Under concurrency a high `conflict_rate` is the
expected, correct outcome rather than a defect, so `409` is treated as an
expected response and tracked separately:

| Metric | Meaning |
| --- | --- |
| `transaction_duration` | Server-side write latency, split from reads |
| `transactions_created` | `201` responses |
| `transactions_conflict` / `conflict_rate` | `409`, the lock-contention outcome |
| `transactions_rejected` | `400` business validation, should stay near zero |
| `transactions_failed` / `unexpected_responses` | `5xx`, must be zero |
| `dashboard_duration` | `GET /users/{id}/dashboard` latency |

`http_req_failed` stays at `0.00%` because `http.expectedStatuses(200, 201,
400, 409)` excludes the documented outcomes. If you change that list, note that
k6 2.x treats those arguments as an exact set of statuses, not a `(min, max)`
range.

The `iterations: count>1` threshold exists so a scenario that throws on every
iteration fails loudly instead of passing with no data.

## Tuning

Executor and pacing:

| Env | Default | Notes |
| --- | --- | --- |
| `EXECUTOR` | `arrival` | `arrival` (open model, fixed rate) or `vus` (closed model) |
| `RAMP_STAGES` | `25:30s,100:1m,0:15s` | `rate:duration,rate:duration` |
| `DURATION` | `1m` | Used when `RAMP_STAGES` is not set |
| `MAX_VUS` | `300` | Ceiling for `arrival` |
| `READ_RATE` | `20` | Dashboard reads/s, `0` disables the read scenario |
| `THINK_TIME_MS` | `0` | Simulated user pause between iterations |

Test data:

| Env | Default |
| --- | --- |
| `NUM_USERS` | `200` |
| `ACCOUNTS_PER_USER` | `2` |
| `BUCKETS_PER_USER` | `3` |
| `INITIAL_BALANCE` | `10000000` |
| `BASE_URL` | `http://localhost:8080` |

Write mix (`W_*` are relative weights):

| Env | Default |
| --- | --- |
| `W_INCOME` | `10` |
| `W_EXPENSE` | `30` |
| `W_ACCOUNT_TRANSFER` | `30` |
| `W_BUCKET_ALLOCATE` | `20` |
| `W_BUCKET_RELEASE` | `10` |

`TOP_UP_EVERY` (default `25`) injects a periodic `INCOME` so balances stay away
from the underflow guard without skewing the mix.

## Two modes

**Many users, low contention (default).** Each VU owns its own user, so writes
spread across independent accounts and buckets. This measures throughput and
latency of the happy path.

```bash
-e NUM_USERS=500 -e RAMP_STAGES='25:30s,200:2m,400:2m,0:15s'
```

**Many users, one account (`SHARED_USER=true`).** Every VU writes to user #1's
accounts and buckets, which maximises row-lock contention. This is the run that
shows how the `409` path behaves under load; the `conflict_rate` threshold is
relaxed automatically in this mode because a high value is the measurement
itself.

```bash
-e SHARED_USER=true -e NUM_USERS=1 -e RAMP_STAGES='5:5s,150:30s,0:5s'
```

## Notes

- Provisioning is not included in the reported metrics; it happens in
  `setup()` before the scenarios start. `setupTimeout` is `5m` by default for
  large `NUM_USERS`.
- Each VU keeps a per-bucket ledger of what it has allocated so a release never
  overdraws a bucket and trips the `400` "final value < 0" check.
- VU-to-user assignment is `(vu.idInTest - 1) % NUM_USERS`, so lowering
  `NUM_USERS` below the VU count is another way to force contention.

## Homelab

The same scripts run in-cluster against an isolated database, so the test never
touches production data. The k6 workload is a suspended `CronJob` in
`apps/wealth-loadtest` of the `golias-homelab` repository, pointed at a separate
`wealth_loadtest` database on the existing PostgreSQL pod.

The deployment mounts these files from a `wealth-loadtest-scripts` ConfigMap,
which is generated rather than committed, so these scripts stay the single
source of truth. Regenerate it after changing anything here:

```bash
kubectl create configmap wealth-loadtest-scripts \
  --from-file=wealth-service.js=wealth-service.js \
  --from-file=config.js=lib/config.js \
  --from-file=api.js=lib/api.js \
  --from-file=workload.js=lib/workload.js \
  --from-file=uuid.js=lib/uuid.js \
  -n wealth-loadtest --dry-run=client -o yaml | kubectl apply -f -
```

`--from-file` flattens the paths, so the ConfigMap volume remaps the library keys
back into `lib/` for the relative imports to resolve.

Then run it on demand:

```bash
kubectl create job --from=cronjob/wealth-loadtest-k6 k6-run -n wealth-loadtest
kubectl logs -f job/k6-run -n wealth-loadtest
```

Test shape is set through the `env` block in `k6-cronjob.yaml`, which
re-initialises on every apply. Full detail, including cleanup, is in
`apps/wealth-loadtest/README.md` in the homelab repository.