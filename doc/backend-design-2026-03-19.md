# Backend Design Snapshot (2026-03-19)

## Runtime and Deployment
- Platform: Firebase Cloud Functions v2 (Node.js 22)
- Canonical source: teacher_app_taskazurah/functions/index.js
- Regions:
  - Callables: asia-southeast1
  - Existing triggers: us-central1 (chat + attendance notification)

## Top-Level Responsibilities
- Authentication support for OTP-gated role access.
- Parent/teacher chat push notifications via FCM.
- Attendance-driven parent notifications.
- Billing catalog delivery and invoice generation.
- Demo checkout/payment completion pipeline for invoice lifecycle, backed by the internal dummy provider.
- Teacher salary configuration read for authenticated teacher user.

## Exposed Callable Functions
- canRequestOtp
  - Input: phone, kind (teacher or parent)
  - Output: allow/deny with reason
  - Behavior: validates registration existence by normalized phone fields.

- claimTeacherRole
  - Requires auth.
  - Behavior: checks teacher registration via phone and sets or clears custom claim role=teacher.
  - Guardrail: never overwrites existing admin role.

- billingGetFeeCatalog
  - Requires auth.
  - Returns active fee table, policy metadata, and default transit monthly code.
  - Source precedence:
    1) billingConfig/current pointer and activeCatalogId
    2) billingCatalog active document
    3) built-in PDF fallback table

- billingCreateDemoInvoiceForCurrentMonth
  - Requires auth.
  - Verifies caller owns parent account by phone matching.
  - Idempotent per parent+period (returns existing invoice if already generated).
  - Computes monthly invoice items using backend-authoritative policy logic.

- billingCreateDemoCheckoutSession
  - Preferred callable name for the current demo checkout provider.
  - Legacy-compatible alias: billingCreateDummyCheckoutSession.
  - Requires auth and parent ownership.
  - Creates pending checkout session for unpaid invoice.

- billingCompleteDemoCheckoutSession
  - Preferred callable name for the current demo checkout provider.
  - Legacy-compatible alias: billingCompleteDummyCheckoutSession.
  - Requires auth and parent ownership.
  - Transactionally marks session succeeded, creates payment record, and marks invoice paid.

- salaryGetTeacherConfigForCurrentUser
  - Requires auth.
  - Resolves teacher by normalized phone and returns salary/overtime configuration.

## Firestore Trigger Functions
- notifyParentOnAttendanceChange
  - Trigger: attendance/{recordId} on write.
  - Behavior: derives check-in/check-out narrative and sends parent FCM notification.

- sendChatNotification
  - Trigger: chats/{chatId}/messages/{messageId} on create.
  - Behavior: determines opposite chat party and sends FCM message.
  - Includes backward-compatible derivation of parentId/teacherId from reference paths.

## Billing Architecture

### Catalog Model
- billingConfig/current:
  - activeCatalogId
  - defaultTransitMonthlyCode
- billingCatalog documents:
  - version
  - active
  - table (code -> {staff, nonstaff})
  - defaultTransitMonthlyCode (optional)

### Catalog Loading and Cache
- loadActiveFeeCatalog uses 60-second in-memory cache.
- Cache invalidation in tests via __resetBillingCatalogCacheForTests.

### Catalog Safety Guardrails
- assertInvoiceCatalogReady enforces required pricing keys before invoice generation.
- Validation fails with failed-precondition when required codes are missing.
- Required coverage includes:
  - monthly fulltime codes
  - registration codes
  - overtime tiers
  - transport, annual, comms book, insurance
  - at least one transit monthly code
  - valid default transit monthly code present in table

### Invoice Derivation Rules
- Base fee source is backend-derived from child profile fields:
  - feePlan monthly/transit
  - careType fallback compatibility
  - age band (3m-2y, 2y-4y)
  - payer type from child.staffChild, fallback to parent payerType
- Registration month logic replaces normal monthly base with registration fee.
- January policy add-ons:
  - annual_fee_yearly
  - comms_book_4months (also in May and September)
  - insurance_yearly_age2plus (age >= 24 months)
- Optional add-ons:
  - transport_tadika_month
  - overtime from attendance aggregation (with manual override support)
- Discount:
  - 10% discount for absence >14 days with letter.
- Due date policy:
  - day 5 or 7 based on child.billingDueDay (fallback 7).

## Data Access and Security Pattern
- requireAuth enforces authentication on callables.
- assertParentOwnerByPhone enforces parent ownership by matching auth phone against parent normalized phone fields.
- Teacher salary callable uses normalized phone lookup path:
  - phoneE164 -> phoneTail -> local phone.

## Production Notes from Current Rollout
- Active catalog mode is enabled in production.
- defaultTransitMonthlyCode is configured and validated in active table.
- Overtime query index for attendance childId+date has been deployed.

## Current Gaps and Next Hardening Opportunities
- Formal error contract standardization (typed code enums) across all callables.
- Optional App Check enforcement for sensitive callables.
- Move trigger regions toward a single regional strategy where feasible.
- Add automated deploy gate that verifies required Firestore indexes are ready before smoke tests.
