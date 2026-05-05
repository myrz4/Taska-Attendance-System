# UAT Questionnaire

Date: 2026-05-05

This questionnaire is for user acceptance testing of the Taska Attendance System across the JavaFX admin app, the teacher Flutter app, and the parent Flutter app.

Use this form after the tester has completed the main tasks for their role.

## How To Answer

- For rating questions, use this scale:
  - `1 = Strongly Disagree`
  - `2 = Disagree`
  - `3 = Neutral`
  - `4 = Agree`
  - `5 = Strongly Agree`
- For workflow checks, mark `Pass`, `Fail`, or `Not Tested`.
- For open questions, write specific examples, screens, and timestamps where possible.

## Tester Details

- Name:
- Role: `Admin` / `Teacher` / `Parent`
- Test date:
- Device used:
- App used:
- Internet connection quality: `Good` / `Average` / `Poor`

## Section A: Questions For All Users

### A1. Access And Reliability

1. I was able to sign in and access the correct part of the system for my role. `1-5`
2. The system clearly showed errors or warnings when something went wrong. `1-5`
3. The system responded fast enough for normal daily use. `1-5`
4. The screen layout was clear and easy to understand. `1-5`
5. The wording used in the system was clear and not confusing. `1-5`
6. I trust the information shown on the screen to be accurate. `1-5`
7. I could complete my main tasks without needing technical help. `1-5`

### A2. General Open Feedback

1. Which task was easiest to complete?
2. Which task was hardest to complete?
3. Did you see any wrong data, missing data, or confusing status labels?
4. Did any page feel slow, blocked, or unresponsive?
5. What is the first improvement you would request before go-live?

## Section B: Admin UAT Questions

Use this section only for admin users testing the JavaFX desktop app.

### B1. Login, Dashboard, And Visibility

1. Login using my admin account worked correctly. `Pass / Fail / Not Tested`
2. After login, the dashboard opened successfully and showed the expected admin menu items. `Pass / Fail / Not Tested`
3. The dashboard summary cards reflected the real attendance situation for the day. `Pass / Fail / Not Tested`
4. The live check-in and check-out lists showed the correct child names and recent activity. `Pass / Fail / Not Tested`
5. I could recognize the most important operational information without extra explanation. `1-5`

### B2. Child, Parent, And Teacher Management

1. I could add a child record without confusion. `Pass / Fail / Not Tested`
2. I could edit an existing child record and the updated details were saved correctly. `Pass / Fail / Not Tested`
3. I could add or update a parent record and linked family information correctly. `Pass / Fail / Not Tested`
4. I could add or update a teacher record and role-related details correctly. `Pass / Fail / Not Tested`
5. Search, filter, or table browsing made it easy to locate the correct record. `1-5`

### B3. Attendance Operations

1. The attendance views matched what teachers and parents had actually done that day. `Pass / Fail / Not Tested`
2. A child who checked in and later checked out still counted as attended for the day. `Pass / Fail / Not Tested`
3. Attendance history and status labels were understandable and correct. `1-5`
4. If I used an attendance correction, override, or review action, the result was clear and believable. `1-5`
5. I would be comfortable using the admin attendance screens during a busy real school day. `1-5`

### B4. Billing And Policy Screens

1. I could open the Billing Ledger successfully. `Pass / Fail / Not Tested`
2. Invoice, payment, and family billing information were easy to understand. `1-5`
3. Search, filtering, and detail viewing in the ledger worked as expected. `Pass / Fail / Not Tested`
4. Export actions produced information that matched what I saw on screen. `Pass / Fail / Not Tested`
5. The Billing Policy screen was understandable for reviewing or updating policy versions. `1-5`

### B5. Admin Open Feedback

1. Which admin screen would require the most staff training?
2. Which admin workflow feels risky or error-prone?
3. Was any information missing from the dashboard, attendance screens, or billing screens?
4. What should be improved before the admin app is considered ready for daily operations?

## Section C: Teacher UAT Questions

Use this section only for teacher users testing the teacher mobile app.

### C1. Sign-In And Daily Start

1. I could sign in and reach the teacher home area without difficulty. `Pass / Fail / Not Tested`
2. The app showed the correct children, attendance information, or class context for my work. `Pass / Fail / Not Tested`
3. The app was easy to use on my phone screen size. `1-5`

