"use strict";
const crypto = require("node:crypto");
const CODE = /^BB-[A-F0-9]{12}$/;
function validateEvent(input) {
  const fields = ["version","eventId","source","kind","subject","referralCode","reference","planId","amountCents","currency","mode"];
  if (!input || Object.keys(input).some(k => !fields.includes(k)) ||
      input.version !== 1 || !["baker","supplier"].includes(input.source) ||
      !["registration","payment"].includes(input.kind) ||
      !/^evt-[a-f0-9]{32}$/.test(input.eventId || "") ||
      !/^sub-[a-f0-9]{32}$/.test(input.subject || "") || !CODE.test(input.referralCode || "") ||
      input.mode !== "test") throw new Error("Invalid test referral event");
  if (input.kind === "registration" && ["reference","planId","amountCents","currency"].some(k => k in input))
    throw new Error("Unexpected registration payment fields");
  const plans = { baker: ["bakerMonthly","bakerAnnual"], supplier: ["supplierDirectory","supplierGrowth","supplierFeatured"] };
  if (input.kind === "payment" && (!plans[input.source].includes(input.planId) ||
      !/^[A-Za-z0-9_-]{6,150}$/.test(input.reference || "") || input.currency !== "ZAR" ||
      !Number.isSafeInteger(input.amountCents) || input.amountCents <= 0 || input.amountCents > 100000000))
    throw new Error("Invalid test payment");
  return Object.fromEntries(fields.filter(k => k in input).map(k => [k,input[k]]));
}
const digest = input => crypto.createHash("sha256").update(JSON.stringify(validateEvent(input))).digest("hex");
module.exports = { CODE, validateEvent, digest };
