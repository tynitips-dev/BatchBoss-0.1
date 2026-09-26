import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { defineString } from "firebase-functions/params";
import { HttpsError, onCall } from "firebase-functions/v2/https";

initializeApp();

const db = getFirestore();
const auth = getAuth();
const storage = getStorage();
const adminEmail = defineString("ADMIN_EMAIL");

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

export const bootstrapAdmin = onCall({ region: "europe-west1" }, async request => {
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

export const listAccounts = onCall({ region: "europe-west1" }, async request => {
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

export const setPromotionalPro = onCall({ region: "europe-west1" }, async request => {
  const administrator = requireAdmin(request);
  const uid = String(request.data?.uid || "");
  const enabled = request.data?.enabled === true;
  const durationDays = Number(request.data?.durationDays || 30);

  if (!uid) throw new HttpsError("invalid-argument", "A user ID is required.");
  if (enabled && (!Number.isInteger(durationDays) || durationDays < 1 || durationDays > 3650)) {
    throw new HttpsError("invalid-argument", "Promotional access must be between 1 and 3650 days.");
  }

  const userRecord = await auth.getUser(uid);
  const userRef = db.collection("users").doc(uid);
  const userSnapshot = await userRef.get();
  if (!userSnapshot.exists) {
    throw new HttpsError("failed-precondition", "This login does not have a BatchBoss user profile yet. Sign in to the customer portal once, then try again.");
  }

  const expiresAt = enabled ? new Date(Date.now() + durationDays * 24 * 60 * 60 * 1000) : null;
  await userRef.set(enabled ? {
    subscriptionPlan: "promo",
    subscriptionStatus: "active",
    subscriptionSource: "admin_promotion",
    subscriptionStartedAt: FieldValue.serverTimestamp(),
    subscriptionExpiresAt: expiresAt,
    subscriptionGrantedBy: administrator.uid,
    updatedAt: FieldValue.serverTimestamp(),
  } : {
    subscriptionPlan: "free",
    subscriptionStatus: "free",
    subscriptionSource: FieldValue.delete(),
    subscriptionStartedAt: FieldValue.delete(),
    subscriptionExpiresAt: FieldValue.delete(),
    subscriptionGrantedBy: FieldValue.delete(),
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });

  await db.collection("adminAuditLogs").add({
    action: enabled ? "PROMOTIONAL_PRO_GRANTED" : "PROMOTIONAL_PRO_REMOVED",
    targetUid: uid,
    targetEmail: userRecord.email || "",
    durationDays: enabled ? durationDays : null,
    expiresAt,
    actorUid: administrator.uid,
    actorEmail: String(administrator.token.email || ""),
    createdAt: FieldValue.serverTimestamp(),
    status: "completed",
  });

  return { success: true, expiresAt: expiresAt?.toISOString() || null };
});

export const setAccountDisabled = onCall({ region: "europe-west1" }, async request => {
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
  { region: "europe-west1", timeoutSeconds: 540, memory: "1GiB" },
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

export const submitDeletionRequest = onCall({ region: "europe-west1" }, async request => {
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
