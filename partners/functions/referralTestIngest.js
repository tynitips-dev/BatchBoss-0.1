"use strict";
const { validateEvent, digest } = require("./referralContract");
const { calculateCommission } = require("./commissionPolicy");

// Trusted source events in this module are exclusively synthetic emulator events.
// Provider verification must happen in the source checkout service before it emits
// any event. No test record becomes a payable commission or grants an entitlement.
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
    // A suspended agent cannot earn NEW credit, but refunds must still undo
    // the previously recorded first-sale test credit.
    if (e.kind !== "reversal" && partner.get("status") !== "approved") {
      throw new Error("Referral unavailable");
    }
    const subject = await t.get(subjectRef);
    if (e.kind === "registration") {
      if (subject.exists && (subject.get("referralCode") !== e.referralCode ||
          subject.get("partnerUid") !== code.get("partnerUid"))) {
        throw new Error("Attribution is immutable");
      }
      if (!subject.exists) t.create(subjectRef, {
        source: e.source, referralCode: e.referralCode, partnerUid: code.get("partnerUid")
      });
    } else {
      if (!subject.exists) throw new Error("Registration must arrive first; retry later");
      if (subject.get("referralCode") !== e.referralCode ||
          subject.get("partnerUid") !== code.get("partnerUid")) {
        throw new Error("Payment attribution mismatch");
      }
      const paymentRef = db.collection("partnerTestPaymentClaims").doc(e.source + "-" + e.reference);
      const firstRef = db.collection("partnerTestFirstSales").doc(subjectKey);
      if (e.kind === "payment") {
        const [payment, first] = await Promise.all([t.get(paymentRef), t.get(firstRef)]);
        if (payment.exists && payment.get("subjectKey") !== subjectKey) {
          throw new Error("Payment already claimed");
        }
        // A renewed or repeatedly delivered charge cannot create a second
        // first-sale credit, even if the first payment was later refunded.
        if (!payment.exists) t.create(paymentRef, { subjectKey });
        if (!first.exists && !payment.exists) t.create(firstRef, {
          subjectKey, partnerUid: code.get("partnerUid"), planId: e.planId,
          reference: e.reference, amountCents: e.amountCents,
          commissionCents: calculateCommission(e.amountCents, e.planId),
          status: "test-only", payable: false
        });
      } else {
        const reversalRef = db.collection("partnerTestReversalClaims").doc(e.source + "-" + e.reference);
        const [payment, first, reversed] = await Promise.all([
          t.get(paymentRef), t.get(firstRef), t.get(reversalRef)
        ]);
        if (!payment.exists || payment.get("subjectKey") !== subjectKey) {
          throw new Error("Reversal requires the original verified payment first");
        }
        if (!first.exists || first.get("reference") !== e.reference ||
            first.get("planId") !== e.planId || first.get("amountCents") !== e.amountCents) {
          throw new Error("Full reversal must match the first credited charge");
        }
        if (reversed.exists) {
          if (reversed.get("reason") !== e.reason ||
              reversed.get("amountCents") !== e.amountCents ||
              reversed.get("subjectKey") !== subjectKey) {
            throw new Error("Conflicting reversal for payment");
          }
        } else {
          t.create(reversalRef, {
            subjectKey, reason: e.reason, amountCents: e.amountCents
          });
          t.update(firstRef, {
            status: "test-reversed", reversalReason: e.reason,
            reversedAmountCents: e.amountCents, effectiveCommissionCents: 0,
            payable: false
          });
        }
      }
    }
    t.create(eventRef, {
      digest: digest(e), source: e.source, kind: e.kind
    });
    return { accepted: true, duplicate: false, payable: false };
  });
}
module.exports = { ingestTestEvent };
