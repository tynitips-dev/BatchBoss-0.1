# Sales Partners referral integration — development checkpoint, 29 October 2026

Portal domain: **partners.batchboss.co.za**, DNS managed at Xneelo. No DNS, hosting or production configuration was changed.

## Implemented and tested
- Baker signup source located in the existing Android/web repository, on the Android/web alignment branch rather than main.
- New isolated baker companion branch: feature/baker-partner-attribution-test-20261029, based on codex/android-web-alignment-20261029. Preserve that branch's Android entitlement and supplier-sync fixes.
- Shared website signup captures a referral only in a localhost, demo-project pilot. A backend transaction creates the Free bakery, owner membership, private opaque-subject mapping and registration outbox event together. Signup retries do not change attribution; existing profiles cannot acquire signup attribution.
- Android signup points to that shared website instead of reporting a local Room account as a completed Firebase registration. Return to Android and use the same account to sign in. No Android installation or distribution occurred.
- Existing Paystack checkout snapshots server-owned attribution in its private payment intent. Verified TEST settlement creates a deterministic private payment event atomically with the receipt. Referral details and account IDs stay out of Paystack metadata.
- Supplier signup/test checkout keeps its earlier private mapping and event flow; supplier referral registration and event generation now also require an explicit emulator flag and a demo project.
- Central test ingestion uses approved partner codes, immutable subject ownership and separate nonpayable test first-sale records. A scoped baker bridge identity cannot submit supplier events.
- partners/tools/dispatchTestOutbox.cjs provides bounded LOCAL delivery: distinct demo projects, loopback Auth/Firestore and exact loopback receiver URL only, no redirects. It obtains a source-scoped identity from the Auth emulator. Registrations are delivered first; failed/uncertain deliveries stay pending; only acknowledged events are marked delivered.
- Separate baker and supplier signup URLs replace the old marketing-homepage sharing URL. They remain clearly labelled not live; sample codes cannot be copied as real signup links.
- Production function exports do not include referred signup/test-ingest endpoints. Default-deny referral collection access is unchanged. Test events never create partnerCommissions, paid supplier entitlements, or money transfers.

## Validation evidence
- 21 offline partner/supplier policy and handler regressions pass.
- 7 existing Paystack callback/webhook/error/renewal regressions pass.
- Baker backend TypeScript compile and baker website TypeScript/Vite build pass with synthetic demo settings.
- Two full integration scenarios pass using REAL local Auth and Firestore emulators with separate baker, supplier and partner project stores. Actual source handlers are loaded with a transaction adapter; Paystack responses are mocked and all external test network calls are refused.
- The local HTTP test fixture verifies real Auth-emulator ID tokens before invoking the actual partner callable guard. It is not a deployed Functions HTTP endpoint.
- Scenarios exercise duplicate settlement, invalid amounts, private-record read denials using actual rules, cross-source spoof rejection, lost acknowledgement/retry without duplicate credit, and no payable commission or entitlement creation.
- Android compilation and compatibility CI must pass on the new companion PR; designated-phone and browser acceptance remain gates.

## Remaining release gates
1. Reconcile the stacked Android/web/backend branches and the separate entitlement-migration safety draft; do not merge old main-based portal history over newer app/security work.
2. Implement/review a production least-privilege cross-project identity and durable delivery/reconciliation strategy. The current dispatcher intentionally refuses hosted endpoints and production project IDs.
3. Validate partner approval at production signup, approve attribution window and eligibility/lead-conflict policy, rates, refund/chargeback treatment and commission reversal/reconciliation. Current offline signup checks code format; central ingestion checks approval.
4. Exercise the deployed callable protocol, App Check/rate limits and controlled browser/device acceptance in isolated staging. Local handler/storage tests are not release sign-off.
5. Complete programme/legal/privacy review, retention/deletion, authorised administration and dedicated partner project setup before real applicants or payable commissions.
6. Prepare Xneelo static hosting, DNS/SSL and rollback only after the integration gates pass. No deployment, live billing enablement, real records, real provider requests or payouts are authorised by these test changes.

## Local test configuration
Client pilot: VITE_PARTNER_REFERRAL_EMULATOR=true, localhost and demo- Firebase identifiers.
Server pilot: FUNCTIONS_EMULATOR=true, GCLOUD_PROJECT=demo-..., BATCHBOSS_PARTNER_TEST_ENABLED=true.
All test SDKs must use loopback FIRESTORE_EMULATOR_HOST and FIREBASE_AUTH_EMULATOR_HOST.
The delivery tool accepts BATCHBOSS_TEST_SOURCE (baker/supplier), distinct BATCHBOSS_TEST_SOURCE_PROJECT / BATCHBOSS_TEST_PARTNER_PROJECT, and the exact emulator callable URL in BATCHBOSS_TEST_RECEIVER.
These settings are for synthetic local tests only, never public builds or live merchant configuration.
