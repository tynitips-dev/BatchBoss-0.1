# Paddle integration status

The catalog in web/src/regional-plan-prices.json records the actual six-country BatchBoss pricing, including proposed international launch amounts. All amounts are integer minor units. No annual Baker Finder or Full Suite price has been fabricated. Supplier packages are ZA only. Marketplace is per listing.

The /pricing/ page compares catalog prices even with no Paddle configuration. The optional sandbox is explicitly gated by VITE_PADDLE_SANDBOX_ENABLED and a test_ client token; it previews the five existing Pro price IDs separately and opens one test item per checkout. Sandbox results never grant entitlements. No production Paddle initialization or live billing is enabled. Paystack callable, webhooks and management flows are unchanged by this integration.

Before activation: obtain verified LIVE catalog mappings (separate from sandbox); verify each configured country's overrides, free trial and exact renewal terms; obtain Paddle live domain approval; confirm eligibility for Baker Finder, Full Suite and Marketplace; implement authenticated server-created checkout, signature-verified idempotent webhooks, account/subscription binding, expiry, renewals/cancellation/failed-payment handling and duplicate-provider prevention. Keep existing Paystack customers on Paystack. Verify default payment link /pricing/ with the production client only after these gates pass. Test real sandbox and live checkout, including payment-method update and _ptxn links. Never rely on client completion events for granting Pro. International signup and Android remain gated separately.

Custom Pro codes are unrelated to Paddle discounts: they grant a time-limited admin promotion, no card or automatic renewal. Codes are optional, normalized uppercase, 5–24 alphanumeric, unique and hashed; existing BBP codes work. Public campaigns must use bounded redemptions and expiry. Do not put API secrets in frontend config.

## Private pilot implementation

The global branch now exports `getPaddleCheckoutOptions`, `createPaddleCheckout`, `paddleWebhook` and `createPaddlePortalSession`. The signed-in subscription screen routes new international accounts to Paddle and South African accounts to Paystack; existing Paystack subscriptions keep their management flow. The public comparison page is not a live checkout entry point. Standard weekly, monthly and annual Pro are wired to a pilot; launch promotions, Full Suite, Finder, Marketplace and supplier subscriptions are **not** enabled on Paddle pending exact entitlements/catalog/renewal terms. International registration and Android sign-in remain disabled, so use a separately prepared test bakery; never relabel an existing ZAR account.

Server checks the stored bakery country/currency, verified Firebase email, free profile, exact remote price/currency/country override and one pending checkout lock. It creates a provider customer and transaction, persists their exact IDs before returning checkout, and reconciles signed transaction completion and subscription events regardless of ordering. A transaction with an inconsistent address, customer, currency, plan or ownership is held for manual review. Existing customers are never linked solely by email; ambiguous API failures or prior checkouts are held for review. No checkout is unlocked on a timer. Sandbox events never provision live profiles. The app does not grant access based on a checkout redirect. Incoming duplicate and stale events are ignored transactionally. Trial/active access requires a future expiry; paused, canceled and past_due are non-granting pending explicit policy approval. Scheduled changes alone do not revoke active access. Existing paid or pending Paddle accounts cannot receive automatic promo overrides.

### Configuration, still disabled

Use Firebase project `batch-boss-android`; review the environment and permissions in an isolated test project first. Never paste API keys or webhook secrets in chat or put them in Git or frontend config. Store each secret interactively with Firebase CLI:

```sh
firebase functions:secrets:set PADDLE_API_KEY --project batch-boss-android
firebase functions:secrets:set PADDLE_WEBHOOK_SECRET --project batch-boss-android
```

These commands are for the owner to run when the corresponding environment and release are verified, not instructions to deploy now. Keep all Paddle flags false in production until tests pass; use `functions/.env.paddle.example` for parameter shapes. The public client token can be supplied as `PADDLE_CLIENT_TOKEN` (must match the environment). `PADDLE_TEST_UID` restricts new checkouts to one owner-approved test user and no public enable flag exists. Verify all price IDs, product IDs, trial/renewal terms and country overrides directly in Paddle before configuring the map. Create the permanent live notification destination at the actual deployed `paddleWebhook` URL using the verified Firebase project/region, with the subscription lifecycle, customer and transaction.completed events; store the destination signing secret, not an API key. Do not archive/delete the permanent notification destination. No secrets configured, live checkout opened or live records changed in this commit. Deployment and lifecycle testing remain a separate release gate.

### Lifecycle regression checks

