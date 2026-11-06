# Isolated integration — 6 November 2026

Combines baker PR #39 with the stacked partner PRs #38, #48 and #51 for PR #64. Android/web and legacy-server conflict resolutions preserve the baker integration base; unrelated inherited Android/server changes from the partner branch are excluded.

Security fixes prevent administrators reviewing or recording their own payout and prevent recording a payout for a suspended partner. CI now exercises this integrated partner implementation rather than an older companion commit.

Validation completed locally:
- Functions TypeScript build passed.
- 42 baker/web regression tests passed.
- 11 supplier unit tests passed at supplier PR #9 commit 7f1e3ef4a6e431f6536256eb9ee3b04e32ca8d52.
- Real local Auth/Firestore emulator integration: all three scenarios passed, including baker delivery, supplier delivery, payout concurrent locking, self-review denial, suspended/reversed payout denial and private-record read/write denials.
- Partner policy tests: 15 passed; optional supplier unit fixture skipped in that invocation (supplier unit suite and joint emulator scenario ran separately).

Outstanding: complete dependency security remediation/audits and confirm website build results. Backend npm audit found nine moderate dependency vulnerabilities. This is not an all-green release approval. Existing production provider/refund integration, staged/browser/device acceptance and policy/legal release gates remain.

No deployment, production merge, live Firebase/Xneelo changes or real payment processing is authorized or performed.
