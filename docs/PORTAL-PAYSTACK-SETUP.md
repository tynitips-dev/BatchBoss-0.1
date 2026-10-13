# BatchBoss portal repair and Paystack installation

Prepared 12 October 2026. The app and backend code are built and tested locally; they are NOT deployed to Xneelo or Firebase by this package.

## What changed

- Preserves all existing portal sections and adds Sales & stock reports with CSV export.
- Keeps the pink, navy and cream design throughout the real app, including all existing forms.
- Working same-dimension unit conversion, and recipe scaling from saved recipe ingredients.
- Calendar previous/next month, Today shortcut, and task markers.
- Stock prices, quantities, unit costs, minimum levels, search and low-stock filtering.
- Reversible stock archive/restore; suspect imported heading rows are flagged for review. Existing records are not deleted or automatically changed.
- Import skips known heading/instruction rows and requires usable price, quantity and unit columns; CSV parsing uses the existing spreadsheet library.
- Full manual order details and line totals with linked calendar task updates.
- Receipts linked to invoices, payment method/date/reference and branded print/PDF view. A receipt does not itself mark an invoice paid; do that after full settlement.
- Name editing, friendly Baker fallback, full-stop Rand decimals, and Sync now (server refresh).
- Paystack hosted card checkout, monthly/annual plans, signed webhooks, server verification, renewals, idempotent entitlement updates, expiry and hosted subscription management.
- Test transactions never grant live Pro. Existing PayFast code and checkout remain available.
- Firestore rules protect bakery ownership, account roles and provider-owned subscription fields. Existing paid/promo expiry is enforced.

## 1. Get the source changes

Work from the BatchBoss repository, branch codex/web-backend-foundation. Preserve any work you have changed locally first.

The source patch in this package was based on commit b015f79ba84cb4d222d0dfd26e3bd108fff114a6. From the repository root:

```
git apply --check /path/to/source-changes.patch
git apply /path/to/source-changes.patch
npm ci --prefix web
npm install --prefix functions
npm run build --prefix functions
node --test tests/*.cjs
```

If `git apply --check` reports a conflict, stop and reconcile those files; do not overwrite your own edits. The source-update folder contains the changed files for comparison, including the complete replacement SubscriptionView.tsx (do not append it to the old file).

Keep your existing web/.env and other environment settings. No Paystack secret is included in this package.

## 2. Configure Paystack TEST mode first

In your Paystack dashboard, use TEST mode and create these two recurring plans:

- BatchBoss Pro Monthly: ZAR 149.00, interval monthly.
- BatchBoss Pro Annual: ZAR 1199.00, interval annually.

Copy the two PLN_ plan codes. Add the lines from functions/.env.paystack.example to functions/.env.batch-boss-android, replacing both placeholders. Preserve any existing settings in that file.

From your repository root, enter the TEST secret key through Firebase's hidden prompt:

```
firebase functions:secrets:set PAYSTACK_SECRET_KEY --project batch-boss-android
```

Do not paste the secret key into chat, the frontend, GitHub or Xneelo files.

Deploy the backend and rules first:

```
npm run build --prefix functions
firebase deploy --project batch-boss-android --only functions:getPaystackPlans,functions:createPaystackCheckout,functions:verifyPaystackPayment,functions:managePaystackSubscription,functions:paystackWebhook,functions:expirePaystackAccess,firestore:rules
```

The expiry function uses Cloud Scheduler (Blaze billing is needed). These commands deliberately do not redeploy the existing PayFast functions.

In Paystack TEST settings, set the webhook URL to:

https://europe-west1-batch-boss-android.cloudfunctions.net/paystackWebhook

Callback URL:

https://app.batchboss.co.za/?payment=paystack

## 3. Upload the app to Xneelo

Back up the current app.batchboss.co.za website folder first.

Upload the supplied Xneelo-App-Upload.zip into the document root for app.batchboss.co.za and EXTRACT IT THERE. This keeps assets and documents in the right folders. Do not upload into the main marketing website folder.

The ZIP contains index.html, assets/ and documents/ at its root. It contains no backend keys or source files. Replace the existing index.html and add the new assets. Keep old hashed assets initially so cached sessions keep working. You may keep your existing .htaccess and hosting settings.

This build includes the Firebase public web configuration from the existing live app. It uses the same Firebase project and bakery accounts. Future builds use your existing web/.env:

```
npm run build --prefix web
```

Upload the contents of web/dist, maintaining its folder structure. Avoid dragging separate folders through File Manager if it flattens them.

## 4. Test before going live

Use a dedicated free test account. In Subscription, select Paystack. The screen must say Test checkout only. Use Paystack's documented test card details, not a real card.

Check:

1. Cancel checkout: no Pro access granted.
2. Successful test payment: displays Test payment verified, without granting live Pro.
3. Refresh the return page: does not extend the paid period again.
4. Paystack webhook logs show HTTP 200 for verified events. Invalid signatures return 401.
5. In a Pro test account, check recipe costing, order-to-calendar updates, receipt printing, workbook import and mobile display. The paid checkout buttons only show for accounts without active Pro.
6. Check sign-up and Android sync after the rules deployment. Existing profile fields outside the permitted name/contact updates may require review if your local Android version differs from this repository.
7. Review suspect stock records under Ingredients and Archive only actual spreadsheet headings. Archive affects the web views; the existing Android version does not yet hide the archive flag.

The automated tests use mocked payment/Firebase responses. They do not prove a real Paystack transaction, webhook delivery, deployed rules or Android synchronisation.

## 5. Switch to LIVE when Paystack enables your account

Create matching LIVE recurring plans. Test and live plan codes are different.

1. Replace the two plan codes in functions/.env.batch-boss-android with LIVE codes.
2. Run the secret command again and enter the LIVE secret key securely.
3. Repeat the same targeted deploy command.
4. Add the same webhook URL in Paystack LIVE settings.
5. Confirm the test-mode notice is gone and the prices are correct. Complete a small controlled live subscription test yourself; no live charge has been made by this package.

Paystack checkout is for purchasing BatchBoss Pro, not for taking payments from your bakery's customers.

Customers can use Manage card or cancel subscription to open Paystack's hosted page. Cancellation does not remove an already paid period. A scheduled expiry job updates the shared Firebase account after the paid period ends. Failed renewal does not extend access. Refunds/disputes require operator review; this version does not automatically revoke an entitlement following a refund.

Existing PayFast subscribers must contact support before moving to Paystack to avoid two subscriptions. Existing PayFast processing is preserved and has not been re-tested here.

## Rollback

Restore the previous index.html and its assets to undo the front-end change. Restore the prior rules from your source backup if necessary. To pause new Paystack checkouts, set PAYSTACK_ENABLED=false and redeploy the Paystack callables; retain the webhook and expiry functions for existing customers. Do not delete customer payments or subscription records.

## Verification completed

- TypeScript and production build for web.
- TypeScript build for Firebase Functions.
- 13 automated tests covering signatures, wrong amounts/customers/plans/currencies, test/live separation, duplicate confirmations, renewal mapping, billing dates, unit conversion, scaling, currency formatting and import heading detection.
- No production record writes, gateway charges, Xneelo upload or Firebase deployment performed.
- Browser visual QA of the new build and Firebase emulator rules tests remain to be performed.

Official references: https://paystack.com/docs/payments/subscriptions/ ; https://paystack.com/docs/payments/webhooks/ ; https://paystack.com/docs/payments/verify-payments/