`tests/paddle-flow.cjs` exercises the real SDK signature verifier and the callable/webhook handlers with isolated Firestore and HTTP fixtures. It covers both first-purchase event orders, renewals, price changes, scheduled/immediate cancellation, failed payment recovery, stale events, duplicate webhooks, sandbox isolation, country and ownership mismatches, and checkout replay. The CI workflow runs these tests alongside the existing Paystack tests. These are simulated provider and database flows; they do not verify live Paddle, actual Firestore permissions, or real payment delivery.

### PayFast retirement consolidation

The older checkout callables now reject new sales; the previous PR #72's legacy notification, transaction deduplication, status, cancellation, expiry and receipt handling are retained. Pending and historical billing must never overwrite a different provider's access. The signed-in legacy PayFast view supports status and confirmed cancellation; unlinked historical tokens need support reconciliation. No provider records are deleted. The admin checkout tester was already absent in this branch. Verify historical PayFast secret availability (including sandbox notification secrets if that endpoint was previously used), legacy ownership records and receipt delivery in staging before any deployment. No live settings changed.

### Listing-plan consolidation

The regional listing UI, server-controlled listings and public feeds from #67 are included. SA: weekly Pro R39.99, Directory R49/month, Full Suite R220/month with three included listings per calendar month, or R20 once-off Marketplace fee. Directory-only payment does not unlock Pro. Existing Directory billing has a separate authenticated management link; switching between recurring plans is intentionally support-assisted until billing reconciliation is verified. Regional prices remain visible in the catalog; overseas listing sales are not enabled. Do not enable listing flags until real plan codes, amounts, entitlement tests, public listing UX and deployment configuration are verified. Old payment and subscription rows are retained.

### International listing subscription pilot

Finder and Full Suite are additional explicit Paddle plan mappings, disabled by default using `PADDLE_LISTING_SUBSCRIPTIONS_ENABLED`. Finder grants directory access only; Suite grants Pro, Finder and three Marketplace publications per calendar month (UTC internationally; Johannesburg time for SA). The signed subscription projection atomically updates profile and region-bound listing rights. Cancel/pause/past-due or downgrade clears superseded rights, while scheduled cancellation preserves them through expiry. Local entitlement timestamps cannot unlock overseas publications. Standalone international Marketplace checkout and launch/supplier offers are not enabled yet. Plan changes and new checkout on an already linked account remain support-assisted. Existing local pricing and provider configuration are unchanged. The test suite uses signed fixtures and isolated emulator data only; remote catalog IDs and real checkout still require verification.

### International standalone Marketplace fee pilot

One-off Marketplace fees now have a distinct price configuration and checkout callable; they can never grant Pro or create a recurring subscription. Signed completion must match the server-created intent, owner, saved listing, price, billing country, currency and one-time terms before the live listing is published. Sandbox tests have separate locks and never publish or change live access. The provider customer binding is reusable for subsequent subscriptions. The live listing is frozen while payment is pending; ambiguous errors require support reconciliation, not another charge. UI uses regional pricing and opens provider checkout only for the allowed pilot user. This remains disabled by default, and the Paddle price/product IDs, country overrides, actual tax/checkout behavior and live event delivery must be confirmed. The current public site preview links and production deployment still require review. No live payment, discount or infrastructure changes performed.

### International launch offers

Launch prices use the approved six-country catalog. Standard prices remain the recurring products; fixed, currency-specific Paddle discounts must match the price difference, exact standard price, campaign expiry and finite billing period count. Annual offer is one paid year, monthly count must be explicitly confirmed (one-month campaign availability is not a billing period count). No automatic indefinite promotional renewals. The first charge after any trial receives the discount, and the verified standard price resumes after the configured periods. UI displays renewal price and requires consent. Disabled by default; no remote discounts or products created. Do not configure a promo price as a standard price. Confirm discount eligibility and actual checkout behavior before turning on the offer. Existing local Paystack terms remain unchanged.

### Approved payment link

The pricing page no longer creates unbound sample checkouts. It displays the catalog; only a verified signed-in account can resume an original server-created pending transaction via `resumePaddleCheckout`. The SDK never initializes automatically from an unvalidated `_ptxn`. Existing customers should use the provider-hosted customer portal for subscription management. Confirm the deployed approved URL `https://app.batchboss.co.za/pricing/` and real payment-link recovery before launch. Email verification controls are available in the international subscription screen.

## Final preflight tool

Run `node scripts/paddle-live-preflight.cjs functions/.env.batch-boss-android` with the required secrets and deployed webhook URL in the local process environment after compiling Firebase functions. See `docs/releases/global-owner-preflight.md`. This is a read-only check; it never creates or removes Paddle entities. The old sandbox-only public checkout has been superseded by authenticated payment-link recovery. Launch offers are gated pending confirmed terms. Public checkout and signup gates exist but remain false.
