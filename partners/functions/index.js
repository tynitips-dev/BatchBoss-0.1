"use strict";
const crypto = require("node:crypto");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp, getApps } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const {
  PLANS, PAYMENT_PROVIDERS, calculateCommission, canTransition,
  validateBusinessEmail, publicPartner
} = require("./commissionPolicy");
const { MAX_PAYOUT_ITEMS, isPayoutEligible, sumPayout, validPayoutChange } = require("./payoutPolicy");

if (!getApps().length) initializeApp();
const db = () => getFirestore();
const collection = (name) => db().collection(name);
const now = () => FieldValue.serverTimestamp();
const LIMIT = 200;

function authUser(request) {
  if (!request.auth?.uid || request.auth.token.email_verified !== true) {
    throw new HttpsError("unauthenticated", "Please sign in and verify your email address.");
  }
  return request.auth.uid;
}
function adminUser(request) {
  const uid = authUser(request);
  if (request.auth.token.batchbossPartnerAdmin !== true) {
    throw new HttpsError("permission-denied", "Sales partner administrator required.");
  }
  return uid;
}
async function approvedPartner(request) {
  const uid = authUser(request);
  const ref = collection("partnerProfiles").doc(uid);
  const snapshot = await ref.get();
  if (snapshot.get("status") !== "approved") {
    throw new HttpsError("permission-denied", "Your application must be approved.");
  }
  return { uid, ref, profile: snapshot.data() };
}
const safe = (value, max) => typeof value === "string" ? value.trim().slice(0, max) : "";
const sortNewest = (a, b) => (b.createdAtMillis || 0) - (a.createdAtMillis || 0);
const plainTime = (value) => typeof value?.toMillis === "function" ? value.toMillis() : null;
const hash = (value) => crypto.createHash("sha256").update(value).digest("hex");

exports.applyPartner = onCall(async (request) => {
  const uid = authUser(request);
  const fullName = safe(request.data?.fullName, 100);
  const province = safe(request.data?.province, 80);
  if (fullName.length < 3 || !province || request.data?.acceptProgrammeReview !== true) {
    throw new HttpsError("invalid-argument", "Your name, province and programme acknowledgement are required.");
  }
  const doc = collection("partnerProfiles").doc(uid);
  try {
    await doc.create({
      uid, email: String(request.auth.token.email || "").toLowerCase(),
      fullName, province, status: "pending", referralCode: null,
      programmeReviewAcknowledgedAt: now(), createdAt: now(), updatedAt: now()
    });
  } catch (error) {
    if (error?.code === 6 || String(error?.code) === "already-exists") {
      throw new HttpsError("already-exists", "Your application already exists.");
    }
    throw error;
  }
  return { status: "pending" };
});

exports.getPartnerOverview = onCall(async (request) => {
  const uid = authUser(request);
  if (request.auth.token.batchbossPartnerAdmin === true) return { role: "admin" };
  const profile = await collection("partnerProfiles").doc(uid).get();
  if (!profile.exists) return { role: "applicant", status: "not-applied" };
  const partner = publicPartner(profile.data());
  if (partner.status !== "approved") return { role: "applicant", ...partner };
  const [leads, commissions, payouts] = await Promise.all([
    collection("partnerLeads").where("partnerUid", "==", uid).limit(LIMIT + 1).get(),
    collection("partnerCommissions").where("partnerUid", "==", uid).limit(LIMIT + 1).get(),
    collection("partnerPayoutRequests").where("partnerUid", "==", uid).limit(LIMIT + 1).get()
  ]);
  const leadItems = leads.docs.map(doc => {
    const d = doc.data();
    return {
      id: doc.id, businessName: d.businessName, businessEmail: d.businessEmail,
      leadType: d.leadType, province: d.province,
      agentStage: d.agentStage, saleStatus: d.saleStatus || "unverified",
      note: d.note || "", createdAtMillis: plainTime(d.createdAt)
    };
  }).sort(sortNewest);
  const commissionItems = commissions.docs.map(doc => {
    const d = doc.data();
    return {
      id: doc.id, businessName: d.businessName, planId: d.planId,
      amountCents: d.amountCents, commissionCents: d.commissionCents,
      rateBps: d.rateBps, status: d.status,
      paymentVerification: d.paymentVerification, payable: d.payable,
      payoutRequestId: d.payoutRequestId || null, createdAtMillis: plainTime(d.createdAt)
    };
  }).sort(sortNewest);
  const truncated = leads.size > LIMIT || commissions.size > LIMIT || payouts.size > LIMIT;
  return {
    role: "partner", partner, leads: leadItems.slice(0, LIMIT), commissions: commissionItems.slice(0, LIMIT),
    payoutRequests: payouts.docs.slice(0, LIMIT).map(d => ({
      id: d.id, amountCents: d.get("amountCents"), status: d.get("status"),
      createdAtMillis: plainTime(d.get("createdAt")), paidAtMillis: plainTime(d.get("paidAt"))
    })).sort(sortNewest),
    openPayoutRequest: Boolean(profile.get("openPayoutRequestId")),
    payoutEligibleCents: truncated || profile.get("openPayoutRequestId") ? 0 :
      commissionItems.reduce((sum, v) => sum + (isPayoutEligible(v) ? v.commissionCents : 0), 0),
    truncated
  };
});

