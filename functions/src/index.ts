import { createHash, randomUUID, timingSafeEqual } from "node:crypto";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { defineSecret, defineString } from "firebase-functions/params";
import { HttpsError, onCall, onRequest } from "firebase-functions/v2/https";
import { evaluateProEntitlement, recomputeProEntitlement, refreshProEntitlements, type ProGrant } from "./proEntitlements";

initializeApp();

const db = getFirestore();
const auth = getAuth();
const storage = getStorage();
const adminEmail = defineString("ADMIN_EMAIL");
const payfastMerchantId = defineSecret("PAYFAST_MERCHANT_ID");
const payfastMerchantKey = defineSecret("PAYFAST_MERCHANT_KEY");
const payfastPassphrase = defineSecret("PAYFAST_PASSPHRASE");
const payfastSecrets = [payfastMerchantId, payfastMerchantKey, payfastPassphrase];

function requireSignedIn(request: { auth?: { uid: string; token: Record<string, unknown> } }) {
  if (!request.auth) throw new HttpsError("unauthenticated", "Please sign in.");
  return request.auth;
}

function requireAdmin(request: { auth?: { uid: string; token: Record<string, unknown> } }) {
  const current = requireSignedIn(request);
  if (current.token.admin !== true) {
    throw new HttpsError("permission-denied", "BatchBoss administrator access is required.");
  }
  return current;
}

export const bootstrapAdmin = onCall({ region: "europe-west1", invoker: "public" }, async request => {
  const current = requireSignedIn(request);
  const email = String(current.token.email || "").trim().toLowerCase();
  const expected = adminEmail.value().trim().toLowerCase();

  if (!expected || email !== expected) {
    throw new HttpsError("permission-denied", "This account is not the configured BatchBoss administrator.");
  }

  await auth.setCustomUserClaims(current.uid, { admin: true });
  await db.collection("adminAuditLogs").add({
    action: "ADMIN_ACCESS_GRANTED",
    actorUid: current.uid,
    actorEmail: email,
    createdAt: FieldValue.serverTimestamp(),
  });

  return { success: true, refreshToken: true };
});

export const listAccounts = onCall({ region: "europe-west1", invoker: "public" }, async request => {
  requireAdmin(request);
  try {
    const result = await auth.listUsers(1000);
    const profiles = await Promise.all(result.users.map(async user => {
      // Authentication is the master account list. Firestore profile details are
      // optional so a partially-created mobile account can still be administered.
      let data: Record<string, any> = {};
      let bakeryData: Record<string, any> = {};
      try {
        const profile = await db.collection("users").doc(user.uid).get();
        data = profile.data() || {};
        const bakeryId = String(data.bakeryId || "");
        if (bakeryId) {
          const bakery = await db.collection("bakeries").doc(bakeryId).get();
          bakeryData = bakery.data() || {};
        }
      } catch (profileError) {
        console.warn(`Could not load Firestore profile for ${user.uid}`, profileError);
      }

      const expiry = data.subscriptionExpiresAt;
      let subscriptionExpiresAt: string | null = null;
      if (expiry instanceof Date) subscriptionExpiresAt = expiry.toISOString();
      else if (typeof expiry?.toDate === "function") subscriptionExpiresAt = expiry.toDate().toISOString();
      else if (typeof expiry === "string") subscriptionExpiresAt = expiry;

      return {
        uid: user.uid,
        email: user.email || String(data.email || ""),
        displayName: user.displayName || "",
        disabled: user.disabled,
        emailVerified: user.emailVerified,
        createdAt: user.metadata.creationTime || null,
        lastSignInAt: user.metadata.lastSignInTime || null,
        bakeryId: String(data.bakeryId || ""),
        bakeryName: String(data.bakeryName || bakeryData.name || bakeryData.businessName || ""),
        firstName: String(data.firstName || ""),
        surname: String(data.surname || ""),
        subscriptionPlan: String(data.subscriptionPlan || "free"),
        subscriptionStatus: String(data.subscriptionStatus || "free"),
        subscriptionExpiresAt,
      };
    }));

    return { accounts: profiles, nextPageToken: result.pageToken || null };
  } catch (error) {
    console.error("listAccounts failed", error);
    throw new HttpsError("internal", "The Firebase Authentication account list could not be loaded. Check the listAccounts function log.");
  }
});

