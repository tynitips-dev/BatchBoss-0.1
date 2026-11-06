# BatchBoss Sales Partners (development pilot)

A separate, BatchBoss-branded **partners.batchboss.co.za** workspace for:
- Applications and approvals controlled by a trusted admin.
- Personal agent dashboards showing leads and verified commissions.
- Permission-confirmed baker and supplier lead registration, ownership checks, and duplicate prevention by business email per segment.
- Sales follow-ups that agents can update without editing paid-sale status.
- Test-only source-verified first-payment ingestion with nonpayable credits, first-sale ownership checks, and full refund/chargeback reconciliation. Production commissions require a trusted payment bridge.
- Independent partner admin console.
- Agent **Request Payout** action: the agent never selects a payout amount or accesses customer payments/refunds. Only approved, explicitly provider-verified, payable commissions can be requested. A transaction locks the eligible ledger records to one open request.
- Admin payout approvals and separate **record completed external transfer** step. This system does NOT move money; final paid status requires an admin-confirmed outside payment reference and audit record.

## Current status

**Development only; not deployed.** The frontend uses CLEARLY LABELED sample data when Firebase is not configured. No sample data is written to Firestore and buttons that would save information are disabled.

The recommended *pilot policy* is 25% of a newly referred baker's first successful paid subscription, or 20% of a newly referred supplier's first successful paid subscription. These are **provisional**, controlled in functions/commissionPolicy.js; not a binding programme offer. No renewal commissions are implemented.

**IMPORTANT:** Companion baker and supplier development branches have emulator-only referral capture and verified TEST settlement, but production customer attribution is **not connected**. The partner preview does not generate a live merchant-ready referral link. Do not advertise automatic tracking or payable commissions.

The supplier repository currently has an isolated test-only Paystack payment path; do **not** assume supplier live checkout is ready or credit test transactions.

## Structure

- web/ - standalone HTML, CSS and native JavaScript frontend, mobile responsive.
- web/config.js - PUBLIC Firebase web identifiers (currently intentionally blank).
- functions/ - Firebase v2 Callable Cloud Functions with Admin SDK access only.
- functions/commissionPolicy.js - provisional plan rates and state transitions.
- functions/commissionPolicy.test.js - Node built-in unit tests.
- firestore.rules - default-deny. All personal/sales data are available only through role-checked backend functions.
- firebase.json - separate hosting, callable functions and Firestore rules.

## Local preview

Serve partners/web from a local static web server and open index.html. With blank public configuration, the site displays a visual sample; no login or persistent data is enabled.

To run isolated policy tests with Node.js 22:

    cd partners/functions
    npm test

Production runtime setup requires a separate and approved Firebase project, Node.js 22, Email/Password Auth, verified account emails, Firestore, Cloud Functions, and Firebase Hosting (or deploy web/ to Xneelo with an explicit config.js). From partners/, configure Firebase CLI for the *new* project, set the actual public web config in web/config.js, run npm install inside functions/, validate rules/functions in an emulator, then deploy only following approval.

**Never deploy this config against batch-boss-android or batchboss-suppliers as a shortcut.** Establish a cross-project security and payment integration plan first.

## Admin bootstrap

An authorised backend operator must grant the custom claim
    { "batchbossPartnerAdmin": true }
to a verified admin Firebase Authentication user via a trusted one-off Firebase Admin SDK operation. No admin claim or commission permission can be created or modified by an ordinary browser client. Audit who received the claim and refresh their token before sign-in.

## Pilot workflow

1. Candidate creates account, verifies email, completes programme application.
2. Admin opens the partner portal, approves applicant; a random referral code is reserved.
3. Partner enters a baker/supplier lead only where permitted to provide the business email.
4. In the test-only emulator, the baker/supplier backend verifies a TEST Paystack charge and delivers an authenticated private event with an opaque referral subject.
5. The test receiver checks the partner code, immutable subject ownership and first successful payment reference, creating one **NONPAYABLE** credit. A verified full refund/chargeback test event reverses it with effective commission zero; duplicate deliveries cannot pay twice.
6. The admin browser **cannot** create a payable commission by ticking a verification checkbox. A trusted production provider integration and signed cross-project delivery must be reviewed and deployed before genuine commissions can be created.
7. Following provider verification and individual commission approval, a partner may request the server-computed eligible balance. One open request at a time prevents double claims. Emulator and test credits do not meet payout eligibility.
8. Finance may approve or reject a requested payout. Rejection unlocks its commissions. Approval alone never pays.
9. After verifying a real external bank transfer, an admin records its reference and note; the same Firestore transaction marks the linked commissions and request paid. No funds are sent by the software.

**Manual "paid" is a record, not a payment transfer.** New manual payment-credit creation is explicitly blocked. The isolated partner test receiver supports idempotent full reversals; baker and supplier source systems do not yet emit refund events. Partial refunds must be held for policy review. All real refunds, chargebacks and payout adjustments require a production reconciliation service.

## Payout implementation safeguards

- Existing records do **not** become payable merely because their status is approved: `paymentVerification` must equal `provider-verified` and `payable` must be `true`, set exclusively by a future audited trusted settlement bridge.
- The request API accepts **no client amount, commission IDs or recipient details**. The server selects commissions owned by the authenticated partner, checks eligibility again inside a Firestore transaction and attaches each to the request.
- Admin review, completed-transfer attestation and audit logs are role-restricted; regular agent actions cannot refund, reverse, approve or mark paid.
- A reversal during a payout request blocks approval or paid recording until the request is rejected and reconciled.
- Maximum 75 commissions per request and 200 records per account view; accounts above these bounds require finance reconciliation and pagination before commercial use.

## Production gates (not yet complete)

- Labour-law review of independent sales partner arrangements, commission-only remuneration, partner agreement, and supplier representation.
- POPIA marketing/contact-permission review, privacy notice, retention and data deletion process.
- Choose and secure dedicated Firebase project, connect its Auth, Functions, Firestore, Hosting and authorised admin.
- Configure correct DNS/SSL for partners.batchboss.co.za.
- Implement referral capture from baker checkout and supplier checkout, server-to-server payment verification and cross-Firebase identity mapping. Do not expose supplier internal IDs.
- Decide attribution windows, lead ownership conflicts, existing-customer eligibility, renewals, VAT/net-vs-gross basis, reversals, payouts, export/tax documentation and what happens when agents leave.
- Add pages/exports for >200 records, pagination, rate limiting, App Check, audit retention, thorough integration and emulator tests.
- Conduct a controlled internal pilot before inviting real applicants.

None of these steps touches the live websites, billing or existing customer records.
