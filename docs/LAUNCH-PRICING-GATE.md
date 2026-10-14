# BatchBoss launch-pricing implementation gate (not enabled)

## Existing verified billing
- Monthly: R149 per recurring month, plan code configured through PAYSTACK_MONTHLY_PLAN_CODE.
- Annual: R1,199 per recurring year, plan code configured through PAYSTACK_ANNUAL_PLAN_CODE.
- Keep both existing plans and subscriptions intact.

## Proposed launch campaign (requires approval before launch)
- Monthly introductory offer: R99 per month through 31 December 2026, then R149/month from the first renewal on or after 1 January 2027.
- **Approved annual offer:** R990 for the first 12 months, automatically renewing at **R1,199 per year** from the second billing year unless cancelled. Display this prominently before payment, require explicit acceptance of the recurring terms, and record consent and the original offer on the server.
- Do not state that discounted pricing is active until both checkout and backend payment verification work.

## Technical constraints
- Paystack recurring plan codes have fixed amounts. Do not change a regular plan's price while existing subscriptions depend on it.
- Create **separate** live promotional plan codes through the Paystack dashboard, with matching amounts in cents (R99=9900; R990=99000) and correct intervals, after the promotion terms are final.
- A plan-code switch alone is not sufficient: annual renewal price transitions need a provider-supported, tested implementation. Paystack documents updating a **plan** (optionally affecting all subscriptions on that plan), not a safe per-subscriber automatic plan swap. Avoid shared-plan repricing that can change unrelated customers' mandates. If a safe per-subscriber transition is not supported, use an alternative verified billing architecture and clearly explain/consent to automatic renewal before collection. Never silently change a subscriber's recurring payment mandate.
- Extend the server-side pricing resolver to distinguish regular versus promotional plans and validate amount, plan code, mode and interval. Update settle(), syncSubscription(), payment locks, subscription records and webhook reconciliation to accept only legitimate expected plan versions while continuing to support existing customers.
- Return an offer identifier, server-validated price, renewal disclosure and eligibility in getPaystackPlans; the web UI should reflect the backend's authoritative price and full renewal terms, not hardcode offers.
- Enforce launch offer eligibility and deadlines on the server, including South Africa timezone (Africa/Johannesburg). Define whether the intro monthly discount applies to *new purchases* only or to *all monthly renewal charges* through the deadline.
- Annual pre-checkout disclosure: **'Pay R990 today for your first 12 months of BatchBoss Pro. Your subscription will automatically renew at R1,199 every 12 months thereafter unless you cancel before renewal. You can manage or cancel your subscription at any time.'** Display renewal date when known, and never charge a renewal before the disclosed billing cycle.
- Preserve duplicate-charge prevention, idempotency, subscription ownership, test/live separation, and admin promo overrides.
- Test using Paystack test-mode promo equivalents before deploying live changes, including renewal after promo, cancellations, webhooks, customer access and expiry.
- Follow release order: deploy backend first, then portal; verify a low-risk end-to-end purchase. Do not delete existing live plan codes or secrets.

## Current change
- SubscriptionView now displays regular monthly and annual prices from getPaystackPlans() (with existing regular-price fallback), so future verified prices can be returned safely.
- This commit does not create promo plans, alter live prices, or activate an offer.

## Verified Paystack approach (documentation reviewed 14 October 2026)

- Paystack supports a **one-off transaction** without a plan code, then a separate `POST /subscription` using the customer's paid card authorization, the **existing regular R1,199 annual plan**, and `start_date` set to the first anniversary. See https://paystack.com/docs/payments/subscriptions/ and https://paystack.com/docs/api/subscription/.
- This is a **proposed architecture, not yet deployed**. A one-off charge by itself is not proof the future subscription was scheduled. Never claim automatic renewal is configured until Paystack returns a confirmed annual subscription and the backend records its code/status.
- Step 1: record consent, offer terms, price, account UID, reference and expected renewal date on the server BEFORE initiating a R990 **no-plan** checkout. Reject any client-supplied amount.
- Step 2: verify the paid transaction through Paystack server-side; validate amount=99000, currency=ZAR, live/test domain, customer mapping, reference and successful card authorization. Use a separate reconciliation path from the existing plan-coded settlement, which deliberately rejects mismatched plans.
- Step 3: create a future-start R1,199/year Paystack subscription with `start_date` on the anniversary and explicit authorization chosen from the initial verified payment. Check with Paystack that annual scheduling and date semantics behave as expected in the SA account before activation.
- Step 4: record subscription code and payment reference in a durable, retry-safe state machine; handle Paystack API failure, duplicate webhook delivery, cancellation before future debit and partial setup. **Do not** schedule multiple subscriptions on retries. A user who has paid R990 but whose auto-renew setup failed must be surfaced to support, not silently considered fully set up.
- Step 5: grant first-year Pro only once first-year payment settles successfully, while accurately showing renewal-setup status to the customer. Don't imply all renewal setup is complete if it isn't.
- Step 6: reconcile charge.success and invoice events with the correct plan, billing interval and subscription owner, including reminders before the first higher-price renewal and expiry if payment fails.
- Existing paid monthly/annual plans and subscriptions must remain unaffected. Avoid changing the price of any existing Paystack plan.

