import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CreditApi } from '../../core/credit-api';
import { cardLimit, effectiveAvailable, heldAmount } from '../../core/credit-holds';
import { formatIndiaDateTime } from '../../core/india-date-time';
import { Pagination } from '../../shared/pagination/pagination';
@Component({
  selector: 'app-transactions',
  standalone: true,
  imports: [CommonModule, FormsModule, Pagination],
  templateUrl: './transactions.html',
  styleUrl: './transactions.css',
})
export class Transactions implements OnInit {
  transactions: any[] = [];
  cards: any[] = [];
  merchants: any[] = [];
  purchaseOpen = false;
  purchase = {
    cardId: '',
    merchantId: '',
    transactionType: 'PURCHASE',
    amount: null as number | null,
    paymentMethod: 'CHIP',
  };
  search = '';
  notice = '';
  error = '';
  purchaseError = '';
  pendingDelete: any = null;
  typeFilter = 'ALL';
  statusFilter = 'ALL';
  methodFilter = 'ALL';
  loading = true;
  page = 1;
  readonly pageSize = 10;
  floor = 0;
  ceiling = 0;
  lower = 0;
  upper = 0;
  constructor(
    private api: CreditApi,
    private router: Router,
    private cdr: ChangeDetectorRef,
  ) {}
  ngOnInit() {
    this.load();
  }
  async load() {
    this.loading = true;
    try {
      const data: any = await this.api.request('auth', '/api/manager-portal/dashboard');
      this.transactions = Array.isArray(data.transactions) ? data.transactions : data.transactions?.content || [];
      this.cards = Array.isArray(data.cards) ? data.cards : data.cards?.content || [];
      this.merchants = Array.isArray(data.merchants) ? data.merchants : data.merchants?.content || [];
      const firstActive = this.activeCards[0];
      if (!this.purchase.cardId && firstActive)
        this.purchase.cardId = String(this.cardId(firstActive));
      const values = this.transactions.map((t) => Number(t?.amount ?? t?.transactionAmount) || 0);
      this.floor = values.length ? Math.min(...values) : 0;
      this.ceiling = values.length ? Math.max(...values) : 0;
      this.lower = this.floor;
      this.upper = this.ceiling;
    } finally {
      this.loading = false;
      this.cdr.detectChanges();
    }
  }
  id(t: any) {
    return t?.transactionId ?? t?.id;
  }
  type(t: any) {
    return String(t?.transactionType ?? t?.type ?? 'PURCHASE').toUpperCase();
  }
  status(t: any) {
    return String(t?.status ?? t?.transactionStatus ?? 'PENDING').toUpperCase();
  }
  amount(t: any) {
    return Number(t?.amount ?? t?.transactionAmount) || 0;
  }
  money(v: any) {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(Number(v) || 0);
  }
  date(v: any) {
    return formatIndiaDateTime(v);
  }
  number(t: any) {
    const n = String(t?.cardNumber || '').replace(/\D/g, '');
    return n ? n.replace(/(.{4})(?=.)/g, '$1 ') : 'Card number pending';
  }
  nav(path: string) {
    this.router.navigateByUrl(path);
  }
  cardId(card: any) {
    return card?.creditId ?? card?.id;
  }
  cardStatus(card: any) {
    return String(card?.status ?? card?.cardStatus ?? 'ACTIVE').toUpperCase();
  }
  cardHolder(card: any) {
    return card?.cardHolderName || 'Cardholder';
  }
  merchantId(merchant: any) {
    return merchant?.merchantId ?? merchant?.id;
  }
  merchantName(merchant: any) {
    return (
      [merchant?.firstName, merchant?.lastName].filter(Boolean).join(' ') ||
      merchant?.merchantName ||
      'Merchant'
    );
  }
  get activeCards() {
    return this.cards.filter((card) => this.cardStatus(card) === 'ACTIVE');
  }
  get activeMerchants() {
    return this.merchants.filter((merchant) => String(merchant?.status ?? 'ACTIVE').toUpperCase() === 'ACTIVE');
  }
  get selectedCard() {
    return (
      this.activeCards.find((card) => String(this.cardId(card)) === String(this.purchase.cardId)) ||
      this.activeCards[0]
    );
  }
  openPurchase() {
    this.error = '';
    this.purchaseOpen = true;
    const card = this.activeCards[0];
    if (card) this.purchase.cardId = String(this.cardId(card));
    if (this.activeMerchants[0]) this.purchase.merchantId = String(this.merchantId(this.activeMerchants[0]));
  }
  closePurchase() {
    this.purchaseOpen = false;
  }
  available(card: any) {
    return effectiveAvailable(card, this.transactions, this.cards);
  }
  held(card: any) {
    return heldAmount(card, this.transactions, this.cards);
  }
  limit(card: any) {
    return cardLimit(card);
  }
  async savePurchase() {
    const card = this.selectedCard;
    const merchant = this.merchants.find(
      (item) => String(this.merchantId(item)) === String(this.purchase.merchantId),
    );
    const amount = Number(this.purchase.amount || 0);
    if (!card || !merchant) {
      this.purchaseError = 'Select an active card and merchant.';
      return;
    }
    if (amount <= 0) {
      this.purchaseError = 'Enter a valid transaction amount.';
      return;
    }
    const debit = ['PURCHASE', 'AUTHORIZATION'].includes(this.purchase.transactionType);
    if (debit && amount > this.available(card)) {
      this.purchaseError = `Amount exceeds available credit of ${this.money(this.available(card))}.`;
      return;
    }
    try {
      await this.api.request('transactions', '/transaction', {
        method: 'POST',
        body: JSON.stringify({
          cardNumber: card.cardNumber,
          cardHolderName: this.cardHolder(card),
          amount,
          currency: 'INR',
          merchantId: Number(this.merchantId(merchant)),
          merchantName: this.merchantName(merchant),
          transactionType: this.purchase.transactionType,
          status: 'PENDING',
          paymentMethod: this.purchase.paymentMethod,
          timestamp: new Date().toISOString(),
          international: false,
          fee: 0,
        }),
      });
      this.purchaseOpen = false;
      this.notice = 'Transaction recorded successfully.';
      await this.load();
      window.setTimeout(() => {
        this.notice = '';
        this.cdr.detectChanges();
      }, 3000);
    } catch (error: any) {
      this.purchaseError = error?.message || 'Unable to save this transaction.';
    } finally {
      this.cdr.detectChanges();
    }
  }
  requestDelete(transaction: any) {
    this.pendingDelete = transaction;
  }
  async remove(transaction: any) {
    const id = this.id(transaction);
    if (id === undefined || id === null) return;
    this.notice = '';
    this.error = '';
    try {
      await this.api.request('transactions', `/transaction/${id}`, { method: 'DELETE' });
      this.transactions = this.transactions.filter((item) => String(this.id(item)) !== String(id));
      const amounts = this.transactions.map((item) => this.amount(item));
      this.floor = amounts.length ? Math.min(...amounts) : 0;
      this.ceiling = amounts.length ? Math.max(...amounts) : 0;
      this.lower = this.floor;
      this.upper = this.ceiling;
      this.notice = 'Transaction deleted.';
      this.pendingDelete = null;
      window.setTimeout(() => {
        this.notice = '';
        this.cdr.detectChanges();
      }, 3000);
    } catch (error: any) {
      this.error = error?.message || 'Unable to delete this transaction.';
      window.setTimeout(() => {
        this.error = '';
        this.cdr.detectChanges();
      }, 3000);
    } finally {
      this.cdr.detectChanges();
    }
  }
  filterMatches(t: any) {
    const method = String(t?.paymentMethod || '').toUpperCase();
    return (
      (this.typeFilter === 'ALL' || this.type(t) === this.typeFilter) &&
      (this.statusFilter === 'ALL' || this.status(t) === this.statusFilter) &&
      (this.methodFilter === 'ALL' || method === this.methodFilter)
    );
  }
  get visible() {
    const q = this.search.trim().toLowerCase();
    return this.transactions.filter(
      (t) =>
        this.filterMatches(t) &&
        (!q ||
          String(t?.cardNumber || '').includes(q) ||
          String(this.id(t)).includes(q) ||
          String(t?.merchantName || '')
            .toLowerCase()
            .includes(q)) &&
        this.amount(t) >= this.lower &&
        this.amount(t) <= this.upper,
    );
  }
  get pagedVisible() { return this.visible.slice((this.page - 1) * this.pageSize, this.page * this.pageSize); }
  resetPage() { this.page = 1; }
  onLower(value: number) {
    this.lower = Math.min(value, this.upper);
  }
  onUpper(value: number) {
    this.upper = Math.max(value, this.lower);
  }
  get lowerPct() {
    return this.ceiling === this.floor
      ? 0
      : ((this.lower - this.floor) / (this.ceiling - this.floor)) * 100;
  }
  get upperPct() {
    return this.ceiling === this.floor
      ? 100
      : ((this.upper - this.floor) / (this.ceiling - this.floor)) * 100;
  }
}


