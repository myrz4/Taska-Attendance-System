# Frontend and UX Structure Snapshot (2026-03-19)

## Frontend Surfaces in Scope
- JavaFX desktop admin app (primary operational UI).
- Flutter parent app (separate codebase, integrated with same backend).
- Flutter teacher app (separate codebase, integrated with same backend).

This snapshot focuses on the JavaFX admin UX, which drives policy and operational workflows currently validated in production.

## JavaFX App Entry and Launch Flow
- Entry point: src/nfc/LoginView.java
- Login window presents themed visual shell with:
  - green/yellow brand frame motif
  - prominent logo and card-based username/password input
- Successful login routes users into AdminDashboard.

## Main Shell and Navigation
- Main shell: src/nfc/AdminDashboard.java
- Layout:
  - left sidebar for role-aware navigation
  - center content pane as dynamic module host (StackPane)
- Primary navigation targets:
  - Dashboard
  - Attendance
  - Generate Report (Daily, Monthly)
  - Children and Parents
  - Admins
  - Teachers
  - Billing Policy
  - Logout
- Role behavior:
  - teacher users have admin-only modules hidden from sidebar.

## Screen Modules and Responsibilities

### Dashboard Module
- Host method: loadDashboardContent in AdminDashboard.
- Functions:
  - live summary of scanned vs not scanned children
  - check-in/check-out activity panes
  - clock/date header widgets
  - announcement section
- Data source:
  - Firestore reads via FirestoreRestClient (asynchronous refresh).

### Attendance Module
- Screen class: src/nfc/AttendanceView.java
- UI style:
  - table-driven operational list with actions per row
- Features:
  - date filtering/navigation
  - attendance status changes
  - record-oriented actions (including image/file attachment flow)

### Reports
- Daily report: src/nfc/dailyReport.java
- Monthly report: src/nfc/monthlyReport.java
- Behavior:
  - tabular preview and PDF export flow for operational reporting.

### Children and Parents Module
- Parent host: src/nfc/ChildrenView.java
- Structure:
  - TabPane with separate tabs for Children and Parents
- Child operations:
  - CRUD + table listing + NFC and parent linkage visibility
- Parent operations:
  - Embedded pane src/nfc/ParentsPane.java
  - CRUD with relationship and child-link awareness

### Dialog Layer for CRUD
- Shared dialog coordinator: src/nfc/CRUDDialogs.java
- Child dialog updates in current design:
  - simplified fee input path using feePlan (monthly/transit)
  - staffChild toggle as authoritative payer signal
- Parent dialog:
  - relationship details, multi-child linking, and notification preference fields.

### Teachers and Admin Management
- Teachers module: src/nfc/TeacherManagementView.java + src/nfc/TeacherDialog.java
- Admin staff module: src/nfc/StaffManagementView.java
- Purpose:
  - user profile and operational account maintenance from desktop UI.

### Billing Policy Module
- Screen class: src/nfc/BillingPolicyView.java
- Key UX capabilities:
  - load default catalog template
  - browse/select catalog versions
  - edit per-code staff/nonstaff rates
  - validate health against required fee code matrix
  - save as new catalog version
  - activate selected catalog and update billing pointer
  - configure default transit monthly code
  - export health reports as TXT/JSON
- This is the administrative control surface for the backend catalog engine.

## UX Patterns and Interaction Conventions
- Visual language:
  - bright green/yellow playful theme with rounded controls
  - large display typography for module headings
- Interaction style:
  - table-centric CRUD for operational entities
  - modal dialogs for add/edit actions
  - explicit action buttons instead of hidden gestures
- Feedback:
  - alerts for success/error and confirmations for destructive actions.

## Data and Service Integration from UI
- Firestore integration path:
  - FirestoreRestClient and FirestoreRest wrappers
- Callable usage path:
  - FirebaseFunctionsClient for cloud callable invocations
- Auth context:
  - session/role context from UserSession
- Asset and media helpers:
  - ImageLoader, ImageCache, FirebaseStorageRest

## Current UX Strengths
- Clear module segmentation in the admin shell.
- Strong operational visibility for attendance and daily activities.
- Billing policy controls are now aligned with backend-authoritative billing logic.
- Role-based hiding prevents teacher users from seeing admin-only tools.

## Current UX Risks and Refinement Opportunities
- Large class files combine UI composition and data orchestration, increasing maintenance load.
- Inconsistent naming conventions (example: dailyReport class casing) reduce readability.
- More centralized reusable components could reduce repeated styling and button setup code.
- Additional in-app validation messages for catalog and invoice edge cases can further reduce operator error.
