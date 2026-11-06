"use strict";

const PLANS = Object.freeze({
  bakerMonthly: { label: "Baker Pro Monthly", rateBps: 2500, category: "baker" },
  bakerAnnual: { label: "Baker Pro Annual", rateBps: 2500, category: "baker" },
  supplierDirectory: { label: "Supplier Directory", rateBps: 2000, category: "supplier" },
  supplierGrowth: { label: "Supplier Growth", rateBps: 2000, category: "supplier" },
  supplierFeatured: { label: "Supplier Featured", rateBps: 2000, category: "supplier" }
});
const PAYMENT_PROVIDERS = Object.freeze(["paystack", "payfast"]);
const COMMISSION_TRANSITIONS = Object.freeze({
  pending: ["approved", "reversed"],
  approved: ["paid", "reversed"],
  paid: ["reversed"],
  reversed: []
});

function calculateCommission(amountCents, planId) {
  if (!Number.isSafeInteger(amountCents) || amountCents <= 0 || amountCents > 100000000) {
    throw new Error("Invalid positive payment amount in cents");
  }
  const plan = PLANS[planId];
  if (!Object.hasOwn(PLANS, planId)) throw new Error("Unknown plan");
  return Math.round(amountCents * plan.rateBps / 10000);
}
function canTransition(from, to) {
  return Boolean(COMMISSION_TRANSITIONS[from]?.includes(to));
}
function validateBusinessEmail(email) {
  return typeof email === "string" && email.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}
function publicPartner(partner) {
  return {
    fullName: partner.fullName || "",
    email: partner.email || "",
    province: partner.province || "",
    status: partner.status || "pending",
    referralCode: partner.referralCode || null
  };
}
module.exports = { PLANS, PAYMENT_PROVIDERS, calculateCommission, canTransition, validateBusinessEmail, publicPartner };
