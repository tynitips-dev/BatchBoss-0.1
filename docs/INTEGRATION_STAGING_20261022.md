# BatchBoss integration staging plan (2026-10-22)

## Repository inventory and verified ancestry
- BatchBoss main repository: `tynitips-dev/BatchBoss-0.1`.
- `codex/web-backend-foundation` is 136 commits ahead and 5 behind `main` (diverged).
- `safety/pro-migration-gate-20261021` is 33 commits ahead of `codex/web-backend-foundation` and contains that branch completely.
- Relative to `main`, safety branch is 169 ahead and 5 behind. The 5 main-only commits must be reviewed and integrated safely, not discarded or force-pushed.
- This integration branch is forked from safety head `ecb55c6795522b45238bffe60d76e2f02ca914bb`. No main merge was performed.
- Backend/entitlement and Android workflows passed at safety head (GitHub actions 39749687415 and 39749687449), but not all Paystack webhook end-to-end cases are tested.

## Order of work
1. Review main-only five commits, paths and changes; reconcile website, admin, backend and Android code. Preserve both versions until conflicts resolved.
2. Verify the current checkout/web app and admin against backend callable interfaces. Run web, Android and Functions tests.
3. Complete **Paystack-only** webhook emulator integration tests, including duplicate delivery, out-of-order events, failure and retry. Treat PayFast code as legacy and do not deploy/enable it.
4. Confirm latest user local changes are pushed. GitHub history cannot prove that there are no unpublished local changes, but user believes all changes were uploaded.
5. Reconcile production Paystack billing records read-only, rehearse migrations on synthetic/staging records, and prepare backup/rollback plan.
6. Only after verification and explicit approval, merge/release.

## Suppliers (NOT YET CONNECTED)
- Separate private repo `tynitips-dev/BatchBoss-suppliers` remains independent.
- Reserve a future **Supplier Login** entry in the public frontend design, but do not create a live route, grant supplier access, move supplier data, or integrate the supplier repo until it is ready.
- Review existing older `SuppliersScreens.kt` in the main app separately to avoid confusing it with the new supplier portal.

**Release hold:** this is development-only. No live Firebase/Paystack changes, production merge or deployment.