export const setPromotionalPro = onCall({ region: "europe-west1", invoker: "public" }, async request => {
  const administrator = requireAdmin(request);
  const uid = String(request.data?.uid || "");
  const enabled = request.data?.enabled === true;
  const durationDays = Number(request.data?.durationDays || 30);
  const requestId = String(request.data?.requestId || "");

  if (!uid) throw new HttpsError("invalid-argument", "A user ID is required.");
  if (enabled && (!Number.isInteger(durationDays) || durationDays < 1 || durationDays > 3650)) {
    throw new HttpsError("invalid-argument", "Promotional access must be between 1 and 3650 days.");
  }
  if (enabled && !/^[a-zA-Z0-9_-]{1,100}$/.test(requestId)) {
    throw new HttpsError("invalid-argument", "A stable grant request ID is required.");
  }

  const userRecord = await auth.getUser(uid);
  const userRef = db.collection("users").doc(uid);
  const userSnapshot = await userRef.get();
  if (!userSnapshot.exists) {
    throw new HttpsError("failed-precondition", "This login does not have a BatchBoss user profile yet. Sign in to the customer portal once, then try again.");
  }

  await recomputeProEntitlement(uid);
  const grantRef = userRef.collection("proGrants").doc(`admin_${requestId}`);
  const grantState: { expiresAt: Date | null } = { expiresAt: null };
  await db.runTransaction(async transaction => {
    const [profile, grants] = await Promise.all([
      transaction.get(userRef),
      transaction.get(userRef.collection("proGrants")),
    ]);
    const account = profile.data() || {};
    const existingGrants = grants.docs.map(document => ({
      id: document.id,
      ...document.data(),
    } as ProGrant));
    if (enabled) {
      const existingGrant = await transaction.get(grantRef);
      const oldExpiry = existingGrant.data()?.expiresAt;
      grantState.expiresAt = oldExpiry instanceof Date ? oldExpiry :
        typeof oldExpiry?.toDate === "function" ? oldExpiry.toDate() :
          new Date(Date.now() + durationDays * 24 * 60 * 60 * 1000);
      if (!existingGrant.exists) {
        const entitlement = evaluateProEntitlement(account, existingGrants);
        if (entitlement.active && (entitlement.source === "paystack" || entitlement.source === "payfast")) {
          throw new HttpsError("failed-precondition", "This account already has active paid Pro access. Do not replace its billing access with a gift.");
        }
        grantState.expiresAt = new Date(Date.now() + durationDays * 24 * 60 * 60 * 1000);
        transaction.create(grantRef, {
          source: "admin_promotion",
          durationDays,
          startsAt: new Date(),
          expiresAt: grantState.expiresAt,
          grantedBy: administrator.uid,
          requestId,
          createdAt: FieldValue.serverTimestamp(),
        });
        if (durationDays === 7) {
          transaction.create(db.collection("proGiftEmailEvents").doc(`admin_${requestId}`), {
            type: "gift_pro",
            uid,
            durationDays,
            grantId: `admin_${requestId}`,
            expiresAt: grantState.expiresAt,
            createdAt: FieldValue.serverTimestamp(),
          });
        }
      }
    } else {
      for (const grant of grants.docs) {
        if (grant.data().source === "admin_promotion" && !grant.data().revokedAt) {
          transaction.update(grant.ref, { revokedAt: FieldValue.serverTimestamp() });
        }
      }
    }
  });
  await recomputeProEntitlement(uid, { syncTrustedGrant: true });

  await db.collection("adminAuditLogs").add({
    action: enabled ? "PROMOTIONAL_PRO_GRANTED" : "PROMOTIONAL_PRO_REMOVED",
    targetUid: uid,
    targetEmail: userRecord.email || "",
    durationDays: enabled ? durationDays : null,
    expiresAt: grantState.expiresAt,
    actorUid: administrator.uid,
    actorEmail: String(administrator.token.email || ""),
    createdAt: FieldValue.serverTimestamp(),
    status: "completed",
  });

  if (enabled && !grantState.expiresAt) throw new HttpsError("internal", "The existing Pro gift could not be verified.");
  return { success: true, expiresAt: grantState.expiresAt?.toISOString() || null };
});

