# SVIR ERP Security & Vulnerability Assessment Report

**Date:** 2026-10-14  
**Scope:** SVIR ERP Application Backend (`src/main/java/com/svivanrilski/svirerp`), Authentication & Session Management, Webhook Integrations, Ledger Integrity, and Configuration.  
**Auditor:** Antigravity AI Code Assistant  

---

## 1. Executive Summary

A comprehensive source code security audit was performed across the SVIR ERP codebase. The audit inspected authentication and authorization boundaries, session configuration, OAuth 2.0 integration flows, external webhook handlers, ledger transaction integrity, and input handling.

A total of **6 security findings** were identified:
- **2 High Severity** (OAuth 2.0 CSRF state absence, Unbalanced journal entry persistence)
- **2 Medium Severity** (Unbounded webhook payload handling, In-memory brute force lockout limitations)
- **2 Low / Informational** (Missing session cookie security directives, Spring Security CORS preflight filter bypass)

---

## 2. Summary Table of Findings

| Severity | ID | Category / CWE | Affected Component | Summary |
| :--- | :--- | :--- | :--- | :--- |
| **High** | `SEC-01` | CWE-352: Cross-Site Request Forgery (CSRF) | [GmailOAuthController](file:///c:/Users/ZM/GitHub/svirerp/src/main/java/com/svivanrilski/svirerp/email/GmailOAuthController.java#L43-L68), [GoogleCalendarOAuthController](file:///c:/Users/ZM/GitHub/svirerp/src/main/java/com/svivanrilski/svirerp/event/GoogleCalendarOAuthController.java#L43-L68) | OAuth 2.0 authorization code flow lacks a `state` anti-CSRF parameter bound to the admin session. |
| **High** | `SEC-02` | CWE-840: Business Logic / Ledger Integrity | [FinanceService](file:///c:/Users/ZM/GitHub/svirerp/src/main/java/com/svivanrilski/svirerp/finance/FinanceService.java#L259-L288) | Unbalanced General Journal entries can be directly created as `"posted"` without debit/credit validation. |
| **Medium** | `SEC-03` | CWE-400: Uncontrolled Resource Consumption | [StripeWebhookController](file:///c:/Users/ZM/GitHub/svirerp/src/main/java/com/svivanrilski/svirerp/stripeintegration/StripeWebhookController.java#L33-L35) | Public unauthenticated Stripe webhook endpoint accepts an unbounded `@RequestBody String`. |
| **Medium** | `SEC-04` | CWE-307: Improper Restriction of Excessive Authentication Attempts | [LoginAttemptService](file:///c:/Users/ZM/GitHub/svirerp/src/main/java/com/svivanrilski/svirerp/auth/LoginAttemptService.java#L23-L49) | Lockout tracked solely by IP without header-spoofing protection; unbounded memory growth in internal map. |
| **Low** | `SEC-05` | CWE-614: Sensitive Cookie in HTTPS Session Without 'Secure' Attribute | [application.properties](file:///c:/Users/ZM/GitHub/svirerp/src/main/resources/application.properties#L62-L70) | Missing explicit `Secure`, `HttpOnly`, and `SameSite` flags on session cookies. |
| **Low** | `SEC-06` | CWE-942: Permissive / Inconsistent CORS Policy | [SecurityConfig](file:///c:/Users/ZM/GitHub/svirerp/src/main/java/com/svivanrilski/svirerp/auth/SecurityConfig.java#L89-L135) | `SecurityFilterChain` omits `.cors()`, rejecting preflight `OPTIONS` requests before CORS headers apply. |

---

## 3. Detailed Findings & Remediation Guidance

### SEC-01: Missing `state` Parameter in OAuth 2.0 Flows (CWE-352)

- **Severity:** High  
- **Components:**
  - [GmailOAuthController.java](file:///c:/Users/ZM/GitHub/svirerp/src/main/java/com/svivanrilski/svirerp/email/GmailOAuthController.java#L43-L68)
  - [GoogleCalendarOAuthController.java](file:///c:/Users/ZM/GitHub/svirerp/src/main/java/com/svivanrilski/svirerp/event/GoogleCalendarOAuthController.java#L43-L68)
- **Technical Description:**
  In both controllers, the authorization initiation endpoint (`/authorize`) builds a Google OAuth authorization URL without setting a cryptographically random `state` parameter bound to the user's session:
  ```java
  GoogleAuthorizationCodeRequestUrl url = flow.newAuthorizationUrl()
          .setRedirectUri(redirectUri)
          .setAccessType("offline")
          .setApprovalPrompt("force");
  ```
  In the callback endpoint (`/callback`), the incoming `code` parameter is accepted and immediately exchanged for OAuth tokens without verifying that the response originated from an authorization request started by the same user.
- **Risk & Impact:**
  An attacker can start the OAuth flow with their own Google account, capture the authorization code, and trick an authenticated ERP administrator into navigating to `/api/settings/gmail/callback?code=ATTACKER_CODE`. The ERP application will complete token exchange and bind the organization's outbound email dispatcher or Google Calendar sync to the attacker's Google account.
- **Remediation:**
  1. In the `/authorize` endpoint, generate a secure random token (e.g., `UUID.randomUUID().toString()`), store it in `session.setAttribute("GMAIL_OAUTH_STATE", state)`, and attach `.setState(state)` to the authorization URL.
  2. In the `/callback` endpoint, require `@RequestParam("state") String state`, compare it to `session.getAttribute("GMAIL_OAUTH_STATE")`, and reject the request if the token is missing or mismatched.
  3. Invalidate/remove the session attribute once validation succeeds.

---

### SEC-02: Unbalanced General Journal Entries Can Be Persisted as Posted (CWE-840)

- **Severity:** High  
- **Component:**
  - [FinanceService.java](file:///c:/Users/ZM/GitHub/svirerp/src/main/java/com/svivanrilski/svirerp/finance/FinanceService.java#L259-L288) (`createEntry`, `postEntry`)
- **Technical Description:**
  1. `createEntry` persists a `JournalEntry` directly using values from the client request:
     ```java
     public JournalEntry createEntry(JournalEntry entry) {
         if (entry.getStatus() == null) {
             entry.setStatus("draft");
         }
         ...
     ```
     If the caller passes `"status": "posted"`, the entry is stored as posted without checking debit/credit equality.
  2. `postEntry` checks balance equality using header fields on `JournalEntry`:
     ```java
     if (entry.getTotalDebit().compareTo(entry.getTotalCredit()) != 0) {
         throw new IllegalStateException("Journal entry is not balanced");
     }
     ```
     However, adding or deleting `JournalLine` entities via `addJournalLine` or `deleteJournalLine` does not update `entry.totalDebit` or `entry.totalCredit`. If line items are added or edited, the stored header values can diverge completely from the actual persisted lines.
- **Risk & Impact:**
  Ledger integrity compromise. Unbalanced entries can be recorded in the general ledger, corrupting trial balance reports and financial statements.
- **Remediation:**
  1. Enforce `entry.setStatus("draft")` unconditionally in `createEntry`.
  2. In `postEntry`, load all lines for the entry using `journalLineRepo.findByJournalEntryId(id)`.
  3. Sum line debits and credits dynamically; verify both sums are greater than zero and equal.
  4. Update `entry.setTotalDebit(sumDebit)` and `entry.setTotalCredit(sumCredit)` from the calculated line sums before setting status to `"posted"`.

---

### SEC-03: Uncontrolled Request Body on Public Stripe Webhook (CWE-400)

- **Severity:** Medium  
- **Component:**
  - [StripeWebhookController.java](file:///c:/Users/ZM/GitHub/svirerp/src/main/java/com/svivanrilski/svirerp/stripeintegration/StripeWebhookController.java#L33-L35)
- **Technical Description:**
  The webhook endpoint `/api/webhooks/stripe` is exposed publicly (`permitAll()`) and accepts `@RequestBody String payload` with no size constraint. In contrast, [ZeffyWebhookController.java](file:///c:/Users/ZM/GitHub/svirerp/src/main/java/com/svivanrilski/svirerp/zeffy/ZeffyWebhookController.java#L23) enforces a `MAX_PAYLOAD_BYTES = 1024 * 1024` guard on the raw input stream.
- **Risk & Impact:**
  Denial of Service (DoS). An unauthenticated attacker can stream very large payloads (hundreds of megabytes) into the public webhook URL, causing high garbage collection pressure or JVM `OutOfMemoryError`.
- **Remediation:**
  Read the request payload via `HttpServletRequest.getInputStream()` bounded by a maximum size (e.g. 1 MB / 1,048,576 bytes) prior to invoking `Webhook.constructEvent(...)`, returning HTTP 413 Payload Too Large if exceeded.

---

### SEC-04: Brute-Force Rate Limiting Flaws & Unbounded Memory Growth (CWE-307, CWE-400)

- **Severity:** Medium  
- **Component:**
  - [LoginAttemptService.java](file:///c:/Users/ZM/GitHub/svirerp/src/main/java/com/svivanrilski/svirerp/auth/LoginAttemptService.java#L23-L49)
- **Technical Description:**
  1. Failed attempts are tracked strictly by `request.getRemoteAddr()`. Because `server.forward-headers-strategy=framework` is enabled in [application.properties](file:///c:/Users/ZM/GitHub/svirerp/src/main/resources/application.properties#L62), if an upstream reverse proxy does not scrub or normalize `X-Forwarded-For` headers, an attacker can rotate IP headers to evade lockout.
  2. The internal storage structure `ConcurrentHashMap<String, AttemptCounter> attempts` has no eviction policy or background cleaner thread.
- **Risk & Impact:**
  Attackers can circumvent the 5-attempt brute-force threshold against local admin accounts by rotating IP headers. Furthermore, memory consumption grows monotonically as unique client IPs accumulate in the map over time.
- **Remediation:**
  1. Replace `ConcurrentHashMap` with a bounded cache like Caffeine (`Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(15, TimeUnit.MINUTES).build()`).
  2. Implement dual-key rate limiting (track attempts by both IP and normalized username).

---

### SEC-05: Missing Session Cookie Security Directives (CWE-614)

- **Severity:** Low  
- **Component:**
  - [application.properties](file:///c:/Users/ZM/GitHub/svirerp/src/main/resources/application.properties#L62-L70)
- **Technical Description:**
  The application does not explicitly configure session cookie security flags in `application.properties`.
- **Risk & Impact:**
  If SSL termination is handled at a reverse proxy and the connection to the backend is HTTP, the servlet container may omit `Secure` and `SameSite` attributes on `JSESSIONID`, increasing exposure to session interception or cross-site request attacks.
- **Remediation:**
  Add the following settings to `application.properties`:
  ```properties
  server.servlet.session.cookie.secure=true
  server.servlet.session.cookie.http-only=true
  server.servlet.session.cookie.same-site=lax
  ```

---

### SEC-06: Missing CORS Configuration on Spring Security Filter Chain (CWE-942)

- **Severity:** Low  
- **Component:**
  - [SecurityConfig.java](file:///c:/Users/ZM/GitHub/svirerp/src/main/java/com/svivanrilski/svirerp/auth/SecurityConfig.java#L89-L135)
- **Technical Description:**
  [CorsConfig.java](file:///c:/Users/ZM/GitHub/svirerp/src/main/java/com/svivanrilski/svirerp/common/CorsConfig.java) defines a standalone `CorsFilter` bean. However, Spring Security's `SecurityFilterChain` in `SecurityConfig.java` does not declare `.cors(Customizer.withDefaults())`.
- **Risk & Impact:**
  Cross-origin browser preflight `OPTIONS` requests to authenticated endpoints (`/api/**`) are blocked by Spring Security with HTTP 401 before reaching the CORS filter, preventing legitimate cross-origin requests from completing.
- **Remediation:**
  Explicitly enable CORS within `SecurityConfig.java`:
  ```java
  http
      .cors(Customizer.withDefaults())
      .csrf(...)
  ```

---

## 4. Suggested Implementation Roadmap

1. **Sprint 1 (Immediate - High Severity):**
   - Implement `state` parameter validation in [GmailOAuthController](file:///c:/Users/ZM/GitHub/svirerp/src/main/java/com/svivanrilski/svirerp/email/GmailOAuthController.java) and [GoogleCalendarOAuthController](file:///c:/Users/ZM/GitHub/svirerp/src/main/java/com/svivanrilski/svirerp/event/GoogleCalendarOAuthController.java).
   - Recalculate debit and credit line totals in `FinanceService.postEntry` and force `"draft"` status on `FinanceService.createEntry`.
2. **Sprint 2 (Hardening - Medium & Low Severity):**
   - Introduce bounded payload streaming in [StripeWebhookController](file:///c:/Users/ZM/GitHub/svirerp/src/main/java/com/svivanrilski/svirerp/stripeintegration/StripeWebhookController.java).
   - Adopt Caffeine cache in [LoginAttemptService](file:///c:/Users/ZM/GitHub/svirerp/src/main/java/com/svivanrilski/svirerp/auth/LoginAttemptService.java).
   - Configure session cookie attributes in [application.properties](file:///c:/Users/ZM/GitHub/svirerp/src/main/resources/application.properties) and enable `.cors()` in [SecurityConfig](file:///c:/Users/ZM/GitHub/svirerp/src/main/java/com/svivanrilski/svirerp/auth/SecurityConfig.java).
