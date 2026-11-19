import { createHash, timingSafeEqual } from "node:crypto";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { defineSecret, defineString } from "firebase-functions/params";
import { HttpsError, onCall, onRequest } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";

import {accountPlatform} from './accountPlatform';

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
        registrationPlatform: accountPlatform(data.registrationPlatform),
        lastLoginPlatform: accountPlatform(data.lastLoginPlatform),
        lastLoginPlatformAt: typeof data.lastLoginPlatformAt?.toDate === 'function'
          ? data.lastLoginPlatformAt.toDate().toISOString() : null,
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

  const existing = userSnapshot.data() || {};
  if (enabled && ["paystack", "payfast"].includes(existing.subscriptionSource) && existing.subscriptionStatus === "active") {
    throw new HttpsError("failed-precondition", "This account already has an active paid subscription. Do not replace its billing access with a gift.");
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

  // A new 7-day grant generates an email event. Firestore's event handler sends it
  // asynchronously, so a successful admin action does not claim email delivery.
  if (enabled && durationDays === 7 && expiresAt) {
    await db.collection("proGiftEmailEvents").add({
      type: "gift_pro", uid, durationDays, expiresAt,
      createdAt: FieldValue.serverTimestamp(),
    });
  }
  return { success: true, expiresAt: expiresAt?.toISOString() || null };
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
const PAYFAST_VALIDATE_URL = "https://www.payfast.co.za/eng/query/validate";

function payfastEncode(value: string): string {
  return encodeURIComponent(value.trim())
    .replace(/%20/g, "+")
    .replace(/[!'()*~]/g, character =>
      `%${character.charCodeAt(0).toString(16).toUpperCase()}`
    );
}

function signaturesMatch(received: string, expected: string): boolean {
  if (!/^[a-fA-F0-9]{32}$/.test(received) || received.length !== expected.length) return false;
  return timingSafeEqual(
    Buffer.from(received.toLowerCase()),
    Buffer.from(expected.toLowerCase())
  );
}

type PayfastPlan = "weekly" | "monthly" | "annual";
function addBillingPeriod(date: Date, plan: PayfastPlan): Date {
  const result = new Date(date);
  if (plan === "weekly") { result.setUTCDate(result.getUTCDate() + 7); return result; }
  const day = result.getUTCDate();
  result.setUTCDate(1);
  result.setUTCMonth(result.getUTCMonth() + (plan === "annual" ? 12 : 1));
  const last = new Date(Date.UTC(result.getUTCFullYear(), result.getUTCMonth()+1, 0)).getUTCDate();
  result.setUTCDate(Math.min(day,last));
  return result;
}
function cents(value: string): number {
  if (!/^\d{1,9}(\.\d{1,2})?$/.test(value)) return NaN;
  return Math.round(Number(value)*100);
}
function validPlan(value: unknown): value is PayfastPlan {
  return value === "weekly" || value === "monthly" || value === "annual";
}
function paidThrough(value: unknown): number {
  const v = value as { toMillis?: () => number } | undefined;
  return v?.toMillis?.() || 0;
}

// Retained for older admin clients.
export const checkPayfastConnection = onCall(
  { region: "europe-west1", invoker: "public", secrets: payfastSecrets, cors: [APP_URL, "https://admin.batchboss.co.za"] },
  async request => {
    requireAdmin(request);
    const merchantId = payfastMerchantId.value().trim();
    const passphrase = payfastPassphrase.value().trim();
    const merchantKeyPresent = !!payfastMerchantKey.value().trim();
    if (!merchantId || !passphrase || !merchantKeyPresent) {
      return { connected: false, merchantKeyPresent, checkoutEnabled: false, status: "configuration_missing" };
    }
    const timestamp = new Date().toISOString().slice(0,19) + "+00:00";
    const headers = { "merchant-id": merchantId, version: "v1", timestamp };
    const values: Record<string,string> = {...headers, passphrase};
    const canonical = Object.keys(values).sort().map(key => key + "=" + payfastEncode(values[key])).join("&");
    const signature = createHash("md5").update(canonical).digest("hex");
    try {
      const result = await fetch("https://api.payfast.co.za/ping", {
        method: "GET", headers: {...headers,signature}, redirect: "error", signal: AbortSignal.timeout(15000),
      });
      const raw = (await result.text()).trim();
      const connected = result.ok && /^"?payfast api"?$/i.test(raw);
      // Never return credentials or untrusted provider response content.
      return { connected, merchantKeyPresent, merchantKeyVerified: false, checkoutEnabled: false,
        status: connected ? "connected" : "provider_rejected", httpStatus: result.status };
    } catch {
      return { connected: false, merchantKeyPresent, merchantKeyVerified: false, checkoutEnabled: false, status: "provider_unreachable" };
    }
  }
);

async function payfastSubscriptionApi(token:string, operation:"fetch"|"cancel") {
  const timestamp=new Date().toISOString().slice(0,19)+"+00:00";
  const headers={"merchant-id":payfastMerchantId.value().trim(),version:"v1",timestamp};
  const fields:Record<string,string>={...headers,passphrase:payfastPassphrase.value().trim()};
  const canonical=Object.keys(fields).sort().map(key=>key+"="+payfastEncode(fields[key])).join("&");
  const signature=createHash("md5").update(canonical).digest("hex");
  const response=await fetch("https://api.payfast.co.za/subscriptions/"+encodeURIComponent(token)+"/"+operation,{
    method:operation==="fetch"?"GET":"PUT",headers:{...headers,signature},redirect:"error",signal:AbortSignal.timeout(15000)
  });
  if(!response.ok)throw new HttpsError("unavailable","Payfast could not confirm the subscription status. Please try again or contact support.");
  const raw=await response.json() as Record<string,any>;
  if(raw.status==="error"||raw.success===false||raw.response===false)throw new HttpsError("failed-precondition","Payfast rejected the subscription request.");
  return raw.data?.response??raw.data??raw;
}
function remotePayfastStatus(data:Record<string,any>):string {
  return String(data.status_text|| (typeof data.status==="string"?data.status:"")).trim().toLowerCase();
}
async function ownPayfastSubscription(uid:string) {
  const profileRef=db.doc(`users/${uid}`);
  const profile=(await profileRef.get()).data();
  const token=String(profile?.payfastToken||"");
  if(profile?.subscriptionSource!=="payfast"||!/^([a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12})$/.test(token))
    throw new HttpsError("failed-precondition","No Payfast subscription is linked to your account.");
  const subRef=db.doc(`payfastSubscriptions/${token}`);
  const sub=(await subRef.get()).data();
  if(sub?.uid!==uid)throw new HttpsError("failed-precondition","Contact support to reconcile your subscription.");
  return {profileRef,profile,token,subRef};
}
export const getPayfastSubscriptionStatus=onCall({region:"europe-west1",invoker:"public",secrets:payfastSecrets,cors:[APP_URL]},async request=>{
  const current=requireSignedIn(request);
  const {profile,token}=await ownPayfastSubscription(current.uid);
  const remote=await payfastSubscriptionApi(token,"fetch");
  if(remote.token&&remote.token!==token)throw new HttpsError("failed-precondition","Subscription reference mismatch.");
  const status=remotePayfastStatus(remote);
  const next=String(remote.run_date||"");
  return {status:status||"unknown",nextPaymentDate:/^\d{4}-\d{2}-\d{2}$/.test(next)?next:null,
    accessUntil:profile.subscriptionExpiresAt?.toDate?.().toISOString()||null,renews:profile.subscriptionRenews===true};
});
export const cancelPayfastSubscription=onCall({region:"europe-west1",invoker:"public",secrets:payfastSecrets,cors:[APP_URL]},async request=>{
  const current=requireSignedIn(request);
  if(request.data?.confirm!==true)throw new HttpsError("invalid-argument","Confirm that you want to stop recurring payments.");
  const {profileRef,token,subRef}=await ownPayfastSubscription(current.uid);
  const existing=await payfastSubscriptionApi(token,"fetch");
  if(existing.token&&existing.token!==token)throw new HttpsError("failed-precondition","Subscription reference mismatch.");
  if(!["cancelled","canceled"].includes(remotePayfastStatus(existing)))await payfastSubscriptionApi(token,"cancel");
  // A successful HTTP response alone is not enough. Confirm from a fresh provider read.
  const verified=await payfastSubscriptionApi(token,"fetch");
  if(verified.token&&verified.token!==token)throw new HttpsError("failed-precondition","Subscription reference mismatch.");
  if(!["cancelled","canceled"].includes(remotePayfastStatus(verified)))throw new HttpsError("unavailable","Cancellation is not confirmed yet. Refresh your subscription or contact support.");
  const expiry=await db.runTransaction(async t=>{
    const [p,s]=await Promise.all([t.get(profileRef),t.get(subRef)]);
    const data=p.data();
    if(s.data()?.uid!==current.uid||data?.subscriptionSource!=="payfast"||data.payfastToken!==token)
      throw new HttpsError("failed-precondition","Subscription changed. Contact support.");
    t.update(subRef,{status:"cancelled",nextPaymentAt:null,updatedAt:FieldValue.serverTimestamp()});
    t.update(profileRef,{subscriptionRenews:false,updatedAt:FieldValue.serverTimestamp()});
    return data.subscriptionExpiresAt?.toDate?.().toISOString()||null;
  });
  return {cancelled:true,accessUntil:expiry};
});

export const createPayfastCheckout = onCall(
  { region: "europe-west1", invoker: "public", cors: [APP_URL] },
  () => {
    throw new HttpsError("failed-precondition", "PayFast checkout has been retired.");
  }
);

export const payfastNotify = onRequest(
  { region: "europe-west1", invoker: "public", secrets: payfastSecrets },
  async (request,response) => {
    try {
      if(request.method!=="POST"){response.status(405).send("Method not allowed");return;}
      const params = new URLSearchParams(request.rawBody.toString("utf8"));
      const fields: PayfastFields = {};
      for(const [key,value] of params){
        if(!/^[a-zA-Z0-9_]+$/.test(key)||Object.prototype.hasOwnProperty.call(fields,key)){
          response.status(400).send("Invalid fields");return;
        }
        fields[key]=value;
      }
      // ITNs include empty fields; preserve their transmitted order for validation.
      const encoded = Object.entries(fields).filter(([key])=>key!=="signature").map(([key,value])=>key+"="+payfastEncode(value)).join("&");
      const expectedSignature=createHash("md5").update(encoded+"&passphrase="+payfastEncode(payfastPassphrase.value())).digest("hex");
      if(!signaturesMatch(fields.signature||"",expectedSignature)||fields.merchant_id!==payfastMerchantId.value()){
        response.status(400).send("Invalid signature or merchant");return;
      }
      const validation=await fetch(PAYFAST_VALIDATE_URL,{method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded"},body:encoded,signal:AbortSignal.timeout(15000)});
      if(!validation.ok){response.status(503).send("Validation unavailable");return;}
      if((await validation.text()).trim()!=="VALID"){response.status(400).send("Invalid notification");return;}
      const token=String(fields.token||"");
      const paymentId=String(fields.m_payment_id||"");
      const transactionId=String(fields.pf_payment_id||"");
      const status=String(fields.payment_status||"").toUpperCase();
      if(!["COMPLETE","CANCELLED","FAILED"].includes(status)){response.status(200).send("Ignored");return;}
      if(!/^[a-zA-Z0-9_-]{1,100}$/.test(transactionId)||!/^([a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12})$/.test(token)){
        response.status(400).send("Missing transaction or subscription token");return;
      }
      const tokenRef=db.doc(`payfastSubscriptions/${token}`);
      const tokenSnapshot=await tokenRef.get();
      const mapped=tokenSnapshot.data();
      const reference=String(mapped?.paymentId||paymentId);
      if(!/^[a-zA-Z0-9_-]{1,100}$/.test(reference)){response.status(400).send("Unknown reference");return;}
      const paymentRef=db.doc(`subscriptionPayments/${reference}`);
      const initial=(await paymentRef.get()).data();
      if(!initial||initial.provider!=="payfast"||!validPlan(initial.plan)){response.status(404).send("Unknown payment");return;}
      const plan:PayfastPlan=initial.plan;
      const expectedCents=Number(initial.amountCents||Math.round(Number(initial.amount)*100));
      if(status==="COMPLETE"&&cents(fields.amount_gross||"")!==expectedCents){response.status(400).send("Amount mismatch");return;}
      const uid=String(initial.uid||"");
      if(!uid||uid.includes("/")){response.status(400).send("Invalid account");return;}
      const profileRef=db.doc(`users/${uid}`);
      const lockRef=db.doc(`payfastCheckouts/${uid}`);
      const txRef=db.doc(`payfastTransactions/${transactionId}`);
      const eventRef=db.doc(`payfastBillingEvents/${transactionId}_${status}`);
      await db.runTransaction(async t=>{
        const [p,sub,tx,event,payment,lock]=await Promise.all([t.get(profileRef),t.get(tokenRef),t.get(txRef),t.get(eventRef),t.get(paymentRef),t.get(lockRef)]);
        if(event.exists)return;
        if(!p.exists||!payment.exists)throw Error("Missing account or payment");
        const profile=p.data()!, linked=sub.data(), original=payment.data()!;
        if(linked && (linked.uid!==uid||linked.paymentId!==reference))throw Error("Subscription token mismatch");
        if(original.payfastToken&&original.payfastToken!==token)throw Error("Payment token mismatch");
        if(tx.exists&&tx.data()?.uid!==uid)throw Error("Transaction ownership mismatch");
        // Recognise an already-processed payment from the previous implementation.
        const legacyDuplicate=original.payfastPaymentId===transactionId&&original.status==="complete"&&!tx.exists;
        t.create(eventRef,{uid,reference,transactionId,status,provider:"payfast",createdAt:FieldValue.serverTimestamp()});
        if(status!=="COMPLETE"){
          if(linked&&linked.uid===uid){
            t.set(tokenRef,{lastPaymentEvent:status,...(status==="CANCELLED"?{status:"cancelled",nextPaymentAt:null}:{}),updatedAt:FieldValue.serverTimestamp()},{merge:true});
            if(profile.subscriptionSource==="payfast"&&profile.payfastToken===token&&status==="CANCELLED")
              t.update(profileRef,{subscriptionRenews:false,updatedAt:FieldValue.serverTimestamp()});
          }
          return;
        }
        if(tx.exists)return;
        const conflicting=profile.subscriptionSource==="paystack"&&(profile.paystackSubscriptionCode||profile.subscriptionStatus==="active")||
          profile.subscriptionSource==="payfast"&&profile.payfastToken&&profile.payfastToken!==token;
        const existingExpiry=paidThrough(profile.subscriptionExpiresAt);
        const nextDate=addBillingPeriod(new Date(),plan);
        // The provider's next billing date is preferred only if it is a valid future date.
        const remoteDate=/^\d{4}-\d{2}-\d{2}$/.test(fields.billing_date||"")?new Date(fields.billing_date+"T00:00:00+02:00"):null;
        const expires=remoteDate&&Number.isFinite(remoteDate.getTime())&&remoteDate.getTime()>Date.now()&&remoteDate.getTime()<=nextDate.getTime()+86400000?remoteDate:nextDate;
        const expiry=new Date(Math.max(expires.getTime(),existingExpiry));
        t.create(txRef,{uid,reference,transactionId,provider:"payfast",plan,amountCents:expectedCents,currency:"ZAR",status:"success",
          paidAt:FieldValue.serverTimestamp(),legacyDuplicate,requiresReview:!!conflicting});
        t.set(tokenRef,{uid,paymentId:reference,plan,amountCents:expectedCents,currency:"ZAR",status:conflicting?"review":linked?.status==="cancelled"?"cancelled":"active",
          lastTransactionId:transactionId,nextPaymentAt:linked?.status==="cancelled"?null:expires,updatedAt:FieldValue.serverTimestamp()},{merge:true});
        t.set(paymentRef,{status:"complete",payfastPaymentId:transactionId,payfastToken:token,updatedAt:FieldValue.serverTimestamp()},{merge:true});
        if(lock.data()?.paymentId===reference)t.delete(lockRef);
        if(!conflicting&&!legacyDuplicate)t.set(profileRef,{subscriptionPlan:"pro",subscriptionStatus:"active",subscriptionSource:"payfast",
          subscriptionBillingInterval:plan,subscriptionRenews:linked?.status!=="cancelled",subscriptionExpiresAt:expiry,
          subscriptionStartedAt:profile.subscriptionSource==="payfast"&&profile.subscriptionStartedAt?profile.subscriptionStartedAt:FieldValue.serverTimestamp(),
          payfastPaymentId:reference,payfastToken:token,updatedAt:FieldValue.serverTimestamp()},{merge:true});
      });
      response.status(200).send("OK");
    }catch(error){console.error("Payfast notification processing failed",error instanceof Error?error.message:"unknown");response.status(500).send("Retry later");}
  }
);

export const expirePayfastAccess=onSchedule({region:"europe-west1",schedule:"every 60 minutes"},async()=>{
  const users=await db.collection("users").where("subscriptionSource","==","payfast").get();
  for(const user of users.docs)await db.runTransaction(async t=>{
    const current=await t.get(user.ref), p=current.data();
    if(p?.subscriptionSource==="payfast"&&p.subscriptionStatus==="active"&&paidThrough(p.subscriptionExpiresAt)<=Date.now())
      t.update(user.ref,{subscriptionStatus:"expired",updatedAt:FieldValue.serverTimestamp()});
  });
});

export const listPayfastPayments=onCall({region:"europe-west1",invoker:"public"},async request=>{
  requireAdmin(request);
  const result=await db.collection("payfastTransactions").orderBy("paidAt","desc").limit(100).get();
  return {payments:result.docs.map(doc=>{const p=doc.data();return {id:doc.id,uid:p.uid,reference:p.reference,plan:p.plan,
    amountCents:p.amountCents,currency:p.currency,status:p.status,paidAt:p.paidAt?.toDate?.().toISOString()||null,requiresReview:!!p.requiresReview};})};
});

export const getMyPayfastPayments=onCall({region:"europe-west1",invoker:"public"},async request=>{
  const current=requireSignedIn(request);
  const items=await db.collection("payfastTransactions").where("uid","==",current.uid).limit(200).get();
  return {payments:items.docs.map(doc=>{const p=doc.data();return {reference:doc.id,plan:p.plan,amountCents:p.amountCents,currency:p.currency,status:p.status,paidAt:p.paidAt?.toDate?.().toISOString()||null};}).sort((a,b)=>String(b.paidAt).localeCompare(String(a.paidAt)))};
});

export {getPaystackPlans,createPaystackCheckout,verifyPaystackPayment,managePaystackSubscription,paystackWebhook,expirePaystackAccess} from './paystack';

export { sendBatchBossWelcomeEmail } from './welcomeEmail';
export { sendBatchBossGiftProEmail } from './giftProEmail';

export { sendBatchBossProPaymentEmail, sendBatchBossPayfastReceipt } from './proPaymentEmail';

export { sendBatchBossFailedPaymentEmail, sendBatchBossCancellationEmail, sendBatchBossRenewalReminders } from './subscriptionEmails';

export {createProRedeemCode,redeemProCode,listProRedeemCodes,disableProRedeemCode} from './proCodes';

export {createPayfastSandboxCheckout,payfastSandboxNotify,getPayfastSandboxResult} from './payfastSandbox';
