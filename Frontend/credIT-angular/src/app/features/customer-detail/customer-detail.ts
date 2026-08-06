import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { CreditApi } from '../../core/credit-api';

@Component({
  selector: 'app-customer-detail',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './customer-detail.html',
  styleUrl: './customer-detail.css',
})
export class CustomerDetail implements OnInit {
  customer: any;
  cards: any[] = [];
  loading = true;
  error = '';
  notice = '';
  editOpen = false;
  deleteOpen = false;
  edit: any = {};
  editError = '';

  constructor(private api: CreditApi, private route: ActivatedRoute, private router: Router, private cdr: ChangeDetectorRef) {}

  ngOnInit() { this.load(); }
  id(value: any) { return value?.custId ?? value?.customerId ?? value?.id; }
  rows(value: any) { return Array.isArray(value) ? value : value?.content || []; }
  nav(path: string) { this.router.navigateByUrl(path); }
  name(customer = this.customer) { return [customer?.custFirstName ?? customer?.firstName, customer?.custLastName ?? customer?.lastName].filter(Boolean).join(' ') || 'Customer'; }
  cardId(card: any) { return card?.creditId ?? card?.id; }
  status(card: any) { return String(card?.status ?? card?.cardStatus ?? 'ACTIVE').toUpperCase(); }
  number(value: any) { const digits = String(value || '').replace(/\D/g, ''); return digits ? digits.replace(/(.{4})(?=.)/g, '$1 ') : 'Card number pending'; }
  days(value: any) { return value ? Math.ceil((new Date(value).getTime() - Date.now()) / 86400000) : null; }
  lastSix(card: any) { return String(card?.cardNumber || '').slice(-6); }
  get hasPrimary() { return this.cards.some(card => String(card?.cardType).toUpperCase() === 'PRIMARY'); }
  get hasAddOn() { return this.cards.some(card => String(card?.cardType).toUpperCase() === 'ADD_ON'); }
  get currentCards() { return this.cards.filter(card => card?.replacedByCreditId == null); }
  get replacementHistory() { return this.cards.filter(card => card?.replacedByCreditId != null); }
  get activeCards() { return this.currentCards.filter(card => this.status(card) === 'ACTIVE'); }
  get inactiveCards() { return this.currentCards.filter(card => this.status(card) === 'INACTIVE'); }
  get blockedCards() { return this.currentCards.filter(card => this.status(card) === 'BLOCKED'); }
  get alerts() {
    return this.cards.filter(card => card?.replacedByCreditId == null).flatMap(card => {
      const expiry = this.days(card.expiryDate); const due = this.days(card.dueDate); const suffix = this.lastSix(card);
      return [
        ...(expiry !== null && expiry >= 0 && expiry <= 30 ? [{ tone: 'expiry', text: `Card ${suffix}: expires in ${expiry} days` }] : []),
        ...(due !== null && due >= 0 && due <= 14 ? [{ tone: 'due', text: `Card ${suffix}: due in ${due} days` }] : []),
      ];
    });
  }

  async load() {
    this.loading = true; this.error = '';
    try {
      const customerId = this.route.snapshot.paramMap.get('id');
      const [customers, cards] = await Promise.all([
        this.api.request<any>('customers', '/customer'),
        this.api.request<any>('cards', '/card'),
      ]);
      this.customer = this.rows(customers).find((customer: any) => String(this.id(customer)) === String(customerId));
      this.cards = this.rows(cards).filter((card: any) => String(card?.customerId ?? card?.custId) === String(customerId));
      if (!this.customer) this.error = 'Customer not found.';
    } catch (error: any) { this.error = error?.message || 'Unable to load customer details.'; }
    finally { this.loading = false; this.cdr.detectChanges(); }
  }

  openEdit() {
    this.edit = {
      custFirstName: this.customer?.custFirstName ?? this.customer?.firstName ?? '',
      custLastName: this.customer?.custLastName ?? this.customer?.lastName ?? '',
      phoneNumber: this.customer?.phoneNumber ?? '',
      location: this.customer?.location ?? '',
    };
    this.editOpen = true;
  }
  closeEdit() { this.editOpen = false; this.editError = ''; }
  async saveEdit() {
    const updated = { ...this.customer, ...this.edit };
    try {
      await this.api.request('customers', `/putCustomer/${this.id(this.customer)}`, { method: 'PUT', body: JSON.stringify(updated) });
      this.editOpen = false; this.notice = 'Customer profile updated.'; await this.load(); this.clearNotice();
    } catch (error: any) { this.editError = error?.message || 'Unable to update customer.'; }
    finally { this.cdr.detectChanges(); }
  }
  async deleteCustomer() {
    try {
      await this.api.request('customers', `/customer/${this.id(this.customer)}`, { method: 'DELETE' });
      this.deleteOpen = false; this.router.navigateByUrl('/customers');
    } catch (error: any) { this.deleteOpen = false; this.error = error?.message || 'Unable to delete customer.'; this.cdr.detectChanges(); }
  }
  clearNotice() { window.setTimeout(() => { this.notice = ''; this.cdr.detectChanges(); }, 3000); }
}