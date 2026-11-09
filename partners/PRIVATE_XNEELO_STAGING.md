# BatchBoss Sales Partners: PRIVATE Xneelo test bundle
Updated: 9 November 2026. Development only. **Do not publish to partners or change live DNS yet.**

## What has been prepared
- The standalone site is four static files in partners/web/: index.html, styles.css, app.js, config.js.
- The partner website is designed for partners.batchboss.co.za. The site has email/password sign-in and partner-versus-admin permissions **only when its own Firebase backend is securely configured and deployed**.
- If public Firebase config is blank, the hosted site now fails closed with a "Private partner testing" setup notice instead of exposing fake dashboard data.
- The main BatchBoss admin-web development branch adds a **Sales Partners** section for approvals, rejections, commission records, payout requests and recording external EFTs. It uses a second, separate verified admin login to the partner Firebase project. The main site's own admin role alone is NOT accepted.

## Where to get the upload ZIP
GitHub Actions workflow "Build private Partners Xneelo package" creates the artifact
"BatchBoss-Partners-Xneelo-Private-Staging". Inside it is a zip
"BatchBoss-Partners-Xneelo-PRIVATE-STAGING.zip" containing exactly the static web
files to upload into the **isolated** partners.batchboss.co.za document root.

The ZIP contains no private Firebase credentials, Admin SDK keys, payment tokens,
or real customer/partner records. Editing partners/web/config.js to supply the
**public Firebase web app** settings must happen *before* packaging a login-enabled
staging build, after the separate Firebase staging project is ready.

## Correct setup order
1. Create and secure a **dedicated staging Firebase project** (NOT
   batch-boss-android and NOT batchboss-suppliers). Set up Email/Password Authentication,
   Firestore, and Cloud Functions. Deploy the partner functions and default-deny
   partners/firestore.rules to this new project only after private security testing.
   Browser files alone do not create a usable login or approvals backend.
2. Create the approved staging admin Auth user with a **verified email** and set
   { "batchbossPartnerAdmin": true } using a trusted one-off Firebase Admin SDK
   operation inside the separate staging project. Never set admin claims from
   a browser and never reuse the production main-admin Firebase token.
3. Configure the four **public** fields in partners/web/config.js:
   apiKey, authDomain, projectId, appId. Keep ALL private keys out of this folder.
   Include partners.batchboss.co.za as an authorised Firebase Auth domain only
   after domain ownership, access controls, and SSL are confirmed.
4. Build and review the staging ZIP from the exact approved Git commit.
   Set up partners.batchboss.co.za as a **separate Xneelo document root** and
   configure HTTPS plus **Xneelo directory password protection** before upload.
   A hidden or unlinked URL is **not** a security control. Do not reuse the
   main BatchBoss website document root.
5. Upload the contents of the ZIP to the protected directory. Test page
   loading, sign-in, rejection of unverified emails, role restrictions,
   application approve/reject, blocked cross-account reads, commission
   record visibility and request payout. Verify on phone and desktop.
6. For the main admin portal, populate VITE_PARTNERS_FIREBASE_* only in the
   approved development/staging build. Deploying the draft UI to live
   admin.batchboss.co.za is a **separate decision** after tests pass.
7. Only after security acceptance remove Xneelo directory password protection
   and invite approved partners. Do not put a public link on batchboss.co.za
   during testing.

## Commission/payout policy for this pilot
The partner never processes a payment or a refund. The partner may **request**
their server-calculated eligible payout. The admin approves or rejects the
request, pays manually by bank EFT, then records an external transfer reference.
The application does **not** transfer money.

**Current limitation:** Production commission creation is gated by a trusted
provider-verification bridge and cannot be minted by an admin checkbox.
Manual subscription confirmation from existing BatchBoss records needs an
audited backend integration before those figures can become payable. Test
credits and sample data must NEVER become eligible payouts. No refund
interface is included in the agent portal.

## Checks and restrictions
- Preserve existing production baker and supplier Firebase projects, billing,
  Xneelo sites and DNS.
- Do not publish the portal without the second authentication and backend rules.
- GitHub Actions and a static ZIP alone do not establish passing integrated
  Firebase acceptance tests or a production-ready site.
- The web app loads Firebase JavaScript from the Google CDN; validate this
  against hosting CSP/security policy. Protect the site via HTTPS.
- Partner list, commission, and payout callables currently return up to
  200 records. The admin UI warns if more exist; pagination is a release gate.

## Rollback
Keep a copy of the previously approved static bundle. If the staging website
fails, restore the previous files and block access while the problem is fixed.
Do not roll back production Firebase rules or touch the live customer systems.

## Development references
- Partner portal / referral / payout integration: integration PR #64.
- Baker referrals: PR #39. Supplier referrals: BatchBoss-suppliers PR #9.
- Manual payout feature: PR #51. All functionality requires safe final testing.
