# Active Context: SVIR ERP

## Current Branch & State
- **Active Git Branch:** `zeffyAPI`
- **Head Commit:** `e212491` (*"Synching Contacts - Initial Impl. - Phase 5C"*)
- **Working Tree:** Clean, no uncommitted changes.
- **Backend Test Status:** 113 tests running, 0 failures, 0 errors, 0 skipped.
- **Frontend Build Status:** Angular production bundle compiles cleanly via `npm.cmd run build`.

## Recent Major Developments (The Zeffy API Transformation)
The project underwent a fundamental transformation from legacy spreadsheet ingestion to direct API & webhook integration:

1. **Removal of Spreadsheet Import (Migrations V52 & V53):**
   - V52 removed multi-tenant organization scope, enforcing a clean singleton organization profile.
   - V53 dropped obsolete Zeffy spreadsheet import tables (`zeffy_import_row`, `zeffy_import_batch`, and title-based `zeffy_campaign_mapping`).
2. **Phase 1: Campaigns & Mapping (Migration V54):**
   - Introduced `zeffy_campaign` with immutable Zeffy IDs.
   - Operator UI to confirm `APPLY`/`IGNORE`, target `Fund`, target `Account`, and `grants_membership_credit`.
   - Durable sync execution tracking in `zeffy_sync_run`.
3. **Phase 2: Secure Webhook Inbox (Migration V55):**
   - Unauthenticated ingress at `/api/webhooks/zeffy` with raw body HMAC-SHA256 verification (`ZeffyWebhookSignatureVerifier`).
   - Stored in `zeffy_webhook_event` with raw payload, payload SHA-256 hash, and delivery counters.
4. **Phase 3: Automated Payment Application (Migration V56):**
   - Payment-level idempotency via `zeffy_payment`.
   - Single-transaction atomic application: resolves/creates Person, sets Member tier, logs `MemberPayment`, and posts balanced double-entry `JournalEntry` (debiting Zeffy Clearing Account 1020, crediting mapped revenue).
5. **Phase 4: Historical API Payment Synchronization (Migration V57):**
   - Cursor-based pagination (`ZeffyPaymentSyncService`).
   - Two-phase execution: `PREVIEW` run computes anticipated outcomes; `APPLY` run verifies unchanged payloads and commits domain records.
   - Per-payment outcome tracking in `zeffy_sync_payment_result`.
6. **Phase 5A: Payment Lifecycle & State Auditing (Migrations V58, V59):**
   - Tracks updates, deletes, and fetch misses via `zeffy_payment_change`.
   - Records refunds and disputes in `zeffy_refund` and `zeffy_dispute`.
7. **Phase 5B: Payment Financial Corrections (Migration V61):**
   - Handles partial/full refunds and dispute reversals with linked correction journal entries (`corrects_journal_entry_id`).
   - Reverses or reclassifies accounting entries without modifying historical entries.
8. **Deterministic Member Tier Rebuilding (Commit `0e31694`, Migration V60):**
   - Added `source_timestamp` to `member_payment` to store exact external provider transaction times.
   - Implemented `POST /api/members/recompute-tiers` to replay payment histories in chronological order.
9. **Phase 5C: Contact Synchronization (Migration V62, Commit `e212491`):**
   - Added `zeffy_contact` table linking external Zeffy contacts to local `Person` entities.
   - Synchronizes via both cursor-paginated API sync and `contact.created` / `contact.updated` / `contact.deleted` webhooks.
   - Automatic baseline `Follower` tier assignment for new contacts, while safely preserving existing higher tiers (`Member`, `Benefactor`).
   - Enriched person overview (`PersonOverviewService`) displaying batched contact & membership status.

## Current Focus & Next Steps
- Verify integration settings in production / staging environment with live Zeffy credentials.
- Operator validation of Zeffy campaign mapping UI and webhook delivery monitoring.
- Maintain documentation integrity across repository documentation (`README.md`, `ARCHITECTURE.md`, `zeffyIntegration.md`).
- Ensure deployment scripts (`redeploy.sh`) reflect the modern single-tenant and lockfile requirements.
