# Billing Manual QA Checklist

Date: 2026-03-20

This checklist covers the current billing work across the parent Flutter app, the JavaFX admin app, and the dummy payment flow.

## Scope

- Family invoice presentation in parent and admin UI
- Dummy payment flow realism and payment state transitions
- JavaFX admin billing ledger, exports, and billing policy catalog actions
- Parent profile/settings wording updates related to billing notifications

## Automated Baseline

These checks already passed before running manual QA:

- `flutter analyze lib/screens/billing_invoice_presenter.dart lib/screens/fees_dashboard.dart lib/screens/fee_ledger.dart lib/screens/fee_invoice_details.dart`
- `flutter analyze lib/screens/parent_profile_page.dart`
- `npm run smoke:postdeploy-billing`
- `npm run smoke:dummy-billing`
- `Build (JavaFX + Firestore)`

## Parent App QA

### 1. Billing Summary

File:
- `parent_app_taskazurah/lib/screens/fees_dashboard.dart`

Verify:
- The screen title reads `Billing & Payments`.
- The latest invoice summary appears without assuming a single child.
- A family invoice shows family-oriented supporting text rather than a single-child label.
- The current billing summary is based on the active invoice `period`, not merely the latest-created invoice.
- The due amount and payment state are displayed correctly for the active billing period.
- The demo invoice action creates a new billing record for the logged-in parent.

### 2. Billing History

File:
- `parent_app_taskazurah/lib/screens/fee_ledger.dart`

Verify:
- The screen title reads `Billing History`.
- Invoice rows are ordered sensibly by latest activity/creation time.
- Family invoices show combined child names or family coverage text.
- Payment states render correctly for paid, pending, unpaid, and overdue records.
- Opening a record routes correctly into invoice details.

### 3. Billing Details

File:
- `parent_app_taskazurah/lib/screens/fee_invoice_details.dart`

Verify:
- The screen title reads `Billing Details`.
- `Invoice Scope` is shown when applicable.
- Child coverage text is correct for family invoices.
- Itemized charges, totals, receipt data, and payment information are shown correctly.
- The pay button is available only when appropriate.

### 4. Dummy Checkout Flow

File:
- `parent_app_taskazurah/lib/screens/demo_checkout.dart`

Verify:
- The user can progress through bank selection, credentials, OTP, processing, and final state.
- The flow feels like a hosted payment simulation rather than an instant mock toggle.
- Status polling updates the UI until payment settles.
- Success returns the user to invoice details with updated payment status.
- Failed or expired states are handled clearly.

### 5. Dummy-Only Rollout Safety

File:
- `parent_app_taskazurah/lib/screens/fee_invoice_details.dart`

Verify:
- Non-dummy checkout responses are rejected with a clear dummy-simulator-only message.
- Invoice details only route into the demo checkout simulator.
- Returning from the demo simulator refreshes invoice state correctly.

### 6. Parent Profile Billing Reminder

File:
- `parent_app_taskazurah/lib/screens/parent_profile_page.dart`

Verify:
- The notifications section now says `Billing Reminders`.
- The toggle still maps to the existing notifications data without regressions.

## JavaFX Admin QA

### 7. Billing Ledger Entry Point

Files:
- `src/nfc/AdminDashboard.java`
- `src/nfc/BillingLedgerView.java`

Verify:
- Admin users can open `Billing Ledger` from the sidebar.
- Teacher-only users do not see admin-only billing controls.
- The ledger loads invoice and payment data successfully.

### 8. Billing Ledger Family Invoice Display

File:
- `src/nfc/BillingLedgerView.java`

Verify:
- Table rows display aggregated child names for family invoices.
- The details pane uses the same aggregated child display.
- Search/filter behavior still works with multi-child display text.
- Parent summary sections still compute correctly.
- `Current Month` filtering reflects the invoice `period` rather than drifting with payment or creation timestamps.

### 9. Billing Ledger Export Paths

File:
- `src/nfc/BillingLedgerView.java`

Verify:
- TXT export uses `Student(s)` wording.
- HTML export uses `Family Billing and Payment Record` wording where appropriate.
- JSON export includes `childNames` for family invoices.
- Exported parent summaries still match the filtered UI state.

### 10. Billing Policy Catalog

File:
- `src/nfc/BillingPolicyView.java`

Verify:
- The catalog screen loads available versions.
- Health status text uses `billing codes` wording.
- Save and activate actions still work.
- Audit log loading and export still work.

### 11. Admin Parent/Child Dialog Wording

File:
- `src/nfc/CRUDDialogs.java`

Verify:
- Child dialog shows `Billing Plan (Monthly / Transit)`.
- Parent notification preferences show `Billing` instead of `Fees`.
- Child save, parent save, admin save, and admin delete flows still show user-facing error dialogs if operations fail.

## Data Scenarios To Exercise

Use these scenarios during manual QA where possible:

- One parent with one child and one unpaid invoice
- One parent with multiple linked children and a family invoice
- One paid invoice with receipt information
- One overdue unpaid invoice
- One dummy checkout session that settles successfully
- One dummy checkout session that expires or fails

## Expected Outcomes

- Parent billing UI stays parent-scoped and family-invoice-aware.
- Parent billing summary uses current billing-period semantics consistently.
- Dummy payment remains non-production but realistic in flow and state transitions.
- JavaFX admin UI uses consistent billing language.
- JavaFX ledger/export surfaces reflect family child coverage correctly.
- JavaFX current-month filtering matches the billing-period model used by the parent app.
- Automated checks remain green after any follow-up UI fixes.