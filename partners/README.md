# BatchBoss Sales Partners (development pilot)

A separate, BatchBoss-branded **partners.batchboss.co.za** workspace for:
- Applications and approvals controlled by a trusted admin.
- Personal agent dashboards showing leads and verified commissions.
- Permission-confirmed baker and supplier lead registration, ownership checks, and duplicate prevention by business email per segment.
- Sales follow-ups that agents can update without editing paid-sale status.
- Manual first-payment verification, first-sale-only commissions, commission approvals and payout audit notes.
- Independent partner admin console.

## Current status

**Development only; not deployed.** The frontend uses CLEARLY LABELED sample data when Firebase is not configured. No sample data is written to Firestore and buttons that would save information are disabled.

The recommended *pilot policy* is 25% of a newly referred baker's first successful paid subscription, or 20% of a newly referred supplier's first successful paid subscription. These are **provisional**, controlled in functions/commissionPolicy.js; not a binding programme offer. No renewal commissions are implemented.

**IMPORTANT:** The sharing link on the dashboard uses https://batchboss.co.za/?ref=CODE but the main baker checkout and separate supplier checkout do NOT currently capture or automatically attribute this parameter. Sales may only be credited by manual, independent admin verification using a registered lead. Do not advertise automatic referral attribution yet.

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
4. Admin checks a successful REAL ZAR first payment in merchant records, then records provider, payment reference and actual amount.
5. Backend enforces unique first-sale claim per lead and unique payment reference, applies plan-specific provisional basis points, and creates a *pending* commission.
6. Admin approves commission. After transferring funds outside the portal, admin marks Paid with an audit note.

**Manual "paid" is a record, not a payment transfer.** Refunds, chargebacks, and paid reversals require reconciliation outside the portal. Add real verified payment webhooks and reconciliation before automatic crediting or payouts.

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
