import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { defineString } from "firebase-functions/params";
import { periodEnd } from "./paystack-core";

// This is deliberately disabled unless explicitly configured for a reviewed rollout.
const proEntitlementMigrationEnabled = defineString("PRO_ENTITLEMENT_MIGRATION_ENABLED", { default: "false" });
export function migrationEnabled(): boolean {
  return proEntitlementMigrationEnabled.value() === "true";
}

type GrantSource = "paystack" | "payfast" | "admin_promotion";

export type ProGrant = {
  id: string;
  source: GrantSource;
  startsAt?: unknown;
  expiresAt?: unknown;
  revokedAt?: unknown;
};

function asDate(value: unknown): Date | null {
  if (value instanceof Date) return Number.isFinite(value.getTime()) ? value : null;
  if (typeof value === "string") {
    const date = new Date(value);
    return Number.isFinite(date.getTime()) ? date : null;
  }
  if (value && typeof value === "object" && "toDate" in value &&
      typeof value.toDate === "function") {
    const date = value.toDate();
    return date instanceof Date && Number.isFinite(date.getTime()) ? date : null;
  }
  return null;
}

export function evaluateProEntitlement(
  profile: Record<string, unknown>,
  grants: ProGrant[],
  now = new Date()
) {
  const candidates = new Map<string, ProGrant>();
  for (const grant of grants) candidates.set(grant.id, grant);

  if (profile.proEntitlementsMigrated !== true) {
    const status = String(profile.subscriptionStatus || "").toLowerCase();
    const plan = String(profile.subscriptionPlan || "").toLowerCase();
    const rawSource = String(profile.subscriptionSource || "");
    const source: GrantSource | null = rawSource === "paystack" || rawSource === "payfast"
      ? rawSource
      : rawSource === "admin_promotion" || plan === "promo"
        ? "admin_promotion"
        : plan === "pro" || plan === "monthly" || plan === "annual"
          ? "payfast"
          : null;
    const expiresAt = asDate(profile.subscriptionExpiresAt);
    const paidLegacyWithoutExpiry = source === "payfast" && !expiresAt &&
      ["active", "trialing"].includes(status);
    // The legacy promotion fields are mirrors once an explicit admin gift
    // exists. Never resurrect a revoked gift through its old profile mirror.
    const hasExplicitAdminGift = grants.some(grant => grant.source === "admin_promotion");
    if (source && !(source === "admin_promotion" && hasExplicitAdminGift) &&
        ["active", "trialing", "expired"].includes(status) &&
        (expiresAt || paidLegacyWithoutExpiry)) {
      candidates.set("legacy_profile", {
        id: "legacy_profile",
        source,
        startsAt: null,
        expiresAt,
      });
    }
  }

  const active = [...candidates.values()].filter(grant => {
    if (grant.revokedAt) return false;
    const start = asDate(grant.startsAt);
    const expiry = asDate(grant.expiresAt);
    return (!start || start.getTime() <= now.getTime()) &&
      (expiry ? expiry.getTime() > now.getTime() :
        grant.id === "legacy_profile" && grant.source === "payfast");
  });
  const paid = active.filter(grant => grant.source === "paystack" || grant.source === "payfast");
  const selected = paid.length ? paid : active;
  const selectedExpiry = active
    .map(grant => asDate(grant.expiresAt))
    .filter((date): date is Date => date !== null)
    .sort((left, right) => right.getTime() - left.getTime())[0] || null;
  const sourceExpiry = selected
    .map(grant => asDate(grant.expiresAt))
    .filter((date): date is Date => date !== null)
    .sort((left, right) => right.getTime() - left.getTime())[0] || null;
  const source = selected.some(grant => grant.source === "paystack")
    ? "paystack"
    : selected.some(grant => grant.source === "payfast")
      ? "payfast"
      : selected.length ? "admin_promotion" : null;

  return {
    active: selected.length > 0,
    expiresAt: selectedExpiry,
    sourceExpiresAt: sourceExpiry,
    source,
    sources: [...new Set(active.map(grant => grant.source))].sort(),
    plan: source === "admin_promotion" ? "promo" : source ? "pro" : "free",
    status: selected.length ? "active" : candidates.size ? "expired" : "free",
  };
}

