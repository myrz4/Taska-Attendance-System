# Taska Attendance System (JavaFX)


## Firestore Security (Custom Claims)

Your current Firestore Rules are public (`allow read, write: if true`). This is unsafe for a distributed JavaFX app.

This repo includes a locked-down rules file using **Firebase Auth custom claims**:
- `role = "admin"` → full CRUD
- `role = "teacher"` → can read children/parents/teachers and write attendance (claim required)
- Parents are allowed based on your existing derived-login email (`p_<digits>@taskazurah.local`) and phone matching.

### 1) Deploy Firestore rules

From the repo root (recommended). This repo contains a root `firebase.json` + `.firebaserc` so the rules file is inside the deploy project directory (the Firebase CLI blocks using `../firestore.rules`).

```powershell
cd "C:\Users\zafri\Downloads\Taska Attendance System"
npx firebase-tools deploy --only firestore:rules
```

### 2) Set roles (custom claims)

Install dependencies (run at repo root):

```powershell
cd "C:\Users\zafri\Downloads\Taska Attendance System"
npm install
```

Set role by email (run at repo root):

```powershell
npm run set-role -- --serviceAccount .\serviceAccountKey.json --email admin@example.com --role admin
npm run set-role -- --serviceAccount .\serviceAccountKey.json --email teacher@example.com --role teacher
```

Notes:
- The service account JSON must **never** be bundled into the JavaFX EXE. Use it only on your admin machine for deploying rules / setting claims.
- After setting claims, the user must **sign out and sign in again** to refresh the ID token.

This repository is a Java/JavaFX application targeting Java 21. It includes native resources and several external libraries (MySQL Connector/J, jSerialComm, OpenPDF, jBCrypt, OpenCV). This project does not yet contain a full Mavenized layout for native OpenCV binaries; see notes below.

Quick start (Windows):

1. Install JDK 21 and Maven.
2. Set JAVA_HOME to your JDK 21 installation.
3. Build with Maven:

```powershell
mvn -DskipTests package
```

Running the app with JavaFX may require specifying the JavaFX SDK module path if not using the javafx-maven-plugin's auto download. The `javafx-maven-plugin` included in the `pom.xml` will download JavaFX modules for you when you run `mvn javafx:run`.

Notes:
- OpenCV is not added as a maven dependency because it often requires native binaries; you can either add the OpenCV jar to the local lib folder and configure `java.library.path`, or install OpenCV and point to its native libs at runtime.
- The project's sources are under `src/` and compiled output was previously in `bin/`. The `pom.xml` will use Maven's standard `target/` output.

## JavaFX Local Run (Windows)

For the current workspace layout, the fastest local JavaFX flow is to use the helper scripts from the repo root.

Recommended compile command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\compile-javafx.ps1
```

Equivalent VS Code task:

```text
Compile (JavaFX + Firestore)
```

Fallback raw `javac` command if you need to compile manually:

```powershell
javac --module-path "javafx-sdk-21.0.9\lib" `
	--add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base `
	-cp "jar_files/*" `
	-d bin `
	(Get-ChildItem -Path src/nfc -Filter *.java).FullName `
	--release 17
```

Copy runtime assets:

After compiling, copy non-Java assets into `bin\nfc`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\copy-assets.ps1
```

Launch the app:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\run-javafx.ps1
```

If NFC hardware is not connected, start with NFC disabled:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\run-javafx.ps1 -NfcPort disabled
```

Optional NFC overrides:
- PowerShell environment variable: `$env:TASKA_NFC_PORT = 'disabled'`
- Explicit port via helper: `powershell -NoProfile -ExecutionPolicy Bypass -File tools\run-javafx.ps1 -MainClass nfc.AdminDashboard -NfcPort COM4`
- JVM/system property: `-Dtaska.nfc.port=COM4`
- Accepted disable values: `disabled` or `none`

## Billing Release Workflow

Billing rollout now has guarded pre-deploy and post-deploy checks.

Current rollout status and handoff docs:

- Current billing snapshot: `doc/billing-status-2026-04-05.md`
- Operator-only remaining actions: `doc/operator-closeout-2026-04-09.md`
- Parent app validated status: `parent_app_taskazurah/README.md`
- Parent app release manifest refresh command: `npm run manifest:parent-release`
- Android publish/signing handoff: `parent_app_taskazurah/ANDROID-RELEASE-HANDOFF.md`
- iOS completion handoff: `parent_app_taskazurah/IOS-HANDOFF.md`

Current billing stance:

- Billing logic is fixed and validated for the current scope.
- Parent app Android, web, wasm web, and Windows builds are green.
- Parent app card payments now run through Stripe test mode only.
- The only platform still requiring external completion is iOS on a Mac.
- The canonical Cloud Functions deploy target for this repo is `teacher_app_taskazurah/functions`; `parent_app_taskazurah/functions` is a legacy parity mirror and is not used by the repo-level deploy helpers.

Pre-deploy catalog validation:

```powershell
npm run check:billing-predeploy
```

Live post-deploy callable smoke:

```powershell
npm run smoke:postdeploy-billing
```

Recommended single handoff command:

```powershell
npm run release:billing-rollout
```

That command runs JavaFX compile, copies runtime assets, then executes the billing deploy flow with the pre-deploy check before deploy and the live smoke after deploy.

If you only want to validate the local orchestration without deploying functions:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\release-billing-rollout.ps1 -SkipDeploy
```

