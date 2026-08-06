import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { CreditApi } from '../../core/credit-api';
import { cardLimit, effectiveAvailable } from '../../core/credit-holds';
import { formatIndiaDateTime } from '../../core/india-date-time';
import { Pagination } from '../../shared/pagination/pagination';
@Component({
  selector: 'app-card-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, Pagination],
  templateUrl: './card-detail.html',
  styleUrl: './card-detail.css',
})
export class CardDetail implements OnInit, OnDestroy {
  card: any;
  customers: any[] = [];
  merchants: any[] = [];
  transactions: any[] = [];
  allCards: any[] = [];
  allTransactions: any[] = [];
  loading = true;
  historyPage = 1;
  selectedMerchantId: string | null = null;
  readonly historyPageSize = 10;
  notice = '';
  error = '';
  editOpen = false;
  edit: any = {};
  repayOpen = false;
  repayAmount: number | null = null;
  repayError = '';
  renewOpen = false;
  renew = { expiryDate: '', dueDate: '' };
  renewError = '';
  private refreshTimer?: number;
  readonly customerMode: boolean;
  constructor(
    private api: CreditApi,
    private route: ActivatedRoute,
    private router: Router,
    private cdr: ChangeDetectorRef,
  ) { this.customerMode = this.router.url.startsWith('/my-account'); }
  ngOnInit() {
    this.load();
    this.refreshTimer = window.setInterval(() => this.load(), 15000);
  }
  ngOnDestroy() {
    if (this.refreshTimer) window.clearInterval(this.refreshTimer);
  }
  async load() {
    this.loading = true;
    try {
      const id = this.route.snapshot.paramMap.get('id');
      if (this.customerMode) {
        const portal: any = await this.api.request('auth', '/api/customer-portal/dashboard');
        const cards = Array.isArray(portal?.cards) ? portal.cards : [];
        const transactions = Array.isArray(portal?.transactions) ? portal.transactions : [];
        this.allCards = cards;
        this.card = cards.find((item: any) => String(this.id(item)) === String(id));
        this.customers = portal?.customer ? [portal.customer] : [];
        this.allTransactions = transactions;
        this.transactions = transactions.filter((transaction: any) =>
          this.same(transaction?.cardNumber, this.card?.cardNumber) ||
          String(transaction?.creditId ?? transaction?.cardId ?? '') === String(id),
        );
        const merchantMap = new Map<string, any>();
        for (const transaction of this.transactions) {
          if (String(transaction?.transactionType || '').toUpperCase() === 'PAYMENT') continue;
          const merchantId = transaction?.merchantId ?? transaction?.merchantName;
          if (merchantId == null) continue;
          const key = String(merchantId);
          if (!merchantMap.has(key)) merchantMap.set(key, {
            merchantId: transaction?.merchantId,
            merchantName: transaction?.merchantName || 'Merchant',
            merchantCategory: transaction?.merchantCategory || 'CARD TRANSACTION',
          });
        }
        this.merchants = [...merchantMap.values()];
      } else {
        const [cards, customers, merchants, transactions] = await Promise.all([
          this.api.request<any>('cards', '/card'),
          this.api.request<any>('customers', '/customer'),
          this.api.request<any>('merchants', '/merchants'),
          this.api.request<any>('transactions', '/transactions'),
        ]);
        const rows = (value: any) => (Array.isArray(value) ? value : value?.content || []);
        const all = rows(cards);
        this.allCards = all;
        this.card = all.find((item: any) => String(this.id(item)) === String(id));
        this.customers = rows(customers);
        this.merchants = rows(merchants);
        this.allTransactions = rows(transactions);
        this.transactions = this.allTransactions.filter((transaction: any) =>
          this.same(transaction?.cardNumber, this.card?.cardNumber) ||
          String(transaction?.creditId ?? transaction?.cardId ?? '') === String(id),
        );
      }
    } catch (error: any) {
      this.error = error?.message || 'Unable to load this card.';
    } finally {
      this.loading = false;
      this.cdr.detectChanges();
    }
  }  id(x: any) {
    return x?.creditId ?? x?.id;
  }
  same(a: any, b: any) {
    const left = String(a || '').replace(/\D/g, '');
    const right = String(b || '').replace(/\D/g, '');
    return Boolean(
      left && right && (left === right || left.endsWith(right) || right.endsWith(left)),
    );
  }
  money(v: any) {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(Number(v) || 0);
  }
  number(v: any) {
    const n = String(v || '').replace(/\D/g, '');
    return n ? n.replace(/(.{4})(?=.)/g, '$1 ') : 'Card number pending';
  }
  status(x: any) {
    return String(x?.status ?? x?.cardStatus ?? 'ACTIVE').toUpperCase();
  }
  holder(x: any) {
    return x?.cardHolderName || 'Cardholder';
  }
  date(v: any) {
    return formatIndiaDateTime(v);
  }
  days(v: any) {
    return v ? Math.ceil((new Date(v).getTime() - Date.now()) / 86400000) : null;
  }
  expiryWarning() {
    if (this.card?.replacedByCreditId != null) return '';
    const remaining = this.days(this.card?.expiryDate);
    if (remaining === null || remaining > 30) return '';
    if (remaining < 0) return `Card ${this.lastSix(this.card)} expired ${Math.abs(remaining)} days ago.`;
    if (remaining === 0) return `Card ${this.lastSix(this.card)} expires today.`;
    return `Card ${this.lastSix(this.card)} expires in ${remaining} days.`;
  }
  dueWarning() {
    if (this.card?.replacedByCreditId != null) return '';
    const remaining = this.days(this.card?.dueDate);
    if (remaining === null || remaining > 7) return '';
    if (remaining < 0) return `Card ${this.lastSix(this.card)} payment is overdue by ${Math.abs(remaining)} days.`;
    if (remaining === 0) return `Card ${this.lastSix(this.card)} payment is due today.`;
    return `Card ${this.lastSix(this.card)} payment is due in ${remaining} days.`;
  }
  available() {
    return effectiveAvailable(this.card, this.allTransactions, this.allCards);
  }
  limit() {
    return cardLimit(this.card);
  }
  used() {
    return Math.max(0, this.limit() - this.available());
  }
  utilisation() {
    return this.limit() ? Math.round((this.used() / this.limit()) * 100) : 0;
  }
  get customer() {
    const id = this.card?.customerId ?? this.card?.custId;
    return this.customers.find((c) => String(c?.custId ?? c?.id) === String(id));
  }
  get historyTransactions() {
    if (!this.selectedMerchantId) return this.transactions;
    return this.transactions.filter(transaction => String(transaction?.merchantId ?? '') === this.selectedMerchantId);
  }
  get pagedTransactions() { return this.historyTransactions.slice((this.historyPage - 1) * this.historyPageSize, this.historyPage * this.historyPageSize); }
  get trendTransactions() {
    return [...this.transactions]
      .sort((left, right) => new Date(left?.timestamp || 0).getTime() - new Date(right?.timestamp || 0).getTime())
      .slice(-8);
  }
  private trendCoordinates() {
    return this.trendTransactions.map((_, index) => ({ x: this.trendX(index), y: this.trendY(index) }));
  }
  /** Cubic Bézier interpolation keeps live transaction data readable without sharp joins. */
  trendPath() {
    const points = this.trendCoordinates();
    if (!points.length) return '';
    if (points.length === 1) return `M ${points[0].x} ${points[0].y}`;
    let path = `M ${points[0].x} ${points[0].y}`;
    for (let index = 0; index < points.length - 1; index++) {
      const before = points[index - 1] || points[index];
      const start = points[index];
      const end = points[index + 1];
      const after = points[index + 2] || end;
      const controlOne = { x: start.x + (end.x - before.x) / 6, y: start.y + (end.y - before.y) / 6 };
      const controlTwo = { x: end.x - (after.x - start.x) / 6, y: end.y - (after.y - start.y) / 6 };
      path += ` C ${controlOne.x.toFixed(1)} ${controlOne.y.toFixed(1)}, ${controlTwo.x.toFixed(1)} ${controlTwo.y.toFixed(1)}, ${end.x.toFixed(1)} ${end.y.toFixed(1)}`;
    }
    return path;
  }
  trendX(index: number) {
    const count = this.trendTransactions.length;
    return count === 1 ? 50 : 4 + (index * 92) / (count - 1);
  }
  trendY(index: number) {
    const values = this.trendTransactions.map((transaction) => Number(transaction?.amount ?? transaction?.transactionAmount) || 0);
    const maximum = Math.max(...values, 1);
    return 88 - ((values[index] || 0) / maximum) * 72;
  }
  trendAreaPath() {
    const points = this.trendCoordinates();
    if (!points.length) return '';
    const first = points[0];
    const last = points[points.length - 1];
    return `M ${first.x} 92 L ${first.x} ${first.y} ${this.trendPath().replace(/^M\\s+[^ ]+\\s+[^ ]+/, '')} L ${last.x} 92 Z`;
  }
  trendLabel(index: number) {
    const row = this.trendTransactions[index];
    if (!row?.timestamp) return '';
    return new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', day: '2-digit', month: 'short' }).format(new Date(row.timestamp));
  }
  get previousMerchants() {
    return this.merchants.filter((m) =>
      this.transactions.some(
        (t) =>
          String(t?.merchantId ?? '') === String(m?.merchantId ?? m?.id) ||
          String(t?.merchantName ?? '').toLowerCase() === this.merchantName(m).toLowerCase(),
      ),
    );
  }
  lastSix(card: any) {
    return String(card?.cardNumber || '').slice(-6) || 'Number unavailable';
  }
  merchantId(m: any) { return String(m?.merchantId ?? m?.id ?? ''); }
  selectMerchant(merchant: any) { const id = this.merchantId(merchant); this.selectedMerchantId = this.selectedMerchantId === id ? null : id; this.historyPage = 1; }
  navMerchant(merchant: any) { this.nav((this.customerMode ? '/my-account/merchants/' : '/merchants/') + this.merchantId(merchant)); }
  merchantName(m: any) {
    return [m?.firstName, m?.lastName].filter(Boolean).join(' ') || m?.merchantName || 'Merchant';
  }
  nav(path: string) {
    this.router.navigateByUrl(path);
  }
  openTransaction(transaction: any) { this.nav((this.customerMode ? '/my-account/transactions/' : '/transactions/') + (transaction?.transactionId ?? transaction?.id)); }
  outstandingBalance() {
    return Math.max(0, this.limit() - this.available());
  }
  linkedCard(id: any) { return this.allCards.find(card => String(this.id(card)) === String(id)); }
  canRenew() { const remaining = this.days(this.card?.expiryDate); return remaining !== null && (this.customerMode ? remaining < 0 : remaining <= 30) && this.card?.replacedByCreditId == null; }
  isExpired() { const remaining = this.days(this.card?.expiryDate); return remaining !== null && remaining < 0; }
  todayInput() { return new Date().toISOString().slice(0, 10); }
  minimumRenewalExpiry() {
    const current = this.toInputDate(this.card?.expiryDate);
    return current && current > this.todayInput() ? current : this.todayInput();
  }
  plusYears(value: string, years: number) {
    const date = new Date(`${value}T12:00:00`);
    date.setFullYear(date.getFullYear() + years);
    return date.toISOString().slice(0, 10);
  }
  nextDueDate() {
    const source = this.toInputDate(this.card?.dueDate) || this.todayInput();
    const date = new Date(`${source}T12:00:00`);
    while (date.toISOString().slice(0, 10) < this.todayInput()) date.setMonth(date.getMonth() + 1);
    return date.toISOString().slice(0, 10);
  }
  openRenew() {
    this.renewError = '';
    const baseExpiry = this.minimumRenewalExpiry();
    this.renew = { expiryDate: this.plusYears(baseExpiry, 3), dueDate: this.nextDueDate() };
    this.renewOpen = true;
  }
  closeRenew() { this.renewOpen = false; this.renewError = ''; }
  async saveRenew() {
    if (!this.renew.expiryDate || !this.renew.dueDate) { this.renewError = 'Enter the renewed expiry and due dates.'; return; }
    if (new Date(this.renew.dueDate) >= new Date(this.renew.expiryDate)) { this.renewError = 'Due date must be before expiry date.'; return; }
    try {
      const renewed: any = await this.api.request(this.customerMode ? 'auth' : 'cards', this.customerMode ? `/api/customer-portal/cards/${this.id(this.card)}/renew` : `/cards/${this.id(this.card)}/renew`, { method: 'POST', body: JSON.stringify(this.renew) });
      const replacement = (Array.isArray(renewed) ? renewed : []).find(card => String(card?.cardType) === String(this.card?.cardType)) || renewed?.[0];
      this.closeRenew();
      if (replacement) this.nav(`${this.customerMode ? '/my-account/cards/' : '/cards/'}${this.id(replacement)}`);
      else { this.notice = 'Card renewal completed.'; await this.load(); }
    } catch (error: any) { this.renewError = error?.message || 'Unable to renew this card.'; }
    finally { this.cdr.detectChanges(); }
  }
  async blockCustomerCard() {
    if (!this.customerMode || !this.card || this.status(this.card) !== 'ACTIVE') return;
    try {
      await this.api.request('auth', `/api/customer-portal/cards/${this.id(this.card)}/block`, { method: 'POST' });
      this.card = { ...this.card, status: 'BLOCKED', cardStatus: 'BLOCKED' };
      this.notice = 'Card blocked immediately. New transactions are disabled.';
    } catch (error: any) {
      this.error = error?.message || 'Unable to block this card.';
    } finally { this.cdr.detectChanges(); }
  }
  openRepay() { if (this.card?.replacedByCreditId != null) { this.error = 'This physical card was replaced. Open the replacement card to repay the shared balance.'; return; } this.repayError = ''; this.repayAmount = null; this.repayOpen = true; }
  closeRepay() { this.repayOpen = false; this.repayError = ''; }
  async saveRepay() {
    const amount = Number(this.repayAmount || 0);
    const outstanding = this.outstandingBalance();
    if (amount <= 0) { this.repayError = 'Enter a repayment amount greater than zero.'; return; }
    if (amount > outstanding) { this.repayError = `Repayment cannot exceed the outstanding balance of ${this.money(outstanding)}.`; return; }
    try {
      await this.api.request('transactions', '/transaction', { method: 'POST', body: JSON.stringify({ cardNumber: this.card.cardNumber, cardHolderName: this.holder(this.card), amount, currency: 'INR', merchantName: 'Card repayment', transactionType: 'PAYMENT', paymentMethod: 'ONLINE', timestamp: new Date().toISOString(), international: false, fee: 0 }) });
      this.closeRepay(); this.notice = 'Repayment recorded successfully.'; await this.load();
      window.setTimeout(() => { this.notice = ''; this.cdr.detectChanges(); }, 3000);
    } catch (error: any) { this.repayError = error?.message || 'Unable to record repayment.'; } finally { this.cdr.detectChanges(); }
  }  openEdit() {
    this.edit = {
      cardHolderName: this.card?.cardHolderName || '',
      expiryDate: this.toInputDate(this.card?.expiryDate),
      dueDate: this.toInputDate(this.card?.dueDate),
    };
    this.editOpen = true;
  }
  toInputDate(value: any) {
    return value ? String(value).slice(0, 10) : '';
  }
  async saveEdit() {
    const expiry = this.edit.expiryDate;
    const due = this.edit.dueDate;
    if (expiry && due && new Date(due) > new Date(expiry)) {
      this.error = 'Due date cannot be after the expiry date.';
      return;
    }
    try {
      const updated = { ...this.card, ...this.edit };
      await this.api.request('cards', `/putCard/${this.id(this.card)}`, {
        method: 'PUT',
        body: JSON.stringify(updated),
      });

      this.editOpen = false;
      await this.load();
      this.notice = 'Card dates updated for the primary/add-on card pair.';
      window.setTimeout(() => { this.notice = ''; this.cdr.detectChanges(); }, 3000);
    } catch (e: any) {
      this.error = e?.message || 'Unable to update card details.';
    } finally {
      this.cdr.detectChanges();
    }
  }  async setStatus(status: string) {
    if (status === 'ACTIVE' && (this.isExpired() || this.card?.replacedByCreditId != null)) { this.error = 'Expired or replaced cards cannot be reactivated. Renew the card to issue a new one.'; return; }
    try {
      await this.api.request('cards', `/patchCard/${this.id(this.card)}`, {
        method: 'PATCH',
        body: JSON.stringify({ status }),
      });
      this.card = { ...this.card, status };
      this.notice = 'Card status updated.';
      window.setTimeout(() => {
        this.notice = '';
        this.cdr.detectChanges();
      }, 3000);
    } catch (e: any) {
      this.error = e?.message || 'Unable to update card status.';
    } finally {
      this.cdr.detectChanges();
    }
  }
}
