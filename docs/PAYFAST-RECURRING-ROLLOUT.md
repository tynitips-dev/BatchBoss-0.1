# Payfast recurring billing draft

This branch is not deployment-ready. PAYFAST_ENABLED defaults to false. Do not merge or deploy against production until reconciled with the latest deployed source (main's Paystack catalog is older than the reported deployed catalog). Existing Paystack agreements must continue renewing through Paystack. No live payment or migration is part of this change.

## Proposed catalog
Weekly R39.99, monthly R149.99, annual R1199.00: confirm monthly price since main currently contains R149.00. Do not silently change existing agreements. Promotional offers retain existing Paystack terms; do not enable a Payfast promotional checkout until cycle limits and end-of-promotion terms are verified. Directory, Full Suite, supplier plans (separate Firebase project) and once-off marketplace listings require separate entitlement mapping. Do not use Pro activation for these purchases.

## Before production
- Record the exact production branch, project, function exports, Firestore rules and catalog; merge changes without losing newer billing functions.
- Test TypeScript build and Firebase emulator; create a separate Payfast sandbox implementation/configuration so test notifications never grant live access. Test signature input order, blank fields, merchant ID, Payfast server validation and trusted ingress source-IP check behind Firebase proxies using documented Payfast IP ranges. Do not trust client-provided forwarding headers.
- Test first charge, next billing date from Payfast API, weekly/monthly/yearly boundaries and month end, duplicate/out-of-order ITNs, cancel before first payment, provider retries, failed or locked renewal, grace/access expiry and competing gateway checkouts.
- Implement provider API fetch and self-service cancellation (verify successful cancellation before updating local state), card update link, scheduled reconciliation, failed-payment and cancellation emails. CANCELLED ITNs for linked tokens are recorded, but failed debit notifications and missing IDs require real provider verification. Never send a second charge from Firebase.
- Resolve abandoned checkout lock safely by confirming provider agreement cannot be created; do not simply expire a lock while a signed checkout remains usable.
- Backfill historic Payfast tokens/transaction IDs, reconcile payment times and conflicts before enabling. Never count legacy duplicate or review records as new payable revenue.
- Render owner payment history and admin dashboard from the new callable functions, using proper pagination and separate currency totals. Receipt confirmation is not a tax invoice; verify Resend and receipt retries.
- Partner attribution: existing main has no verified payable commission mapping. Use server-validated approved partner and binding, versioned rate and eligibility, transaction-ID deduplication, refund/chargeback adjustments and admin approval. Do not pay commissions until programme rules and production partner integration are confirmed.
- International: Payfast recurring amount is documented in ZAR. Confirm recurring foreign currency support with Payfast. No fixed foreign prices until approved. Never submit a foreign number as a ZAR charge. Keep customer display and authorized billing amounts explicit.
- Store PAYFAST_MERCHANT_ID, PAYFAST_MERCHANT_KEY, PAYFAST_PASSPHRASE and email secret in Firebase Secret Manager, never commit them. Enable the flag only after approval and end-to-end tests.

References: https://developers.payfast.co.za/docs ; https://support.payfast.help/portal/en/kb/articles/multi-currency-processing
