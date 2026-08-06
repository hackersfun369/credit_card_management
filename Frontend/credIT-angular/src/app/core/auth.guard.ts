import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { Session } from './session';

export const authGuard: CanActivateFn = (_route, state) => {
  const session = inject(Session);
  if (session.authenticated) return true;
  session.clear();
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};