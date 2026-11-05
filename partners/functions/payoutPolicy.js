"use strict";
// Only independently provider-verified, administrator-approved first-sale
// commissions can be requested. Emulator/test credits are never payable.
const MAX_PAYOUT_ITEMS = 75;
function isPayoutEligible(v) {
  return Boolean(v && v.status === "approved" &&
    v.paymentVerification === "provider-verified" &&
    v.payable === true &&
    !v.payoutRequestId &&
    Number.isSafeInteger(v.commissionCents) && v.commissionCents > 0);
}
function sumPayout(commissions) {
  let cents = 0;
  for (const item of commissions) {
    if (!isPayoutEligible(item)) throw new Error("Commission is not eligible for payout");
    cents += item.commissionCents;
    if (!Number.isSafeInteger(cents)) throw new Error("Invalid payout total");
  }
  return cents;
}
function validPayoutChange(current, next) {
  return (current === "requested" && ["approved", "rejected"].includes(next)) ||
    (current === "approved" && next === "paid");
}
module.exports = { MAX_PAYOUT_ITEMS, isPayoutEligible, sumPayout, validPayoutChange };
