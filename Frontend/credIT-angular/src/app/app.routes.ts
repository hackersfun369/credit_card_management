import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { Dashboard } from './features/dashboard/dashboard'; import { Cards } from './features/cards/cards'; import { CardDetail } from './features/card-detail/card-detail'; import { Customers } from './features/customers/customers'; import { CustomerDetail } from './features/customer-detail/customer-detail'; import { Merchants } from './features/merchants/merchants'; import { MerchantDetail } from './features/merchant-detail/merchant-detail'; import { Transactions } from './features/transactions/transactions'; import { TransactionDetail } from './features/transaction-detail/transaction-detail'; import { Attention } from './features/attention/attention'; import { Auth } from './features/auth/auth'; import { RefreshView } from './shared/refresh-view/refresh-view';
export const routes: Routes = [
  { path:'login', component:Auth },
  { path:'', canActivate:[authGuard], children:[
    {path:'__refresh',component:RefreshView},{path:'',component:Dashboard},{path:'cards',component:Cards},{path:'cards/attention',component:Attention},{path:'cards/status/:status',component:Cards},{path:'cards/:id',component:CardDetail},{path:'customers',component:Customers},{path:'customers/:id',component:CustomerDetail},{path:'merchants',component:Merchants},{path:'merchants/:id',component:MerchantDetail},{path:'transactions',component:Transactions},{path:'transactions/:filter/:value',component:Transactions},{path:'transactions/:id',component:TransactionDetail}
  ]},
  {path:'**',redirectTo:''}
];