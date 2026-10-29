# Android version22 reconciliation

AI Studio source export: BatchBoss project, downloaded 29 October 2026.
Archive SHA-256: a8fbefa053a84bf56dd8506da4c037b99ae4043866548063cc8a6805d02dc3ca
Source declares app version22.0 and Room schema14. This commit preserves the Android source baseline; it is not a deployment. The existing web release remains separate.

Client configuration, signing keys, environment secrets and generated build files are excluded. The phone's installation and data remain unchanged.

## Combined development revision

Development app version23.0 / Room schema15 combines version22 business sync with reviewed supplier discovery, bakery account isolation, safe supplier transactions and server-authoritative subscription access.

Schema12->13 retains canonical packaging sync. Schema13->14 repairs the alternate supplier-only schema13 before adding business metadata. Schema14->15 adds supplier sync fields without assigning legacy ownership. Unsupported migration paths fail rather than clearing records.

Migration fixtures derive table/index declarations from the installed app's code, not user data. Tests cover schemas11,12,canonical13,alternate13,14, record preservation, full Room validation and unsupported-version rejection. Run Version22MigrationTest, SupplierCompatibilityTest and SubscriptionAlignmentTest, plus the isolated Firestore transaction tests. Device acceptance and a Play-compatible signing route remain required; no Android distribution is authorised by these passing checks alone.
