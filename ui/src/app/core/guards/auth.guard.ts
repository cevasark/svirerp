import { inject } from '@angular/core';
import { CanActivateFn, Router, RouterStateSnapshot } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { ReturnUrlService } from '../services/return-url.service';

/**
 * Gates every route under the shell (see app.routes.ts). Also doubles as the "land back on the
 * shared link" restore point: since login always re-enters this same guarded `''` route (Google
 * OAuth via a full-page reload to `/`, local-admin via `navigateByUrl('/')`), the first
 * authenticated activation after login is exactly where a pending ReturnUrlService value needs to
 * be consumed and redirected to — no separate post-login hook needed in either login component.
 */
export const authGuard: CanActivateFn = (_route, state: RouterStateSnapshot) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const returnUrl = inject(ReturnUrlService);

  if (auth.isAuthenticated()) {
    const pending = returnUrl.consume();
    return pending ? router.parseUrl(pending) : true;
  }

  returnUrl.save(state.url);
  return router.createUrlTree(['/login']);
};
