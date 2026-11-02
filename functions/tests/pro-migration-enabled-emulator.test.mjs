import assert from 'node:assert/strict';
import test, { before, after } from 'node:test';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.env.FIRESTORE_EMULATOR_HOST !== '127.0.0.1:8080' ||
    process.env.GCLOUD_PROJECT !== 'demo-batchboss' ||
    process.env.PRO_ENTITLEMENT_MIGRATION_ENABLED !== 'true') {
  throw new Error('Migration dry run requires the isolated demo Firestore emulator');
}
const require = createRequire(import.meta.url);
const { initializeApp, deleteApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { recomputeProEntitlement } = require(join(dirname(fileURLToPath(import.meta.url)), '../lib/proEntitlements.js'));
const app = initializeApp({ projectId: 'demo-batchboss' });
const db = getFirestore(app);
const ids = ['dry_legacy_paid', 'dry_revoked_gift'];
const future = new Date(Date.now() + 10 * 86400000);
const past = new Date(Date.now() - 86400000);
before(async () => { for (const id of ids) await db.recursiveDelete(db.doc(`users/${id}`)).catch(() => {}); });
after(async () => {
  for (const id of ids) await db.recursiveDelete(db.doc(`users/${id}`)).catch(() => {});
  await deleteApp(app);
});
test('migration enabled still keeps customer reads read-only', async () => {
  const ref = db.doc('users/dry_legacy_paid');
  await ref.set({ subscriptionPlan: 'pro', subscriptionStatus: 'active',
    subscriptionSource: 'payfast', subscriptionExpiresAt: future });
  const beforeData = (await ref.get()).data();
  const access = await recomputeProEntitlement('dry_legacy_paid');
  assert.equal(access.active, true);
  assert.deepEqual((await ref.get()).data(), beforeData);
  assert.equal((await ref.collection('proGrants').get()).size, 0);
});
test('explicit migration imports original PayFast expiry and is idempotent', async () => {
  const ref = db.doc('users/dry_legacy_paid');
  await recomputeProEntitlement('dry_legacy_paid', { allowMigration: true });
  const snapshot = await ref.get();
  assert.equal(snapshot.data().proEntitlementsMigrated, true);
  assert.equal(snapshot.data().subscriptionStatus, 'active');
  const grant = (await ref.collection('proGrants').doc('legacy_profile').get()).data();
  assert.equal(grant.source, 'payfast');
  assert.equal(grant.expiresAt.toDate().getTime(), future.getTime());
  await recomputeProEntitlement('dry_legacy_paid', { allowMigration: true });
  assert.equal((await ref.collection('proGrants').get()).size, 1);
});
test('explicit migration does not revive previously revoked gift', async () => {
  const ref = db.doc('users/dry_revoked_gift');
  await ref.set({ subscriptionPlan: 'promo', subscriptionStatus: 'active',
    subscriptionSource: 'admin_promotion', subscriptionExpiresAt: future });
  await ref.collection('proGrants').doc('admin_revoked').set({
    source: 'admin_promotion', expiresAt: future, revokedAt: past,
  });
  const result = await recomputeProEntitlement('dry_revoked_gift', { allowMigration: true });
  assert.equal(result.active, false);
  assert.equal((await ref.get()).data().proEntitlementsMigrated, true);
  assert.equal((await ref.collection('proGrants').get()).size, 1);
});
