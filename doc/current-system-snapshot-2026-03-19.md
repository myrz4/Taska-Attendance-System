Current System Snapshot (2026-03-19)

Purpose
- Capture the current architecture after billing/policy implementation.
- Provide an actionable next-phase roadmap for admin, parent, and teacher apps.

Application Inventory
- JavaFX Admin app (desktop): source under src/nfc.
  - Key entry/auth UI: LoginView.java.
  - Admin modules: ChildrenView.java, ParentsPane.java, TeacherManagementView.java, AttendanceView.java, CRUDDialogs.java, TeacherDialog.java.
  - Firestore integration: FirestoreRest.java, FirestoreRestClient.java, FirebaseAuthClient.java, FirebaseFunctionsClient.java.
- Parent Flutter app: parent_app_taskazurah/lib.
  - Entry + route map: main.dart.
  - Billing screens: fees_dashboard.dart, fee_ledger.dart, fee_invoice_details.dart, demo_checkout.dart.
  - Attendance + memory + chat features are also active.
- Teacher Flutter app: teacher_app_taskazurah/lib.
  - Entry/auth gate: main.dart and auth_gate.dart.
  - Teacher operations: attendance_list_screen.dart, daily_report_screen.dart, chat_inbox_screen.dart, salary_tips_screen.dart.
- Canonical Cloud Functions backend: teacher_app_taskazurah/functions/index.js.
  - Deployed callable and trigger set includes OTP gates, role-claim, billing callables, salary callable, chat push trigger, and attendance push trigger.

Backend Design (Current)
- Data platform: Firestore + Firebase Auth + Firebase Cloud Messaging + Cloud Functions v2.
- Authorization model:
  - Firestore rules + custom claims (admin/teacher), with parent ownership by phone-matching strategy.
  - Teacher role assignment handled by callable claimTeacherRole after phone auth.
- Billing architecture:
  - Server-authoritative invoice generation in Cloud Functions.
  - Parent app requests invoice/session creation, then completes the in-app demo checkout via callable.
  - Policy and pricing matrix is centralized in function code and documented in doc/fee-policy-matrix.md.
- Billing callables (canonical):
  - billingGetFeeCatalog
  - billingCreateDemoInvoiceForCurrentMonth
  - billingCreateDemoCheckoutSession
  - billingCompleteDemoCheckoutSession
  - salaryGetTeacherConfigForCurrentUser
- Notification triggers:
  - sendChatNotification (chat message created)
  - notifyParentOnAttendanceChange (attendance writes)

Frontend and UX Structure (Current)
- Parent app UX
  - Auth-gated shell.
  - Primary journeys: dashboard, attendance, fees, memory journey, chat, pickup scanner.
  - Billing journey: ledger -> invoice details -> demo checkout -> paid status reflection.
- Teacher app UX
  - Auth-gated shell.
  - Primary journeys: attendance operations, daily reports, chat inbox/screen, settings, profile, salary/tips view.
  - Push notifications enabled in foreground/background for chat.
- JavaFX admin UX
  - Desktop operational dashboard with CRUD-heavy workflows.
  - Child/parent identity + fee-driver fields are configurable.
  - Teacher salary parameters are configurable and stored.

Scope and Requirement Restatement
- Implemented scope
  - Fee logic aligned to fee guide PDF categories.
  - Registration month counted as monthly fee.
  - Due day policy supports 5th or 7th.
  - Staff/non-staff, age bands, transit modes, overtime, transport, annual/book/insurance, and absence discount logic are modeled.
  - Parent and child registration identity fields are present in admin dialogs.
  - Teacher salary configuration fields are present and retrievable by callable.
- Out-of-scope or partially implemented
  - Real payment gateway integration (currently demo checkout backed by the internal dummy provider).
  - Full payroll computation and payslip workflow.
  - Dedicated admin UI for editable fee catalog/policy table (rates still code-driven).
  - Full cross-app visibility of every fee variable as user-editable controls.

Verified Quality Gates
- Build
  - JavaFX compile + assets copy task succeeds.
- Functions
  - Canonical deploy completed from teacher_app_taskazurah/functions.
  - Unified function inventory verified live in Firebase.