exports.createPartnerLead = onCall(async (request) => {
  const { uid } = await approvedPartner(request);
  const businessName = safe(request.data?.businessName, 120);
  const businessEmail = safe(request.data?.businessEmail, 254).toLowerCase();
  const province = safe(request.data?.province, 80);
  const leadType = request.data?.leadType;
  if (businessName.length < 2 || !validateBusinessEmail(businessEmail) || !province ||
      !["baker", "supplier"].includes(leadType) || request.data?.permissionConfirmed !== true) {
    throw new HttpsError("invalid-argument", "Enter a business, valid email, province and contact permission.");
  }
  const leadRef = collection("partnerLeads").doc();
  const claimRef = collection("partnerLeadClaims").doc(hash(leadType + ":" + businessEmail));
  await db().runTransaction(async (t) => {
    const prior = await t.get(claimRef);
    if (prior.exists) throw new HttpsError("already-exists", "This business has already been registered as a lead.");
    t.create(claimRef, { leadId: leadRef.id, createdAt: now() });
    t.create(leadRef, {
      partnerUid: uid, businessName, businessEmail, province, leadType,
      agentStage: "new", saleStatus: "unverified", note: "",
      permissionConfirmedAt: now(), createdAt: now(), updatedAt: now()
    });
  });
  return { id: leadRef.id, status: "new" };
});

exports.updatePartnerLead = onCall(async (request) => {
  const { uid } = await approvedPartner(request);
  const leadId = safe(request.data?.leadId, 160);
  const agentStage = request.data?.agentStage;
  const note = safe(request.data?.note, 500);
  if (!/^[a-zA-Z0-9_-]{10,160}$/.test(leadId) || !["new", "contacted", "follow-up"].includes(agentStage)) {
    throw new HttpsError("invalid-argument", "Invalid lead or follow-up status.");
  }
  const ref = collection("partnerLeads").doc(leadId);
  await db().runTransaction(async t => {
    const doc = await t.get(ref);
    if (!doc.exists || doc.get("partnerUid") !== uid) {
      throw new HttpsError("not-found", "Lead not found.");
    }
    t.update(ref, { agentStage, note, updatedAt: now() });
  });
  return { updated: true };
});

exports.adminListPartners = onCall(async (request) => {
  adminUser(request);
  const snapshot = await collection("partnerProfiles").limit(LIMIT).get();
  return { partners: snapshot.docs.map(d => ({ uid: d.id, ...publicPartner(d.data()) })),
    truncated: snapshot.size === LIMIT };
});

exports.adminReviewPartner = onCall(async (request) => {
  const adminUid = adminUser(request);
  const partnerUid = safe(request.data?.partnerUid, 160);
  const decision = request.data?.decision;
  if (!/^[a-zA-Z0-9_-]{10,160}$/.test(partnerUid) ||
      !["approved", "rejected", "suspended"].includes(decision)) {
    throw new HttpsError("invalid-argument", "Invalid partner review.");
  }
  const ref = collection("partnerProfiles").doc(partnerUid);
  const code = "BB-" + crypto.randomBytes(6).toString("hex").toUpperCase();
  const codeRef = collection("partnerCodes").doc(code);
  await db().runTransaction(async t => {
    const doc = await t.get(ref);
    if (!doc.exists) throw new HttpsError("not-found", "Application not found.");
    const status = doc.get("status");
    if (!["pending", "approved", "suspended", "rejected"].includes(status)) {
      throw new HttpsError("failed-precondition", "Unknown application state.");
    }
    if (decision === "approved" && status === "rejected") {
      throw new HttpsError("failed-precondition", "Rejected applications require manual review before reinstatement.");
    }
    const existingCode = doc.get("referralCode");
    if (decision === "approved" && !existingCode) {
      const codeTaken = await t.get(codeRef);
      if (codeTaken.exists) throw new HttpsError("aborted", "Referral code collision. Please retry.");
      t.create(codeRef, { partnerUid, createdAt: now() });
    }
    t.update(ref, {
      status: decision,
      referralCode: existingCode || (decision === "approved" ? code : null),
      reviewedAt: now(), reviewedBy: adminUid, updatedAt: now()
    });
  });
  return { status: decision };
});