## Billplz Billing Callback

The parent billing flow now supports Billplz redirect checkout with two backend reconciliation paths:

### Required function environment variables

Create these Firebase Functions secrets before deploying:

```powershell
cd "C:\Users\zafri\Downloads\Taska Attendance System\teacher_app_taskazurah\functions"

firebase functions:secrets:set BILLPLZ_API_KEY
firebase functions:secrets:set BILLPLZ_X_SIGNATURE_KEY
firebase functions:secrets:set STRIPE_SECRET_KEY
npm run deploy
```

Or from the repo root, use the helper script entry:

```powershell
cd "C:\Users\zafri\Downloads\Taska Attendance System"
npm run deploy:billing-functions
```

Manual QA checklist for the current billing, family invoice, JavaFX admin, and dummy payment flows:

- `doc/billing-manual-qa-checklist.md`

If you want the helper to prompt for Billplz and Stripe secrets first:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\deploy-billing-functions.ps1 -SetSecrets
```

Notes:
- `BILLPLZ_API_KEY` is used for bill creation and sync requests.
- `BILLPLZ_X_SIGNATURE_KEY` is required for verifying the signed Billplz callback payload.
- `STRIPE_SECRET_KEY` must be a Stripe test secret (`sk_test_...`) for the parent app PaymentSheet flow.
- The backend binds these through Firebase Functions v2 secret definitions.
- Local emulator and direct test runs can still use temporary `process.env` values when needed.

## Parent App Stripe Test Mode

The parent app reads the Stripe publishable key from a Dart define at launch time.

Recommended local VS Code launch configuration:

- `Run Parent App (Stripe Test Mode)`

Equivalent manual command:

```powershell
cd "C:\Users\zafri\Downloads\Taska Attendance System\parent_app_taskazurah"
flutter run --dart-define=STRIPE_PUBLISHABLE_KEY=pk_test_...
```

Notes:
- Use Stripe test keys only.
- Do not place `sk_test_...` inside Flutter code or Dart defines.
- The backend stores only safe Stripe metadata such as `stripePaymentIntentId`, card brand, and card last four digits.

### Post-deploy rollout verification

From the repo root, verify the live Firestore payment gateway document and the expected callback URL:

```powershell
cd "C:\Users\zafri\Downloads\Taska Attendance System"
npm run verify:billing-rollout
```

If you want to force `billingConfig/paymentGateway.callbackUrl` to the deployed `billingBillplzCallback` URL pattern:

```powershell
node tools/verify-billing-rollout.js --fix-callback-url
```

### Firestore payment gateway config

The functions read payment gateway settings from `billingConfig/paymentGateway`.

Example document:

```json
{
	"provider": "billplz",
	"mode": "redirect",
	"enabled": true,
	"isSandbox": true,
	"collectionId": "your-billplz-collection-id",
	"callbackUrl": "",
	"returnUrl": "https://your-parent-app-return-page"
}
```

Notes:
- If `callbackUrl` is blank, checkout creation will automatically use the deployed `billingBillplzCallback` function URL.
- `returnUrl` is optional for user experience, but Billplz callback handling is the authoritative server-to-server finalization path.
- Billplz may send redirect and callback in either order, so the backend callback handling is idempotent and safe to retry.

To write the payment gateway document from the repo root:

```powershell
npm run set:billing-gateway -- --collectionId YOUR_COLLECTION_ID --returnUrl https://your-app.example/return --clear-callback-url
```

If you do not want real payment yet and want to keep the app on the in-app demo flow:

```powershell
npm run set:billing-gateway -- --provider dummy --mode dummy --enable
```

Shortcut:

```powershell
npm run use:demo-payments
```

Backward-compatible alias:

```powershell
npm run use:dummy-payments
```

To smoke-test the current demo payment setup end to end from the repo root:

```powershell
npm run smoke:demo-billing
```

Backward-compatible alias:

```powershell
npm run smoke:dummy-billing
```

If you want me to convert the source layout to follow standard Maven conventions (move source files to `src/main/java` and resources to `src/main/resources`) I can do that in the next step.
