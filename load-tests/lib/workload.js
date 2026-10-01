import { AMOUNT_MIN, AMOUNT_MAX, TOP_UP_AMOUNT, CATEGORIES, SHARED_USER } from './config.js';
import { money, pickIndex, today } from './uuid.js';

function base(user, type, amount, category, description) {
  return {
    userId: user.id,
    providerAccountId: null,
    destinationAccountId: null,
    sourceBucketId: null,
    destinationBucketId: null,
    transactionAmount: amount,
    type: type,
    category: category,
    occurredAt: today(),
    description: description,
  };
}

function randomAmount() {
  return money(AMOUNT_MIN, AMOUNT_MAX);
}

/**
 * INCOME: none -> Bank, no buckets.
 * Both the action's validate() and the `transaction_accounts` CHECK constraint
 * require providerAccountId and sourceBucketId to be NULL here.
 */
export function income(user, amount) {
  const payload = base(user, 'INCOME', amount || TOP_UP_AMOUNT, 'OTHER', 'k6 income');
  payload.destinationAccountId = user.accounts[pickIndex(user.accounts.length)];
  return payload;
}

/**
 * EXPENSE: Account -> none, with an optional source bucket.
 * sourceBucketId stays NULL so the expense draws on unallocated cash; debiting
 * a bucket that a concurrent transaction is also touching is what produces the
 * 400 "final value < 0" rejections.
 */
export function expense(user, amount) {
  const payload = base(user, 'EXPENSE', amount || randomAmount(), CATEGORIES[pickIndex(CATEGORIES.length)], 'k6 expense');
  payload.providerAccountId = user.accounts[pickIndex(user.accounts.length)];
  return payload;
}

/**
 * TRANSFER between two different accounts of the same user.
 * Lock order is normalised by UUID inside the action, so this exercises two
 * row locks without risking a deadlock.
 */
export function accountTransfer(user, amount) {
  const payload = base(user, 'TRANSFER', amount || randomAmount(), 'OTHER', 'k6 account transfer');
  payload.providerAccountId = user.accounts[0];
  payload.destinationAccountId = user.accounts[user.accounts.length - 1];
  return payload;
}

/**
 * TRANSFER Bank -> Bank with none -> Bucket: money becomes allocated.
 * Both sides of the account are the same row, so only the bucket balance moves.
 */
export function allocateToBucket(user, amount) {
  const payload = base(user, 'TRANSFER', amount || randomAmount(), 'INVESTMENT', 'k6 allocate');
  payload.providerAccountId = user.accounts[0];
  payload.destinationAccountId = user.accounts[0];
  payload.destinationBucketId = user.buckets[pickIndex(user.buckets.length)];
  return payload;
}

/**
 * TRANSFER Bank -> Bank with Bucket -> none: freeing an allocation.
 * This is the only path that debits a source bucket, so the caller must have
 * allocated first (see releasableAmount in the main script).
 */
export function releaseFromBucket(user, bucketId, amount) {
  const payload = base(user, 'TRANSFER', amount || randomAmount(), 'INVESTMENT', 'k6 release');
  payload.providerAccountId = user.accounts[0];
  payload.destinationAccountId = user.accounts[0];
  payload.sourceBucketId = bucketId;
  return payload;
}

export const BUCKET_RELEASES_SUPPORTED = !SHARED_USER;