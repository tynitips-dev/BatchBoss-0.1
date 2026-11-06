"use strict";
const { validateEvent, digest } = require("./referralContract");
const { calculateCommission } = require("./commissionPolicy");
// Only the emulator adapter exports this service. Test receipts never enter the payable ledger.
async function ingestTestEvent(db, raw) {
  const e = validateEvent(raw);
  const eventRef = db.collection("partnerTestEvents").doc(e.eventId);
  const subjectKey = e.source + "-" + e.subject;
  const subjectRef = db.collection("partnerTestSubjects").doc(subjectKey);
  return db.runTransaction(async t => {
    const prior = await t.get(eventRef);
    if (prior.exists) {
      if (prior.get("digest") !== digest(e)) throw new Error("Event replay changed payload");
      return { accepted: true, duplicate: true, payable: false };
    }
    const code = await t.get(db.collection("partnerCodes").doc(e.referralCode));
    if (!code.exists) throw new Error("Referral unavailable");
    const partner = await t.get(db.collection("partnerProfiles").doc(code.get("partnerUid")));
    if (partner.get("status") !== "approved") throw new Error("Referral unavailable");
    const subject = await t.get(subjectRef);
    if (e.kind === "registration") {
      if (subject.exists && subject.get("referralCode") !== e.referralCode) throw new Error("Attribution is immutable");
      if (!subject.exists) t.create(subjectRef, { source:e.source, referralCode:e.referralCode, partnerUid:code.get("partnerUid") });
    } else {
      if (!subject.exists) throw new Error("Registration must arrive first; retry later");
      if (subject.get("referralCode") !== e.referralCode) throw new Error("Payment attribution mismatch");
      const paymentRef = db.collection("partnerTestPaymentClaims").doc(e.source + "-" + e.reference);
      const firstRef = db.collection("partnerTestFirstSales").doc(subjectKey);
      const [payment,first] = await Promise.all([t.get(paymentRef),t.get(firstRef)]);
      if (payment.exists && payment.get("subjectKey") !== subjectKey) throw new Error("Payment already claimed");
      // Renewals and repeated provider notifications cannot produce another first-sale credit.
      if (!payment.exists) t.create(paymentRef, { subjectKey });
      if (!first.exists && !payment.exists) t.create(firstRef, {
        subjectKey, partnerUid:code.get("partnerUid"), planId:e.planId,
        amountCents:e.amountCents, commissionCents:calculateCommission(e.amountCents,e.planId),
        status:"test-only", payable:false
      });
    }
    t.create(eventRef, { digest:digest(e), source:e.source, kind:e.kind });
    return { accepted:true, duplicate:false, payable:false };
  });
}
module.exports = { ingestTestEvent };