exports.adminListLeads = onCall(async request => {
  adminUser(request);
  const snapshot = await collection("partnerLeads").limit(LIMIT).get();
  return { leads: snapshot.docs.map(d => ({
    id: d.id, partnerUid: d.get("partnerUid"), businessName: d.get("businessName"),
    businessEmail: d.get("businessEmail"), leadType: d.get("leadType"),
    saleStatus: d.get("saleStatus") || "unverified"
  })), truncated: snapshot.size === LIMIT };
});

// Deliberately refuses client-supplied payment claims, including admin checkboxes.
// Only trusted, source-side provider verification may eventually write payable
// commissions. The offline referral bridge writes separate NONPAYABLE test records.
exports.adminRecordVerifiedSale = onCall(async request => {
  adminUser(request);
  throw new HttpsError(
    "failed-precondition",
    "Manual payment entry cannot create commissions. Use independently verified provider settlement and reconciliation."
  );
});

exports.adminListCommissions = onCall(async request => {
  adminUser(request);
  const snapshot = await collection("partnerCommissions").limit(LIMIT).get();
  return { commissions: snapshot.docs.map(d => {
    const v = d.data();
    return {
      id: d.id, partnerUid: v.partnerUid, businessName: v.businessName,
      planId: v.planId, amountCents: v.amountCents, commissionCents: v.commissionCents,
      status: v.status, provider: v.provider, reference: v.reference
    };
  }), truncated: snapshot.size === LIMIT };
});

exports.adminChangeCommissionStatus = onCall(async request => {
  const adminUid = adminUser(request);
  const commissionId = safe(request.data?.commissionId, 160);
  const nextStatus = request.data?.nextStatus;
  const note = safe(request.data?.note, 250);
  if (!/^[a-zA-Z0-9_-]{10,160}$/.test(commissionId) ||
      !["approved", "reversed"].includes(nextStatus) || !note) {
    throw new HttpsError("invalid-argument", "Commission status and audit note required.");
  }
  const ref = collection("partnerCommissions").doc(commissionId);
  await db().runTransaction(async t => {
    const doc = await t.get(ref);
    if (!doc.exists) throw new HttpsError("not-found", "Commission not found.");
    if (!canTransition(doc.get("status"), nextStatus)) {
      throw new HttpsError("failed-precondition", "Invalid commission status change.");
    }
    t.update(ref, { status: nextStatus, updatedAt: now(),
      [nextStatus + "At"]: now(), [nextStatus + "By"]: adminUid });
    t.create(collection("partnerCommissionAudit").doc(), {
      commissionId, previousStatus: doc.get("status"),
      nextStatus, adminUid, note, createdAt: now()
    });
  });
  // "paid" records a verified manual payout; it does not send money.
  return { status: nextStatus };
});


function payoutId(value) {
  const id = safe(value, 160);
  if (!/^[a-zA-Z0-9_-]{10,160}$/.test(id)) {
    throw new HttpsError("invalid-argument", "Invalid payout request.");
  }
  return id;
}
function auditPayout(t, requestId, status, adminUid, note) {
  t.create(collection("partnerPayoutAudit").doc(), {
    requestId, status, adminUid, note, createdAt: now()
  });
}

