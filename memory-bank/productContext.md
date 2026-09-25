# Product Context: SVIR ERP

## Background & Operational Needs
SVIR (Saint Ivan Rilski) is a parish community and non-profit organization whose staff and board of trustees oversee a complex range of community activities:
- Religious services, liturgical calendar scheduling, sacramental requests (weddings, baptisms, funerals, memorials).
- Community events, festival ticket sales, hall rentals, and volunteer coordination.
- Board governance, monthly trustee meetings, formal voting membership, resolutions, and parish enhancement projects.
- Annual stewardship campaigns, general plate donations, and restricted capital campaigns.

## Core Domain Personas & Roles
1. **Parish Administrators & Office Staff:**
   - Maintain Person directory and contact details.
   - Record manual donations, cash/check contributions, and member dues.
   - Schedule calendar events, register attendees, assign volunteers.
   - Review incoming Zeffy and Stripe transactions, map campaigns, and resolve unmapped items.
2. **Treasurer & Finance Officers:**
   - Manage chart of accounts, restricted funds, bank accounts, and journal entries.
   - Reconcile online clearing accounts (Zeffy Account 1020, Stripe Account 1030) into the primary Checking account upon bank deposit.
   - Run Statement of Financial Position (Balance Sheet), Statement of Activities (Income Statement), and Fund Summaries.
   - Audit corrections, refunds, disputes, and vendor payables.
3. **Board of Trustees & Committee Chairs:**
   - Record board minutes and track open action items.
   - Oversee parish enhancement projects, assign project leads, and track multi-phase project checklists (`new`, `done`, `skipped`, `reopen`).
   - Monitor voting eligibility based on active membership tier status.
4. **Local System Administrator (Break-Glass):**
   - Configure encrypted API keys (Zeffy bearer key, webhook secret, Stripe keys, Gmail OAuth, Google Calendar OAuth).
   - Manage application-wide settings and organization profile.

## Key Business Rules & Policies
1. **Membership Tier Hierarchy & Lifecycle (`TierCalculator`):**
   - **Follower ($0 or < $150 history):** Free tier, active indefinitely once granted by staff or Zeffy contact sync, preserving active status until explicitly deactivated.
   - **Member ($150 - $999 single qualifying payment):** Grants 1 full year of active voting membership.
   - **Benefactor ($1,000+ single qualifying payment):** Grants 1 full year of premier active benefactor membership.
   - **Renewal Chaining:** A qualifying payment received before current expiry extends the expiration date by 1 year. A payment received after lapse establishes a new 1-year window from the payment date.
   - **Historical Rebuild:** Recomputing membership tiers replays all recorded payments chronologically using their original source transaction timestamps (`source_timestamp`).
2. **Segregation of Manual vs. Automated Operations:**
   - Manual contributions and manual journal entries entered by staff are strictly preserved. Automated sync will never overwrite, reverse, or delete manual accounting entries.
   - Staff deliberately maintain manual ledger entries separately from automated processor sync.
3. **Zeffy Integration Philosophy:**
   - Zeffy is an external intake stream, never the master of internal domain models.
   - Stable external IDs (`zeffy_campaign_id`, `zeffy_payment_id`, `zeffy_contact_id`) are immutable identifiers.
   - No payment creates ledger or membership effects without explicit campaign mapping (`APPLY` vs `IGNORE`, assigned Fund, assigned Revenue Account, and `grants_membership_credit` flag).
   - Deletions in Zeffy result in tombstones (`deleted_at`) in integration tracking tables; they never delete local Person, Member, or JournalEntry records.
   - Inbound webhooks must be verified using HMAC-SHA256 (`Zeffy-Signature`). Raw payloads are durably stored in `zeffy_webhook_event` for audit and recovery.
4. **Fund Accounting (Restricted Funds):**
   - Every financial transaction belongs to a `Fund` (e.g., General Fund, Building Fund, Memorial Fund).
   - Revenue and expense accounts are segregated by fund, allowing exact balance calculation per restricted project.
   - Transfer transactions allow moving money from processor clearing accounts (e.g. Undeposited Funds 1020) to operating Checking without impacting revenue accounts.
