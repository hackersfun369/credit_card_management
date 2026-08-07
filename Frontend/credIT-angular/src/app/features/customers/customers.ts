import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CreditApi } from '../../core/credit-api';
import { Pagination } from '../../shared/pagination/pagination';

@Component({ selector:'app-customers', standalone:true, imports:[CommonModule, FormsModule, Pagination], templateUrl:'./customers.html', styleUrl:'./customers.css' })
export class Customers implements OnInit {
  customers:any[]=[]; cards:any[]=[]; search=''; page=1; readonly pageSize=10; loading=true;
  registerOpen=false; registerError=''; registration:any=this.emptyRegistration();
  constructor(private api:CreditApi, private router:Router, private cdr:ChangeDetectorRef) {}
  ngOnInit(){ this.load(); }
  emptyRegistration(){ return { firstName:'', lastName:'', location:'', phoneNumber:'', aadharNumber:'', accountNumber:this.generateAccountNumber() }; }
  generateAccountNumber(){ return Array.from({length:12},()=>Math.floor(Math.random()*10)).join(''); }
  openRegister(){ this.registration=this.emptyRegistration(); this.registerError=''; this.registerOpen=true; }
  closeRegister(){ this.registerOpen=false; this.registerError=''; }
  async register(){
    const item=this.registration;
    if(!/^\d{10}$/.test(String(item.phoneNumber))) { this.registerError='Phone number must contain exactly 10 digits.'; return; }
    if(!/^\d{12}$/.test(String(item.aadharNumber))) { this.registerError='Aadhaar number must contain exactly 12 digits.'; return; }
    try {
      await this.api.request('auth','/api/manager-portal/customers',{ method:'POST', body:JSON.stringify({...item, phoneNumber:Number(item.phoneNumber), aadharNumber:Number(item.aadharNumber), accountNumber:Number(item.accountNumber)}) });
      this.closeRegister(); await this.load();
    } catch(error:any) { this.registerError=error?.message||'Unable to register customer.'; }
    finally { this.cdr.detectChanges(); }
  }
  async load(){ this.loading=true; try { const data:any=await this.api.request('auth','/api/manager-portal/dashboard'); this.customers=this.rows(data.customers); this.cards=this.rows(data.cards); } finally { this.loading=false; this.cdr.detectChanges(); } }
  rows(value:any){ return Array.isArray(value)?value:(value?.content||[]); }
  id(c:any){ return c?.custId??c?.customerId??c?.id; }
  name(c:any){ return [c?.custFirstName??c?.firstName,c?.custLastName??c?.lastName].filter(Boolean).join(' ')||'Unknown customer'; }
  location(c:any){ return c?.location??c?.address??'No location'; }
  count(c:any){ const id=this.id(c); return this.cards.filter(card=>String(card?.customerId??card?.custId??card?.customer?.custId)===String(id)).length; }
  nav(path:string){ this.router.navigateByUrl(path); }
  get visible(){ const q=this.search.trim().toLowerCase(); return this.customers.filter(c=>!q||this.name(c).toLowerCase().includes(q)||String(this.id(c)).includes(q)||String(c?.accountNumber??'').includes(q)); }
  get pagedVisible(){ return this.visible.slice((this.page-1)*this.pageSize,this.page*this.pageSize); }
  resetPage(){ this.page=1; }
}
