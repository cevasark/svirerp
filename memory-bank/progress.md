# Progress & Status: SVIR ERP

## Domain Feature Status

| Domain | Status | Key Implemented Features | Known Notes / Follow-ups |
|---|---|---|---|
| **Organization & Core Identity** | Complete | Singleton profile (`V52`), Person entity, phone/email/address, overview service (`PersonOverviewService`) | Single-tenant model strictly enforced |
| **Membership & Dues** | Complete | Membership types, member list/detail, contribution logging, `TierCalculator` renewal chaining, manual tier rebuild (`POST /api/members/recompute-tiers`) | Preserves active status for free Followers |
| **Finance & Fund Accounting** | Complete | Chart of accounts (auto-seeded), restricted funds, balanced double-entry journal entries, statement of activities, balance sheet, clearing accounts (1020, 1030) | Open-in-view disabled; fetch graphs required |
| **Governance & Board** | Complete | Trustees with 2-year renewal terms, committees, meeting minutes, action items, projects, project tasks, multi-checklist tracking (`new`/`done`/`skipped`/`reopen`) | Project deletion cascades to tasks and checklists |
| **Events & Church Services** | Complete | Liturgical & community calendar events, sacramental church service details, attendee registrations, event resources, Google Calendar 1-way sync | One-way sync from ERP to Google |
| **Volunteers** | Complete | Volunteer roster, volunteer areas (7 default areas), area assignments, service hour logging & totals | Contact person linking supported |
| **Stripe Integration** | Complete | Signed webhook receiver (`Stripe-Signature`), price-to-fund/account mapping (`StripeProductMapping`), fee accounting, reprocess flow | Handles dues, tickets, donations |
| **Zeffy Integration (API + Webhook)** | Complete (Phases 1–5C) | 1 req/sec pacing, campaign sync & mapping, HMAC-SHA256 webhook inbox, automated payment application, cursor API payment sync (preview/apply), payment lifecycle auditing (changes, refunds, disputes), financial correction journals, contact sync (`zeffy_contact`), follower baseline | Comprehensive 47-class backend suite + 16 test classes |
| **Settings & Security** | Complete | AES-GCM encrypted `app_setting` store, break-glass local admin, Google Workspace OIDC login with `hd` domain restriction, Gmail sending via HTTPS | Secret settings never returned to frontend |

## Test Suite & Build Verification

- **Backend Test Suite:**
  - Total tests: **113 tests**
  - Failures: **0**
  - Errors: **0**
  - Skipped: **0**
  - Key test packages: `zeffyintegration` (16 classes, 65+ assertions), `membership` (summary, tier calculation, renewal chaining), `finance` (income splits, transfers, reports, correction entries), `stripeintegration`, `organization`, `person`, `auth`.
- **Frontend Build:**
  - Angular 21 production build compiles successfully via `npm.cmd run build`.
  - Lazy chunks properly generated for feature routes.
  - Initial bundle size ~777 kB (exceeds default 500 kB warning budget, but fully functional).

## Migrations History Summary (V1 to V62)
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
- **V60:** External `source_timestamp` on `member_payment` for deterministic tier rebuilding.
- **V61:** Phase 5B: Financial correction journals and refund/dispute accounting reversals.
- **V62:** Phase 5C: `zeffy_contact` table, stable contact ID linking, follower tier baseline.
