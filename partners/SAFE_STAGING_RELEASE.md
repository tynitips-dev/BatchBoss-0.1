# Sales Partners: secure staging and payment release gates

Status: **NOT APPROVED FOR PRODUCTION OR XNEELO PUBLIC ACCESS**
Updated: 2026-11-02

This is a release-runbook draft. It authorises no credential access, merge, DNS change, live provider request, agent onboarding, or payout.

## Source of truth

- Partner portal / private ledger: BatchBoss-0.1 PR #38.
- Safer test-only reconciliation and least-privilege handoff: PR #48, stacked on #38.
- Baker account/referral + Paystack TEST settlement: PR #39, stacked on Android/web alignment branch. Its CI pins the exact partner-bridge commit. Do **not** assume it can merge to main independently.
- Supplier private signup and TEST settlement: BatchBoss-suppliers PR #9.
- Each project uses separate Firebase Auth and Firestore data; never copy customer or supplier UID to partner-facing endpoints.

## Required verified-payment production architecture (not implemented)

1. **Registration:** The bakery/supplier *source backend* checks that a referral code is approved with the central partner authority before storing an immutable, scope-tagged, server-owned attribution record. Reject existing accounts, expired or suspended links, and conflicting lead claims according to a published policy.
2. **Checkout:** Snapshots the attribution on the backend. The price, currency, plan, mode, provider customer reference, and partner subject come from trusted records, not browser input.
3. **Provider settlement:** In the owning source backend, verify Paystack with a server-to-server transaction lookup or validated raw-body webhook signature. PayFast must use its independently verified ITN/transaction process; do not equate a checkout redirect, an admin checkbox, or emailed proof with settlement.
4. **Outbox:** In the same atomic Firestore transaction that records the verified receipt, create an opaque, versioned, immutable event keyed deterministically from source/provider/reference and event type. Emit no account IDs, contact emails, provider tokens, provider secrets, or sensitive invoice data.
5. **Delivery:** Use a dedicated per-source machine identity and a **private** authenticated production receiver, not an administrator or a public client-callable function. Narrow its IAM to the one receiver and project. Use a durable task/outbox with bounded retries, exponential backoff, dead-letter inspection and idempotent acknowledgements. Do not reuse the emulator-only dispatcher. Enforce expected workload identity **and** expected source at the receiver.
6. **Partner ledger:** Verify owner was eligible when the signup was attributed and first successful *live* charge occurs; match plan, currency and paid amount against source-owned, signed/verified facts. Atomically deduplicate provider receipts, registration subjects, first-sale claims and commission event IDs. Store the rate/version captured by the approved policy. Mark pending until reconciliation or cooling-off checks are complete. **Never create payable commissions from test receipts.**
7. **Reversals:** Verify actual provider refund/chargeback events server-side. Link to the original receipt and hold pending commission or create an auditable offset/negative adjustment for a previously paid commission. Do not silently overwrite a paid historical record. Support partial refunds only after specifying an exact proportion/net-tax policy; hold ambiguous events.
8. **Finance:** Admin approve/mark-manually-paid controls are valid only on trusted ledger entries; require payout reference, double-check permissions, export/reconciliation trail, and separation between service bridge and admin. No automated money movement.
9. **Operational recovery:** Reconcile ledger against provider receipts and billing subscriptions after outages, with metrics for stuck outbox, unknown references, duplicate attempts, provider mismatches, stale approvals, chargebacks and negative adjustments. Disable incoming production commission processing if invariants fail.

## Security review and acceptance gates

- [ ] Dedicated **staging**, isolated from `batch-boss-android` and `batchboss-suppliers` production Firebase projects. Configure verified admins, deployment roles and separate service identities.
- [ ] All current draft branches reconciled with Android/web alignment and entitlement-migration security baselines; no regression in existing bakery/supplier service.
- [ ] Firebase rules remain default-deny for untrusted clients. Per-owner callable reads; emails never exposed across partner accounts. Rate limits and App Check applied where relevant, with abuse tests.
- [ ] Real Paystack TEST-mode end-to-end via staging providers; reject mismatched amount/currency/domain, forged callbacks and unauthorized user mapping. PayFast independently tested if supported.
- [ ] Cross-project staging registration, first charge, duplicate webhook, retries, delayed registration, suspended agent, canceled subscription, refund and chargeback scenarios on real Firebase services. **Current emulators and synthetic Paystack mocks do not satisfy this.**
- [ ] Automated CI + security checks green on final coordinated commits, including baker Android sign-in and a real Android device, supplier browser and partner admin/agent browser.
- [ ] Confirm referral attribution window, existing-customer eligibility, lead ownership/disputes, commission calculation tax basis, promotional package treatment, commissions on renewals, clawback, payout timing, minimum payout threshold and termination terms.
- [ ] South African labour/employment and POPIA/privacy notices, consent, retention/deletion and contractual approvals obtained. Code review does **not** constitute these approvals.

## Private Xneelo staging readiness (do not perform until above engineering gates)

1. Choose staging hostname and account access model. `partners.batchboss.co.za` must not become a public recruitment site by accident. Prefer temporary access controls/private review while validating.
2. Confirm Xneelo subdomain document root, DNS ownership, desired hosting route and existing HTTPS certificates without changing other BatchBoss sites.
3. Build static partner website from **specific reviewed commit**. Use only PUBLIC Firebase web identifiers and approved API origin; no server secret or service account in JS bundle or Xneelo web root.
4. Configure dedicated Firebase authorized domains, CORS, backend functions, region and staging credentials. Verify no emulator flags, localhost targets, source maps with secrets, open redirect or real live checkout link are shipped.
5. Upload static site to its isolated staging root; create the domain/DNS/HTTPS only after explicit owner approval. Check certificate, security headers, page loading, private access, email verification, restricted admin, no unauthenticated Firestore access and mobile display.
6. Record deployment SHA, integrity, DNS TTL, old static build copy, owner, test evidence, and rollback owner before opening invitations.

## Rollback / kill switches

- Stop agent recruitment and disable registration/approval first.
- Disable only the new referral-event receiver or production commission-ingest flag, not existing baker/supplier checkout or subscription access.
- Keep source outbox events retained for later reconciliation. Do not drop events or write irreversible payout status.
- Restore the versioned partner website bundle and roll back DNS if hosting cutover fails, verifying certificate and correct document root afterwards.
- Preserve immutable commission and reconciliation audit logs; do not delete or reset verified payment records.
- Re-run pending events only after source verification and idempotency check.

## Current decision

**NO-GO:** Existing code covers a dashboard and secure *emulator-only* first-sale / full-reversal checks. The production verified-payment-to-ledger receiver, source refund/chargeback producers, durable deliveries, independent-hosted acceptance, live payment verification, privacy/legal signoff and Xneelo private hosting are incomplete. Owner should not log in to Xneelo yet.
