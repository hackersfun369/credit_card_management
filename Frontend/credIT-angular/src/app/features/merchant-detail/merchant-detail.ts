import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { CreditApi } from '../../core/credit-api';
import { formatIndiaDateTime } from '../../core/india-date-time';
import { Pagination } from '../../shared/pagination/pagination';

@Component({
  selector: 'app-merchant-detail', standalone: true,
  imports: [CommonModule, FormsModule, Pagination],
  templateUrl: './merchant-detail.html', styleUrl: './merchant-detail.css',
})
export class MerchantDetail implements OnInit {
  merchant: any; transactions: any[] = []; cards: any[] = [];
  loading = true; error = ''; notice = ''; page = 1; readonly pageSize = 10;
  cardSearch = ''; typeFilter = 'ALL'; statusFilter = 'ALL'; dateFilter = '';
  editOpen = false; edit: any = {};
  constructor(private api: CreditApi, private route: ActivatedRoute, private router: Router, private cdr: ChangeDetectorRef) {}
  ngOnInit() { this.load(); }
  rows(value: any) { return Array.isArray(value) ? value : value?.content || []; }
  id(value: any) { return value?.merchantId ?? value?.id; }
  transactionId(value: any) { return value?.transactionId ?? value?.id; }
  cardId(value: any) { return value?.creditId ?? value?.id; }
  merchantName(value = this.merchant) { return [value?.firstName, value?.lastName].filter(Boolean).join(' ') || value?.merchantName || 'Merchant'; }
  digits(value: any) { return String(value ?? '').replace(/\D/g, ''); }
  cardNumber(value: any) { const digits = this.digits(value?.cardNumber); return digits ? digits.replace(/(.{4})(?=.)/g, '$1 ') : 'Card number pending'; }
  money(value: any) { return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(Number(value) || 0); }
  date(value: any) { return formatIndiaDateTime(value); }
  status(value: any) { return String(value?.status ?? value?.transactionStatus ?? 'PENDING').toUpperCase(); }
  get filteredTransactions() { const query = this.digits(this.cardSearch); return this.transactions.filter(item => (!query || this.digits(item?.cardNumber).includes(query)) && (this.typeFilter === 'ALL' || String(item?.transactionType ?? '').toUpperCase() === this.typeFilter) && (this.statusFilter === 'ALL' || this.status(item) === this.statusFilter) && (!this.dateFilter || String(item?.timestamp ?? '').slice(0, 10) === this.dateFilter)); }
  get pagedTransactions() { return this.filteredTransactions.slice((this.page - 1) * this.pageSize, this.page * this.pageSize); }
  resetPage() { this.page = 1; }
  volume() { return this.transactions.reduce((total, item) => total + Number(item?.amount ?? item?.transactionAmount ?? 0), 0); }
  nav(path: string) { this.router.navigateByUrl(path); }
  openEdit() { this.edit = { firstName: this.merchant?.firstName || '', lastName: this.merchant?.lastName || '', merchantCategory: this.merchant?.merchantCategory || 'OTHER', status: this.merchant?.status || 'ACTIVE', bankName: this.merchant?.bankName || '', ifscCode: this.merchant?.ifscCode || '' }; this.editOpen = true; }
  closeEdit() { this.editOpen = false; }
  async saveEdit() { try { await this.api.request('merchants', '/merchant/' + this.id(this.merchant), { method: 'PATCH', body: JSON.stringify(this.edit) }); this.editOpen = false; this.notice = 'Merchant details updated.'; await this.load(); window.setTimeout(() => { this.notice = ''; this.cdr.detectChanges(); }, 3000); } catch (error: any) { this.error = error?.message || 'Unable to update merchant details.'; } finally { this.cdr.detectChanges(); } }
  async load() { this.loading = true; this.error = ''; try { const merchantId = this.route.snapshot.paramMap.get('id'); const [merchants, transactions, cards] = await Promise.all([this.api.request<any>('merchants', '/merchants'), this.api.request<any>('transactions', '/transactions'), this.api.request<any>('cards', '/card')]); this.merchant = this.rows(merchants).find((item: any) => String(this.id(item)) === String(merchantId)); this.transactions = this.rows(transactions).filter((item: any) => String(item?.merchantId) === String(merchantId)); const usedCardNumbers = new Set(this.transactions.map(item => this.digits(item?.cardNumber)).filter(Boolean)); this.cards = this.rows(cards).filter((item: any) => usedCardNumbers.has(this.digits(item?.cardNumber))); if (!this.merchant) this.error = 'Merchant not found.'; } catch (error: any) { this.error = error?.message || 'Unable to load merchant details.'; } finally { this.loading = false; this.cdr.detectChanges(); } }
}