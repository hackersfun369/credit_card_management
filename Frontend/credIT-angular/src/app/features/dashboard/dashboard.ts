import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CreditApi } from '../../core/credit-api';
import { formatIndiaDateTime } from '../../core/india-date-time';
import { cardLimit, effectiveAvailable, heldAmount } from '../../core/credit-holds';
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard implements OnInit {
  customers: any[] = [];
  cards: any[] = [];
  transactions: any[] = [];
  merchants: any[] = [];
  cardRequests: any[] = [];
  approvalOpen = false;
  approvalTarget: any = null;
  approval = { expiryDate: '', dueDate: '' };
  issueOpen = false;
  transactionOpen = false;
  dialogError = '';
  issue = { customerId: '', cardHolderName: '', cardName: 'SILVER', cardType: 'PRIMARY', expiryDate: '', dueDate: '' };
  purchase = { cardId: '', merchantId: '', transactionType: 'PURCHASE', amount: null as number | null, paymentMethod: 'CHIP' };
  loading = true;
  error = '';
  constructor(
    private api: CreditApi,
    private router: Router,
    private cdr: ChangeDetectorRef,
  ) {}
  ngOnInit() {
    this.refresh();
  }
  async refresh() {
    this.loading = true;
    this.error = '';
    try {
      const data: any = await this.api.request('auth', '/api/manager-portal/dashboard');
      this.customers = this.rows(data.customers);
      this.cards = this.rows(data.cards);
      this.merchants = this.rows(data.merchants);
      this.transactions = this.rows(data.transactions);
      this.cardRequests = this.rows(data.cardRequests);
    } catch (error: any) {
      this.error = 'Unable to reach the services: ' + (error.message || 'Unknown error');
    } finally {
      this.loading = false;
      this.cdr.detectChanges();
    }
  }
  rows(value: any) {
    return Array.isArray(value) ? value : value?.content || [];
  }
  cardId(card: any) { return card?.creditId ?? card?.id; }
  merchantId(merchant: any) { return merchant?.merchantId ?? merchant?.id; }
  merchantName(merchant: any) { return [merchant?.firstName, merchant?.lastName].filter(Boolean).join(' ') || merchant?.merchantName || 'Merchant'; }
  get activeCards() { return this.cards.filter(card => this.status(card) === 'ACTIVE'); }
  get activeMerchants() { return this.merchants.filter(merchant => String(merchant?.status ?? 'ACTIVE').toUpperCase() === 'ACTIVE'); }
  get selectedCard() { return this.activeCards.find(card => String(this.cardId(card)) === String(this.purchase.cardId)) || this.activeCards[0]; }
  available(card: any) { return effectiveAvailable(card, this.transactions, this.cards); }
  limit(card: any) { return cardLimit(card); }
  held(card: any) { return heldAmount(card, this.transactions, this.cards); }
  openIssue() { this.dialogError = ''; this.issueOpen = true; }
  closeIssue() { this.issueOpen = false; this.dialogError = ''; }
  onIssueCustomerChange() {
    const customer = this.customers.find(item => String(item?.custId ?? item?.id) === String(this.issue.customerId));
    if (customer) this.issue.cardHolderName = [customer.custFirstName ?? customer.firstName, customer.custLastName ?? customer.lastName].filter(Boolean).join(' ');
  }
  async saveIssue() {
    const customerId = Number(this.issue.customerId);
    if (!customerId || !this.issue.cardHolderName.trim() || !this.issue.expiryDate || !this.issue.dueDate) { this.dialogError = 'Enter the customer, expiry date, and due date.'; return; }
    if (new Date(this.issue.dueDate) > new Date(this.issue.expiryDate)) { this.dialogError = 'Due date cannot be after expiry date.'; return; }
    try { await this.api.request('cards', '/card', { method: 'POST', body: JSON.stringify({ ...this.issue, customerId }) }); this.closeIssue(); await this.refresh(); }
    catch (error: any) { this.dialogError = error?.message || 'Unable to issue the card.'; }
    finally { this.cdr.detectChanges(); }
  }
  openTransaction() {
    this.dialogError = ''; this.transactionOpen = true;
    if (this.activeCards[0]) this.purchase.cardId = String(this.cardId(this.activeCards[0]));
    if (this.activeMerchants[0]) this.purchase.merchantId = String(this.merchantId(this.activeMerchants[0]));
  }
  closeTransaction() { this.transactionOpen = false; this.dialogError = ''; }
  async saveTransaction() {
    const card = this.selectedCard;
    const merchant = this.merchants.find(item => String(this.merchantId(item)) === String(this.purchase.merchantId));
    const amount = Number(this.purchase.amount || 0);
    if (!card || !merchant || !this.activeMerchants.includes(merchant) || amount <= 0) { this.dialogError = 'Select an active card, active merchant, and valid amount.'; return; }
    if (['PURCHASE', 'AUTHORIZATION'].includes(this.purchase.transactionType) && amount > this.available(card)) { this.dialogError = `Amount exceeds available credit of ${this.money(this.available(card))}.`; return; }
    try {
      await this.api.request('transactions', '/transaction', { method: 'POST', body: JSON.stringify({ cardNumber: card.cardNumber, cardHolderName: this.holder(card), amount, currency: 'INR', merchantId: Number(this.merchantId(merchant)), merchantName: this.merchantName(merchant), transactionType: this.purchase.transactionType, status: 'PENDING', paymentMethod: this.purchase.paymentMethod, timestamp: new Date().toISOString(), international: false, fee: 0 }) });
      this.closeTransaction(); await this.refresh();
    } catch (error: any) { this.dialogError = error?.message || 'Unable to record this transaction.'; }
    finally { this.cdr.detectChanges(); }
  }  customerForRequest(request: any) { return this.customers.find(c => String(c?.custId ?? c?.customerId ?? c?.id) === String(request?.customerId)); }
  requestCustomerName(request: any) { const c = this.customerForRequest(request); return c ? this.nameOfCustomer(c) : `Customer #${request?.customerId}`; }
  nameOfCustomer(c: any) { return [c?.firstName ?? c?.custFirstName, c?.lastName ?? c?.custLastName].filter(Boolean).join(' ') || 'Customer'; }
  openApproval(request: any) { this.approvalTarget = request; this.approval = { expiryDate: '', dueDate: '' }; this.dialogError = ''; this.approvalOpen = true; }
  closeApproval() { this.approvalOpen = false; this.approvalTarget = null; this.dialogError = ''; }
  async decideRequest(request: any, status: 'APPROVED'|'REJECTED'|'ON_HOLD') {
    this.dialogError = '';
    if (status === 'APPROVED' && (!this.approval.expiryDate || !this.approval.dueDate || new Date(this.approval.dueDate) >= new Date(this.approval.expiryDate))) { this.dialogError = 'Choose a due date before the expiry date.'; return; }
    try { await this.api.request('auth', `/api/manager-portal/card-requests/${request.id}`, { method: 'PATCH', body: JSON.stringify({ status, ...(status === 'APPROVED' ? this.approval : {}) }) }); if (status === 'APPROVED') this.closeApproval(); await this.refresh(); }
    catch (error: any) { this.dialogError = error?.message || 'Unable to update this request.'; }
    finally { this.cdr.detectChanges(); }
  }
  get openCardRequests() { return this.cardRequests.filter(r => ['PENDING','ON_HOLD'].includes(String(r?.status).toUpperCase())); }
  nav(path: string) {
    this.router.navigateByUrl(path);
  }
  money(value: any) {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(Number(value) || 0);
  }
  date(value: any) {
    return formatIndiaDateTime(value);
  }
  id(x: any) {
    return x?.transactionId ?? x?.creditId ?? x?.id;
  }
  status(card: any) {
    return String(card?.status || card?.cardStatus || 'ACTIVE').toUpperCase();
  }
  type(t: any) {
    return String(t?.transactionType || t?.type || 'PURCHASE').toUpperCase();
  }
  holder(card: any) {
    return (
      card?.cardHolderName ||
      [card?.customer?.firstName, card?.customer?.lastName].filter(Boolean).join(' ') ||
      'Unknown cardholder'
    );
  }
  amount(card: any) {
    return Math.max(
      0,
      Number(card?.cardLimit || card?.creditLimit || 0) -
        Number(card?.availableCredit || card?.availableBalance || 0),
    );
  }
  get metrics() {
    const active = this.cards.filter((c) => this.status(c) === 'ACTIVE').length;
    const blocked = this.cards.filter((c) => this.status(c) === 'BLOCKED').length;
    const outstanding = this.cards.reduce((sum, c) => sum + this.amount(c), 0);
    const purchases = this.transactions
      .filter((t) => this.type(t) !== 'PAYMENT')
      .reduce((sum, t) => sum + (Number(t?.amount || t?.transactionAmount) || 0), 0);
    return [
      {
        label: 'Total customers',
        value: this.customers.length,
        note: 'Live customer directory',
        tone: 'indigo',
        icon: 'users',
      },
      {
        label: 'Active cards',
        value: active,
        note: `${blocked} cards blocked`,
        tone: 'blue',
        icon: 'card',
      },
      {
        label: 'Outstanding balance',
        value: this.money(outstanding),
        note: '0% credit utilized',
        tone: 'orange',
        icon: 'arrow',
      },
      {
        label: 'Total purchases',
        value: this.money(purchases),
        note: 'Across all live transactions',
        tone: 'green',
        icon: 'bars',
      },
    ];
  }
  get trend() {
    const rows = [...this.transactions]
      .sort((a, b) => new Date(a.timestamp || 0).getTime() - new Date(b.timestamp || 0).getTime())
      .slice(-7);
    const values = rows.map((t) => Number(t.amount || t.transactionAmount) || 0);
    const max = Math.max(...values, 1);
    return rows.map((item, index) => ({
      x: values.length === 1 ? 50 : (index / (values.length - 1)) * 100,
      y: 90 - (values[index] / max) * 70,
      label: new Date(item.timestamp || Date.now()).toLocaleDateString('en-IN', {
        day: '2-digit',
        month: 'short',
      }),
    }));
  }
  /** Smooth cubic Bézier curve generated from live transaction values. */
  get trendPath() {
    const points = this.trend;
    if (!points.length) return '';
    if (points.length === 1) return `M ${points[0].x} ${points[0].y}`;
    let path = `M ${points[0].x} ${points[0].y}`;
    for (let index = 0; index < points.length - 1; index++) {
      const before = points[index - 1] || points[index];
      const start = points[index];
      const end = points[index + 1];
      const after = points[index + 2] || end;
      path += ` C ${(start.x + (end.x - before.x) / 6).toFixed(1)} ${(start.y + (end.y - before.y) / 6).toFixed(1)}, ${(end.x - (after.x - start.x) / 6).toFixed(1)} ${(end.y - (after.y - start.y) / 6).toFixed(1)}, ${end.x.toFixed(1)} ${end.y.toFixed(1)}`;
    }
    return path;
  }
  get trendAreaPath() {
    const points = this.trend;
    if (!points.length) return '';
    const first = points[0];
    const last = points[points.length - 1];
    return `M ${first.x} 100 L ${first.x} ${first.y} ${this.trendPath.replace(/^M\\s+[^ ]+\\s+[^ ]+/, '')} L ${last.x} 100 Z`;
  }
  get attention() {
    return this.cards
      .filter((card) => {
        if (card?.replacedByCreditId != null) return false;
        const expiry = this.days(card.expiryDate),
          due = this.days(card.dueDate);
        return (
          this.status(card) !== 'ACTIVE' ||
          (expiry !== null && expiry <= 30) ||
          (due !== null && due <= 14)
        );
      })
      .slice(0, 5);
  }
  days(value: any) {
    return value ? Math.ceil((new Date(value).getTime() - Date.now()) / 86400000) : null;
  }
  reason(card: any) {
    const status = this.status(card);
    const due = this.days(card.dueDate);
    return status !== 'ACTIVE'
      ? status.toLowerCase()
      : due !== null && due <= 14
        ? `due in ${due} days`
        : `expires in ${this.days(card.expiryDate)} days`;
  }
  count(status: string) {
    return this.cards.filter((card) => this.status(card) === status).length;
  }
  lastSix(card: any) {
    return String(card?.cardNumber || '').slice(-6) || 'Number unavailable';
  }
  get recent() {
    return [...this.transactions]
      .sort((a, b) => new Date(b.timestamp || 0).getTime() - new Date(a.timestamp || 0).getTime())
      .slice(0, 5);
  }
}


