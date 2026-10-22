# BatchBoss Pro entitlement release gate

**Status: DRAFT / NOT APPROVED FOR PRODUCTION**

This document covers the separate safety branch and draft PR #25. Passing unit tests and demo-emulator tests alone do not authorize production changes.

## Current automated coverage
- Functions TypeScript compile/lint; pure entitlement and Paystack signature/payment matching tests.
- Demo Firestore emulator: read-only customer checks, verified-grant compatibility mirroring without migration marker, and revoked gift protection.
- Android debug unit tests in CI, including customer/recipe sync with isolated mock HTTP server.

## Remaining mandatory gates
- [ ] Reconcile all 5 user local source archives with GitHub (functions, app, web, admin-web, tests), inspect changes and exclude secrets.
- [ ] End-to-end **Paystack and PayFast webhook** tests covering signature/ITN validation, valid payment, duplicate delivery, retries after recomputation failure, concurrent payments, invalid amount/plan/currency and refunds/cancellations.
- [ ] Demo-emulator migration-enabled dry-run, with known and mixed legacy profiles; test batch size, write counts, rollback and no-expiry PayFast.
- [ ] Verify web portal, admin promotion, promo redemption, and Android clients against the same backend shape and Firestore security rules.
- [ ] Read-only, privacy-preserving production inventory of status/source/expiry cohorts; reconcile with independent payment processor records without printing customer data.
- [ ] Produce backups, staged canary population, alerting thresholds, backout procedure and explicit written deploy/merge authorization.
- [ ] Review branch/base merge conflicts and obtain final human review of this cross-product feature.

## Hard safety constraints
- `PRO_ENTITLEMENT_MIGRATION_ENABLED` is **false by default**. Do not enable for any customer environment without the separate migration approval.
- Do not run `firebase deploy`, merge the draft PR, write production Firestore accounts, or edit real Paystack/PayFast payment records without explicit approval.
- All tests and samples use demo projects or synthetic accounts.
- A successful CI job must never be described as payment-provider or production certification.

## Gate ownership
Developer may continue non-production development and automated tests. Owner approval is required at the production-change boundary.
