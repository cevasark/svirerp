# SVIR ERP Memory Bank

This directory maintains the persistent system state, architectural decisions, and domain context for **SVIR ERP**. It serves as the primary context repository for AI-assisted development across sessions.

## Structure
- [`projectbrief.md`](projectbrief.md): Foundation document explaining the organization, core problems, scope, and non-profit ERP goals.
- [`productContext.md`](productContext.md): Operational workflows, key user personas, non-profit business rules, tier policies, fund accounting, and lifetime Total Paid rules and limits.
- [`systemPatterns.md`](systemPatterns.md): Technical architecture, package-by-domain conventions, JPA/Flyway rules, webhook ingress, and frontend patterns.
- [`techContext.md`](techContext.md): Tech stack details (Java 25, Spring Boot 3.5, Angular 21, MariaDB), tooling, local Windows setup instructions, and configuration.
- [`activeContext.md`](activeContext.md): Current branch snapshot, Total Paid aggregation and sorting, V63 collation fix/recovery, recent Zeffy phases, and next steps.
- [`progress.md`](progress.md): Implementation status across business domains, dated full-suite and focused verification results, and migration history through V63.
- [`about.md`](about.md): Audit log tracking memory bank creation and major milestones.
