# Private staging preparation — 6 November 2026

Status: prepared for review, NOT deployed; production remains NO-GO.

## Exact sources

- Integrated baker/partner baseline: `55c137dd05e0bbcd3a1e727ec2a05679289b8411`, branch `integration/sales-partners-all-20261106` (PRs 38, 39, 48, 51).
- Supplier PR 9: `7f1e3ef4a6e431f6536256eb9ee3b04e32ca8d52`; remains separate and unmerged.
- Supplier registration/payment payloads match the central version-1 TEST contract: opaque subject, source, immutable referral ownership, deterministic payment ID, ZAR integer cents and source-specific plan. No customer UID/email is delivered to partners.
- There is NO production event contract implementation. `mode=live` is rejected. No production refund/chargeback producer, authenticated durable delivery or payable commission creation is supplied by these branches. Do not remove emulator guards to make hosted staging work.

## Test-only settings

Use three distinct demo project IDs: `demo-batchboss-baker`, `demo-batchboss-suppliers`, `demo-batchboss-partners`. Auth/Firestore bind only to `127.0.0.1`. Set `FUNCTIONS_EMULATOR=true` and `BATCHBOSS_PARTNER_TEST_ENABLED=true` only in the local test process. Provider calls in automated integration tests are mocked; `sk_test_fixture` is not a merchant key. No production credentials or customer exports are needed.

The partner public config remains blank. No `.firebaserc` production alias is added. Hosted UI review requires a separately approved dedicated non-production Firebase project and separate Auth users, rules, functions and web configuration. Hosted referral capture/delivery remains disabled under current code. Do not ship emulator flags or localhost targets to a hosted browser. Test credits must stay nonpayable; no listing/subscription entitlements or external transfers.

## CI operation

Integration pushes now run partner policies plus baker build/regressions and Auth/Firestore tests, including payout transaction/rule checks. Partner code changes also trigger referral PR checks. Manual private-supplier coverage uses `include_private_supplier=true` and exact supplier SHA above. It requires `SUPPLIER_REPO_READ_TOKEN` with contents-read access only to the private supplier repository; the public repository's ordinary token cannot read it. Never put this token in a file, bundle, PR output or browser. Supplier checkout does not persist credentials. Missing access fails that job; a default skipped supplier job is not evidence of cross-project success.

## Xneelo plan — perform only after explicit approval

1. Record account/hosting product, existing zone records and TTLs, current document roots, certificates, release SHA and rollback owner. Choose an unused staging hostname; do not change `partners.batchboss.co.za` or any existing public site. Do not invent a server IP.
2. Verify Xneelo supports a separate document root, HTTPS and access protection for that hostname. Subdomain hosting may incur fees. Confirm the actual server IP and authoritative DNS owner before proposing the exact A record. Preserve apex, mail/MX/TXT and other sites.
3. Keep an empty deny-all root until protection is configured. Enable host-level directory authentication for the entire root and all assets, or an approved private access proxy/VPN. Unique reviewer logins, secrets outside the web root, no directory listing, no shared credentials in Git. A hidden URL, robots.txt or Firebase login alone does not make staging private.
4. Establish the exact-host SSL certificate and renewal plan. Where HTTP validation is needed, arrange the minimum ACME challenge exception without exposing the application; confirm this against the chosen hosting product. Reject HTTP application traffic, validate hostname/chain/expiry and then verify redirects retain authentication. No DNS or certificate action is authorised by this document.
5. Upload only reviewed static assets to a versioned staging root. Never upload repository metadata, backend code, service accounts, `.env`, provider secrets or test fixtures. Use encrypted transfer supported by the account. Add `X-Robots-Tag: noindex, nofollow`, `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, frame denial, and a tested CSP compatible with the Firebase modules used. These headers supplement access control.
6. Before invitations, prove unauthenticated root and asset access is denied; check TLS, mobile browser layout, verified-email Auth, independent admin/agent identities, direct Firestore denial and test-credit payout rejection. Verify backend authorisation independently of host authentication. Real hosted referral/payment acceptance is blocked until a separately reviewed staging backend exists.

Official hosting references checked 6 November 2026:
- https://xneelo.co.za/help-centre/control-panel/how-to-order-a-subdomain/
- https://xneelo.co.za/help-centre/website/protect-directory-tool/
- https://xneelo.co.za/help-centre/website/htaccess-explained/

## Rollback

Keep the prior static bundle and its checksum outside the live root. On failed private-access/TLS/backend checks, close access first and restore the previous versioned root. Restore only the recorded staging DNS values if an approved change was made; allow for the recorded TTL. Recheck certificate and access denial. Disable only the new staging receiver/users; preserve outbox and ledger audit records. Never roll back baker/supplier billing or erase receipts to recover partner staging. No deployment or DNS changes were made during preparation.

## Remaining gates

### Hosted test receiver candidate

Two HTTPS receivers are ready for code review. They are exported only with
`BATCHBOSS_HOSTED_REFERRAL_TEST=true` in `batchboss-partner-stage-261106`.
These are proposed IDs. Confirm ownership and availability before provisioning.
An ID change requires updating the code and rerunning the isolation tests.

| Component | Proposed project | Service account |
| --- | --- | --- |
| Receiver | `batchboss-partner-stage-261106` | `referral-receiver` |
| Baker synthetic sender | `batchboss-baker-stage-261106` | `referral-sender` |
| Supplier synthetic sender | `batchboss-supply-stage-261106` | `referral-sender` |

After explicit approval, provision only these isolated projects. Enable
Firestore with the partner deny-all client rules. Give the receiver Firestore
data access in its own project. Give each sender Cloud Run Invoker on its
own endpoint only. Use short-lived service account impersonation and
audience-bound ID tokens for smoke tests. Keep public and all-authenticated
grants absent and verify the effective IAM policy. Apply the flag during
Functions discovery and in the staging runtime. Scope the approved deploy to
`stageBakerReferralEvent` and `stageSupplierReferralEvent` only.

Before sending any fixture, verify unauthenticated access and cross-source
invocation are denied by Cloud Run. Seed one synthetic approved partner and
referral code in staging. For each sender submit registration, payment,
identical retry and full reversal fixtures using the version-1 test contract.
Confirm one nonpayable credit per source, reversal to zero effective credit,
and empty payable commission and payout collections. Exercise wrong source,
live mode, changed replay and partial reversal denial. Verify no test records
contain customer identifiers. On failure, revoke invoker access on both
services, disable their runtime flag and retain synthetic records for diagnosis.

This receiver candidate supports synthetic hosted acceptance. The source apps
still emit referral events only in their local emulator pilots. Hosted signup
and real provider test checkout need separately reviewed source adapters.
The local route tests verify configured identities and handler behavior.
Actual Cloud Run IAM and hosted Firestore behavior require the approved smoke test.

Production source approval at signup, provider-signed settlement/reversal events, workload-identity receiver, durable retry/dead-letter/reconciliation, attribution/refund policy and hosted/device acceptance remain open. See `SAFE_STAGING_RELEASE.md`. Xneelo account inspection can be planned; deployment/DNS is not the next engineering step.
