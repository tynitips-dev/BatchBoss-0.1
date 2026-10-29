"use strict";
const crypto = require("node:crypto");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp, getApps } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const {
  PLANS, PAYMENT_PROVIDERS, calculateCommission, canTransition,
  validateBusinessEmail, publicPartner
} = require("./commissionPolicy");

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
  const [leads, commissions] = await Promise.all([
    collection("partnerLeads").where("partnerUid", "==", uid).limit(LIMIT).get(),
    collection("partnerCommissions").where("partnerUid", "==", uid).limit(LIMIT).get()
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
      rateBps: d.rateBps, status: d.status, createdAtMillis: plainTime(d.createdAt)
    };
  }).sort(sortNewest);
  return {
    role: "partner", partner, leads: leadItems, commissions: commissionItems,
    truncated: leads.size === LIMIT || commissions.size === LIMIT
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

exports.adminRecordVerifiedSale = onCall(async (request) => {
  const adminUid = adminUser(request);
  const leadId = safe(request.data?.leadId, 160);
  const planId = request.data?.planId;
  const provider = request.data?.provider;
  const reference = safe(request.data?.reference, 150);
  const amountCents = request.data?.amountCents;
  if (!/^[a-zA-Z0-9_-]{10,160}$/.test(leadId) || !Object.hasOwn(PLANS, planId) ||
      !PAYMENT_PROVIDERS.includes(provider) || !/^[A-Za-z0-9_./:-]{6,150}$/.test(reference) ||
      request.data?.merchantPaymentConfirmed !== true) {
    throw new HttpsError("invalid-argument", "Provide verified payment evidence and a valid product.");
  }
  let commissionCents;
  try { commissionCents = calculateCommission(amountCents, planId); }
  catch { throw new HttpsError("invalid-argument", "Invalid paid amount."); }
  const paymentClaim = collection("partnerPaymentClaims").doc(hash(provider + ":" + reference));
  const firstSaleClaim = collection("partnerFirstSales").doc(leadId);
  const leadRef = collection("partnerLeads").doc(leadId);
  const commissionRef = collection("partnerCommissions").doc();
  await db().runTransaction(async t => {
    const [lead, payment, first] = await Promise.all([
      t.get(leadRef), t.get(paymentClaim), t.get(firstSaleClaim)
    ]);
    if (!lead.exists) throw new HttpsError("not-found", "Lead does not exist.");
    if (payment.exists || first.exists) {
      throw new HttpsError("already-exists", "This payment or first-sale commission was already recorded.");
    }
    if (PLANS[planId].category !== lead.get("leadType")) {
      throw new HttpsError("invalid-argument", "Plan type does not match the lead.");
    }
    const partnerUid = lead.get("partnerUid");
    const partner = await t.get(collection("partnerProfiles").doc(partnerUid));
    if (partner.get("status") !== "approved") {
      throw new HttpsError("failed-precondition", "Partner must be approved.");
    }
    t.create(paymentClaim, { provider, leadId, commissionId: commissionRef.id, recordedAt: now() });
    t.create(firstSaleClaim, { leadId, commissionId: commissionRef.id, recordedAt: now() });
    t.create(commissionRef, {
      partnerUid, leadId, businessName: lead.get("businessName"),
      planId, provider, reference, amountCents, commissionCents,
      rateBps: PLANS[planId].rateBps, status: "pending",
      verificationMethod: "manual-merchant-dashboard", verifiedBy: adminUid,
      createdAt: now(), updatedAt: now()
    });
    t.update(leadRef, { saleStatus: "payment-verified", updatedAt: now() });
  });
  return { commissionId: commissionRef.id, commissionCents, status: "pending" };
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
      !["approved", "paid", "reversed"].includes(nextStatus) || !note) {
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

// Test bridge is deliberately absent from every deployed Functions export.
if (process.env.FUNCTIONS_EMULATOR === "true") {
  exports.ingestPartnerTestEvent = onCall(async request => {
    adminUser(request);
    const { ingestTestEvent } = require("./referralTestIngest");
    return ingestTestEvent(db(), request.data);
  });
}
