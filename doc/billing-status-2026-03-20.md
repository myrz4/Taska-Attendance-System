# Billing Status - 2026-03-20

## Summary

The billing flow is now aligned around family invoices and a realistic dummy payment path.

Update on 2026-03-24:

- Parent billing summary now resolves the current billing record by invoice `period`, not by latest-created invoice.
- JavaFX `Current Month` filtering now prefers invoice `period` over due/payment timestamps, so desktop and Flutter views use the same billing-period semantics.
- Teacher app Firestore permission issues were removed by stopping forbidden `salary` reads and limiting teacher self-updates to `fcmToken` only.
- Root billing rollout verification still reports the live gateway config in intentional `dummy` mode with no rollout warnings.
- JavaFX local compile and helper-script launch paths were revalidated on Windows, including NFC-disabled startup.

Current state:

- Invoices are parent-scoped and support family coverage across multiple linked children.
- Payment remains dummy-only for now.
- The dummy payment path behaves like a real hosted gateway flow rather than an instant toggle.
- Parent Flutter billing screens and JavaFX admin billing screens now use consistent billing language.

## Implemented

### Family invoice model

- Backend creates one invoice per parent per billing period.
- Invoice documents can store:
  - `childIds`
  - `childNames`
  - `billingMeta.invoiceScope = "family"`
- Parent UI and JavaFX admin UI now read and present those fields.

### Dummy payment realism

- Dummy checkout simulates:
  - bank selection
  - credential entry
  - OTP/TAC confirmation
  - async processing
  - settlement sync
- Backend dummy adapter supports a provider-like lifecycle rather than immediate success.

### Parent app updates

- Billing summary, billing history, and billing details screens are family-invoice-aware.
- Stale child-scoped navigation assumptions were removed from parent billing routes.
- Parent settings now use `Billing Reminders` wording.
- The current summary card now loads the invoice for the active billing `period` instead of whichever invoice was created most recently.

### JavaFX admin updates

- Billing Ledger shows aggregated child names for family invoices.
- Billing exports use family-oriented wording and include `childNames` in JSON paths.
- Billing Policy and CRUD dialog wording were normalized to `billing` terminology where appropriate.
- Billing-related JavaFX files touched during this work were cleaned up enough to keep compile/build paths green.
- `Current Month` filtering in the ledger now respects the invoice `period` when available, which keeps desktop filtering aligned with the parent app.

### Teacher app permission hardening

- Teacher profile no longer queries the admin-only `salary` collection.
- Teacher profile no longer writes `tips_total` back into teacher documents from the client.
- Firestore rules allow a teacher to update only their own `teachers/{docId}.fcmToken` when the phone ownership checks match.
- Updated Firestore rules were deployed successfully.

## Validation Completed

### Automated

- `flutter analyze lib/screens/billing_invoice_presenter.dart lib/screens/fees_dashboard.dart lib/screens/fee_ledger.dart lib/screens/fee_invoice_details.dart`
- `flutter analyze lib/screens/parent_profile_page.dart`
- `flutter analyze lib/screens/demo_checkout.dart`
- `flutter analyze lib/screens/fees_dashboard.dart`
- `flutter analyze lib/profile_screen.dart lib/teacher_dashboard.dart`
- `npm run smoke:dummy-billing`
- `npm run verify:billing-rollout`
- `Compile (JavaFX + Firestore)`
- `Build (JavaFX + Firestore)`
- `powershell -NoProfile -ExecutionPolicy Bypass -File tools/compile-javafx.ps1`

### Manual QA guidance

- See `doc/billing-manual-qa-checklist.md`

## Intentionally Not Done Yet

- No real payment provider is enabled for parent checkout.
- Billplz production credentials and rollout are not required while dummy mode remains active.
- The system is prepared for a future real hosted-payment integration, but current behavior is intentionally non-production.

## Recommended Usage Right Now

- Keep payment gateway config in dummy mode.
- Use the billing smoke script before future billing changes:

```powershell
npm run smoke:dummy-billing
```

- Use the manual checklist after UI-affecting billing changes:

```text
doc/billing-manual-qa-checklist.md
```

- For JavaFX local desktop checks on Windows, use the helper flow:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\copy-assets.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\run-javafx.ps1 -NfcPort disabled
```

## Future Work

- Replace the dummy provider with a real provider implementation when rollout is required.
- Run the documented manual QA checklist against real UI sessions and collect any layout or workflow findings.
- If needed later, reduce the final non-blocking editor inspection noise in `BillingLedgerView.java`.