# Project Brief: SVIR ERP

## Executive Summary
**SVIR ERP** is a custom, unified Enterprise Resource Planning (ERP) platform developed specifically for **SVIR** (Saint Ivan Rilski Bulgarian Eastern Orthodox Church / Non-profit Organization). It integrates all administrative, operational, membership, governance, financial, event, volunteer, and external donation workflows into a single-tenant, full-stack application.

## Core Problem Statement
Prior to this system, the church/non-profit operated with fragmented, manual processes:
- Member records, annual dues, and tiered memberships were tracked separately from financial accounting.
- Donations and event tickets arrived across multiple channels: cash/checks, Stripe card payments, and Zeffy online giving campaigns.
- Zeffy was previously handled via error-prone spreadsheet export/import cycles.
- Governance (Trustee board meetings, action items, parish projects, checklists) had no structured historical record.
- Financial reporting required complex manual reconciliation across bank accounts, clearing accounts, and restricted project funds.

## Goals & Key Capabilities
1. **Single Unified Source of Truth:** Unify Person profiles, active membership tiers, double-entry financial accounting, and church governance in one installation.
2. **Direct Zeffy API & Webhook Integration:** Replace legacy CSV/Excel imports with automated, durable, signed webhook processing and historical API synchronization.
3. **Automated Membership Tier Calculation:** Provide deterministic tier tracking (`Follower`, `Member`, `Benefactor`) driven by actual qualifying payments and renewal chaining.
4. **GAAP/Non-Profit Fund Accounting:** Strict double-entry ledger with restricted funds ("Projects" in financial context), clearing accounts for online processors (Zeffy 1020, Stripe 1030), and balanced journal entries.
5. **Governance & Board Operations:** Manage Trustees (2-year re-election cycles), Committees, Meeting Minutes, Action Items, and Parish Projects with structured task checklists.
6. **Event & Volunteer Operations:** Manage liturgical and parish calendar events (with Google Calendar one-way sync), attendee registrations, volunteer areas, and logged volunteer service hours.
7. **Secure, Single-Origin Architecture:** Deploy as a single self-contained JAR (Spring Boot serving the Angular SPA from `/` and REST endpoints from `/api/**`) using session-cookie authentication and domain-restricted Google Workspace OIDC login.

## Target Deployment Model
- **Single-Tenant Architecture:** Exactly one Organization record per database instance (enforced at the DB constraint level by Flyway migration V52).
- **Runtime Environment:** Linux/WSL2 host running MariaDB 11.8 / MySQL 8.0, with Caddy reverse proxy handling HTTPS and port routing.
- **Development Environment:** Windows host with JDK 25/26, Node 24+, Angular 21, and Maven 3.9+.