// Agents can only REQUEST an amount derived on the server from immutable,
// independently provider-verified commission ledger records. Never send money.
exports.requestPartnerPayout = onCall(async request => {
  const { uid } = await approvedPartner(request);
  const snapshot = await collection("partnerCommissions")
    .where("partnerUid", "==", uid).limit(LIMIT + 1).get();
  if (snapshot.size > LIMIT) {
    throw new HttpsError("failed-precondition", "Account requires finance reconciliation before requesting a payout.");
  }
  const eligible = snapshot.docs.filter(doc => isPayoutEligible(doc.data()));
  if (!eligible.length || eligible.length > MAX_PAYOUT_ITEMS) {
    throw new HttpsError("failed-precondition", "No eligible payout, or too many entries for one request.");
  }
  const requestRef = collection("partnerPayoutRequests").doc();
  const partnerRef = collection("partnerProfiles").doc(uid);
  const summary = await db().runTransaction(async t => {
    const profile = await t.get(partnerRef);
    if (profile.get("status") !== "approved" || profile.get("openPayoutRequestId")) {
      throw new HttpsError("failed-precondition", "Partner not approved or another payout request is open.");
    }
    const verified = [];
    for (const item of eligible) {
      const doc = await t.get(item.ref);
      if (!doc.exists || doc.get("partnerUid") !== uid || !isPayoutEligible(doc.data())) {
        throw new HttpsError("failed-precondition", "Payout balance changed. Refresh and try again.");
      }
      verified.push(doc.data());
    }
    const amountCents = sumPayout(verified);
    for (const item of eligible) t.update(item.ref, { payoutRequestId: requestRef.id, updatedAt: now() });
    t.create(requestRef, {
      partnerUid: uid, commissionIds: eligible.map(d => d.id),
      amountCents, status: "requested", createdAt: now(), updatedAt: now()
    });
    t.update(partnerRef, { openPayoutRequestId: requestRef.id, updatedAt: now() });
    return { id: requestRef.id, amountCents };
  });
  return { ...summary, status: "requested", message: "Request submitted for administrator review; no funds transferred." };
});

exports.adminListPayoutRequests = onCall(async request => {
  adminUser(request);
  const snapshot = await collection("partnerPayoutRequests").limit(LIMIT + 1).get();
  return {
    payouts: snapshot.docs.slice(0, LIMIT).map(d => {
      const v = d.data();
      return { id: d.id, partnerUid: v.partnerUid, amountCents: v.amountCents,
        status: v.status, createdAtMillis: plainTime(v.createdAt) };
    }).sort(sortNewest),
    truncated: snapshot.size > LIMIT
  };
});

// A reviewer may approve or reject the request, but approval does NOT pay.
exports.adminReviewPayoutRequest = onCall(async request => {
  const adminUid = adminUser(request);
  const requestId = payoutId(request.data?.requestId);
  const decision = request.data?.decision;
  const note = safe(request.data?.note, 250);
  if (!["approved", "rejected"].includes(decision) || note.length < 8) {
    throw new HttpsError("invalid-argument", "Decision and a review note are required.");
  }
  const requestRef = collection("partnerPayoutRequests").doc(requestId);
  await db().runTransaction(async t => {
    const payout = await t.get(requestRef);
    if (!payout.exists || !validPayoutChange(payout.get("status"), decision)) {
      throw new HttpsError("failed-precondition", "Payout request is no longer awaiting review.");
    }
    const uid = payout.get("partnerUid");
    if (adminUid === uid) {
      throw new HttpsError("permission-denied", "A different administrator must review your payout.");
    }
    const partnerRef = collection("partnerProfiles").doc(uid);
    const partner = await t.get(partnerRef);
    if (partner.get("openPayoutRequestId") !== requestId) {
      throw new HttpsError("failed-precondition", "Payout request ownership changed.");
    }
    const docs = [];
    for (const id of payout.get("commissionIds") || []) {
      const ref = collection("partnerCommissions").doc(id);
      const doc = await t.get(ref);
      if (!doc.exists || doc.get("partnerUid") !== uid || doc.get("payoutRequestId") !== requestId) {
        throw new HttpsError("failed-precondition", "Locked commission mismatch.");
      }
      docs.push({ ref, data: doc.data() });
    }
    if (decision === "approved") {
      if (partner.get("status") !== "approved" ||
          docs.length === 0 ||
          !docs.every(d => isPayoutEligible({ ...d.data, payoutRequestId: null })) ||
          sumPayout(docs.map(d => ({ ...d.data, payoutRequestId: null }))) !== payout.get("amountCents")) {
        throw new HttpsError("failed-precondition", "Commission balance or verification changed. Reject and reconcile.");
      }
    } else {
      for (const d of docs) t.update(d.ref, { payoutRequestId: FieldValue.delete(), updatedAt: now() });
      t.update(partnerRef, { openPayoutRequestId: FieldValue.delete(), updatedAt: now() });
    }
    t.update(requestRef, { status: decision, reviewedBy: adminUid, reviewedAt: now(), updatedAt: now() });
    auditPayout(t, requestId, decision, adminUid, note);
  });
  return { status: decision };
});

