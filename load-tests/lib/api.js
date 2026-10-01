import http from 'k6/http';
import { check } from 'k6';

import {
  BASE_URL, TAGS, TIMEOUT,
  NUM_USERS, ACCOUNTS_PER_USER, BUCKETS_PER_USER, INITIAL_BALANCE, SETUP_CONCURRENCY,
  ACCOUNT_NAMES, BUCKET_NAMES,
} from './config.js';

// k6 2.x takes an exact status list here; (200, 499) would reject every 201.
http.setResponseCallback(http.expectedStatuses(200, 201, 400, 409));

const JSON_HEADERS = { 'Content-Type': 'application/json' };

function post(path, body, tags) {
  return http.post(BASE_URL + path, JSON.stringify(body), {
    headers: JSON_HEADERS,
    timeout: TIMEOUT,
    tags: Object.assign({ name: path }, TAGS, tags || {}),
  });
}

function postBatch(requests) {
  return http.batch(
    requests.map(function (r) {
      return {
        method: 'POST',
        url: BASE_URL + r.path,
        body: JSON.stringify(r.body),
        params: {
          headers: JSON_HEADERS,
          timeout: TIMEOUT,
          tags: Object.assign({ name: r.path }, TAGS, r.tags || {}),
        },
      };
    })
  );
}

/**
 * Onboards NUM_USERS users: POST /users -> POST /accounts -> POST /buckets.
 * Runs inside setup(), so it is not attributed to the measured scenarios, and
 * uses http.batch to keep provisioning time reasonable at large user counts.
 */
export function provisionUsers(runId) {
  const users = [];
  const byId = new Map();

  for (let batchStart = 0; batchStart < NUM_USERS; batchStart += SETUP_CONCURRENCY) {
    const batchSize = Math.min(SETUP_CONCURRENCY, NUM_USERS - batchStart);
    const emails = [];

    for (let i = 0; i < batchSize; i++) {
      const index = batchStart + i;
      emails.push('k6-' + runId + '-' + index + '@loadtest.local');
    }

    const userResponses = postBatch(
      emails.map(function (email, i) {
        return {
          path: '/users',
          body: { name: 'k6 User ' + (batchStart + i), email: email },
          tags: { phase: 'provision', entity: 'user' },
        };
      })
    );

    const children = [];

    userResponses.forEach(function (res, i) {
      const index = batchStart + i;
      if (res.status !== 201) {
        throw new Error(
          'provision: POST /users failed with ' + res.status + ' for ' + emails[i] +
          ' -> ' + (res.body || '').slice(0, 300)
        );
      }
      const userId = res.json('id');
      const user = { index: index, id: userId, accounts: [], buckets: [] };
      users.push(user);
      byId.set(userId, user);

      for (let a = 0; a < ACCOUNTS_PER_USER; a++) {
        children.push({
          userId: userId,
          bucket: 'account',
          slot: a,
          body: {
            userId: userId,
            name: ACCOUNT_NAMES[a % ACCOUNT_NAMES.length] + ' ' + index,
            currentAmount: INITIAL_BALANCE,
          },
        });
      }

      for (let b = 0; b < BUCKETS_PER_USER; b++) {
        children.push({
          userId: userId,
          bucket: 'bucket',
          slot: b,
          body: { userId: userId, name: BUCKET_NAMES[b % BUCKET_NAMES.length] + ' ' + index, currentAmount: 0 },
        });
      }
    });

    const childResponses = postBatch(
      children.map(function (c) {
        return {
          path: '/' + c.bucket + 's',
          body: c.body,
          tags: { phase: 'provision', entity: c.bucket },
        };
      })
    );

    childResponses.forEach(function (res, i) {
      const c = children[i];
      if (res.status !== 201) {
        throw new Error(
          'provision: POST /' + c.bucket + 's failed with ' + res.status +
          ' for user ' + c.userId + ' -> ' + (res.body || '').slice(0, 300)
        );
      }
      const target = c.bucket === 'account' ? byId.get(c.userId).accounts : byId.get(c.userId).buckets;
      target[c.slot] = res.json('id');
    });
  }

  users.forEach(function (u) {
    if (u.accounts.some(function (id) { return !id; })) throw new Error('provision: missing account id');
    if (u.buckets.some(function (id) { return !id; })) throw new Error('provision: missing bucket id');
  });

  return users;
}

export function createTransaction(body, tags) {
  const res = post('/transactions', body, Object.assign({ phase: 'workload' }, tags || {}));
  check(res, {
    'transaction responded 201 or 409': function (r) {
      return r.status === 201 || r.status === 409;
    },
    'transaction did not hit the server': function (r) {
      return r.status < 500;
    },
  });
  return res;
}

export function readDashboard(userId, tags) {
  const path = '/users/' + userId + '/dashboard';
  const res = http.get(BASE_URL + path, {
    timeout: TIMEOUT,
    tags: Object.assign({ name: path, phase: 'workload' }, TAGS, tags || {}),
  });
  check(res, {
    'dashboard responded 200': function (r) { return r.status === 200; },
  });
  return res;
}

export function healthCheck() {
  const res = http.get(BASE_URL + '/actuator/health', {
    timeout: TIMEOUT,
    tags: Object.assign({ name: '/actuator/health' }, TAGS),
  });
  if (res.status !== 200) {
    throw new Error(
      'service not healthy at ' + BASE_URL + ' (' + res.status + ') - start the app or set BASE_URL'
    );
  }
  return res;
}