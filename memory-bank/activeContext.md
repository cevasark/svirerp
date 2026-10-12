# Active Context: SVIR ERP

## Current Branch & State
- **Last Updated:** 2026-10-12.
- **Active Git Branch:** `antigravity-free-version-from-zeffyAPI` (branched from `zeffyAPI`)
- **Head Commit at Update:** `1fa5437` (*"Add filter on the Person screen"*).
- **Working Tree:** Members screen email filter + Filter/Reset buttons (uncommitted).
- **Latest Full Backend Verification:** `mvn test` executed 142 tests (0 failures, 0 errors, 10 integration tests skipped requiring MariaDB `TOTAL_PAID_TEST_URL`).
- **Latest Frontend Verification:** Angular production build passed via `npm.cmd --prefix ui run build`.

## Latest Changes: Members Screen Email Filter
- `GET /api/members` accepts optional `email`. When present, `MembershipService.findAllMembers(status, typeId, email, pageable)` uses `MemberSpecifications.filter` (AND of status, membership type, `LOWER(person.email) LIKE` with the same wildcard rules as People via `PersonSpecifications.toWildcardPattern`). Without email, the existing derived queries are used unchanged.
- `MemberRepository` now extends `JpaSpecificationExecutor` and overrides `findAll(Specification, Pageable)` with the `person`/`membershipType` entity graph (required because open-in-view is off). The `totalPaid` ID tie-breaker applies to all paths.
- `MemberListComponent`: Email input, Status and Membership Type dropdowns no longer auto-query; results load on **Filter** click or Enter. **Reset** clears everything. The applied filter is kept in an `appliedFilter` signal so paging/sorting reuse it. Stats-bar links set the dropdowns, clear email, and apply.
- Tests: `MemberSpecificationsTest` (4 tests).

## Latest Changes: People View Backend Filter
- Added backend filtering to the People view (`PersonController`, `PersonOverviewService`, `PersonService`, and `PersonSpecifications`).
- **Name Filter:** One text input comparing against first name, last name, and concatenated full name. Uses wildcard matching (`%` appended to user input if not present; user-entered `%` preserved; case-insensitive).
- **Email Filter:** One text input comparing against email with wildcard matching.
- **Membership Type Filter:** Dropdown with single selection of available membership types. Applied via `EXISTS` subquery on `Member`.
- **Combination & Execution:** Combined via strict `AND` condition. Query executes only after the user clicks the "Filter" button (or hits Enter on an input). Reset button clears filters and resets page to 0. Pagination and sorting preserve active filters.


## Latest Changes: Membership Total Paid
- The Members table now shows **Total Paid** immediately after Expiry Date, formatted as USD with `$0.00` for no payments. The amount belongs to the Person, so it includes lifetime payments across membership records, tiers, statuses, campaigns, and payment purposes.
- V63 adds three derived views: `zeffy_payment_loss`, `person_payment_amount`, and `person_payment_total`. They combine successful Zeffy payments, posted revenue/refund journal lines (manual and Stripe), successful Stripe events awaiting accounting, and standalone completed membership payments. Refunds and lost disputes reduce the amount; processing fees do not.
- Integration links and external references prevent counting membership/accounting copies twice. Zeffy refund/dispute snapshots take precedence over lifecycle rows with the same external ID. Historical attribution uses existing person/member/contact/journal links, unique transaction-reference matches, or an unambiguous normalized email.
- `Member.totalPaid` is a read-only `BigDecimal` backed by a Hibernate `@Formula`. **Sorting is now enabled**, using the desktop column header or mobile Sort by control. The API orders numerically in the database before pagination and appends an ascending member ID tie-breaker when none is supplied. The UI preserves filters and returns to page one when the sort changes.
- The reported MariaDB error 1267 in V63 was reproduced and fixed: comparisons/grouping explicitly use `utf8mb4_general_ci`, and all three views use `CREATE OR REPLACE` for retry after partial DDL. Existing table collations and payment records are unchanged. A mixed-collation migration/replay regression test passes.
- Recovery for a **failed** V63 is documented in [the repository README](../README.md#membership-payment-totals): deploy the corrected build, remove only the failed V63 history entry, and restart. Do not delete successful migration history; checksum repair alone does not update views where the old V63 succeeded. Recovery/deployment on the user's database has not been verified.

## Known Limits
- Totals reflect locally recorded USD payments. Stripe refund webhooks are not imported; Stripe refunds reduce totals when recorded in the local ledger.
- Payments that cannot be attributed to a person are excluded. Independently entered manual finance and membership records without shared source identity remain separate payments.
- Reporting across Zeffy campaign policies does not create business records or change APPLY/IGNORE policy or membership tier eligibility.

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
   - Added `source_created_at` to `member_payment` to store exact external provider transaction times.
   - Implemented `POST /api/members/recompute-tiers` to replay payment histories in chronological order.
9. **Phase 5C: Contact Synchronization (Migration V62, Commit `e212491`):**
   - Added `zeffy_contact` table linking external Zeffy contacts to local `Person` entities.
   - Synchronizes via both cursor-paginated API sync and `contact.created` / `contact.updated` / `contact.deleted` webhooks.
   - Automatic baseline `Follower` tier assignment for new contacts, while safely preserving existing higher tiers (`Member`, `Benefactor`).
   - Enriched person overview (`PersonOverviewService`) displaying batched contact & membership status.

## Current Focus & Next Steps
- Total Paid display, aggregation, mixed-collation fix, and backend sorting are implemented. No additional migration was needed to enable the UI sort controls.
- Package the latest UI build before deployment; the last recorded packaged JAR predates the September 29 sorting change. Apply the failed-V63 recovery only if the target database needs it.
- Verify integration settings in production / staging environment with live Zeffy credentials.
- Operator validation of Zeffy campaign mapping UI and webhook delivery monitoring.
- Maintain documentation integrity across repository documentation (`README.md`, `ARCHITECTURE.md`, `zeffyIntegration.md`).
- Ensure deployment scripts (`redeploy.sh`) reflect the modern single-tenant and lockfile requirements.
