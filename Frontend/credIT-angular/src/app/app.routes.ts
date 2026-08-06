import { Routes } from '@angular/router';
import { authGuard, customerGuard, managerGuard } from './core/auth.guard';
import { Dashboard } from './features/dashboard/dashboard';
import { Cards } from './features/cards/cards';
import { CardDetail } from './features/card-detail/card-detail';
import { Customers } from './features/customers/customers';
import { CustomerDetail } from './features/customer-detail/customer-detail';
import { Merchants } from './features/merchants/merchants';
import { MerchantDetail } from './features/merchant-detail/merchant-detail';
import { Transactions } from './features/transactions/transactions';
import { TransactionDetail } from './features/transaction-detail/transaction-detail';
import { Attention } from './features/attention/attention';
import { Auth } from './features/auth/auth';
import { RefreshView } from './shared/refresh-view/refresh-view';
import { CustomerPortal } from './features/customer-portal/customer-portal';

export const routes: Routes = [
  { path: 'login', component: Auth },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      { path: 'my-account', canActivate: [customerGuard], children: [
        { path: '', component: CustomerPortal },
        { path: 'cards', component: CustomerPortal },
        { path: 'cards/:id', component: CardDetail },
        { path: 'merchants', component: CustomerPortal },
        { path: 'merchants/:id', component: MerchantDetail },
        { path: 'transactions', component: CustomerPortal },
        { path: 'transactions/:id', component: TransactionDetail },
        { path: 'attention', component: CustomerPortal },
      ]},
      { path: '__refresh', component: RefreshView, canActivate: [managerGuard] },
      { path: '', component: Dashboard, canActivate: [managerGuard] },
      { path: 'cards', component: Cards, canActivate: [managerGuard] },
      { path: 'cards/attention', component: Attention, canActivate: [managerGuard] },
      { path: 'cards/status/:status', component: Cards, canActivate: [managerGuard] },
      { path: 'cards/:id', component: CardDetail, canActivate: [managerGuard] },
      { path: 'customers', component: Customers, canActivate: [managerGuard] },
      { path: 'customers/:id', component: CustomerDetail, canActivate: [managerGuard] },
      { path: 'merchants', component: Merchants, canActivate: [managerGuard] },
      { path: 'merchants/:id', component: MerchantDetail, canActivate: [managerGuard] },
      { path: 'transactions', component: Transactions, canActivate: [managerGuard] },
      { path: 'transactions/:filter/:value', component: Transactions, canActivate: [managerGuard] },
      { path: 'transactions/:id', component: TransactionDetail, canActivate: [managerGuard] },
    ],
  },
  { path: '**', redirectTo: '' },
];