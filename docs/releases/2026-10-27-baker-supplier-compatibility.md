# Baker supplier compatibility — work in review

The previous verified desktop release is recorded in both GitHub release branches. This change builds on that baseline and is not deployed to the live portals or installed on phones.

## Changes

- Baker web: show public discovery inside Suppliers as well as Find suppliers; add city/province filters; save a listing to a deterministic bakery-owned document using a transaction. Repeated saving retains existing bakery edits.
- Android: show the same public directory without supplier-project credentials; use the same saved-document key and field contract; refresh saved suppliers from the main bakery workspace. Supplier businesses continue using their website.
- Android local records and detail screens are scoped to the active user. Newly created manual suppliers record their owner and pending sync state. Previously fabricated ratings/opening hours are no longer assigned to new manual records.
- Supplier favourites preserve newer website descriptions; confirmed cloud records deleted on the website are removed on refresh rather than uploaded again. Unsent local additions remain pending on network failure.
- Room migration 12 to 13 adds sync metadata without deleting or reassigning existing records.

## Validation

Web production build passes. Three shared-record contract tests pass. Applying the actual migration SQL to a legacy SQLite table preserves existing rows/owners and initializes safe sync defaults.

Android compilation and the isolated SupplierCompatibilityTest suite passed on GitHub. These cover account isolation, additive migration, parsing, shared keys and canonical descriptions. Local Gradle 9.3.1 still reports the project directory is not writable. CI uses no Google service configuration or live Firebase connections; this is not an installed-device or live-data acceptance result.

## Required before release

- Recheck the full CI result after correcting the web build job to use the committed dependency lockfile and existing public Firebase build settings.
- Verify two-way save/favourite/edit/delete and account switching with designated test records; test directory paging and failed writes without publishing real suppliers or enabling charges.
- Older manual suppliers were stored with userId=0. The migration retains them without exposing names to another account. Recovery now requires a matching signed-in bakery and an explicit ownership confirmation. Only the original sole local account is eligible; the SQLite account sequence disqualifies a device even if a second account was later deleted. Shared/history-ambiguous devices receive a support-review notice; nothing is silently reassigned. Verify this flow on a device before distribution.
- Full contact details/public supplier pages and paid-listing eligibility remain separate work. Do not imply all supplier onboarding is finished.

Live payment flags, supplier entitlements, public listings, other websites and live bakery records are unchanged.

Local labelled sample preview verified city/province filtering, a retryable failed save and a successful one-time save that disables duplicates. No live bakery records were written. Three web contract tests and direct migration-SQL checks passed locally.

Legacy recovery tests additionally cover confirmation, record retention, shared/deleted account history and wrong-workspace refusal. These latest additions await CI.
