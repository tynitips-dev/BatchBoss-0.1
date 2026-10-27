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

Android account-isolation, migration, parsing, shared-key and canonical-description tests are added under SupplierCompatibilityTest. Local Gradle 9.3.1 cannot configure the project because it reports the project directory is not writable. The new CI workflow compiles Android and runs only these isolated supplier tests without Google service configuration or live Firebase connections. No phone build is certified until those checks pass.

## Required before release

- Resolve Android compilation/CI errors and run the new tests.
- Verify two-way save/favourite/edit/delete and account switching with designated test records; test directory paging and failed writes without publishing real suppliers or enabling charges.
- Older manual suppliers were stored with userId=0. The migration retains them but does not guess their owner or expose them to every account. Provide an explicitly authorized ownership-recovery path before distributing this Android update.
- Full contact details/public supplier pages and paid-listing eligibility remain separate work. Do not imply all supplier onboarding is finished.

Live payment flags, supplier entitlements, public listings, other websites and live bakery records are unchanged.
