# Verified desktop release — 27 October 2026

The baker web app, supplier portal and admin were deployed selectively to their existing Xneelo folders. Main and supplier Firebase projects remain separate. No payments or campaign moderation decisions were submitted.

## Main release

- Baker web: announcements with dismiss/scheduling, and public supplier directory search. Live JS index-CnCQdhbj.js and CSS index-BCHv2y7Y.css match the tested local build.
- Admin: native campaign dialog, full private artwork, approval/rejection controls, artwork timeout/retry. Live JS index-CSIMmG4A.js and CSS index-BqepAtuc.css.
- Original policy pages recovered from a hosting backup and their styling restored. Their content still needs review against current functionality.
- Android announcement source is included for the next compatibility/build checkpoint. It has NOT been compiled, installed or distributed. The current phone release is not certified by this record.

## Validation and limits

All three web production builds passed. Signed-in live web checks verified both banners, dismissal persistence in baker web, the directory's empty response, supplier image guidance/previews and real private artwork in the admin dialog. Published directory results and saved-supplier integration remain incomplete. Broader Android/web workflow parity remains unverified.

The public directory URL in web/.env.production contains no credential. Other local environment files, signing keys, previews, archives, tooling and auth stores are excluded from this release commit. Admin Firebase settings are still supplied through the deployment environment; never commit account credentials.

The remote branch's earlier campaign-lightbox implementation was reviewed. This recorded version uses the tested native dialog with focus/Escape behavior and retry handling. No unrelated remote source changes are replaced.

Future releases must keep checkout disabled until merchant configuration, authenticated payment confirmation and subscription lifecycle testing pass. Existing source code alone does not establish payment readiness.
