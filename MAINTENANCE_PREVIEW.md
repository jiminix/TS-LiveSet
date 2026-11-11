# Maintenance preview (non-production)

Repository: TS-LiveSet
Technology: Android + GitHub Pages viewer

## Objective
Prepare a reversible, opt-in maintenance mode on this isolated branch.

## Implementation plan
Plan a maintenance landing page for viewer-ios and an opt-in Android maintenance screen. Keep registry and existing installations intact.

## Safety constraints
- Do not corrupt or delete application source files, data, credentials, or history.
- Do not merge to main or deploy to production automatically.
- Keep the default behavior unchanged until a separate reviewed release.
- Verify builds and provide rollback instructions before any rollout.

## Status
Planning document only. No executable application behavior has changed.