export const setAccountDisabled = onCall({ region: "europe-west1", invoker: "public" }, async request => {
  const administrator = requireAdmin(request);
  const uid = String(request.data?.uid || "");
  const disabled = request.data?.disabled === true;
  if (!uid) throw new HttpsError("invalid-argument", "A user ID is required.");
  if (uid === administrator.uid) throw new HttpsError("failed-precondition", "You cannot disable your own administrator account.");

  await auth.updateUser(uid, { disabled });
  await db.collection("adminAuditLogs").add({
    action: disabled ? "ACCOUNT_DISABLED" : "ACCOUNT_ENABLED",
    targetUid: uid,
    actorUid: administrator.uid,
    actorEmail: String(administrator.token.email || ""),
    createdAt: FieldValue.serverTimestamp(),
  });

  return { success: true };
});

export const deleteAccountAndData = onCall(
  { region: "europe-west1", invoker: "public", timeoutSeconds: 540, memory: "1GiB" },
  async request => {
    const administrator = requireAdmin(request);
    const uid = String(request.data?.uid || "");
    const confirmation = String(request.data?.confirmation || "");

    if (!uid) throw new HttpsError("invalid-argument", "A user ID is required.");
    if (confirmation !== "DELETE") throw new HttpsError("failed-precondition", "Type DELETE to confirm permanent deletion.");
    if (uid === administrator.uid) throw new HttpsError("failed-precondition", "You cannot delete your own administrator account.");

    const userRef = db.collection("users").doc(uid);
    const userSnapshot = await userRef.get();
    const profile = userSnapshot.data() || {};
    const bakeryId = String(profile.bakeryId || "");
    const auditRef = db.collection("adminAuditLogs").doc();

    await auditRef.set({
      action: "ACCOUNT_DELETION_STARTED",
      targetUid: uid,
      targetEmail: String(profile.email || ""),
      bakeryId,
      actorUid: administrator.uid,
      actorEmail: String(administrator.token.email || ""),
      createdAt: FieldValue.serverTimestamp(),
      status: "processing",
    });

    try {
      if (bakeryId) {
        await db.recursiveDelete(db.collection("bakeries").doc(bakeryId));
        await storage.bucket().deleteFiles({ prefix: `bakeries/${bakeryId}/` });
      }
      await userRef.delete();
      await auth.deleteUser(uid);

      await auditRef.update({
        status: "completed",
        completedAt: FieldValue.serverTimestamp(),
      });
      return { success: true };
    } catch (error) {
      await auditRef.update({
        status: "failed",
        error: error instanceof Error ? error.message : String(error),
        completedAt: FieldValue.serverTimestamp(),
      });
      throw new HttpsError("internal", "The account could not be completely deleted. Check the administrator audit log.");
    }
  }
);

export const submitDeletionRequest = onCall({ region: "europe-west1", invoker: "public" }, async request => {
  const current = requireSignedIn(request);
  const profile = await db.collection("users").doc(current.uid).get();
  const data = profile.data() || {};

  const requestRef = await db.collection("deletionRequests").add({
    uid: current.uid,
    email: String(current.token.email || data.email || ""),
    bakeryId: String(data.bakeryId || ""),
    reason: String(request.data?.reason || "Account and data deletion"),
    status: "pending",
    requestedAt: FieldValue.serverTimestamp(),
  });

  return { success: true, requestId: requestRef.id };
});