function paymentExpiry(payment: Record<string, unknown>): Date | null {
  const interval = payment.interval === "annual" || payment.plan === "annual"
    ? "annual"
    : payment.interval === "monthly" || payment.plan === "monthly"
      ? "monthly"
      : null;
  if (!interval) return null;
  const paidAt = asDate(payment.paidAt) || asDate(payment.processedAt) || asDate(payment.createdAt);
  if (!paidAt) return null;
  try {
    return periodEnd(paidAt.toISOString(), interval);
  } catch {
    return null;
  }
}

async function migrateLegacyGrants(
  transaction: FirebaseFirestore.Transaction,
  userRef: FirebaseFirestore.DocumentReference,
  profile: Record<string, unknown>,
  existing: ProGrant[],
  payments: FirebaseFirestore.QueryDocumentSnapshot[]
): Promise<ProGrant[]> {
  const grants = [...existing];
  const known = new Set(existing.map(grant => grant.id));
  const add = (grant: ProGrant) => {
    if (known.has(grant.id)) return;
    known.add(grant.id);
    grants.push(grant);
    const { id, ...data } = grant;
    transaction.set(userRef.collection("proGrants").doc(id), {
      ...data,
      migrated: true,
      createdAt: FieldValue.serverTimestamp(),
    });
  };

  const legacy = evaluateProEntitlement(
    { ...profile, proEntitlementsMigrated: false },
    [],
    new Date(0)
  );
  if (legacy.source) {
    const source = legacy.source as GrantSource;
    add({
      id: "legacy_profile",
      source,
      startsAt: null,
      expiresAt: asDate(profile.subscriptionExpiresAt),
    });
  }

  const latestBySource = new Map<"paystack" | "payfast", Date>();
  for (const paymentSnapshot of payments) {
    const payment = paymentSnapshot.data();
    const provider = payment.provider;
    if (provider !== "paystack" && provider !== "payfast") continue;
    const isVerified = provider === "paystack"
      ? payment.status === "success" && payment.domain === "live"
      : String(payment.status || "").toLowerCase() === "complete";
    if (!isVerified) continue;
    const expiry = paymentExpiry(payment);
    if (expiry && (!latestBySource.has(provider) || expiry > latestBySource.get(provider)!)) {
      latestBySource.set(provider, expiry);
    }
  }
  for (const [source, expiresAt] of latestBySource) {
    add({ id: `legacy_paid_${source}`, source, startsAt: null, expiresAt });
  }
  return grants;
}