- Billing tests
  - Emulator-backed E2E billing suite passes via root command npm run e2e:billing.
  - Includes January-specific assertions for annual/book/insurance behavior.

Near-Term Advancements (Recommended Order)
1. Fee policy admin editor
- Build a JavaFX policy screen that persists rate tables/version in Firestore (billingCatalog), then switch billing engine to read active catalog instead of hardcoded map.

2. Payroll workflow
- Add monthly payroll generation callable based on salaryBaseSen + overtime buckets.
- Add teacher-facing payslip/history UI and admin reconciliation view.

3. Production billing hardening
- Add immutable invoice snapshots and write-once payment audit trail.
- Add idempotency keys for checkout completion and replay-safe session handling.

4. Real payment adapter
- Keep current invoice/session model and replace the current demo completion path with provider webhooks.
- Preserve existing UI journey and status fields to minimize frontend refactor.

5. Observability and ops
- Add structured function logs per invoice/session id.
- Add a monthly reconciliation report (expected vs paid vs outstanding).

Operational Commands
- Root E2E billing check: npm run e2e:billing
- Canonical functions deploy: from teacher_app_taskazurah/functions run firebase deploy --only functions

Execution Board (Next 8-12 Weeks)

Sizing key
- S: 1-3 dev days
- M: 4-10 dev days
- L: 2-4 dev weeks

Phase 1: Stability and control (do first)
1. P1-1 Fee policy admin editor
- Size: L
- Depends on: none
- Scope: Add JavaFX policy screen to manage fee catalog values/version in Firestore and activate one policy version.
- Deliverables:
  - billingCatalog documents managed by UI
  - active policy version selector
  - audit fields (updatedBy, updatedAt)
- Acceptance criteria:
  - Admin can change monthly/transit/overtime/annual/book/insurance rates without code change
  - New invoice generation uses active catalog version

2. P1-2 Billing engine reads active catalog
- Size: M
- Depends on: P1-1
- Scope: Replace hardcoded rate source in Cloud Functions with Firestore-backed active catalog and fallback safety.
- Deliverables:
  - catalog fetch + in-memory cache in function runtime
  - strict validation for missing required fee codes
- Acceptance criteria:
  - billingCreateDemoInvoiceForCurrentMonth produces identical totals to E2E baseline with same rates
  - Function returns clear errors when catalog is incomplete

3. P1-3 Idempotent checkout completion
- Size: S
- Depends on: none
- Scope: Harden the demo checkout completion path against retries and races. Legacy callable name: billingCompleteDummyCheckoutSession.
- Deliverables:
  - idempotency key/session status guard
  - repeat-safe payment write behavior
- Acceptance criteria:
  - Repeated completion calls do not duplicate payment records

Phase 2: Payroll and operations
4. P2-1 Payroll generation service
- Size: L
- Depends on: none
- Scope: Add monthly payroll computation from salaryBaseSen + overtime buckets + optional adjustments.
- Deliverables:
  - callable/admin job to generate payroll docs
  - per-teacher payroll breakdown lines
- Acceptance criteria:
  - Salary totals match configured teacher salary fields and attendance/overtime inputs

5. P2-2 Teacher payslip/history UI
- Size: M
- Depends on: P2-1
- Scope: Teacher app screen to view payroll history and monthly breakdown.
- Deliverables:
  - payslip list and detail screens
  - empty/error states
- Acceptance criteria:
  - Teacher can view current and prior payroll periods with breakdown details

6. P2-3 Admin reconciliation dashboard
- Size: M
- Depends on: P1-2
- Scope: JavaFX dashboard for expected vs paid vs outstanding invoices by period.
- Deliverables:
  - totals and aging buckets
  - export-ready summary view
- Acceptance criteria:
  - Admin can identify unpaid/partially paid accounts per month in one screen

Phase 3: Payment gateway readiness
7. P3-1 Payment adapter abstraction
- Size: M
- Depends on: P1-3
- Scope: Introduce provider-agnostic checkout/session interface in functions.
- Deliverables:
  - adapter layer preserving current invoice/session schema
  - internal dummy adapter remains as the sandbox-backed demo path
