# BatchBoss Global: release gates

This is a staged integration, not a launch authorization. Do not merge or deploy solely because a build compiles.

## Supported first-wave regional presets
ZA (ZAR), US (USD), GB (GBP), CA (CAD), AU (AUD), NZ (NZD).

## Accounting and old records
- Persist the bakery country/currency; existing records without denomination remain legacy ZAR.
- Keep historic cup quantities unchanged (web legacy 250 ml, Android legacy 240 ml). Label explicit new cups and tablespoons.
- Never combine different currencies in costing or reporting. New monetary records and recipe lines must include currency.
- Decimal drafts use a full stop, including input typed with a decimal comma; preserve fractional zeros.
- Configurable tax rates are user-supplied; validate local invoice requirements separately.
- Test stock/recipe imports, prices, saved totals, print/share, and client contact details in each first-wave accounting currency.

## Android and cloud
- Run the Room migration tests and explicit measurement/formatting tests, plus emulator sync tests. Test an upgrade with a copy of real-shaped legacy records; preserve IDs, ownership, prices and quantities.
- Native non-ZAR access remains gated because native costing screens, account/default rates, country-specific supplier discovery and older Android client interoperability require further work. Do not switch a user’s workspace currency.
- Test Firestore rules, record ownership and older-device compatibility before any non-ZAR registration, including server-side registration controls.

## Payments
- Paystack South Africa charges in ZAR. PAYSTACK_INTERNATIONAL_ZAR_ENABLED defaults false. Switch only after merchant international-card approval, actual plan pricing reconciliation, and consent/end-to-end payment tests.
- The optional cross-border ZAR flow uses verified existing Paystack standard plans; overseas launch promotions are blocked.
- Local-currency subscription billing needs a verified payment provider, credentials, configured products/prices and verified webhook/renewal/cancellation handling.
- Never make a live charge or grant Pro without confirmed payment.

## Suppliers and public marketplace
- Missing countryCode in historical supplier listings/ads means ZA. Explicit countryCode filters the baker portal. The separate supplier service must publish and validate country metadata to populate other countries.
- Baker Finder/Marketplace are separate integrations; verify their actual deployed repository and regional pricing/visibility before launch.

## Before enabling public signup
- Test web + Android CI on the exact final commit.
- Verify invoice/quote/receipt output and native print/share on actual devices.
- Reconcile any separate production payment/marketplace updates and the open invoice-PDF hotfix PR.
- Confirm launch countries, pricing, payment provider, merchant approval, and rollout plan. Keep new country registration disabled by default.

## Staged web registration and rollout

International workspaces can now be created only by the server. Default-off public checkout and registration flags are independent of the pilot. An authorized administrator may prepare a new Firebase Auth account matching `PADDLE_TEST_UID` using `preparePaddlePilotWorkspace` (uid, country, bakeryName, firstName, surname). This operation creates only a new free international workspace; existing bakery currency cannot be changed. Keep public flags false until live checkout/lifecycle, regional accounting and legal QA pass. Android remains ZA-only; do not advertise native international access. Public registration opens only with the explicitly configured production flags. Test with a separate owner-approved account; no production account has been created by these development changes.