type PayfastFields = Record<string, string>;

const APP_URL = "https://app.batchboss.co.za";
const PAYFAST_URL = "https://www.payfast.co.za/eng/process";
const PAYFAST_VALIDATE_URL = "https://www.payfast.co.za/eng/query/validate";
const PAYFAST_NOTIFY_URL =
  "https://europe-west1-batch-boss-android.cloudfunctions.net/payfastNotify";

function payfastEncode(value: string): string {
  return encodeURIComponent(value.trim())
    .replace(/%20/g, "+")
    .replace(/[!'()~]/g, character =>
      `%${character.charCodeAt(0).toString(16).toUpperCase()}`
    );
}

function payfastParameterString(fields: PayfastFields): string {
  return Object.entries(fields)
    .filter(([key, value]) => key !== "signature" && value !== "")
    .map(([key, value]) => `${key}=${payfastEncode(value)}`)
    .join("&");
}

function createPayfastSignature(
  fields: PayfastFields,
  passphrase: string
): string {
  let parameterString = payfastParameterString(fields);
  if (passphrase) {
    parameterString += `&passphrase=${payfastEncode(passphrase)}`;
  }
  return createHash("md5").update(parameterString).digest("hex");
}

function signaturesMatch(received: string, expected: string): boolean {
  if (!received || received.length !== expected.length) return false;
  return timingSafeEqual(
    Buffer.from(received.toLowerCase()),
    Buffer.from(expected.toLowerCase())
  );
}

function addBillingPeriod(date: Date, plan: "monthly" | "annual"): Date {
  const result = new Date(date);
  if (plan === "annual") result.setUTCFullYear(result.getUTCFullYear() + 1);
  else result.setUTCMonth(result.getUTCMonth() + 1);
  return result;
}

export const createPayfastCheckout = onCall(
  {
    region: "europe-west1",
    invoker: "public",
    secrets: payfastSecrets,
    cors: [APP_URL],
  },
  async request => {
    const current = requireSignedIn(request);
    const plan = String(request.data?.plan || "").toLowerCase();

    if (plan !== "monthly" && plan !== "annual") {
      throw new HttpsError(
        "invalid-argument",
        "Choose the monthly or annual BatchBoss Pro plan."
      );
    }

    const userRecord = await auth.getUser(current.uid);
    const profileSnapshot = await db.collection("users").doc(current.uid).get();
    const profile = profileSnapshot.data() || {};
    const email = String(userRecord.email || profile.email || "").trim();

    if (!email) {
      throw new HttpsError(
        "failed-precondition",
        "Your BatchBoss account needs an email address before subscribing."
      );
    }

    const amount = plan === "annual" ? "1199.00" : "149.00";
    const frequency = plan === "annual" ? "6" : "3";
    const paymentId = randomUUID();
    const firstName =
      String(profile.firstName || userRecord.displayName || "BatchBoss")
        .trim()
        .split(/\s+/)[0]
        .slice(0, 100);

    await db.collection("subscriptionPayments").doc(paymentId).set({
      uid: current.uid,
      email,
      plan,
      amount: Number(amount),
      currency: "ZAR",
      provider: "payfast",
      status: "pending",
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });

    const fields: PayfastFields = {
      merchant_id: payfastMerchantId.value(),
      merchant_key: payfastMerchantKey.value(),
      return_url: `${APP_URL}/?payment=success`,
      cancel_url: `${APP_URL}/?payment=cancelled`,
      notify_url: PAYFAST_NOTIFY_URL,
      name_first: firstName,
      email_address: email,
      m_payment_id: paymentId,
      amount,
      item_name:
        plan === "annual"
          ? "BatchBoss Pro Annual"
          : "BatchBoss Pro Monthly",
      subscription_type: "1",
      billing_date: new Date().toISOString().slice(0, 10),
      recurring_amount: amount,
      frequency,
      cycles: "0",
    };

    fields.signature = createPayfastSignature(
      fields,
      payfastPassphrase.value()
    );

    return {
      success: true,
      checkoutUrl: PAYFAST_URL,
      fields,
    };
  }
);

export const payfastNotify = onRequest(
  {
    region: "europe-west1",
    invoker: "public",
    secrets: payfastSecrets,
  },
  async (request, response) => {
    try {
      if (request.method !== "POST") {
        response.status(405).send("Method not allowed");
        return;
      }

      const rawBody = request.rawBody.toString("utf8");
      const searchParams = new URLSearchParams(rawBody);
      const fields: PayfastFields = {};
      searchParams.forEach((value, key) => {
        fields[key] = value;
      });

      const receivedSignature = String(fields.signature || "");
      const expectedSignature = createPayfastSignature(
        fields,
        payfastPassphrase.value()
      );

      if (!signaturesMatch(receivedSignature, expectedSignature)) {
        console.error("Rejected PayFast ITN with an invalid signature.");
        response.status(400).send("Invalid signature");
        return;
      }

      const validationResponse = await fetch(PAYFAST_VALIDATE_URL, {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
        },
        body: rawBody,
      });
      const validationResult = (await validationResponse.text()).trim();

      if (validationResult !== "VALID") {
        console.error("PayFast server validation failed:", validationResult);
        response.status(400).send("Invalid payment notification");
        return;
      }

      const paymentId = String(fields.m_payment_id || "");
      const paymentRef = db.collection("subscriptionPayments").doc(paymentId);
      const paymentSnapshot = await paymentRef.get();

      if (!paymentSnapshot.exists) {
        response.status(404).send("Unknown payment");
        return;
      }

      const payment = paymentSnapshot.data() || {};
      // Only a server-created PayFast ZAR checkout may be settled here.
      // This protects against callbacks reusing unrelated payment document IDs.
      if (payment.provider !== "payfast" || payment.currency !== "ZAR" ||
          !["monthly", "annual"].includes(String(payment.plan || ""))) {
        response.status(400).send("Invalid payment record");
        return;
      }
      const expectedAmount = Number(payment.amount);
      const receivedAmount = Number(fields.amount_gross);

      if (
        !Number.isFinite(expectedAmount) || expectedAmount <= 0 ||
        !Number.isFinite(receivedAmount) || receivedAmount <= 0 ||
        Math.abs(receivedAmount - expectedAmount) > 0.01
      ) {
        response.status(400).send("Payment amount does not match");
        return;
      }

      const paymentStatus = String(fields.payment_status || "").toUpperCase();
      const uid = String(payment.uid || "");
      const plan = payment.plan === "annual" ? "annual" : "monthly";

      if (paymentStatus === "COMPLETE" && uid) {
        const userRef = db.collection("users").doc(uid);
        const grantRef = userRef.collection("proGrants").doc(`payfast_${paymentId}`);
        let changed = false;
        await db.runTransaction(async transaction => {
          const [currentPayment, userSnapshot, grants, existingGrant] = await Promise.all([
            transaction.get(paymentRef),
            transaction.get(userRef),
            transaction.get(userRef.collection("proGrants")),
            transaction.get(grantRef),
          ]);
          if (currentPayment.data()?.provider !== "payfast" ||
              currentPayment.data()?.currency !== "ZAR" ||
              currentPayment.data()?.uid !== uid ||
              currentPayment.data()?.plan !== plan ||
              Number(currentPayment.data()?.amount) !== expectedAmount) {
            throw new Error("PayFast checkout changed during settlement.");
          }
          if (String(currentPayment.data()?.status || "").toLowerCase() === "complete") return;
          if (!userSnapshot.exists) throw new Error("Missing user profile for verified PayFast payment.");
          const user = userSnapshot.data() || {};
          const paidExpiries = grants.docs
            .filter(grant => ["paystack", "payfast"].includes(String(grant.data().source)) && !grant.data().revokedAt)
            .map(grant => {
              const expiry = grant.data().expiresAt;
              return expiry instanceof Date ? expiry :
                typeof expiry?.toDate === "function" ? expiry.toDate() : null;
            })
            .filter((expiry): expiry is Date => expiry instanceof Date && expiry.getTime() > Date.now());
          if (["paystack", "payfast"].includes(String(user.subscriptionSource))) {
            const legacy = user.subscriptionExpiresAt;
            const expiry = legacy instanceof Date ? legacy :
              typeof legacy?.toDate === "function" ? legacy.toDate() : null;
            if (expiry instanceof Date && expiry.getTime() > Date.now()) paidExpiries.push(expiry);
          }
          const periodStart = paidExpiries.sort((left, right) => right.getTime() - left.getTime())[0] || new Date();
          const expiresAt = addBillingPeriod(periodStart, plan);
          if (!existingGrant.exists) {
            transaction.create(grantRef, {
              source: "payfast",
              reference: paymentId,
              startsAt: periodStart,
              expiresAt,
              createdAt: FieldValue.serverTimestamp(),
            });
          }
          transaction.set(paymentRef, {
            status: "complete",
            payfastPaymentStatus: paymentStatus,
            payfastPaymentId: String(fields.pf_payment_id || ""),
            payfastToken: String(fields.token || ""),
            processedAt: FieldValue.serverTimestamp(),
            updatedAt: FieldValue.serverTimestamp(),
          }, { merge: true });
          transaction.set(userRef, {
            payfastPaymentId: paymentId,
            payfastToken: String(fields.token || ""),
            subscriptionBillingInterval: plan,
            updatedAt: FieldValue.serverTimestamp(),
          }, { merge: true });
          changed = true;
        });
        // Safe on repeated, verified PayFast callbacks: repair access mirrors if
        // the original settlement committed but the previous recompute failed.
        await recomputeProEntitlement(uid, { syncTrustedGrant: true });
      } else {
        await db.runTransaction(async transaction => {
          const current = await transaction.get(paymentRef);
          if (String(current.data()?.status || "").toLowerCase() === "complete") return;
          transaction.set(paymentRef, {
            status: paymentStatus.toLowerCase(),
            payfastPaymentStatus: paymentStatus,
            payfastPaymentId: String(fields.pf_payment_id || ""),
            payfastToken: String(fields.token || ""),
            processedAt: FieldValue.serverTimestamp(),
            updatedAt: FieldValue.serverTimestamp(),
          }, { merge: true });
        });
      }

      response.status(200).send("OK");
    } catch (error) {
      console.error("payfastNotify failed", error);
      response.status(500).send("Payment processing failed");
    }
  }
);

export {getPaystackPlans,createPaystackCheckout,verifyPaystackPayment,managePaystackSubscription,paystackWebhook} from './paystack';
export { refreshProEntitlements as expirePaystackAccess };

export const getProEntitlement = onCall({ region: "europe-west1", invoker: "public" }, async request => {
  const current = requireSignedIn(request);
  const entitlement = await recomputeProEntitlement(current.uid);
  if (!entitlement) throw new HttpsError("not-found", "The BatchBoss user profile was not found.");
  return {
    active: entitlement.active,
    expiresAt: entitlement.expiresAt?.toISOString() || null,
    source: entitlement.source,
    sources: entitlement.sources,
  };
});

export { sendBatchBossWelcomeEmail } from './welcomeEmail';
export { sendBatchBossGiftProEmail } from './giftProEmail';

export { sendBatchBossProPaymentEmail } from './proPaymentEmail';

export { sendBatchBossFailedPaymentEmail, sendBatchBossCancellationEmail, sendBatchBossRenewalReminders } from './subscriptionEmails';