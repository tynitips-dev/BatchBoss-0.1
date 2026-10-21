import assert from 'node:assert/strict';
import test, { before, after } from 'node:test';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

if (process.env.FIRESTORE_EMULATOR_HOST !== '127.0.0.1:8080' ||
    process.env.GCLOUD_PROJECT !== 'demo-batchboss') {
  throw new Error('Integration tests must run only on local demo-batchboss emulator');
}
const require = createRequire(import.meta.url);
const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const { initializeApp, deleteApp } = require(join(root, 'node_modules/firebase-admin/app'));
const { getFirestore } = require(join(root, 'node_modules/firebase-admin/firestore'));
const { recomputeProEntitlement } = require(join(root, 'lib/proEntitlements.js'));
const app = initializeApp({ projectId: 'demo-batchboss' });
const db = getFirestore(app);
const uids = ['migration_read_only', 'trusted_payment', 'revoked_gift'];
const now = Date.now();
const future = new Date(now + 7 * 86400000);

before(async () => {
  for (const uid of uids) {
    const ref = db.doc(`users/${uid}`);
    await db.recursiveDelete(ref).catch(() => {});
  }
});
after(async () => {
  for (const uid of uids) await db.recursiveDelete(db.doc(`users/${uid}`)).catch(() => {});
  await deleteApp(app);
});
test('ordinary entitlement reads leave unmigrated profile and grants untouched', async () => {
  const ref = db.doc('users/migration_read_only');
  await ref.set({ subscriptionPlan: 'pro', subscriptionStatus: 'active',
    subscriptionSource: 'payfast', subscriptionExpiresAt: future, name: 'synthetic' });
  const original = (await ref.get()).data();
  const a = await recomputeProEntitlement('migration_read_only');
  assert.equal(a.active, true);
  assert.deepEqual((await ref.get()).data(), original);
  assert.equal((await ref.collection('proGrants').get()).size, 0);
});
test('trusted verified payment mirror updates access, not migration state', async () => {
  const ref = db.doc('users/trusted_payment');
  await ref.set({ subscriptionPlan: 'free', subscriptionStatus: 'free' });
  await ref.collection('proGrants').doc('paystack_test_verified').set({
    source: 'paystack', startsAt: new Date(now - 60000), expiresAt: future,
  });
  const value = await recomputeProEntitlement('trusted_payment', { syncTrustedGrant: true });
  assert.equal(value.active, true);
  const profile = (await ref.get()).data();
  assert.equal(profile.subscriptionStatus, 'active');
  assert.equal(profile.subscriptionSource, 'paystack');
  assert.notEqual(profile.proEntitlementsMigrated, true);
  assert.equal((await ref.collection('proGrants').get()).size, 1);
});
test('revoked promotional grant stays expired without legacy resurrection', async () => {
  const ref = db.doc('users/revoked_gift');
  await ref.set({ subscriptionPlan: 'promo', subscriptionStatus: 'active',
    subscriptionSource: 'admin_promotion', subscriptionExpiresAt: future });
  await ref.collection('proGrants').doc('admin_revoked').set({
    source: 'admin_promotion', expiresAt: future, revokedAt: new Date(now - 30000),
  });
  const a = await recomputeProEntitlement('revoked_gift');
  assert.equal(a.active, false);
  assert.equal((await ref.get()).data().subscriptionStatus, 'active');
  assert.equal((await ref.collection('proGrants').get()).size, 1);
});
