import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { CreditApi } from '../../core/credit-api';
import { cardLimit, effectiveAvailable } from '../../core/credit-holds';
import { Pagination } from '../../shared/pagination/pagination';

@Component({
  selector: 'app-attention',
  standalone: true,
  imports: [CommonModule, Pagination],
  templateUrl: './attention.html',
  styleUrl: './attention.css',
})
export class Attention implements OnInit {
  cards: any[] = [];
  transactions: any[] = [];
  loading = true;
  error = '';
  page = 1;
  readonly pageSize = 10;
  constructor(private api: CreditApi, private router: Router, private cdr: ChangeDetectorRef) {}
  ngOnInit() { this.load(); }
  rows(value: any) { return Array.isArray(value) ? value : value?.content || []; }
  id(card: any) { return card?.creditId ?? card?.id; }
  status(card: any) { return String(card?.status ?? card?.cardStatus ?? 'ACTIVE').toUpperCase(); }
  holder(card: any) { return card?.cardHolderName || 'Cardholder'; }
  number(card: any) { const digits = String(card?.cardNumber || '').replace(/\D/g, ''); return digits ? digits.replace(/(.{4})(?=.)/g, '$1 ') : 'Card number pending'; }
  days(value: any) { return value ? Math.ceil((new Date(value).getTime() - Date.now()) / 86400000) : null; }
  available(card: any) { return effectiveAvailable(card, this.transactions, this.cards); }
  limit(card: any) { return cardLimit(card); }
  nav(card: any) { this.router.navigateByUrl('/cards/' + this.id(card)); }
  async load() {
    this.loading = true; this.error = '';
    try {
      const [cards, transactions] = await Promise.all([this.api.request<any>('cards', '/card'), this.api.request<any>('transactions', '/transactions')]);
      this.cards = this.rows(cards); this.transactions = this.rows(transactions);
    } catch (error: any) { this.error = error?.message || 'Unable to load cards needing attention.'; }
    finally { this.loading = false; this.cdr.detectChanges(); }
  }
  reasons(card: any) {
    const reasons: { tone: string; text: string }[] = [];
    const status = this.status(card); const expiry = this.days(card.expiryDate); const due = this.days(card.dueDate); const available = this.available(card); const limit = this.limit(card);
    if (status === 'BLOCKED') reasons.push({ tone: 'blocked', text: 'Card is blocked' });
    if (status === 'INACTIVE') reasons.push({ tone: 'inactive', text: 'Card is inactive' });
    if (expiry !== null && expiry < 0) reasons.push({ tone: 'urgent', text: `Expired ${Math.abs(expiry)} days ago` });
    else if (expiry !== null && expiry <= 30) reasons.push({ tone: 'warning', text: `Expires in ${expiry} days` });
    if (due !== null && due < 0) reasons.push({ tone: 'urgent', text: `Overdue by ${Math.abs(due)} days` });
    else if (due !== null && due <= 14) reasons.push({ tone: 'warning', text: `Due in ${due} days` });
    if (limit > 0 && available / limit <= .15) reasons.push({ tone: 'urgent', text: `Low credit: ${new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',maximumFractionDigits:0}).format(available)} available` });
    return reasons;
  }
  get visible() { return this.cards.filter(card => card?.replacedByCreditId == null && this.reasons(card).length); }
  get pagedVisible() { return this.visible.slice((this.page - 1) * this.pageSize, this.page * this.pageSize); }
}