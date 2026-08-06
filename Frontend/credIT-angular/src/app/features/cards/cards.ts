import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { CreditApi } from '../../core/credit-api';
import { Pagination } from '../../shared/pagination/pagination';
@Component({selector:'app-cards',standalone:true,imports:[CommonModule,FormsModule,Pagination],templateUrl:'./cards.html',styleUrl:'./cards.css'})
export class Cards implements OnInit {
  cards:any[]=[]; customers:any[]=[]; search=''; status=''; loading=true; issueOpen=false; issueError=''; notice=''; pendingDelete:any=null; deleteError=''; deleteBusy=false; issue={customerId:'',cardHolderName:'',cardName:'SILVER',cardType:'PRIMARY',expiryDate:'',dueDate:''}; page=1; readonly pageSize=10;
  constructor(private api:CreditApi,private router:Router,private route:ActivatedRoute,private cdr:ChangeDetectorRef){}
  ngOnInit(){this.route.paramMap.subscribe(params=>{this.status=(params.get('status')||'').toUpperCase();this.load()})}
  async load(){this.loading=true;try{const [value, customerRows]=await Promise.all([this.api.request<any>('cards','/card'),this.api.request<any>('customers','/customer')]);this.cards=Array.isArray(value)?value:(value?.content||[]);this.customers=Array.isArray(customerRows)?customerRows:(customerRows?.content||[])}finally{this.loading=false;this.cdr.detectChanges()}}
  get issueCustomerCards(){return this.cards.filter(card=>String(card?.customerId??card?.custId??'')===String(this.issue.customerId))}
  get requiredTier(){return this.issueCustomerCards[0]?.cardName||''}
  onCustomerIdChange(){if(this.requiredTier)this.issue.cardName=String(this.requiredTier).toUpperCase()}  openIssue(){this.issueError='';this.issueOpen=true}
  closeIssue(){this.issueOpen=false;this.issueError=''}
  requestDelete(card:any){this.pendingDelete=card;this.deleteError='';this.deleteBusy=false}
  closeDelete(){if(this.deleteBusy)return;this.pendingDelete=null;this.deleteError=''}
  async deleteCard(){
    const card=this.pendingDelete;
    if(!card||this.deleteBusy)return;
    const cardId=this.id(card);
    if(cardId===null||cardId===undefined){this.deleteError='This card does not have a valid identifier.';return}
    this.deleteBusy=true;this.deleteError='';
    try{
      await this.api.request('cards','/card/'+cardId,{method:'DELETE'});
      this.pendingDelete=null;
      this.notice='Credit card deleted.';
      await this.load();
      const lastPage=Math.max(1,Math.ceil(this.visible.length/this.pageSize));
      if(this.page>lastPage)this.page=lastPage;
      window.setTimeout(()=>{this.notice='';this.cdr.detectChanges()},3000);
    }catch(error:any){
      this.deleteError=error?.message||'Unable to delete this card.';
    }finally{
      this.deleteBusy=false;
      this.cdr.detectChanges();
    }
  }
  async saveIssue(){
    const customerId=Number(this.issue.customerId);
    if(!customerId||!this.issue.cardHolderName.trim()){this.issueError='Customer ID and cardholder name are required.';return}
    const existing=this.issueCustomerCards;if(existing.length>=2){this.issueError='This customer already has two cards: one Primary and one Add-on. A third card cannot be issued.';return}if(existing.some(card=>String(card?.cardType||'').toUpperCase()===this.issue.cardType)){this.issueError='This customer already has this card type. Choose the other type.';return}if(this.requiredTier&&String(this.issue.cardName).toUpperCase()!==String(this.requiredTier).toUpperCase()){this.issueError='The add-on card must use the existing '+this.requiredTier+' tier.';return}if(!this.issue.expiryDate||!this.issue.dueDate){this.issueError='Expiry date and due date are required.';return}
    if(new Date(this.issue.dueDate)>=new Date(this.issue.expiryDate)){this.issueError='Due date must be before expiry date.';return}
    try{await this.api.request('cards','/card',{method:'POST',body:JSON.stringify({...this.issue,customerId})});this.closeIssue();this.notice='Credit card issued successfully.';await this.load();window.setTimeout(()=>{this.notice='';this.cdr.detectChanges()},3000)}catch(error:any){const message=error?.message||'Unable to issue this card.';this.issueError=message.includes('only one PRIMARY')?'This customer already has this card type. Choose the other type.':message.includes('one PRIMARY and one ADD_ON')?'A customer can have only one Primary and one Add-on card.':message}finally{this.cdr.detectChanges()}}  nav(path:string){this.router.navigateByUrl(path)} id(card:any){return card?.creditId??card?.id} statusOf(card:any){return String(card?.status||card?.cardStatus||'ACTIVE').toUpperCase()}
  holder(card:any){return card?.cardHolderName||[card?.customer?.firstName,card?.customer?.lastName].filter(Boolean).join(' ')||'Unknown cardholder'}
  cardNumber(card:any){const n=String(card?.cardNumber||'').replace(/\D/g,'');return n?`**** ${n.slice(-4)}`:'Card number pending'}
  money(value:any){return new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',maximumFractionDigits:0}).format(Number(value)||0)}
  limit(card:any){return Number(card?.cardLimit??card?.creditLimit??0)} available(card:any){return Number(card?.availableCredit??card?.availableBalance??0)}
  get visible(){const query=this.search.trim().toLowerCase();return this.cards.filter(card=>(!this.status||this.statusOf(card)===this.status)&&(!query||String(card?.cardNumber||'').includes(query)||String(card?.customerId??card?.custId??'').includes(query)))}
  get pagedVisible(){return this.visible.slice((this.page-1)*this.pageSize,this.page*this.pageSize)}
  resetPage(){this.page=1}}