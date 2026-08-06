import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CreditApi } from '../../core/credit-api';
import { Pagination } from '../../shared/pagination/pagination';

@Component({ selector: 'app-merchants', standalone: true, imports: [CommonModule, FormsModule, Pagination], templateUrl: './merchants.html', styleUrl: './merchants.css' })
export class Merchants implements OnInit {
  merchants: any[] = []; transactions: any[] = []; search = ''; page = 1; readonly pageSize = 10; loading = true;
  pendingDelete: any = null; notice = ''; error = ''; addOpen = false; addError = '';
  add: any = this.blankMerchant();
  constructor(private api: CreditApi, private router: Router, private cdr: ChangeDetectorRef) {}
  ngOnInit() { this.load(); }
  blankMerchant() { return { firstName: '', lastName: '', mid: '', merchantCategory: 'GROCERY', merchantAccountNmber: '', status: 'ACTIVE', bankName: '', ifscCode: '' }; }
  async load() { this.loading = true; try { const [merchants, transactions] = await Promise.all([this.api.request<any>('merchants', '/merchants'), this.api.request<any>('transactions', '/transactions')]); this.merchants = this.rows(merchants); this.transactions = this.rows(transactions); } catch (error: any) { this.error = error?.message || 'Unable to load merchants.'; } finally { this.loading = false; this.cdr.detectChanges(); } }
  rows(value: any) { return Array.isArray(value) ? value : value?.content || []; }
  id(merchant: any) { return merchant?.merchantId ?? merchant?.id; }
  name(merchant: any) { return [merchant?.firstName, merchant?.lastName].filter(Boolean).join(' ') || merchant?.merchantName || 'Unknown merchant'; }
  count(merchant: any) { return this.transactions.filter(transaction => String(transaction?.merchantId ?? '') === String(this.id(merchant)) || String(transaction?.merchantName ?? '').trim().toLowerCase() === this.name(merchant).toLowerCase()).length; }
  nav(path: string) { this.router.navigateByUrl(path); }
  get visible() { const query = this.search.trim().toLowerCase(); return this.merchants.filter(merchant => !query || this.name(merchant).toLowerCase().includes(query) || String(merchant?.merchantAccountNmber ?? merchant?.accountNumber ?? '').includes(query) || String(merchant?.merchantCategory ?? '').toLowerCase().includes(query)); }
  get pagedVisible() { return this.visible.slice((this.page - 1) * this.pageSize, this.page * this.pageSize); }
  resetPage() { this.page = 1; }
  openAdd() { this.addError = ''; this.add = this.blankMerchant(); this.addOpen = true; }
  closeAdd() { this.addOpen = false; this.addError = ''; }
  async saveAdd() { const mid = String(this.add.mid || '').replace(/\D/g, ''); const account = String(this.add.merchantAccountNmber || '').replace(/\D/g, ''); if (!this.add.firstName.trim() || !this.add.lastName.trim() || !this.add.bankName.trim() || !this.add.ifscCode.trim()) { this.addError = 'Complete all merchant details.'; return; } if (!/^\d{15}$/.test(mid)) { this.addError = 'MID must be a 15-digit number.'; return; } if (!/^\d{12}$/.test(account)) { this.addError = 'Account number must be a 12-digit number.'; return; } try { await this.api.request('merchants', '/merchant', { method: 'POST', body: JSON.stringify({ ...this.add, mid, merchantAccountNmber: account }) }); this.closeAdd(); this.notice = 'Merchant added.'; await this.load(); window.setTimeout(() => { this.notice = ''; this.cdr.detectChanges(); }, 3000); } catch (error: any) { this.addError = error?.message || 'Unable to add merchant.'; } finally { this.cdr.detectChanges(); } }
  confirmDelete(merchant: any) { this.pendingDelete = merchant; }
  async deleteMerchant() { const merchant = this.pendingDelete; if (!merchant) return; try { await this.api.request('merchants', '/merchant/' + this.id(merchant), { method: 'DELETE' }); this.pendingDelete = null; this.notice = 'Merchant deleted.'; await this.load(); window.setTimeout(() => { this.notice = ''; this.cdr.detectChanges(); }, 3000); } catch (error: any) { this.pendingDelete = null; this.error = error?.message || 'Unable to delete merchant.'; this.cdr.detectChanges(); } }
}