const env = __ENV;

function int(value, fallback) {
  if (value === undefined || value === null || value === '') return fallback;
  return parseInt(value, 10);
}

function num(value, fallback) {
  if (value === undefined || value === null || value === '') return fallback;
  return Number(value);
}

function bool(value, fallback) {
  if (value === undefined || value === null || value === '') return fallback;
  return String(value).toLowerCase() === 'true' || value === '1';
}

/** "10:1m,100:2m,0:15s" -> [{ target: 10, duration: '1m' }, ...] */
function stages(raw, fallback) {
  if (!raw) return fallback;
  return raw.split(',').map(function (chunk) {
    const parts = chunk.split(':');
    return { target: parseInt(parts[0], 10), duration: (parts[1] || '1m').trim() };
  });
}

export const BASE_URL = (env.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
export const SETUP_TIMEOUT = env.SETUP_TIMEOUT || '5m';
export const SETUP_CONCURRENCY = int(env.SETUP_CONCURRENCY, 25);

// --- test data shape -------------------------------------------------------
export const NUM_USERS = int(env.NUM_USERS, 200);
export const ACCOUNTS_PER_USER = int(env.ACCOUNTS_PER_USER, 2);
export const BUCKETS_PER_USER = int(env.BUCKETS_PER_USER, 3);
export const INITIAL_BALANCE = num(env.INITIAL_BALANCE, 10000000.0);

// Pin every VU onto user #1 and its accounts. Turns the run into a pure
// row-lock contention test: every write fights for the same FOR UPDATE NOWAIT
// locks and you should see the 409 rate climb.
export const SHARED_USER = bool(env.SHARED_USER, false);

// --- write mix (weights are relative, they do not have to add up to 100) ----
export const W_INCOME = num(env.W_INCOME, 10);
export const W_EXPENSE = num(env.W_EXPENSE, 30);
export const W_ACCOUNT_TRANSFER = num(env.W_ACCOUNT_TRANSFER, 30);
export const W_BUCKET_ALLOCATE = num(env.W_BUCKET_ALLOCATE, 20);
export const W_BUCKET_RELEASE = num(env.W_BUCKET_RELEASE, 10);

export const AMOUNT_MIN = num(env.AMOUNT_MIN, 2);
export const AMOUNT_MAX = num(env.AMOUNT_MAX, 50);
export const TOP_UP_EVERY = int(env.TOP_UP_EVERY, 25);
export const TOP_UP_AMOUNT = num(env.TOP_UP_AMOUNT, 50000);

// --- executors -------------------------------------------------------------
// arrival: open model, one transaction per iteration at a fixed rate. Use this
//          to find the saturation point (it keeps pushing while latency rises).
// vus:     closed model, N concurrent users. Use this for per-user latency.
export const EXECUTOR = env.EXECUTOR || 'arrival';
export const DURATION = env.DURATION || '1m';
export const RAMP_STAGES = stages(env.RAMP_STAGES, [
  { target: 25, duration: '30s' },
  { target: 100, duration: DURATION },
  { target: 0, duration: '15s' },
]);
export const MAX_VUS = int(env.MAX_VUS, 300);
export const PRE_ALLOCATED_VUS = int(env.PRE_ALLOCATED_VUS, 50);

// The read scenario is paced by arrival rate too. A plain constant-vus loop
// with no think time turns into a read firehose that starves the write
// scenario and tells you nothing about mixed-load behaviour.
export const READ_RATE = int(env.READ_RATE, 20);
export const READ_STAGES = stages(env.READ_STAGES, [{ target: READ_RATE, duration: DURATION }]);

// --- misc ------------------------------------------------------------------
export const THINK_TIME_MS = int(env.THINK_TIME_MS, 0);
export const TIMEOUT = env.TIMEOUT || '30s';
export const TAGS = { suite: 'wealth-service' };

export const READ_THRESHOLD_MS = int(env.READ_THRESHOLD_MS, 500);
export const WRITE_THRESHOLD_MS = int(env.WRITE_THRESHOLD_MS, 750);

export const ACCOUNT_NAMES = ['Bank', 'Broker', 'Cash', 'Poupanca', 'XTB'];
export const BUCKET_NAMES = ['Casa', 'Emergencia', 'Carro', 'Investimento', 'Festas'];
export const CATEGORIES = ['HOUSE', 'EMERGENCY', 'CAR', 'INVESTMENT', 'OTHER'];

/**
 * Statuses that must not be scored as HTTP failures:
 *  200/201 success, 409 the documented "row is locked" outcome, 400 business
 *  validation. Everything else (5xx, transport errors) is a real failure.
 *
 * Kept as a list because k6 2.x reads expectedStatuses() as an exact list of
 * statuses, not as a (min, max) range.
 */
export const EXPECTED_STATUS_CODES = [200, 201, 400, 409];

export function scenarios() {
  const out = {};

  out.transactions = {
    exec: 'transactions',
    startTime: '0s',
    gracefulStop: '30s',
    tags: { path: 'transactions' },
  };

  if (EXECUTOR === 'arrival') {
    out.transactions.executor = 'ramping-arrival-rate';
    out.transactions.startRate = int(env.START_RATE, 10);
    out.transactions.timeUnit = '1s';
    out.transactions.preAllocatedVUs = PRE_ALLOCATED_VUS;
    out.transactions.maxVUs = MAX_VUS;
    out.transactions.stages = RAMP_STAGES;
  } else {
    out.transactions.executor = 'ramping-vus';
    out.transactions.startVUs = 0;
    out.transactions.stages = stages(env.VUS_STAGES, [
      { target: MAX_VUS, duration: DURATION },
    ]);
  }

  if (READ_RATE > 0) {
    out.dashboard_reads = {
      executor: 'ramping-arrival-rate',
      exec: 'dashboardReads',
      startTime: '0s',
      startRate: Math.min(READ_RATE, int(env.READ_START_RATE, 5)),
      timeUnit: '1s',
      preAllocatedVUs: int(env.READ_PRE_ALLOCATED_VUS, 10),
      maxVUs: int(env.READ_MAX_VUS, 100),
      stages: READ_STAGES,
      gracefulStop: '30s',
      tags: { path: 'dashboard' },
    };
  }

  return out;
}

export function thresholds() {
  const t = {
    // Guards against a silently dead run: a scenario that throws on every
    // iteration produces zero checks and would otherwise pass every threshold.
    iterations: ['count>1'],
    http_req_duration: ['p(95)<' + (WRITE_THRESHOLD_MS + READ_THRESHOLD_MS)],
    transaction_duration: ['p(95)<' + WRITE_THRESHOLD_MS],
    unexpected_responses: ['count==0'],
    checks: ['rate>0.95'],
  };

  if (READ_RATE > 0) {
    t.dashboard_duration = ['p(95)<' + READ_THRESHOLD_MS];
  }

  // Under SHARED_USER the 409s are the measurement, not a regression.
  if (!SHARED_USER) {
    t.conflict_rate = ['rate<0.15'];
  }

  return t;
}