### C2. Check-In And Attendance Tracking

1. Recording attendance for a child was straightforward. `1-5`
2. After a child checked in, the dashboard or attendance count updated correctly. `Pass / Fail / Not Tested`
3. Attendance history for a child was easy to find and understand. `1-5`
4. A same-day attendance record still appeared correctly after checkout. `Pass / Fail / Not Tested`
5. Attendance statuses such as present, checked out, or corrected were easy to distinguish. `1-5`

### C3. Parent QR Pickup Flow

1. Scanning a parent pickup QR code was fast and clear. `1-5`
2. The app correctly accepted a valid pickup QR code. `Pass / Fail / Not Tested`
3. The app correctly rejected an expired, reused, or invalid QR code. `Pass / Fail / Not Tested`
4. The verification result clearly showed which child was being checked out. `Pass / Fail / Not Tested`
5. I would trust this flow during a busy pickup period. `1-5`

### C4. Reporting, Communication, And Day-End Use

1. Daily report or attendance review features gave me the information I needed. `1-5`
2. Chat or communication features were easy to use when needed. `1-5`
3. I could complete key end-of-day tasks without confusion. `1-5`
4. The app felt stable enough to rely on during the full school day. `1-5`

### C5. Teacher Open Feedback

1. Which teacher task took longer than expected?
2. Did any child record, status, or attendance history look incorrect?
3. Did the QR scanner or camera flow cause any difficulty?
4. What would make the teacher app easier to use every day?

## Section D: Parent UAT Questions

Use this section only for parent users testing the parent mobile or web app.

### D1. Sign-In, Dashboard, And Basic Navigation

1. I could sign in to the parent app successfully. `Pass / Fail / Not Tested`
2. The dashboard was easy to understand. `1-5`
3. I could quickly find attendance, billing, chat, and pickup features. `1-5`
4. The information shown for my child or family looked correct. `Pass / Fail / Not Tested`

### D2. Attendance And Pickup QR

1. I could tell whether my child was checked in, checked out, or not yet attended. `1-5`
2. The pickup QR feature was easy to find and use. `1-5`
3. The QR was available only when it made sense for pickup. `Pass / Fail / Not Tested`
4. QR expiry or unavailability messages were clear. `1-5`
5. I would feel confident using this QR flow for real pickup. `1-5`

### D3. Billing And Payment Experience

Note: the current payment flow is a demo checkout simulator, not a live bank payment gateway.

1. The Billing & Payments screen was easy to understand. `1-5`
2. I could view my billing summary and current amount due clearly. `Pass / Fail / Not Tested`
3. Billing history and invoice details were easy to follow. `1-5`
4. The checkout simulation steps were understandable from start to finish. `1-5`
5. After payment simulation, the invoice status updated clearly. `Pass / Fail / Not Tested`
6. The family billing wording made sense if more than one child was linked. `1-5 / Not Applicable`

### D4. Communication And Ongoing Use

1. Chat or message features were easy to access. `1-5`
2. Notifications or reminders were useful and understandable. `1-5`
3. I would feel comfortable using this app without calling the school for help. `1-5`
4. Overall, this app would make it easier for me to manage attendance and payments. `1-5`

### D5. Parent Open Feedback

1. Which parent feature gave you the most confidence?
2. Which parent feature felt confusing or unnecessary?
3. Was any billing, attendance, or pickup information unclear?
4. What should be improved before the parent app is released widely?

## Section E: Final Acceptance Summary

1. Did the system support your real work for your role? `Yes / No / Partly`
2. Would you be comfortable using this system in production? `Yes / No / Partly`
3. What are the top 3 issues that must be fixed before production?
4. What are the top 3 things the team should keep as-is?
5. Final recommendation: `Accept` / `Accept With Minor Changes` / `Retest Required`

## Suggested Facilitator Notes

- Ask each tester to perform real role-based tasks before answering.
- Capture screenshots or short videos for every `Fail` answer.
- Record whether the issue is a usability problem, data problem, permission problem, or performance problem.
- Separate feedback about the current demo payment flow from feedback about real payment expectations.