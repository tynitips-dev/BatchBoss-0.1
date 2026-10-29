# Browser referral acceptance — 29 October 2026

Development only. Tested the baker website against separate local Auth, Firestore and real callable Functions emulators. The payment provider was mocked inside a guarded local fixture; no merchant checkout, card details, real provider request, hosted record or payout was used.

## Passed in the actual browser
- The referral signup link opened the correct signup view and prefilled the partner code.
- Deliberately failed workspace setup after account creation. The form retained name, bakery, email, password and referral details and displayed the returned error.
- Changed the bakery name and retried using the same synthetic account. Signup reached the bakery dashboard and created exactly one workspace and one registration event.
- Created a server-priced mocked monthly payment intent for that account and navigated the browser to its callback. The actual callable verified the mocked transaction.
- The UI displayed "Test payment verified. Test payments do not activate live Pro access." and the account remained Free.
- Rechecked the payment through the browser and delivered the resulting events through the actual local partner callable, using a scoped identity from the Auth emulator.
- The partner store contained exactly one nonpayable first-sale test credit and zero payable commission records. Repeat delivery had no pending events or additional credit.
- Verified the payment confirmation at 390 x 844; the page had no horizontal overflow and the status/check-again controls were readable.
- Source and partner callable requests passed the real Firebase SDK token-verification layer. This closes the earlier local callable-protocol gap; it is not hosted-staging or real-merchant acceptance.

## Bugs addressed
1. Firebase auth changed while workspace setup was running, temporarily unmounting the signup form. Errors and input values were lost. The parent now tracks the active auth flow so the form remains mounted until the operation finishes, and duplicate submissions are ignored.
2. The local browser's Firestore streaming connection could stall during emulator testing. The explicit localhost/demo pilot uses long polling; production transport is unchanged.

The existing BatchBoss CSS, branding and checkout security checks are preserved.

## Still required before release
- A connected designated Android phone and the reviewed test build for actual signup-to-browser/return-to-app acceptance. ADB reported no connected devices during this pass; no phone installation or distribution occurred. A phone-width browser check is not native Android acceptance.
- Hosted-staging browser/callable tests, real test-mode merchant checkout acceptance, approval validation at signup, production cross-project identities/delivery/reconciliation, policy decisions and stacked branch/security review.
- Xneelo/DNS deployment and real payments remain disabled. All PRs remain drafts.

Account-switch review additionally clears the previous profile before observing a new account and ignores stale callbacks after account changes/unmount. Native phone acceptance remains outstanding.

Two automated regressions now execute the actual App source with controlled SDK callbacks: form identity is preserved during auth/setup failure, and account changes/unmount discard old profile/error callbacks. Both passed locally and are included in CI.