- Acceptance criteria:
  - Existing UI flow unchanged while backend adapter can switch providers

8. P3-2 Webhook and signature verification
- Size: M
- Depends on: P3-1
- Scope: Implement webhook endpoint, signature verification, and payment state transition rules.
- Deliverables:
  - verified webhook ingestion
  - deterministic mapping to payments/invoice status
- Acceptance criteria:
  - Webhook replay/duplication is safely ignored
  - Payment state transitions are auditable

9. P3-3 Production cutover checklist
- Size: S
- Depends on: P3-2
- Scope: Runbook for gateway go-live, rollback, and monitoring thresholds.
- Deliverables:
  - cutover checklist
  - rollback path
  - alert thresholds/log queries
- Acceptance criteria:
  - Team can execute go-live with documented rollback in under 30 minutes

Suggested sequence
1. P1-1
2. P1-2
3. P1-3
4. P2-1
5. P2-2
6. P2-3
7. P3-1
8. P3-2
9. P3-3

Sprint Plan (Weekly)

Week 1-2: Policy editor foundation
- Target items: P1-1 (part 1)
- Milestone checklist:
  - Define Firestore billingCatalog schema and activeVersion pointer.
  - Build JavaFX policy list/edit screen with validation.
  - Persist audit fields (updatedBy, updatedAt, version).
- Validation commands:
  - Build JavaFX: run VS Code task Build (JavaFX + Firestore).
  - Smoke billing tests: npm run e2e:billing
- Exit criteria:
  - Admin can create/edit policy versions and mark one active.

Week 3: Billing engine catalog switch
- Target items: P1-2
- Milestone checklist:
  - Replace hardcoded fee table lookup with active billingCatalog lookup.
  - Add fallback guard for missing required fee codes.
  - Keep response format unchanged for Flutter apps.
- Validation commands:
  - Local syntax check: node --check teacher_app_taskazurah/functions/index.js
  - Emulator tests: npm run e2e:billing
  - Deploy functions: from teacher_app_taskazurah/functions run firebase deploy --only functions
- Exit criteria:
  - Existing billing E2E suite passes using catalog-backed rates.

Week 4: Checkout idempotency hardening
- Target items: P1-3
- Milestone checklist:
  - Add idempotency/session completion guard.
  - Ensure repeated completion attempts are no-op after success.
  - Add test for repeated completion behavior.
- Validation commands:
  - Emulator run: npm run e2e:billing
  - Manual replay check: call completion callable twice on same session and verify single payment record.
- Exit criteria:
  - No duplicate payment docs after repeated completion calls.

Week 5-6: Payroll backend
- Target items: P2-1
- Milestone checklist:
  - Design payroll collection and period model.
  - Implement payroll generation callable/job.
  - Include overtime and manual adjustment support.
- Validation commands:
  - Functions syntax: node --check teacher_app_taskazurah/functions/index.js
  - New payroll emulator tests: run alongside npm run e2e:billing
- Exit criteria:
  - Payroll totals reproducible for sample teachers and periods.

Week 7: Teacher payslip UI
- Target items: P2-2
- Milestone checklist:
  - Add payslip list screen in teacher app.
  - Add payslip detail breakdown screen.
  - Handle loading/empty/error states.
- Validation commands:
  - Flutter analyzer (teacher app): flutter analyze
  - End-to-end spot check with seeded payroll docs.
- Exit criteria:
  - Teacher can view current and past payroll breakdowns.

Week 8: Admin reconciliation dashboard
- Target items: P2-3
- Milestone checklist:
  - Add JavaFX dashboard for expected/paid/outstanding totals.
  - Add monthly filters and aging buckets.
  - Add export-friendly tabular layout.
- Validation commands:
  - Build JavaFX: run VS Code task Build (JavaFX + Firestore).
  - Cross-check totals against invoice/payment docs in Firestore.
- Exit criteria:
  - Monthly receivable status visible in one admin view.

Week 9: Payment adapter abstraction
- Target items: P3-1
- Milestone checklist:
  - Introduce provider-agnostic checkout/session interface.
  - Keep the internal dummy adapter as the default implementation for demo mode.
  - Preserve existing callable contracts.
