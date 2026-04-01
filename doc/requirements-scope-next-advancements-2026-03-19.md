# Requirements, Scope, and Near-Term Advancements (2026-03-19)

## Restated Requirements

### Core Business Requirements
- Maintain reliable child attendance operations for daily daycare workflow.
- Provide role-appropriate access for admin, teacher, and parent users.
- Keep billing policy centrally configurable by admin while enforcing server-side authority.
- Support invoice generation from canonical fee policy with predictable due-date and policy rules.
- Preserve auditability and operational safety for catalog changes and payment status transitions.

### Technical Requirements
- Canonical backend execution on Firebase Functions v2 (Node.js 22).
- Firestore as primary operational data source (attendance, children, parents, billing catalog).
- JavaFX desktop app as active admin control plane.
- Emulator-backed billing regression suite as release gate.
- Production smoke checks after deployments for callable and policy integrity.

## Current Scope (As-Is)

### In Scope and Already Delivered
- OTP pre-check and teacher claim assignment flow.
- Chat and attendance push notification triggers.
- Billing catalog retrieval and invoice generation callables.
- Billing policy admin UI with catalog versioning and activation.
- Default transit monthly code configuration from admin policy.
- Fail-fast catalog validation to prevent incomplete invoicing.
- Demo checkout and payment completion workflow for paid-state transition, backed by the internal dummy provider.
- Firestore index deployment for overtime attendance query shape.

### Out of Scope or Partial
- Real payment gateway integration (currently demo checkout path backed by the internal dummy provider).
- Complete payroll processing engine (currently teacher salary config retrieval only).
- Full standardization of callable error contracts and typed responses.
- Consolidated regional alignment for all functions and triggers.
- End-to-end CI/CD gates across JavaFX + Functions + mobile apps in one pipeline.

## Constraints and Assumptions
- Existing repository is multi-surface and currently has many unrelated local changes.
- Production data model includes legacy compatibility fields; backend keeps fallback logic.
- Some workflows depend on normalized phone fields for ownership and identity resolution.
- Firestore index build lag can temporarily affect immediately-post-deploy smoke checks.

## Near-Term Advancements (Priority Ordered)

### 1) Billing and Policy Reliability
- Add a callable health endpoint that reports active catalog validity and pointer consistency.
- Add pre-deploy check script to verify required billing codes and default transit code presence.
- Add post-deploy smoke script with pass/fail exit code for operational handoff.
- Add one release handoff command that runs Java compile plus billing deploy verification steps in sequence.

### 2) Error Contract Standardization
- Introduce a shared error helper for callables with stable reason codes.
- Map all major billing/auth failures to explicit machine-readable error categories.
- Update JavaFX and mobile clients to render user-friendly messages from these codes.

### 3) Security and Guardrails
- Enable optional App Check enforcement for sensitive callables after app rollout readiness.
- Add stricter write guards for billing catalog activation paths (admin-only assertions).
- Add lightweight immutable audit log collection for policy activation events.

### 4) Frontend Maintainability
- Refactor large JavaFX classes into composable presenters/services.
- Normalize naming conventions and module packaging for readability.
- Extract shared visual style constants to reduce duplicated inline style strings.

### 5) Payments and Finance Roadmap
- Replace the current demo checkout flow with real gateway flow (session create, webhook verify, reconciliation).
- Add invoice lifecycle states for partial/failed/refund handling.
- Expand salary module from config retrieval to period payroll calculation and payout export.

## Suggested Execution Sequence (2-Week Practical Plan)
- Week 1:
  - implement billing health callable + standardized error helper
  - add deployment smoke script and integrate into release checklist
  - add admin audit log on catalog activation
- Week 2:
  - begin payment provider integration skeleton
  - refactor one high-churn JavaFX module (BillingPolicyView) into smaller units
  - add CI command that runs Java compile and billing emulator suite together

Update on 2026-04-01:
- Added `npm run ci:billing-gate` to run JavaFX compile plus the dummy billing emulator regression in one command.

## Definition of Done for Next Milestone
- Every release includes:
  - passing Java compile and billing E2E regression
  - automated post-deploy callable smoke success
  - active catalog validity report success
  - no runtime index errors in billing overtime path logs
