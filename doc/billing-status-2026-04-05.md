# Billing Status - 2026-04-05

## Summary

Billing is now functionally complete for the current rollout scope.

Update on 2026-04-11:

- Registered monthly children now bill overtime on the next invoice from the previous closed month instead of the same-period invoice.
- Production overtime calculation was corrected to use Malaysia local wall-clock time explicitly, avoiding UTC-based underbilling on Cloud Functions.
- The JavaFX dashboard refresh path now guards against overlapping async refreshes and uncaught runtime failures, reducing intermittent missing `Scanned Today` and `Not Yet Scanned` cards.

- Live billing logic was fixed so invoices no longer depend on fragile Firestore query paths.
- Taska Zurah fee policy is aligned across the backend, the duplicate parent-app backend copy, and the parent Flutter UI.
- Parent app validation is clean on Windows for Android, standard web, and wasm web builds.
- Payment remains intentionally in dummy mode.
- The only material work still open is iOS completion on a Mac.

## What Changed Since 2026-03-20

### Live backend billing repair

- Replaced billing flows that depended on `collectionGroup("invoices")` and direct invoice `period` queries with lookup-document registries and per-parent in-memory selection.
- Added lookup sync coverage for billing invoices and provider sessions.
- Added a backfill script to populate lookup docs from existing data.
- Repriced the live April 2026 period successfully after the lookup rollout.

### Taska Zurah fee-policy alignment

- Uniform was removed completely.
- Registration month now stacks the base monthly or transit charge plus registration.
- Communication book is registration-only.
- Insurance is registration-only for age 2+.
- January only repeats the yearly annual fee.
- Legacy overnight overtime logic was removed.
- Age handling now follows the current below-48-month rule.

### Closed-month overtime rollout

- Registered monthly children now carry overtime from the previous closed month onto the next invoice, which freezes historical billed periods instead of recalculating from the current invoice month.
- The first overtime cycle for a newly registered monthly child now starts at the child registration date rather than the first day of that month.
- Attendance edits now refresh or adjust the invoice period that actually carries the overtime instead of only touching the attendance month.
- Canonical and legacy-mirror backends were both updated so the invoice metadata, source-period labels, and parent/admin presentation stay aligned.
- Production overtime windows now use explicit Malaysia local time handling, so late pickups recorded in Malaysia no longer disappear when the Cloud Functions runtime is on UTC.

### JavaFX dashboard refresh hardening

- `AdminDashboardContentSupport` now single-flights the Firestore dashboard refresh path.
- Runtime exceptions during async dashboard refresh are now caught and logged instead of silently leaving stale scan counters/cards behind.

### Parent Flutter app cleanup

- Parent billing summary no longer queries invoices by `period`; it resolves the active period from the parent invoice stream in memory.
- The stale default widget test was replaced with billing-specific pure tests.
- Remaining Flutter analyzer findings were cleaned up.
- `flutter_secure_storage` was upgraded to a wasm-compatible release.
- Added `web/firebase-messaging-sw.js` so web Firebase Messaging no longer requests a missing service-worker path during browser startup.
- Replaced leftover `parent_app` shell metadata with Taska Zurah branding across web, Android, Windows, and the iOS display name.
- Prepared the remaining Apple/Linux shell metadata by updating the macOS product name, macOS RunnerTests bundle IDs, iOS bundle name, and Linux window title to Taska Zurah branding.

## Current Validated State

### Teacher/backend billing

- Production repricing path succeeded after the lookup-based fix.
- Shared invoice payment sync now uses the safer coverage model.
- Live rollout verification was rerun on 2026-04-08 with `npm run verify:billing-rollout`; the project is still intentionally on `provider=dummy`, `mode=dummy`, `allowRealProvider=false`, and the check reported no rollout warnings.
- Live post-deploy smoke was rerun on 2026-04-08 with `npm run smoke:postdeploy-billing`; `billingGetHealth`, `billingAdminListCatalogs`, and `billingAdminListAudit` all passed, the active catalog pointer stayed consistent at `8MNFkVIanXDjIxB9Sy0K`, and no required billing codes were missing.
- Dummy-mode billing regression was rerun on 2026-04-08 with `npm run smoke:dummy-billing`; it reaffirmed dummy payment config on the live project and then passed the full Firestore-emulator `e2e:billing` suite, including family invoice aggregation, shared-child payment sync, dummy checkout flow, real-provider lockout, callback idempotency, and January annual-fee-only policy coverage.
- Canonical billing functions were redeployed on 2026-04-11 after the overtime-cycle and timezone fixes; the live post-deploy billing smoke stayed green with payment intentionally still on `provider=dummy`, `mode=dummy`, `allowRealProvider=false`.
- The canonical Firestore-emulator `e2e:billing` suite was rerun on 2026-04-11 and passed with the new carried-overtime cases covering previous-month sourcing, unpaid invoice refresh, paid invoice adjustment recording, and first-cycle registration-date cutoff behavior.
- Live May invoices for existing safe test parents `test` and `1` were refreshed successfully after the rollout, and the zero-overtime registration-cutoff note was removed from invoices that carried no overtime total.
- A synthetic live verification family with April after-hours attendance was repriced for May 2026 after the timezone fix, and the live invoice updated in place to include `overtime_after_530 = 2400` sen and `overtime_8pm_12am = 1300` sen sourced from `2026-04`, confirming the production UTC-vs-Malaysia bug is fixed.