// Records an already-completed EXTERNAL transfer. This function never sends money.
exports.adminRecordCompletedPayout = onCall(async request => {
  const adminUid = adminUser(request);
  const requestId = payoutId(request.data?.requestId);
  const transferReference = safe(request.data?.transferReference, 100);
  const note = safe(request.data?.note, 250);
  if (transferReference.length < 4 || note.length < 8) {
    throw new HttpsError("invalid-argument", "External transfer reference and audit note are required.");
  }
  const requestRef = collection("partnerPayoutRequests").doc(requestId);
  await db().runTransaction(async t => {
    const payout = await t.get(requestRef);
    if (!payout.exists || !validPayoutChange(payout.get("status"), "paid")) {
      throw new HttpsError("failed-precondition", "Payout must be approved and unpaid.");
    }
    const uid = payout.get("partnerUid");
    if (adminUid === uid) {
      throw new HttpsError("permission-denied", "A different administrator must record your payout.");
    }
    const partnerRef = collection("partnerProfiles").doc(uid);
    const partner = await t.get(partnerRef);
    if (partner.get("status") !== "approved" || partner.get("openPayoutRequestId") !== requestId) {
      throw new HttpsError("failed-precondition", "Open payout request mismatch.");
    }
    const docs = [];
    for (const id of payout.get("commissionIds") || []) {
      const ref = collection("partnerCommissions").doc(id);
      const doc = await t.get(ref);
      if (!doc.exists || doc.get("partnerUid") !== uid || doc.get("payoutRequestId") !== requestId ||
          !isPayoutEligible({ ...doc.data(), payoutRequestId: null })) {
        throw new HttpsError("failed-precondition", "A commission changed or was reversed. Reconcile before paying.");
      }
      docs.push({ ref, data: doc.data() });
    }
    if (!docs.length || sumPayout(docs.map(d => ({ ...d.data, payoutRequestId: null }))) !== payout.get("amountCents")) {
      throw new HttpsError("failed-precondition", "Payout amount mismatch.");
    }
    for (const d of docs) {
      t.update(d.ref, { status: "paid", paidAt: now(), paidBy: adminUid, updatedAt: now() });
      t.create(collection("partnerCommissionAudit").doc(), {
        commissionId: d.ref.id, previousStatus: "approved", nextStatus: "paid",
        adminUid, note: "External payout " + transferReference, createdAt: now()
      });
    }
    t.update(requestRef, {
      status: "paid", paidAt: now(), paidBy: adminUid, transferReference, updatedAt: now()
    });
    t.update(partnerRef, { openPayoutRequestId: FieldValue.delete(), updatedAt: now() });
    auditPayout(t, requestId, "paid", adminUid, note);
  });
  return { status: "paid", message: "External payout recorded; no money was sent by the portal." };
});

// Hosted test endpoints exist only in the dedicated staging project.
const { PROJECT: REFERRAL_STAGE_PROJECT, stagingEndpoints } = require("./stagingReferral");
Object.assign(exports, stagingEndpoints(
  require("firebase-functions/v2/https").onRequest,
  () => getFirestore(getApps().find(app => app.name === "referral-staging") ||
    initializeApp({ projectId: REFERRAL_STAGE_PROJECT }, "referral-staging"))
));

// The Firebase Auth callable remains exclusive to the local emulator.
if (process.env.FUNCTIONS_EMULATOR === "true" && /^demo-[a-z0-9-]+$/.test(process.env.GCLOUD_PROJECT || "") && process.env.BATCHBOSS_PARTNER_TEST_ENABLED === "true") {
  exports.ingestPartnerTestEvent = onCall(async request => {
    // An integration identity must NEVER be a portal administrator. Scope both
    // its Firebase UID and its signed custom claim to exactly one source.
    const uid = authUser(request);
    const source = request.auth.token.batchbossPartnerBridgeSource;
    if (!["baker", "supplier"].includes(source) ||
        uid !== "referral-bridge-" + source ||
        request.auth.token.batchbossPartnerAdmin === true ||
        source !== request.data?.source) {
      throw new HttpsError("permission-denied", "Dedicated test bridge identity required.");
    }
    const { ingestTestEvent } = require("./referralTestIngest");
    return ingestTestEvent(db(), request.data);
  });
}
