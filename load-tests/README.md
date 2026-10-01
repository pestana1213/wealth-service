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
| `START_RATE` | `10` | Initial arrival rate for `EXECUTOR=arrival` |
| `MAX_VUS` | `300` | Ceiling for `arrival`; see below |
| `PRE_ALLOCATED_VUS` | `50` | VUs created up front |
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

## Load profiles

There are no named profiles. Everything is a combination of env flags, and the
table above lists all of them. What follows is a set of starting points.

### Pick the executor first

`EXECUTOR=arrival` (the default) is an **open model**: k6 keeps offering load at
the requested rate no matter how slow the service gets. Throughput is
independent of latency, so latency climbing while throughput holds flat is the
clearest signal that you are approaching the ceiling. This is the mode to use for
capacity work.

`EXECUTOR=vus` is a **closed model**: a fixed number of concurrent users. Once
the service slows down, each user simply takes longer and offered load falls
away. That hides saturation, but it is the right model when the question is
"what does one user experience".

Do not compare a rate from one executor against a rate from the other. The
arrival number is offered load, the VU number is concurrent users.

### Profiles

**Baseline, low contention.** Each VU owns its own user, so writes spread across
independent accounts and buckets. Measures happy-path throughput and latency.

```bash
-e NUM_USERS=500 -e RAMP_STAGES='25:30s,200:2m,400:2m,0:15s' -e MAX_VUS=600
```

**Contention.** Every VU writes to user #1's accounts and buckets, which
maximises `FOR UPDATE NOWAIT` contention. This is the run that shows how the
`409` path behaves under load; the `conflict_rate` threshold is relaxed
automatically in this mode because a high value is the measurement itself.

```bash
-e SHARED_USER=true -e RAMP_STAGES='25:30s,100:1m,200:1m,0:15s'
```

**Heavy.** Roughly 4x the contention profile. Expect the p95 thresholds to
breach, which fails the run on purpose — that is the signal, not a bug.

```bash
-e SHARED_USER=true -e RAMP_STAGES='50:30s,400:2m,800:2m,0:15s' \
  -e MAX_VUS=1200 -e PRE_ALLOCATED_VUS=200
```

**Saturation hunt.** Raise the last stage until something gives. Stop when
either `dropped_iterations` is non-zero (k6 could not keep up) or the service
stops holding the requested rate.

```bash
-e SHARED_USER=true -e RAMP_STAGES='100:30s,400:2m,800:2m,1600:3m,0:15s' \
  -e MAX_VUS=2500 -e PRE_ALLOCATED_VUS=400
```

### Raising the load without breaking the measurement

**`MAX_VUS` has to clear the peak rate.** Each VU runs one iteration at a time,
so k6 needs roughly `peak_rate x p95_latency` concurrent VUs to hold a rate. It
does not slow down when it runs short of VUs, it silently drops iterations and
reports a throughput number lower than the one you asked for. If
`dropped_iterations` climbs, raise `MAX_VUS` before concluding the service is
saturated.

**`PRE_ALLOCATED_VUS` only affects startup.** It is how many VUs are created up
front, so it should be near `MAX_VUS` for a short run. Raising it costs memory
and makes the run slower to begin, which is why it is kept well below the
ceiling by default.

**`SHARED_USER=true` makes `NUM_USERS` irrelevant.** Every VU is pinned to user
#1, so raising `NUM_USERS` does nothing in that mode; only the arrival rate
matters. Conversely, to raise load in low-contention mode you must raise
`NUM_USERS`, since that is what caps the number of distinct users.

**Stages must end with `0`.** `ramping-arrival-rate` uses the final stage to
ramp down, and a run that stops at a non-zero target lingers at peak load during
teardown.

**Raise one thing at a time.** Changing the rate and the VU ceiling together
makes it impossible to tell which one caused a threshold to move.

### Reading a run

`transaction_duration` p95 is the headline number. `conflict_rate` tells you
whether you were measuring the service or the lock. If p95 rose while
`transactions_created` held steady, the service is degrading under load; if
`transactions_created` fell and `transactions_conflict` rose with it, the extra
load was absorbed by `409`s and the write path was never actually slower.

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

These scripts stay the single source of truth. The homelab repo gets them as a
generated ConfigMap, `apps/wealth-loadtest/scripts-configmap.yaml`, which the
pipeline regenerates and commits on every push, so it is never stale. To
regenerate it yourself, which needs no cluster access and no `kubectl`:

```bash
./render-configmap.py > \
  ~/Desktop/Projects/golias-homelab/apps/wealth-loadtest/scripts-configmap.yaml
```

ConfigMap keys cannot contain `/`, so `lib/api.js` is stored under the key
`api.js` and the CronJob's volume remaps the keys back into `lib/` for the
relative imports to resolve. `render-configmap.py` refuses to emit content that
a YAML block scalar could not round-trip, so the mounted files are
byte-identical to the ones in git.

Then run it from whichever machine has `kubectl`:

```bash
kubectl apply -k apps/wealth-loadtest/
kubectl create job --from=cronjob/wealth-loadtest-k6 k6-run -n wealth-loadtest
kubectl logs -f job/k6-run -n wealth-loadtest
```

Test shape is set through the `env` block in `k6-cronjob.yaml`, which
re-initialises on every apply. Full detail, including cleanup, is in
`apps/wealth-loadtest/README.md` in the homelab repository.