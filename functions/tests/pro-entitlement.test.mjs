// Pure entitlement evaluator regressions. Run after compiling functions:
// npm run test:entitlements --prefix functions
import assert from 'node:assert/strict';
import test from 'node:test';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);
const { evaluateProEntitlement } = require(join(here, '../lib/proEntitlements.js'));
const NOW = new Date('2026-10-21T12:00:00.000Z');
const before = '2026-10-20T12:00:00.000Z';
const after = '2026-10-30T12:00:00.000Z';

test('free profile without grants stays free', () => {
  const result = evaluateProEntitlement({ subscriptionPlan: 'free', subscriptionStatus: 'free' }, [], NOW);
  assert.equal(result.active, false);
  assert.equal(result.plan, 'free');
});

test('an expired admin gift is not active', () => {
  const result = evaluateProEntitlement({ proEntitlementsMigrated: true }, [
    { id: 'admin_gift', source: 'admin_promotion', expiresAt: before },
  ], NOW);
  assert.equal(result.active, false);
  assert.equal(result.status, 'expired');
});

test('a current admin gift gives temporary Pro access', () => {
  const result = evaluateProEntitlement({ proEntitlementsMigrated: true }, [
    { id: 'admin_gift', source: 'admin_promotion', expiresAt: after },
  ], NOW);
  assert.equal(result.active, true);
  assert.equal(result.source, 'admin_promotion');
  assert.equal(result.plan, 'promo');
});

test('expired gift cannot override active paid Paystack grant', () => {
  const result = evaluateProEntitlement({ proEntitlementsMigrated: true }, [
    { id: 'admin_old', source: 'admin_promotion', expiresAt: before },
    { id: 'paystack_current', source: 'paystack', expiresAt: after },
  ], NOW);
  assert.equal(result.active, true);
  assert.equal(result.source, 'paystack');
  assert.equal(result.plan, 'pro');
});

test('active gift does not replace active paid grant as source', () => {
  const result = evaluateProEntitlement({ proEntitlementsMigrated: true }, [
    { id: 'admin_current', source: 'admin_promotion', expiresAt: after },
    { id: 'payfast_current', source: 'payfast', expiresAt: after },
  ], NOW);
  assert.equal(result.source, 'payfast');
  assert.deepEqual(result.sources, ['admin_promotion', 'payfast']);
});

test('a revoked gift is inactive', () => {
  const result = evaluateProEntitlement({ proEntitlementsMigrated: true }, [
    { id: 'admin_revoked', source: 'admin_promotion', expiresAt: after, revokedAt: before },
  ], NOW);
  assert.equal(result.active, false);
});

test('legacy PayFast paid access with missing expiry is preserved', () => {
  const result = evaluateProEntitlement({
    subscriptionStatus: 'active', subscriptionPlan: 'pro', subscriptionSource: 'payfast',
  }, [], NOW);
  assert.equal(result.active, true);
  assert.equal(result.source, 'payfast');
  assert.equal(result.expiresAt, null);
});

test('expired legacy PayFast is not revived', () => {
  const result = evaluateProEntitlement({
    subscriptionStatus: 'expired', subscriptionPlan: 'pro',
    subscriptionSource: 'payfast', subscriptionExpiresAt: before,
  }, [], NOW);
  assert.equal(result.active, false);
});

test('future-start grant does not activate early', () => {
  const result = evaluateProEntitlement({ proEntitlementsMigrated: true }, [
    { id: 'future', source: 'paystack', startsAt: after, expiresAt: '2026-11-30T00:00:00.000Z' },
  ], NOW);
  assert.equal(result.active, false);
});

test('multiple paid grants retain valid paid access if one expires', () => {
  const result = evaluateProEntitlement({ proEntitlementsMigrated: true }, [
    { id: 'old_paystack', source: 'paystack', expiresAt: before },
    { id: 'new_payfast', source: 'payfast', expiresAt: after },
  ], NOW);
  assert.equal(result.active, true);
  assert.equal(result.source, 'payfast');
});

test('revoked gift cannot reappear from its legacy subscription mirror', () => {
  const result = evaluateProEntitlement({
    proEntitlementsMigrated: false,
    subscriptionPlan: 'promo',
    subscriptionSource: 'admin_promotion',
    subscriptionStatus: 'active',
    subscriptionExpiresAt: after,
  }, [{ id: 'admin_revoked', source: 'admin_promotion', expiresAt: after, revokedAt: before }], NOW);
  assert.equal(result.active, false);
});

test('revoked gift leaves an independent paid grant active', () => {
  const result = evaluateProEntitlement({
    proEntitlementsMigrated: false,
    subscriptionPlan: 'promo',
    subscriptionSource: 'admin_promotion',
    subscriptionStatus: 'active',
    subscriptionExpiresAt: after,
  }, [
    { id: 'admin_revoked', source: 'admin_promotion', expiresAt: after, revokedAt: before },
    { id: 'paystack_paid', source: 'paystack', expiresAt: after },
  ], NOW);
  assert.equal(result.active, true);
  assert.equal(result.source, 'paystack');
});
