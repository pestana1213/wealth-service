import { Counter, Rate, Trend } from 'k6/metrics';
import { sleep } from 'k6';
import exec from 'k6/execution';

import {
  SHARED_USER, THINK_TIME_MS, SETUP_TIMEOUT,
  TOP_UP_EVERY, W_INCOME, W_EXPENSE, W_ACCOUNT_TRANSFER, W_BUCKET_ALLOCATE, W_BUCKET_RELEASE,
  scenarios, thresholds,
} from './lib/config.js';
import { provisionUsers, createTransaction, readDashboard, healthCheck } from './lib/api.js';
import {
  income, expense, accountTransfer, allocateToBucket, releaseFromBucket,
} from './lib/workload.js';

const txDuration = new Trend('transaction_duration', true);
const dashboardDuration = new Trend('dashboard_duration', true);
const conflictRate = new Rate('conflict_rate');
const unexpected = new Counter('unexpected_responses');

const transactionsCreated = new Counter('transactions_created');
const transactionsConflict = new Counter('transactions_conflict');
const transactionsRejected = new Counter('transactions_rejected');
const transactionsFailed = new Counter('transactions_failed');

export const options = {
  scenarios: scenarios(),
  thresholds: thresholds(),
  setupTimeout: SETUP_TIMEOUT,
  discardResponseBodies: false,
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// ---------------------------------------------------------------- setup ----
// Onboards NUM_USERS (user + accounts + buckets) before the clock starts, so
// the measured scenarios only exercise the transaction write path.
export function setup() {
  healthCheck();
  const runId = exec.test.runId || String(Date.now());
  const users = provisionUsers(runId);
  console.log(
    'provisioned ' + users.length + ' users with ' + users[0].accounts.length +
    ' accounts and ' + users[0].buckets.length + ' buckets each'
  );
  return { users: users, runId: runId };
}

// Every VU owns an isolated JS runtime, so this ledger is per VU. It tracks,
// per bucket, how much this VU has allocated and not yet released, so a
// release can never overdraw a bucket and trip the "final value < 0" 400.
// Amounts are held in integer cents to avoid float drift.
let ledger = {};

/** Stable user assignment: VU n owns user n, so contention is controllable. */
function userFor(data) {
  if (SHARED_USER) return data.users[0];
  return data.users[(exec.vu.idInTest - 1) % data.users.length];
}

function ledgerCents(bucketId) {
  return ledger[bucketId] || 0;
}

function ledgerCredit(bucketId, amount) {
  ledger[bucketId] = ledgerCents(bucketId) + Math.round(amount * 100);
}

function ledgerDebit(bucketId, amount) {
  ledger[bucketId] = ledgerCents(bucketId) - Math.round(amount * 100);
}

/**
 * The bucket holding the largest unreleased allocation plus a safe amount to
 * release from it: at most half of it, so that VUs sharing the same user can
 * never drive a bucket negative.
 */
function releaseCandidate() {
  let bestId = null;
  let bestCents = 0;
  Object.keys(ledger).forEach(function (bucketId) {
    const cents = ledger[bucketId];
    if (cents > bestCents) {
      bestCents = cents;
      bestId = bucketId;
    }
  });
  if (bestId === null) return null;
  const amount = Math.floor(bestCents * 0.5) / 100;
  if (amount < 0.01) return null;
  return { bucketId: bestId, amount: amount };
}

function record(res) {
  txDuration.add(res.timings.duration);

  switch (res.status) {
    case 201:
      transactionsCreated.add(1);
      conflictRate.add(false);
      break;
    case 409:
      // Documented behaviour: FOR UPDATE NOWAIT, no wait and no retry.
      transactionsConflict.add(1);
      conflictRate.add(true);
      break;
    case 400:
      transactionsRejected.add(1);
      conflictRate.add(false);
      break;
    default:
      transactionsFailed.add(1);
      conflictRate.add(false);
      unexpected.add(1, { status: String(res.status) });
      break;
  }

  return res;
}

function send(payload) {
  return record(createTransaction(payload, { route: payload.type }));
}

// ------------------------------------------------------- transaction load --
export function transactions(data) {
  const user = userFor(data);
  const iteration = exec.scenario.iterationInTest;

  // Periodic income keeps account balances away from the underflow guard
  // without polluting the write mix.
  if (TOP_UP_EVERY > 0 && iteration % TOP_UP_EVERY === 0) {
    send(income(user));
  }

  const total = W_INCOME + W_EXPENSE + W_ACCOUNT_TRANSFER + W_BUCKET_ALLOCATE + W_BUCKET_RELEASE;
  let roll = Math.random() * total;

  if ((roll -= W_INCOME) < 0) {
    send(income(user));
  } else if ((roll -= W_EXPENSE) < 0) {
    send(expense(user));
  } else if ((roll -= W_ACCOUNT_TRANSFER) < 0) {
    send(accountTransfer(user));
  } else if ((roll -= W_BUCKET_ALLOCATE) < 0) {
    const payload = allocateToBucket(user);
    send(payload);
    // Credit the ledger even on 409: the release path is a separate request,
    // and under-releasing is safe while over-releasing is not.
    ledgerCredit(payload.destinationBucketId, payload.transactionAmount);
  } else {
    // Bucket release needs a matching allocation. In SHARED_USER mode the
    // ledger is meaningless because all VUs share one bucket, so fall back.
    const candidate = (!SHARED_USER && releaseCandidate());
    if (candidate) {
      send(releaseFromBucket(user, candidate.bucketId, candidate.amount));
      ledgerDebit(candidate.bucketId, candidate.amount);
    } else if (user.buckets.length > 0) {
      send(accountTransfer(user));
    } else {
      send(income(user));
    }
  }

  if (THINK_TIME_MS > 0) sleep(Math.random() * THINK_TIME_MS / 1000);
}

// ----------------------------------------------------------- read load ----
export function dashboardReads(data) {
  const user = userFor(data);
  const res = readDashboard(user.id);
  dashboardDuration.add(res.timings.duration);
  if (res.status !== 200) {
    unexpected.add(1, { status: String(res.status) });
  }
  if (THINK_TIME_MS > 0) sleep(Math.random() * THINK_TIME_MS / 1000);
}