- Validation commands:
  - Emulator tests: npm run e2e:billing
  - Contract checks for billingCreateDemoCheckoutSession and billingCompleteDemoCheckoutSession outputs, with legacy dummy aliases kept compatible.
- Exit criteria:
  - Swapping adapter does not require frontend route/model changes.

Week 10: Webhook integration and replay safety
- Target items: P3-2
- Milestone checklist:
  - Implement webhook endpoint with signature verification.
  - Add deterministic payment state transitions.
  - Add replay/duplicate event suppression.
- Validation commands:
  - Local webhook simulation tests.
  - Functions logs review: firebase functions:log
- Exit criteria:
  - Duplicate webhook events do not create duplicate payments.

Week 11: Go-live preparation and runbook
- Target items: P3-3
- Milestone checklist:
  - Draft cutover runbook and rollback steps.
  - Define alert thresholds and on-call response actions.
  - Dry-run go-live in staging-like environment.
- Validation commands:
  - Final regression: npm run e2e:billing
  - Deploy verification: npx firebase-tools functions:list
- Exit criteria:
  - Team can execute cutover and rollback from documented steps.

PM View (Compact Tracking Table)

| Week | Owner | Deliverable | Primary Risk | Status |
|---|---|---|---|---|
| 1-2 | JavaFX Admin + Backend | Billing policy editor + active version control | Schema drift between UI and function expectations | Planned |
| 3 | Backend (Functions) | Catalog-backed billing engine | Missing fee code configuration causing runtime failures | Planned |
| 4 | Backend (Functions) | Idempotent checkout completion | Duplicate payment writes under retry/race | Planned |
| 5-6 | Backend (Functions) | Payroll generation callable/job | Incorrect overtime aggregation logic | Planned |
| 7 | Teacher Flutter | Payslip list/detail UI | Incomplete empty/error states and query pagination | Planned |
| 8 | JavaFX Admin | Reconciliation dashboard | Totals mismatch due to data quality in legacy invoices | Planned |
| 9 | Backend (Functions) | Payment adapter abstraction | Contract breaks with existing Flutter billing flow | Planned |
| 10 | Backend (Functions) | Webhook verification + replay protection | Signature verification and idempotency gaps | Planned |
| 11 | Tech Lead + Ops | Go-live runbook + rollback + alerts | Insufficient observability during cutover | Planned |

Status legend
- Planned: not started
- In Progress: active development
- Blocked: waiting dependency/decision
- Done: delivered and validated

Critical Path (Dependency Map)

Primary critical path
1. P1-1 Fee policy admin editor
2. P1-2 Billing engine reads active catalog
3. P1-3 Idempotent checkout completion
4. P3-1 Payment adapter abstraction
5. P3-2 Webhook and signature verification
6. P3-3 Production cutover checklist

Why this is critical
- P1-1 and P1-2 unlock policy-driven billing stability.
- P1-3 is required before safe payment provider integration.
- P3-1 and P3-2 are required before real-money go-live.
- P3-3 is the final operational gate.

Parallel lane A (can run during critical path)
- P2-1 Payroll generation service
- P2-2 Teacher payslip/history UI

Parallel lane B (partially parallel)
- P2-3 Admin reconciliation dashboard
- Dependency note: best started after P1-2 so totals reflect catalog-backed billing.

Highest schedule-risk nodes
- P1-1: schema and UI model finalization (risk of rework across JavaFX and Functions).
- P3-2: webhook verification and replay safety (risk of production payment inconsistency).

Delay impact guidance
- 1 week delay in P1-1 typically shifts entire payment-go-live chain by about 1 week.
- 1 week delay in P3-2 shifts go-live directly by about 1 week (hard blocker).
- Delays in P2 lane usually do not block payment go-live, but can block payroll/reporting milestones.

Control actions to protect timeline
- Freeze billingCatalog schema before P1-1 implementation midpoint.
- Add replay/idempotency tests before starting P3-2 deployment prep.
- Keep weekly regression command as release gate: npm run e2e:billing
