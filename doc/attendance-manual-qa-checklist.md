# Attendance Manual QA Checklist

Date: 2026-04-04

This checklist covers the stabilized attendance flow across the teacher Flutter app, the parent Flutter app, and the JavaFX admin app.

## Scope

- NFC check-in from teacher/admin attendance flow
- Parent pickup QR eligibility and token generation
- Teacher QR scan checkout
- Teacher attendance dashboard and history views
- JavaFX dashboard counts and live attendance lists

## Key Semantic Rule

- `Today attendance` means the child attended on that day.
- A child who checked in and was later checked out must still count toward `today attendance` and must not return to `absent`.
- `Checked out` is a status detail for the record, not a reason to remove the child from the day-level attendance count.

## Recommended Automated Baseline

Run these before manual QA when attendance code changes:

- `flutter analyze lib/teacher_dashboard.dart lib/attendance_list_screen.dart lib/student_attendance_detail_screen.dart`
- `flutter analyze lib/screens/dashboard_page.dart`
- `node --check functions/index.js`

## Teacher App QA

### 1. Dashboard Counters

Files:

- `teacher_app_taskazurah/lib/teacher_dashboard.dart`

Verify:

- After one NFC check-in, `Today's Attendance` increases by 1.
- After the same child is checked out by parent QR, `Today's Attendance` stays at 1.
- `Attendance Overview` shows `Present: 1` and `Absent` reduced accordingly.
- Counts match the JavaFX dashboard for the same dataset.

### 2. QR Checkout Flow

Files:

- `teacher_app_taskazurah/lib/qr_scanner_screen.dart`

Verify:

- Scanning a valid parent QR closes the correct child attendance record.
- The verification result screen shows the child name correctly.
- Re-scanning the same QR after successful checkout is rejected with the correct already-used/already-closed behavior.

### 3. Attendance History Detail Screen

Files:

- `teacher_app_taskazurah/lib/attendance_list_screen.dart`
- `teacher_app_taskazurah/lib/student_attendance_detail_screen.dart`

Verify:

- Opening a child history screen shows the same-day attendance record even when the canonical attendance doc is keyed by NFC UID.
- The same-day record appears when matched through `childId`, `child_id`, `nfc_uid`, or `childRef` aliases.
- A record with check-in and checkout shows status `Checked Out`, not `Absent`.
- A record with check-in only shows `On Time` or `Manual` as appropriate.
- Audit history still opens for the displayed attendance row.

## Parent App QA

### 4. Pickup QR Eligibility

Files:

- `parent_app_taskazurah/lib/screens/dashboard_page.dart`

Verify:

- Pickup QR is unavailable before the child checks in.
- Pickup QR becomes available after the child checks in.
- After teacher QR checkout succeeds, the pickup QR becomes unavailable again with the checked-out message.
- Opening the QR modal does not produce Firestore permission errors.

## JavaFX Admin QA

### 5. Dashboard Snapshot Consistency

Files:

- `src/nfc/AdminDashboardContentSupport.java`
- `src/nfc/AdminDashboardRefreshDataSupport.java`

Verify:

- `Scanned Today` counts a child who checked in and later checked out.
- `Not Yet Scanned` decreases after the first check-in and does not increase again after checkout.
- Check-ins and check-outs both appear in the correct live list boxes.
- The child name displayed in both live lists is correct.

## Data Scenarios To Exercise

- One child checked in only
- One child checked in and then checked out with parent QR
- One child with attendance doc keyed by NFC UID
- One child with legacy numeric `child_id` attendance lookup
- One invalid or expired parent QR token

## Expected Outcomes

- Teacher app, parent app, and JavaFX use the same day-level attendance semantics.
- QR checkout updates the attendance record without breaking history visibility.
- Dashboard counts remain stable after checkout.
- Attendance can be treated as release-stable before moving on to fee-rule and payment work.