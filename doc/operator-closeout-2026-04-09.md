# Operator Closeout - 2026-04-09

This file lists only the remaining tasks that still require a human operator after the repo-side and Windows-verifiable work was completed.

## Repo-Side Status

- Billing backend rollout checks are green.
- Dummy-mode billing smoke and E2E coverage are green.
- JavaFX compile and AdminDashboard runtime smoke are green.
- Parent app Android APK, Android AAB, web, wasm web, and Windows builds are green.
- Parent app release manifest refresh is automated through `npm run manifest:parent-release`.
- Canonical Cloud Functions deploy target is `teacher_app_taskazurah/functions`.

## Required Human Tasks

### 1. Manual Windows QA

Run the billing and admin walkthrough from `doc/billing-manual-qa-checklist.md`.

Highest-priority checks:

1. Parent billing summary, history, and invoice details.
2. Dummy checkout success and failure/expiry behavior.
3. JavaFX Billing Ledger family invoice display and exports.
4. JavaFX Billing Policy catalog save/activate and audit log behavior.

Suggested local commands before the walkthrough if needed:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\run-javafx.ps1 -MainClass nfc.AdminDashboard -NfcPort disabled
```

Use the already-built parent app artifacts or rerun the documented Flutter build commands from `parent_app_taskazurah/README.md` if you want a fresh local build first.

### 2. Mac-Side iOS Completion

Finish the sequence in `parent_app_taskazurah/IOS-HANDOFF.md`.

Required items:

1. Add `ios/Runner/GoogleService-Info.plist` for `com.example.parentAppFixed`.
2. Run `flutter pub get`.
3. Run `pod install` inside `ios/`.
4. Open `ios/Runner.xcworkspace` in Xcode.
5. Confirm signing, push notification capability, background modes, and provisioning.
6. Validate push and biometrics on a real iPhone.

## Optional Human Tasks

### Android Publishing

Only needed if you plan to distribute beyond local/internal use.

1. Create `parent_app_taskazurah/android/key.properties` from `android/key.properties.example`.
2. Supply the real release keystore.
3. Rebuild the APK/AAB so they are no longer debug-signed.

See `parent_app_taskazurah/ANDROID-RELEASE-HANDOFF.md`.

## If Manual QA Finds Issues

After any code changes or rebuilt release artifacts, refresh the parent app manifest snapshot from the repo root:

```powershell
npm run manifest:parent-release
```