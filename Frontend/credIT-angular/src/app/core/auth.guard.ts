import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { Session } from './session';

export const authGuard: CanActivateFn = (_route, state) => {
  const session = inject(Session);
  if (session.authenticated) return true;
  session.clear();
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

export const managerGuard: CanActivateFn = () => {
  const session = inject(Session);
  return session.user?.role === 'CUSTOMER'
    ? inject(Router).createUrlTree(['/my-account'])
    : true;
};

export const customerGuard: CanActivateFn = () => {
  const session = inject(Session);
  return session.user?.role === 'CUSTOMER'
    ? true
    : inject(Router).createUrlTree(['/']);
};