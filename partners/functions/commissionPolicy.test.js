"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { calculateCommission, canTransition, validateBusinessEmail, publicPartner } = require("./commissionPolicy");

test("baker commission uses 25% on verified gross cents", () => {
  assert.equal(calculateCommission(14999, "bakerMonthly"), 3750);
  assert.equal(calculateCommission(119900, "bakerAnnual"), 29975);
});
test("supplier commission uses 20% and rounding", () => {
  assert.equal(calculateCommission(39900, "supplierGrowth"), 7980);
  assert.equal(calculateCommission(79900, "supplierFeatured"), 15980);
});
test("invalid prices and plans are rejected", () => {
  for (const amount of [-1, 0, 1.25, NaN, Number.MAX_SAFE_INTEGER]) {
    assert.throws(() => calculateCommission(amount, "bakerMonthly"));
  }
  assert.throws(() => calculateCommission(12000, "unknown"));
});
test("commission state machine forbids skipping approval or unpaying", () => {
  assert.equal(canTransition("pending", "paid"), false);
  assert.equal(canTransition("pending", "approved"), true);
  assert.equal(canTransition("approved", "paid"), true);
  assert.equal(canTransition("paid", "pending"), false);
  assert.equal(canTransition("paid", "reversed"), true);
});
test("email and public partner fields are restricted", () => {
  assert.equal(validateBusinessEmail("buyer@example.co.za"), true);
  assert.equal(validateBusinessEmail("not-an-email"), false);
  assert.deepEqual(Object.keys(publicPartner({ fullName: "Test", status: "approved", secret: "hidden" })).sort(),
    ["email", "fullName", "province", "referralCode", "status"].sort());
});