## API citations
- https://paystack.com/docs/payments/subscriptions/
- https://paystack.com/docs/api/subscription/
- https://paystack.com/docs/payments/recurring-charges/

## Entitlement-state separation added on 14 October 2026

- When the first-year R990 transaction is independently verified, its payment record now stores `firstYearAccessUntil` (the first anniversary) and `entitlementStatus: awaiting_renewal_setup`.
- After a test-mode renewal subscription is confirmed, the payment record moves to `entitlementStatus: test_verified_only`. This is an audit trail **not** a grant of production Pro access.
- **Remaining blocking work:** define and implement a production-only, transactional user profile grant after the first payment and confirmed safe renewal arrangement; reconcile cancellation and invoices; ensure already-active, admin-promoted and existing-plan accounts cannot be overwritten; prohibit renewal if authorization is revoked. Current promotional functions deliberately refuse live mode. Never enable production offer flags on this implementation.
- The new `firstYearAccessUntil` field is only a calculated record value. It must not be treated as an entitlement without a verified live payment and explicit guarded profile update.

## Monthly offer rules, committed 14 October 2026
- Monthly marketing proposal: R99 per month for eligible monthly charges processed through 31 December 2026 in Africa/Johannesburg; R149 per month for charges on or after 1 January 2027.
- The pure pricing helper `monthlyChargeCents` computes the amount using the Johannesburg calendar day and `validateMonthlyConsent` enforces versioned renewal disclosure.
- `getMonthlyLaunchQuote` currently returns `enabled:false` and `createMonthlyLaunchCheckout` refuses checkout even if a caller sets the feature parameter true. **No R99 payments are enabled.**
- A discounted recurring plan alone does not meet the requirement to charge R149 after the promotion, so provider billing transition plus payment verification must be completed and tested first. Do not adjust the existing standard monthly Paystack plan or its subscribers.
- Tests: `npm run build --prefix functions && node --test tests/monthly-launch.cjs`.

## Integrated billing audit, 14 October 2026

**Release remains blocked.** This PR is a development-only preparation, not a complete payment launch.

1. Test-only Paystack cancellation reconciliation now looks up the subscription by code, compares its customer, plan and environment with the payment record, calls Paystack's `POST /subscription/disable` using the provider-issued email token if needed, then fetches the subscription again before marking provider confirmation. It never claims success based solely on our own cancellation request. No live promotional cancellation is permitted.
2. Annual launch Pro activation is not implemented. Verified test transactions must never set users to paid. Production activation must run as an idempotent Firestore transaction only after verified live charge and carefully reconciled renewal arrangement, preserving admin promotional access and existing subscriptions.
3. Monthly R99 to R149 transition is **not implemented** at Paystack. The existing monthly R149 recurring plan must not be changed to R99. A testable transition mechanism is required before offering monthly discounts.
4. Full tests needed for duplicate webhooks, webhook order reversals, Paystack network timeouts, provider accepted/local write failed, cancellation concurrent with subscription POST, subscription end states, expiry and renewals, impersonation, and missing/invalid email token.
5. Test-mode integration must verify Paystack actually respects `start_date` for deferred first renewal on the relevant account, and that provider `status` values match our confirmation logic.
6. Before release: run `npm run lint --prefix functions && npm run build --prefix functions && node --test tests/annual-launch.cjs tests/monthly-launch.cjs tests/paystack-flow.cjs && npm run build --prefix web`, inspect any failures, and validate against real Paystack test-mode transactions. No promotion switch or production deploy until those gates pass.

Relevant Paystack docs: https://paystack.com/docs/api/subscription/ and https://paystack.com/docs/payments/subscriptions/.