### Parent app

Validated on 2026-04-05:

- `flutter analyze`
- `flutter test`
- `flutter build apk --release`
- `flutter build appbundle --release`
- `flutter build web --release`
- `flutter build web --release --wasm`
- `flutter build windows --release`

Follow-up runtime smoke on 2026-04-09:

- Launching `parent_app_taskazurah/build/windows/x64/runner/Release/parent_app.exe` from the built Windows release output succeeded and the process stayed resident until manually stopped.
- Rebuilt `flutter build web --release --wasm` after adding `web/firebase-messaging-sw.js`; local HTTP smoke returned `200` for both `/` and `/firebase-messaging-sw.js`, and browser asset requests completed without the earlier Firebase Messaging service-worker `404`.
- Rebuilt `flutter build apk --release`, `flutter build appbundle --release`, `flutter build web --release --wasm`, and `flutter build windows --release` after the platform-shell branding cleanup; all four succeeded.
- Verified rebuilt web output now advertises `Taska Zurah Parent App` in `build/web/index.html` and `build/web/manifest.json`.
- Verified rebuilt Windows native shell now reports `Taska Zurah Parent App` as the main window title and EXE file metadata while keeping the existing `parent_app.exe` filename.
- Repo-side macOS and Linux branding cleanup was also applied and passed editor error checks, but those targets were not build-validated from this Windows environment.

Current outputs:

- Android APK: `parent_app_taskazurah/build/app/outputs/flutter-apk/app-release.apk`
- Android app bundle: `parent_app_taskazurah/build/app/outputs/bundle/release/app-release.aab`
- Web build: `parent_app_taskazurah/build/web`
- Windows desktop app: `parent_app_taskazurah/build/windows/x64/runner/Release/parent_app.exe`

Artifact hashes and signer metadata are captured in `parent_app_taskazurah/RELEASE-MANIFEST-2026-04-05.md`.

### JavaFX admin app

- JavaFX compile and helper-run paths were kept working during the billing cleanup.
- Follow-up compile revalidation on 2026-04-09: `powershell -NoProfile -ExecutionPolicy Bypass -File tools/compile-javafx.ps1` exited with code `0`.
- Follow-up runtime smoke on 2026-04-09: `powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-javafx.ps1 -MainClass nfc.AdminDashboard -NfcPort disabled` reached normal idle startup with the expected REST-mode and NFC-disabled messages and shut down cleanly.
- Follow-up dashboard reliability hardening on 2026-04-11: `AdminDashboardContentSupport` now blocks overlapping refreshes and logs runtime refresh failures, targeting the intermittent missing `Scanned Today` / `Not Yet Scanned` bars.
- Follow-up compile revalidation on 2026-04-11: `powershell -NoProfile -ExecutionPolicy Bypass -File tools/compile-javafx.ps1` exited with code `0` after the dashboard refresh guard was added.

## Intentionally Still True

- Payment remains in dummy mode.
- No real provider rollout was enabled.
- The session model still supports a future hosted-payment adapter, but production payment enablement is out of scope for this rollout.

## Remaining Work

### Required to fully finish the current release story

1. Complete iOS setup on a Mac.
2. Add the matching `GoogleService-Info.plist` for the iOS Firebase app.
3. Run `pod install` and finish Xcode signing and capabilities.
4. Validate push and biometrics on a real iPhone.

See `parent_app_taskazurah/IOS-HANDOFF.md` for the exact iOS sequence.

### Optional follow-up

1. If Android store publishing is needed, supply a real release keystore through `parent_app_taskazurah/android/key.properties` instead of the local debug-signing fallback. See `parent_app_taskazurah/ANDROID-RELEASE-HANDOFF.md`.
2. If a real payment rollout is needed later, replace dummy mode with the intended provider path.

Canonical backend note:

- Repo-level deploy and billing helper scripts target `teacher_app_taskazurah/functions`.
- `parent_app_taskazurah/functions` should be treated as a legacy parity mirror, not the active deploy target for this workspace.

## Recommended Status

- Android: builds successfully; production store signing still needs a real release keystore
- Web: ready
- Wasm web: builds successfully, but still merits browser smoke testing before production use
- Windows: builds successfully
- iOS: repo prepared, Mac-side completion still required