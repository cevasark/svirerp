import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { NotificationService } from '../services/notification.service';
import { ReturnUrlService } from '../services/return-url.service';
import { ApiError } from '../models/api.model';

/**
 * Global HTTP error handler.
 *
 * Shows a user-facing snack-bar message for each error category, then
 * re-throws the original error so component-level handlers can still react
 * (e.g. resetting a loading flag).
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const notifications = inject(NotificationService);
  const router = inject(Router);
  const returnUrl = inject(ReturnUrlService);

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      const apiError = err.error as ApiError | undefined;
      const message = apiError?.message ?? 'An unexpected error occurred';

      // GET /api/auth/me legitimately 401s for every logged-out user on every
      // app load (see AuthService), and POST /login/local 401s on a wrong
      // password — both are expected outcomes handled inline by their own
      // component, not a "session expired, redirect to login" situation.
      const isAuthEndpoint = req.url.endsWith('/auth/me') || req.url.endsWith('/login/local');
      // Most events have no ChurchEvent row yet — a 404 here just means
      // "no church details recorded", handled inline by ChurchEventService.
      const isExpected404 = req.url.includes('/church-details');

      if (err.status === 0) {
        notifications.error('Cannot reach the server. Check your connection.');
      } else if (err.status === 401) {
        if (!isAuthEndpoint) {
          // Session expired mid-visit (not the initial page-load gate — authGuard already
          // handles that case) — save where we were so login lands back here, same as a shared
          // link. See ReturnUrlService's class doc for why this is sessionStorage, not a query param.
          returnUrl.save(router.url);
          router.navigate(['/login']);
        }
      } else if (err.status >= 500) {
        notifications.error('Server error — please try again later.');
      } else if (err.status === 409) {
        notifications.error(message);
      } else if (err.status === 404) {
        if (!isExpected404) {
          notifications.error('Resource not found.');
        }
      } else if (err.status === 400) {
        notifications.error(message);
      }

      return throwError(() => err);
    }),
  );
};
