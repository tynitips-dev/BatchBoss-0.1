# BatchBoss launch-pricing implementation gate (not enabled)

## Existing verified billing
- Monthly: R149 per recurring month, plan code configured through PAYSTACK_MONTHLY_PLAN_CODE.
- Annual: R1,199 per recurring year, plan code configured through PAYSTACK_ANNUAL_PLAN_CODE.
- Keep both existing plans and subscriptions intact.

## Proposed launch campaign (requires approval before launch)
- Monthly introductory offer: R99 per month through 31 December 2026, then R149/month from the first renewal on or after 1 January 2027.
- Annual introductory offer: R990 for the first 12 months. Confirm and publish the year-two renewal charge and terms before implementing checkout.
- Do not state that discounted pricing is active until both checkout and backend payment verification work.

## Technical constraints
- Paystack recurring plan codes have fixed amounts. Do not change a regular plan's price while existing subscriptions depend on it.
- Create **separate** live promotional plan codes through the Paystack dashboard, with matching amounts in cents (R99=9900; R990=99000) and correct intervals, after the promotion terms are final.
- A plan-code switch alone is not sufficient: renewal price transitions need an explicit, tested provider-supported migration or a non-auto-renewing first-period checkout followed by user-authorized recurring enrolment. Never silently change a subscriber's recurring payment mandate.
- Extend the server-side pricing resolver to distinguish regular versus promotional plans and validate amount, plan code, mode and interval. Update settle(), syncSubscription(), payment locks, subscription records and webhook reconciliation to accept only legitimate expected plan versions while continuing to support existing customers.
- Return an offer identifier, server-validated price, renewal disclosure and eligibility in getPaystackPlans; the web UI should reflect the backend's authoritative price and full renewal terms, not hardcode offers.
- Enforce launch offer eligibility and deadlines on the server, including South Africa timezone (Africa/Johannesburg). Define whether the intro monthly discount applies to *new purchases* only or to *all monthly renewal charges* through the deadline.
- Preserve duplicate-charge prevention, idempotency, subscription ownership, test/live separation, and admin promo overrides.
- Test using Paystack test-mode promo equivalents before deploying live changes, including renewal after promo, cancellations, webhooks, customer access and expiry.
- Follow release order: deploy backend first, then portal; verify a low-risk end-to-end purchase. Do not delete existing live plan codes or secrets.

## Current change
- SubscriptionView now displays regular monthly and annual prices from getPaystackPlans() (with existing regular-price fallback), so future verified prices can be returned safely.
- This commit does not create promo plans, alter live prices, or activate an offer.
