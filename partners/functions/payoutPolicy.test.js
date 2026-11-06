"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { isPayoutEligible, sumPayout, validPayoutChange } = require("./payoutPolicy");
const approved = { status: "approved", paymentVerification: "provider-verified", payable: true, commissionCents: 3750 };
test("only approved real provider-verified commissions are eligible", () => {
  assert.equal(isPayoutEligible(approved), true);
  for (const v of [
    { ...approved, status: "pending" }, { ...approved, status: "paid" },
    { ...approved, status: "reversed" }, { ...approved, payable: false },
    { ...approved, paymentVerification: "emulator-test" },
    { ...approved, paymentVerification: undefined },
    { ...approved, payoutRequestId: "already-locked" },
    { ...approved, commissionCents: -100 },
    { ...approved, commissionCents: 0 },
    { ...approved, commissionCents: 1.25 }
  ]) assert.equal(isPayoutEligible(v), false);
});
test("payout total is derived exclusively from verified ledger records", () => {
  assert.equal(sumPayout([approved, { ...approved, commissionCents: 7980 }]), 11730);
  assert.throws(() => sumPayout([approved, { ...approved, payable: false }]));
});
test("requests cannot be self-approved or auto-paid", () => {
  assert.equal(validPayoutChange("requested", "approved"), true);
  assert.equal(validPayoutChange("requested", "rejected"), true);
  assert.equal(validPayoutChange("approved", "paid"), true);
  assert.equal(validPayoutChange("requested", "paid"), false);
  assert.equal(validPayoutChange("rejected", "paid"), false);
  assert.equal(validPayoutChange("paid", "requested"), false);
});
