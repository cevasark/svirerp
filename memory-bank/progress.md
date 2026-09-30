# Progress & Status: SVIR ERP

## Domain Feature Status

| Domain | Status | Key Implemented Features | Known Notes / Follow-ups |
|---|---|---|---|
| **Organization & Core Identity** | Complete | Singleton profile (`V52`), Person entity, phone/email/address, overview service (`PersonOverviewService`) | Single-tenant model strictly enforced |
| **Membership & Dues** | Complete | Membership types, member list/detail, contribution logging, `TierCalculator` renewal chaining, manual tier rebuild (`POST /api/members/recompute-tiers`), lifetime Total Paid with desktop/mobile backend sorting | Preserves active status for free Followers; totals are per person, independent of tier |
| **Finance & Fund Accounting** | Complete | Chart of accounts (auto-seeded), restricted funds, balanced double-entry journal entries, statement of activities, balance sheet, clearing accounts (Zeffy 1020, Stripe 1021) | Open-in-view disabled; fetch graphs required |
| **Governance & Board** | Complete | Trustees with 2-year renewal terms, committees, meeting minutes, action items, projects, project tasks, multi-checklist tracking (`new`/`done`/`skipped`/`reopen`) | Project deletion cascades to tasks and checklists |
| **Events & Church Services** | Complete | Liturgical & community calendar events, sacramental church service details, attendee registrations, event resources, Google Calendar 1-way sync | One-way sync from ERP to Google |
| **Volunteers** | Complete | Volunteer roster, volunteer areas (7 default areas), area assignments, service hour logging & totals | Contact person linking supported |
| **Stripe Integration** | Complete | Signed webhook receiver (`Stripe-Signature`), price-to-fund/account mapping (`StripeProductMapping`), fee accounting, reprocess flow | Handles dues, tickets, donations; refund webhooks are not imported, so Total Paid uses locally recorded ledger refunds |
| **Zeffy Integration (API + Webhook)** | Complete (Phases 1–5C) | 1 req/sec pacing, campaign sync & mapping, HMAC-SHA256 webhook inbox, automated payment application, cursor API payment sync (preview/apply), payment lifecycle auditing (changes, refunds, disputes), financial correction journals, contact sync (`zeffy_contact`), follower baseline | Comprehensive 47-class backend suite + 16 test classes |
| **Settings & Security** | Complete | AES-GCM encrypted `app_setting` store, break-glass local admin, Google Workspace OIDC login with `hd` domain restriction, Gmail sending via HTTPS | Secret settings never returned to frontend |

## Test Suite & Build Verification

- **2026-09-27 — Full backend package after the collation fix:** `mvn package` passed **126 tests, 0 failures, 0 errors, 0 skipped** and rebuilt the executable JAR. Real database coverage used disposable MariaDB 11.8, matching the production engine. Evidence: `target/total-paid-collation-fixed.log` (local build output).
- **2026-09-29 — Sorting change:** `mvn.cmd -B -ntp '-Dtest=MembershipServiceTotalPaidTest' test` passed **3 tests, 0 failures, 0 errors, 0 skipped**. Evidence: `target/total-paid-sort-test.log`. This was a focused rerun, not a new full-suite/package run.
- **2026-09-29 — Frontend:** `npm.cmd run build` passed. Existing warnings remain: initial bundle **777.12 kB** versus the **500 kB** budget, and project-detail component styles **2.78 kB** versus **2 kB**. Repackage after this UI build to include the enabled sorting in the executable JAR.
- **Total Paid regression coverage:**
  - `MembershipServiceTotalPaidTest`: 3 tests for stable sort/tie-break handling across filters and read-only JSON serialization.
  - `MemberTotalPaidRepositoryTest`: 9 real MariaDB tests covering sources, fees, refunds/disputes, duplicate prevention, identity resolution/ambiguity, historical memberships, zero totals, and numeric sorting before pagination with filters.
  - `MemberTotalPaidCollationMigrationTest`: 1 real MariaDB test migrating legacy `utf8mb4_general_ci` tables under `utf8mb4_uca1400_ai_ci` defaults, checking refund deduplication and replay of V63. The original migration reproduced error 1267; the corrected migration passes.
- The 10 database tests require `TOTAL_PAID_TEST_URL` pointing to a disposable local test schema and are skipped without it. See [techContext.md](techContext.md) for setup. MySQL 8.4 was not validated successfully: earlier V40/V42/V44 check-constraint migrations behave differently from MariaDB.
- No production deployment or recovery on the user's database was verified during this work.

## Total Paid Implementation Status

- **Implemented:** USD display after Expiry Date; derived person-level aggregation across manual/Stripe/Zeffy sources; net refunds and lost disputes before fees; source-copy deduplication; read-only `Member.totalPaid`; database sorting before pagination with a stable ID tie-breaker; desktop and mobile sort controls preserving filters and resetting the page.
- **Fixed:** V63 mixed-collation failure via explicit comparison/grouping collations and replayable `CREATE OR REPLACE VIEW` definitions. [Failed-V63 recovery instructions](../README.md#membership-payment-totals) are documented; existing data/table collations are not changed.
- **Known data limits:** Locally recorded USD amounts only; Stripe refunds require ledger recording; unattributable payments are excluded; separately entered manual records without shared source identity cannot be deduplicated safely. These reporting changes do not change membership tier or campaign application policy.

## Migrations History Summary (V1 to V63)
- **V1–V39:** Core schema (person, organization, membership, governance, events, finance, bank accounts, settings).
- **V40–V47:** Historical Stripe & Zeffy spreadsheet migrations.
- **V48–V51:** Governance projects and project-level checklists with status tracking.
- **V52:** Removal of multi-tenant organization scoping (singleton organization).
- **V53:** Dropping obsolete Zeffy spreadsheet import tables.
- **V54:** Phase 1: `zeffy_campaign`, `zeffy_sync_run`.
- **V55:** Phase 2: `zeffy_webhook_event` (signed webhook inbox).
- **V56:** Phase 3: `zeffy_payment` (payment idempotency and domain linking).
- **V57:** Phase 4: `zeffy_sync_payment_result` (historical cursor sync).
- **V58–V59:** Phase 5A: `zeffy_payment_change`, `zeffy_refund`, `zeffy_dispute`.
- **V60:** External `source_created_at` on `member_payment` for deterministic tier rebuilding.
- **V61:** Phase 5B: Financial correction journals and refund/dispute accounting reversals.
- **V62:** Phase 5C: `zeffy_contact` table, stable contact ID linking, follower tier baseline.
- **V63:** Lifetime Total Paid derived views (`zeffy_payment_loss`, `person_payment_amount`, `person_payment_total`), including refund/dispute reconciliation and duplicate-source exclusion. Corrected for mixed MariaDB collations and retry after partial migration. Enabling the UI sort required no additional migration.
