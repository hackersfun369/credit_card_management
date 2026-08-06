import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs';
import { Session } from '../session';
@Component({selector:'app-shell',standalone:true,imports:[RouterLink,RouterLinkActive],templateUrl:'./shell.html',styleUrl:'./shell.css'})
export class Shell {
  label='Dashboard'; menuOpen=false; private readonly session=inject(Session);
  get username(){return this.session.user?.username || 'Admin Manager';} get initials(){return this.username.split(/\s+/).map(x=>x[0]).join('').slice(0,2).toUpperCase() || 'AM';}
  constructor(router:Router){router.events.pipe(filter(event=>event instanceof NavigationEnd)).subscribe((event:NavigationEnd)=>this.label=this.title(event.urlAfterRedirects));this.label=this.title(router.url)}
  title(path:string){if(path.startsWith('/cards/attention'))return 'Needs attention';if(/^\/cards\/\d+$/.test(path))return 'Card details';if(path.startsWith('/cards'))return 'Cards';if(/^\/customers\/\d+$/.test(path))return 'Customer details';if(path.startsWith('/customers'))return 'Customers';if(/^\/merchants\/\d+$/.test(path))return 'Merchant details';if(path.startsWith('/merchants'))return 'Merchants';if(/^\/transactions\/\d+$/.test(path))return 'Transaction details';if(path.startsWith('/transactions'))return 'Transactions';return 'Dashboard'}
  logout(){this.session.logout();}
}