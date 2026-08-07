import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CreditApi } from '../../core/credit-api';
import { Pagination } from '../../shared/pagination/pagination';

@Component({
  selector: 'app-card-requests',
  standalone: true,
  imports: [CommonModule, FormsModule, Pagination],
  templateUrl: './card-requests.html',
  styleUrl: './card-requests.css',
})
export class CardRequests implements OnInit {
  requests: any[] = [];
  customers: any[] = [];
  loading = true;
  error = '';
  search = '';
  statusFilter = 'ALL';
  page = 1;
  readonly pageSize = 10;
  approvalTarget: any = null;
  approval = { expiryDate: '', dueDate: '' };
  dialogError = '';

  constructor(private api: CreditApi, private router: Router, private cdr: ChangeDetectorRef) {}
  ngOnInit() { this.load(); }

  async load() {
    this.loading = true;
    this.error = '';
    try {
      const data: any = await this.api.request('auth', '/api/manager-portal/dashboard');
      this.requests = this.rows(data.cardRequests);
      this.customers = this.rows(data.customers);
    } catch (error: any) {
      this.error = error?.message || 'Unable to load card requests.';
    } finally {
      this.loading = false;
      this.cdr.detectChanges();
    }
  }

  rows(value: any) { return Array.isArray(value) ? value : value?.content || []; }
  customer(request: any) { return this.customers.find(item => String(this.customerId(item)) === String(request?.customerId)); }
  customerId(customer: any) { return customer?.custId ?? customer?.customerId ?? customer?.id; }
  customerName(request: any) {
    const customer = this.customer(request);
    return customer ? [customer?.firstName ?? customer?.custFirstName, customer?.lastName ?? customer?.custLastName].filter(Boolean).join(' ') : `Customer #${request?.customerId}`;
  }
  status(request: any) { return String(request?.status || 'PENDING').toUpperCase(); }
  count(status: string) { return this.requests.filter(request => this.status(request) === status).length; }
  openCustomer(request: any) { this.router.navigate(['/customers', request.customerId]); }
  openApproval(request: any, event?: Event) { event?.stopPropagation(); this.approvalTarget = request; this.approval = { expiryDate: '', dueDate: '' }; this.dialogError = ''; }
  closeApproval() { this.approvalTarget = null; this.dialogError = ''; }

  async decide(request: any, status: 'APPROVED' | 'REJECTED' | 'ON_HOLD', event?: Event) {
    event?.stopPropagation();
    this.dialogError = '';
    if (status === 'APPROVED' && (!this.approval.expiryDate || !this.approval.dueDate || new Date(this.approval.dueDate) >= new Date(this.approval.expiryDate))) {
      this.dialogError = 'Choose a due date before the expiry date.';
      return;
    }
    try {
      await this.api.request('auth', `/api/manager-portal/card-requests/${request.id}`, {
        method: 'PATCH',
        body: JSON.stringify({ status, ...(status === 'APPROVED' ? this.approval : {}) }),
      });
      if (status === 'APPROVED') this.closeApproval();
      await this.load();
    } catch (error: any) {
      this.dialogError = error?.message || 'Unable to update this request.';
    } finally {
      this.cdr.detectChanges();
    }
  }

  get visible() {
    const query = this.search.trim().toLowerCase();
    return this.requests.filter(request => {
      const matchesStatus = this.statusFilter === 'ALL' || this.status(request) === this.statusFilter;
      const text = `${this.customerName(request)} ${request.customerId} ${request.cardName} ${request.cardType} ${request.id}`.toLowerCase();
      return matchesStatus && (!query || text.includes(query));
    });
  }
  get paged() { return this.visible.slice((this.page - 1) * this.pageSize, this.page * this.pageSize); }
  resetPage() { this.page = 1; }
}

