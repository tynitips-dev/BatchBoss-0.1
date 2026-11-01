# Recipe repairs and integration checkpoint — 1 November 2026

Based on the recorded published baker website source, PR #41 at 6003dfa. This inherits the reconciled Android source in #40 and supplier directory/account compatibility work. It does not imply the uploaded Android version 24 contains these changes: this repository revision still declares version 23. Obtain/reconcile the exact version 24 source and designated-device acceptance before distributing a replacement.

## Repairs

The shared create/edit recipe calculator used min=0.01 with step=0.1. Whole hours therefore had a step mismatch, and the default 0 was invalid even when the details element was closed. Use min=0 and step=any. Zero leaves the optional estimate unavailable without blocking recipe save. Utilities and overhead estimate application remains optional and explicit; saved costing is not silently replaced.

Web photo views now read canonical photoDataUrl and fall back to legacy portable photoUri. Empty canonical artwork means explicit removal. Phone-local content/file paths are not exposed as usable web artwork. Android cloud read/restore paths accept the same canonical field, and cloud writes prepare bounded JPEG data rather than uploading phone paths. Coil receives decoded bytes for web data photos. New phone recipes store portable artwork immediately; changing a cloud-linked photo updates only the artwork fields, avoiding replacement of concurrent recipe details. A linked-photo edit requires connectivity; a failed cloud write preserves the previous photo and reports a sync error. Existing inaccessible phone files cannot be recovered remotely; reselect the photo on its original device.

## Integration state

- PR #41 records the baker website release published 31 October; #40 records Android/supplier source compatibility and migration checks, with device/signing acceptance remaining.
- Supplier PR #10 records the published supplier website; #7/#8 supply grants, campaign moderation and business classification. Discovery uses the supplier project's public feeds rather than bakery credentials or private supplier account IDs.
- Referral attribution exists in isolated test drafts: main #38/#39 and suppliers #9. These flows do not constitute live coupon redemption, payable commission or a Pro entitlement. The currently published baker source does not include that referral signup field. Production validation/delivery, programme rules and reconciliation remain outstanding.
- No production coupon or discount terms are defined by this repair. A source/referral code and a benefit-bearing coupon must remain separate, so entering an unverified source cannot unlock paid access.

## Validation

Local web TypeScript/Vite production build using demo Firebase identifiers passed. Twenty existing costing/subscription/supplier contract checks and the photo compatibility check passed. Android photo compatibility tests and the existing migration/supplier suites are added to CI. Local Android compilation is unavailable; the runtime has no Gradle/Android SDK. Local browser installation failed (download returned a truncated archive), so no browser/device acceptance is claimed. CI results must be checked on the final revision.

No live data, live payments, customer entitlement, website deployment, Android signing or Play release changed.


## Pro source-code redemption implementation

The owner confirmed codes must record the source AND grant Pro. This branch adds administrator-created random codes with a source label, 1–365 Pro days, an expiry within one year and a maximum redemption count. The raw code is displayed only at creation; hashed definitions and redemption records stay server-private. The admin can disable future redemptions. The baker Subscription screen accepts the code; shareable signup links prefill it after login without automatically redeeming it. The shared web subscription route also serves Android users, and the existing authoritative subscription listener reads the resulting expiring promotion.

Actual server transactions grant the existing admin_promotion entitlement, retain immutable source receipts, enforce per-account replay protection and serialize the global usage limit. Existing active paid subscriptions are rejected rather than overwritten. Existing active gifts extend from their expiry. Codes neither charge a customer nor create partner commission. Supplier-package discounts are not implemented by these baker Pro codes.

New callable functions: createProRedeemCode, listProRedeemCodes, disableProRedeemCode, redeemProCode. Deploy these plus the built baker/admin websites only after final validation and a reviewable release package. No codes or real Pro grants have been created in production. Source attribution for payable sales partners remains a separate production integration gate.

CI on recipe/photo revision 3ab142f passed web checks, Android compilation and photo/migration/subscription/supplier tests, including actual supplier emulator transactions (run 41283946152). New code redemption and auth-flow checks must pass on the final revision; isolated Firestore tests exercise concurrent redemption, retries, expiry, private rules and paid-access protection.


The web directory now defaults to the same approved public supplier endpoint as Android when the optional VITE_SUPPLIER_DIRECTORY_URL setting is absent. This prevents an otherwise valid website build from silently disabling supplier discovery. An explicitly configured endpoint is retained.