export async function recomputeProEntitlement(
  uid: string,
  options: { syncTrustedGrant?: boolean; allowMigration?: boolean } = {}
) {
  const db = getFirestore();
  const userRef = db.collection("users").doc(uid);

  // Only explicitly authorized background jobs may run historical migration.
  // Even when the gate is enabled, a customer profile read is always read-only.
  if (!(migrationEnabled() && options.allowMigration === true)) {
    if (options.syncTrustedGrant === true) {
      // A verified payment or administrator gift is the only caller allowed
      // to mirror current access while historical migration is disabled.
      // Read the profile and grants in one transaction so we do not persist
      // stale values from a concurrent grant operation.
      let result: ReturnType<typeof evaluateProEntitlement> | null = null;
      await db.runTransaction(async transaction => {
        const profileSnapshot = await transaction.get(userRef);
        if (!profileSnapshot.exists) return;
        const grantSnapshot = await transaction.get(userRef.collection("proGrants"));
        const profile = profileSnapshot.data() || {};
        const grants = grantSnapshot.docs.map(doc => ({ id: doc.id, ...doc.data() } as ProGrant));
        result = evaluateProEntitlement(profile, grants);
        transaction.set(userRef, {
          proEntitlementActive: result.active,
          proEntitlementExpiresAt: result.expiresAt,
          proEntitlementSource: result.source,
          proEntitlementSources: result.sources,
          proEntitlementUpdatedAt: FieldValue.serverTimestamp(),
          subscriptionPlan: result.plan,
          subscriptionStatus: result.status,
          ...(result.source ? { subscriptionSource: result.source } : {
            subscriptionSource: FieldValue.delete(),
          }),
          subscriptionExpiresAt: result.sourceExpiresAt || FieldValue.delete(),
          updatedAt: FieldValue.serverTimestamp(),
        }, { merge: true });
      });
      return result;
    }

    // Customer reads must not create grant records, update compatibility fields,
    // mark a profile as migrated, or import payment history.
    const [profileSnapshot, grantSnapshot] = await Promise.all([
      userRef.get(),
      userRef.collection("proGrants").get(),
    ]);
    if (!profileSnapshot.exists) return null;
    const profile = profileSnapshot.data() || {};
    const grants = grantSnapshot.docs.map(doc => ({ id: doc.id, ...doc.data() } as ProGrant));
    return evaluateProEntitlement(profile, grants);
  }
  const state: { result?: ReturnType<typeof evaluateProEntitlement> } = {};

  await db.runTransaction(async transaction => {
    const profileSnapshot = await transaction.get(userRef);
    if (!profileSnapshot.exists) return;
    const profile = profileSnapshot.data() || {};
    const grantQuery = userRef.collection("proGrants");
    const grantSnapshot = await transaction.get(grantQuery);
    let grants: ProGrant[] = grantSnapshot.docs.map(document => ({
      id: document.id,
      ...document.data(),
    } as ProGrant));
    if (profile.proEntitlementsMigrated !== true) {
      const payments = await transaction.get(
        db.collection("subscriptionPayments").where("uid", "==", uid)
      );
      grants = await migrateLegacyGrants(
        transaction,
        userRef,
        profile,
        grants,
        payments.docs
      );
    }
    const result = evaluateProEntitlement(profile, grants);
    state.result = result;
    const existingExpiry = asDate(profile.proEntitlementExpiresAt);
    const existingSourceExpiry = asDate(profile.subscriptionExpiresAt);
    const existingSources = Array.isArray(profile.proEntitlementSources)
      ? [...profile.proEntitlementSources].sort()
      : [];
    const unchanged = profile.proEntitlementsMigrated === true &&
      profile.proEntitlementActive === result.active &&
      existingExpiry?.getTime() === result.expiresAt?.getTime() &&
      profile.proEntitlementSource === result.source &&
      JSON.stringify(existingSources) === JSON.stringify(result.sources) &&
      profile.subscriptionPlan === result.plan &&
      profile.subscriptionStatus === result.status &&
      (profile.subscriptionSource || null) === result.source &&
      existingSourceExpiry?.getTime() === result.sourceExpiresAt?.getTime();
    if (unchanged) return;
    transaction.set(userRef, {
      proEntitlementsMigrated: true,
      proEntitlementActive: result.active,
      proEntitlementExpiresAt: result.expiresAt,
      proEntitlementSource: result.source,
      proEntitlementSources: result.sources,
      proEntitlementUpdatedAt: FieldValue.serverTimestamp(),
      subscriptionPlan: result.plan,
      subscriptionStatus: result.status,
      ...(result.source ? { subscriptionSource: result.source } : {
        subscriptionSource: FieldValue.delete(),
      }),
      subscriptionExpiresAt: result.sourceExpiresAt || FieldValue.delete(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
  });
  return state.result || null;
}

export const refreshProEntitlements = onSchedule({
  region: "europe-west1",
  schedule: "every 60 minutes",
  timeoutSeconds: 540,
}, async () => {
  if (!migrationEnabled()) return;
  const db = getFirestore();
  const processed = new Set<string>();
  for (const baseQuery of [
    db.collection("users").where("subscriptionStatus", "in", ["active", "trialing"]),
    db.collection("users").where("proEntitlementActive", "==", true),
  ]) {
    let cursor: FirebaseFirestore.QueryDocumentSnapshot | undefined;
    while (true) {
      let query = baseQuery.limit(200);
      if (cursor) query = query.startAfter(cursor);
      const page = await query.get();
      if (!page.docs.length) break;
      const pending = page.docs.filter(user => !processed.has(user.id));
      for (const user of pending) processed.add(user.id);
      for (let offset = 0; offset < pending.length; offset += 10) {
        await Promise.all(pending.slice(offset, offset + 10).map(user => recomputeProEntitlement(user.id, { allowMigration: true })));
      }
      if (page.docs.length < 200) break;
      cursor = page.docs[page.docs.length - 1];
    }
  }
});