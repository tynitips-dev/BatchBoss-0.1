# Baker registration and sign-in platform

This change builds on the recipe/photo/Pro-code draft #43 at ed7ac768e2b96574b33a8e042a3a01b88e2b4b4e. No live customer record is changed by this code commit.

Admin Accounts and Pro gifts display Registered via and Last sign-in platform, with registration-source filtering. Values are Android app, Web app and Unknown. The source records the registration route; it is not proof of a Play Store download. A URL marker is client-reported analytics and must never grant access or commission.

New direct web registrations store registrationPlatform=web. The Android signup screen opens the same Firebase web registration with from=android, producing registrationPlatform=android while the actual browser signup records lastLoginPlatform=web. Subsequent native sign-in writes lastLoginPlatform=android and a server timestamp. Web email/password sign-in writes web. Metadata failures do not block valid sign-in.

This replaces the app's local-only account-success route; existing Room records remain untouched. Returning from signup requires signing in with the same email/password. The older createAccountWithDetails implementation is not used by MainActivity's signup route. Existing phone-local accounts are not silently uploaded, reassigned or reported as registered cloud accounts.

Firestore rules permit only the two recognized last-platform values with a server timestamp. Users cannot rewrite registrationPlatform, update another user's metadata or combine metadata changes with Pro grants. Existing platform fields are optional: older accounts remain Unknown and registration history is not guessed from bakery IDs, timestamps, phone type or email.

Release order: deploy the reviewed Firestore rules and listAccounts in batch-boss-android, build/publish baker and admin portals from this revision, then distribute a compatible Android build after reconciling the exact version 24 source. Do not overwrite newer deployment files or use the standalone draft functions index to replace live billing functions.

Live read-only inspection on 1 November 2026 confirmed the administrator portal loads 11 Firebase Authentication accounts. One has no bakery workspace; it requires a separate owner-authorized recovery check. No account details or private identifiers are included in this document.

Validation includes actual signup/login handler regressions and isolated Auth/Firestore rule tests for immutable signup source, cross-account denial, metadata updates and rejection of subscription changes. Android compilation and existing supplier/subscription/migration/photo checks remain in CI. Hosted end-to-end platform acceptance and deployment remain pending; Firebase sign-in alone does not provide a working deployment shell in this browser.
