import { Component, inject } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter } from 'rxjs';
import { Session } from '../session';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class Shell {
  label = 'Dashboard';
  menuOpen = false;
  readonly session = inject(Session);

  get customer() { return this.session.user?.role === 'CUSTOMER'; }
  get username() { return this.session.user?.username || (this.customer ? 'Customer' : 'Admin Manager'); }
  get initials() { return this.username.split(/\s+/).map(value => value[0]).join('').slice(0, 2).toUpperCase() || (this.customer ? 'CU' : 'AM'); }

  constructor(router: Router) {
    router.events.pipe(filter(event => event instanceof NavigationEnd)).subscribe((event: NavigationEnd) => this.label = this.title(event.urlAfterRedirects));
    this.label = this.title(router.url);
  }

  title(path: string) {
    if (path === '/my-account') return 'Dashboard';
    if (/^\/my-account\/cards\/\d+$/.test(path)) return 'Card details';
    if (path.startsWith('/my-account/cards')) return 'My cards';
    if (/^\/my-account\/merchants\/\d+$/.test(path)) return 'Merchant details';
    if (path.startsWith('/my-account/merchants')) return 'My merchants';
    if (/^\/my-account\/transactions\/\d+$/.test(path)) return 'Transaction details';
    if (path.startsWith('/my-account/transactions')) return 'My transactions';
    if (path.startsWith('/my-account/attention')) return 'Needs attention';
    if (path.startsWith('/cards/attention')) return 'Needs attention';
    if (/^\/cards\/\d+$/.test(path)) return 'Card details';
    if (path.startsWith('/cards')) return 'Cards';
    if (/^\/customers\/\d+$/.test(path)) return 'Customer details';
    if (path.startsWith('/customers')) return 'Customers';
    if (/^\/merchants\/\d+$/.test(path)) return 'Merchant details';
    if (path.startsWith('/merchants')) return 'Merchants';
    if (/^\/transactions\/\d+$/.test(path)) return 'Transaction details';
    if (path.startsWith('/transactions')) return 'Transactions';
    return 'Dashboard';
  }

  logout() { this.session.logout(); }
}