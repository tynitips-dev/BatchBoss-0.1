"use strict";
const crypto = require("node:crypto");
const CODE = /^BB-[A-F0-9]{12}$/;
const PAYMENT_FIELDS = ["reference", "planId", "amountCents", "currency"];
const FIELDS = ["version", "eventId", "source", "kind", "subject", "referralCode", ...PAYMENT_FIELDS, "mode", "reason"];
function validateEvent(input) {
  if (!input || typeof input !== "object" || Array.isArray(input) ||
      Object.keys(input).some(k => !FIELDS.includes(k)) ||
      input.version !== 1 || !["baker", "supplier"].includes(input.source) ||
      !["registration", "payment", "reversal"].includes(input.kind) ||
      !/^evt-[a-f0-9]{32}$/.test(input.eventId || "") ||
      !/^sub-[a-f0-9]{32}$/.test(input.subject || "") ||
      !CODE.test(input.referralCode || "") || input.mode !== "test") {
    throw new Error("Invalid test referral event");
  }
  if (input.kind === "registration") {
    if (PAYMENT_FIELDS.some(k => k in input) || "reason" in input) {
      throw new Error("Unexpected registration payment fields");
    }
  } else {
    const plans = {
      baker: ["bakerMonthly", "bakerAnnual"],
      supplier: ["supplierDirectory", "supplierGrowth", "supplierFeatured"]
    };
    if (!plans[input.source].includes(input.planId) ||
        !/^[A-Za-z0-9_-]{6,150}$/.test(input.reference || "") ||
        input.currency !== "ZAR" || !Number.isSafeInteger(input.amountCents) ||
        input.amountCents <= 0 || input.amountCents > 100000000) {
      throw new Error("Invalid test payment or reversal");
    }
    if (input.kind === "payment" && "reason" in input) {
      throw new Error("Payment cannot contain reversal reason");
    }
    // First pilot intentionally supports ONLY verified full-amount reversals.
    // Partial refunds must be held for manual reconciliation, not guessed.
    if (input.kind === "reversal" && !["refund", "chargeback"].includes(input.reason)) {
      throw new Error("Invalid reversal reason");
    }
  }
  return Object.fromEntries(FIELDS.filter(k => k in input).map(k => [k, input[k]]));
}
const digest = input => crypto.createHash("sha256").update(JSON.stringify(validateEvent(input))).digest("hex");
module.exports = { CODE, validateEvent, digest };
