# Referral attribution checkpoint — 29 October 2026

Development only. No deployment, live payment request, entitlement activation or payout was performed.

## Implemented
- A strict versioned event contract for baker and supplier registration and test payment events.
- Random opaque supplier subjects stored only in server-owned supplierPartnerReferrals; no account IDs or emails in referral events.
- Emulator-only supplier referred signup, atomic application/mapping/outbox creation and immutable first-touch ownership. Existing accounts cannot attach a signup referral.
- Supplier test checkout copies server-owned attribution into its private intent. Verified Paystack TEST payments atomically create a private outbox event with the receipt. Retry does not duplicate the event.
- Paystack metadata no longer includes the supplier account ID. Attribution stays out of processor metadata.
- Emulator-only, verified-partner-admin receiver resolves approved partner codes and creates a separate nonpayable test first-sale record. Payment replay, changed payloads, ownership changes, renewals and out-of-order delivery are handled.
- Default-deny Firestore rules are unchanged. No new partner or supplier client collection access.
- Partner email verification now refreshes its token; prototype-inherited plan names are rejected.
- Existing BatchBoss layout and styles are retained.

## What is NOT complete
The central receiver and supplier producer are tested together using actual handler code with an in-memory transactional Firestore adapter and mocked Paystack responses. This is **not** Firebase emulator integration validation or a real provider test.
There is no automatic cross-project outbox dispatcher. Pending events are not delivered by a deployed worker. The only central receiver is omitted from production exports.
The baker repository contains Android authentication and a web data demo, but no web registration/subscription checkout handler. Baker event contract tests are synthetic; no baker registration/payment integration is claimed.
No real commission is earned by test events. partnerTestFirstSales is separate from partnerCommissions. No payout function is added.

## Required gates
1. Locate the actual baker web registration and billing source, including a trusted verified-payment hook. Bind signup attribution on the server before checkout; do not trust browser payment success or an arbitrary customer ID.
2. Agree on a trusted cross-project dispatcher/receiver identity, least-privilege permissions, per-source authentication, replay retention, retries and reconciliation. Do not open the test admin callable to browser customers.
3. Validate approved partner codes against the partner authority before production signup capture; current offline test capture only validates format and receiver approval.
4. Exercise Auth, Firestore rules, transaction retries and Functions in isolated demo projects/emulators, then browser-test signup and failed signup recovery.
5. Approve prices, attribution windows, existing-customer eligibility, rates, refunds and chargebacks before any payable ledger integration.
6. Keep production deployment, live credentials, payouts and real applicant onboarding off until these gates pass.

## Local pilot configuration
The supplier referral form appears only with VITE_PARTNER_REFERRAL_EMULATOR=true on localhost/127.0.0.1.
That flag additionally requires a demo- Firebase project and connects Auth (9099), Firestore (8080) and the signup function (5001) to localhost emulators.
It does not enable an automatic event dispatcher. Do not set this flag in a public build.

## Validation
Node behavioral tests cover both event segments, approval checks, first-touch conflicts, duplicate/concurrent delivery, payment reference conflicts, event privacy, supplier signup retries, checkout metadata privacy and live-secret rejection.
A local cross-repository test executes supplier signup -> checkout initialization -> mocked server verification -> outbox -> partner test ingestion.
No network calls, production Firebase data or merchant secrets are used in these tests.
