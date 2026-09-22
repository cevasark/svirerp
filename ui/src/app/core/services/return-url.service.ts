import { Injectable } from '@angular/core';

const STORAGE_KEY = 'returnUrl';

/**
 * Carries the URL an unauthenticated user was trying to reach (e.g. a shared link to a Project or
 * Meeting Minutes page) across the login flow, so they land back on it instead of the dashboard.
 *
 * Backed by `sessionStorage`, not an Angular/router-level value or a query param, for two reasons:
 * - Google OAuth is a full-page redirect away to accounts.google.com and back — any in-memory
 *   Angular state is gone by the time the browser returns, but sessionStorage (scoped to this tab
 *   + origin) survives a full page reload, so it's the only thing that reliably makes the round
 *   trip.
 * - Never appearing in the URL bar as a `?returnUrl=` query param avoids any open-redirect concern
 *   from a crafted link — the only place a value is ever written here is `state.url`, which the
 *   Angular Router itself already resolved as an internal path.
 *
 * `consume()` is one-shot (removes the key on read), so it can't hijack unrelated navigation after
 * the first authenticated route activation following login — see `authGuard`.
 */
@Injectable({ providedIn: 'root' })
export class ReturnUrlService {
  save(url: string): void {
    sessionStorage.setItem(STORAGE_KEY, url);
  }

  consume(): string | null {
    const url = sessionStorage.getItem(STORAGE_KEY);
    if (url) {
      sessionStorage.removeItem(STORAGE_KEY);
    }
    return url;
  }
}
