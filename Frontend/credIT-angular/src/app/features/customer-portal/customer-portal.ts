import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { CreditApi } from '../../core/credit-api';
import { Session } from '../../core/session';
import { formatIndiaDateTime } from '../../core/india-date-time';
import { cardLimit, effectiveAvailable, heldAmount } from '../../core/credit-holds';

@Component({selector:'app-customer-portal',standalone:true,imports:[FormsModule],templateUrl:'./customer-portal.html',styleUrl:'./customer-portal.css'})
export class CustomerPortal implements OnInit {
  private api=inject(CreditApi);
  private router=inject(Router);
  private cdr=inject(ChangeDetectorRef);
  readonly session=inject(Session);
  data:any={customer:{},cards:[],transactions:[],cardRequests:[],availableMerchants:[]};
  loading=true;error='';message='';section='dashboard';page=1;pageSize=10;search='';statusFilter='ALL';
  requestOpen=false;request={cardName:'SILVER',cardType:'PRIMARY',note:''};
  blockTarget:any=null;renewTarget:any=null;renew={expiryDate:'',dueDate:''};actionError='';
  transactionOpen=false;transactionError='';transaction={cardId:'',merchantId:'',transactionType:'PURCHASE',amount:null as number|null,paymentMethod:'CHIP'};

  constructor(){this.syncSection();this.router.events.pipe(filter(e=>e instanceof NavigationEnd)).subscribe(()=>{this.syncSection();this.page=1;this.search='';});}
  async ngOnInit(){await this.refresh();}
  syncSection(){this.section=this.router.url.split('?')[0].split('/')[2]||'dashboard';}
  async refresh(){this.loading=true;this.error='';try{this.data=await this.api.request<any>('auth','/api/customer-portal/dashboard');}catch(e:any){this.error=e?.message||'Unable to load your account.';}finally{this.loading=false;this.cdr.detectChanges();}}
  name(){const c=this.data.customer||{};return [c.custFirstName??c.firstName,c.custLastName??c.lastName].filter(Boolean).join(' ')||this.session.user?.username||'Customer';}
  status(card:any){return String(card?.status??card?.cardStatus??'ACTIVE').toUpperCase();}
  current(card:any){return card?.replacedByCreditId==null;}
  get cards(){return [...(this.data.cards||[])].filter(c=>this.current(c)).sort((a,b)=>(this.status(a)==='ACTIVE'?-1:1)-(this.status(b)==='ACTIVE'?-1:1));}
  get activeCards(){return this.cards.filter(c=>this.status(c)==='ACTIVE');}
  get eligibleCards(){return this.activeCards.filter(card=>!this.isExpired(card));}
  merchantId(merchant:any){return merchant?.merchantId??merchant?.id;}
  merchantName(merchant:any){return [merchant?.firstName,merchant?.lastName].filter(Boolean).join(' ')||merchant?.merchantName||'Merchant';}
  get activeMerchants(){return (this.data.availableMerchants||[]).filter((merchant:any)=>String(merchant?.status||'ACTIVE').toUpperCase()==='ACTIVE');}
  get selectedTransactionCard(){return this.eligibleCards.find(card=>String(card.creditId??card.id)===String(this.transaction.cardId))||null;}
  selectedAvailable(){return this.selectedTransactionCard?effectiveAvailable(this.selectedTransactionCard,this.transactions,this.cards):0;}
  selectedHeld(){return this.selectedTransactionCard?heldAmount(this.selectedTransactionCard,this.transactions,this.cards):0;}
  selectedLimit(){return this.selectedTransactionCard?cardLimit(this.selectedTransactionCard):0;}
  get transactions(){return [...(this.data.transactions||[])].sort((a,b)=>new Date(b.timestamp||0).getTime()-new Date(a.timestamp||0).getTime());}
  requestStatus(request:any){return String(request?.status||'PENDING').toUpperCase();}
  get pendingCardRequests(){return (this.data.cardRequests||[]).filter((request:any)=>['PENDING','ON_HOLD'].includes(String(request?.status||'PENDING').toUpperCase())).length;}
  get canRequestCard(){return this.cards.length+this.pendingCardRequests<2;}
  get requestLimitMessage(){if(this.cards.length>=2)return 'You already have the maximum of 2 cards.';if(this.cards.length+this.pendingCardRequests>=2)return 'A card request is already awaiting manager approval.';return '';}
  hasCardType(type:string){const expected=type.toUpperCase();return this.cards.some(card=>String(card.cardType||'').toUpperCase()===expected)||(this.data.cardRequests||[]).some((request:any)=>['PENDING','ON_HOLD'].includes(String(request?.status||'PENDING').toUpperCase())&&String(request?.cardType||'').toUpperCase()===expected);}
  openRequest(){this.actionError='';if(!this.canRequestCard){this.showMessage(this.requestLimitMessage);return;}const existingTypes=new Set(this.cards.map(card=>String(card.cardType||'').toUpperCase()));this.request.cardType=existingTypes.has('PRIMARY')?'ADD_ON':'PRIMARY';if(this.cards.length)this.request.cardName=String(this.cards[0].cardName||'SILVER').toUpperCase();this.requestOpen=true;}
  get trend(){const rows=[...this.transactions].reverse().slice(-8);const values=rows.map(transaction=>Number(transaction.amount??transaction.transactionAmount)||0);const max=Math.max(...values,1);return rows.map((transaction,index)=>({x:values.length===1?50:4+(index/(values.length-1))*92,y:88-(values[index]/max)*66,label:new Date(transaction.timestamp||Date.now()).toLocaleDateString('en-IN',{day:'2-digit',month:'short'}),amount:values[index]}));}
  get trendPath(){const points=this.trend;if(!points.length)return '';if(points.length===1)return `M ${points[0].x} ${points[0].y}`;let path=`M ${points[0].x} ${points[0].y}`;for(let index=0;index<points.length-1;index++){const before=points[index-1]||points[index],start=points[index],end=points[index+1],after=points[index+2]||end;path+=` C ${(start.x+(end.x-before.x)/6).toFixed(1)} ${(start.y+(end.y-before.y)/6).toFixed(1)}, ${(end.x-(after.x-start.x)/6).toFixed(1)} ${(end.y-(after.y-start.y)/6).toFixed(1)}, ${end.x.toFixed(1)} ${end.y.toFixed(1)}`;}return path;}
  get trendAreaPath(){const points=this.trend;if(!points.length)return '';const first=points[0],last=points[points.length-1];return `M ${first.x} 96 L ${first.x} ${first.y} ${this.trendPath.replace(/^M\s+[^ ]+\s+[^ ]+/,'')} L ${last.x} 96 Z`;}
  get outstanding(){return this.cards.length?Math.max(...this.cards.map(c=>Math.max(0,Number(c.cardLimit||0)-Number(c.availableCredit||0)))):0;}
  get available(){return this.cards.length?Math.max(...this.cards.map(c=>Number(c.availableCredit||0))):0;}
  get merchants(){const map=new Map<string,any>();for(const t of this.transactions){if(String(t.transactionType).toUpperCase()==='PAYMENT')continue;const key=String(t.merchantId??t.merchantName??'Unknown');const item=map.get(key)||{id:t.merchantId,name:t.merchantName||'Unknown merchant',count:0,total:0,last:null};item.count++;item.total+=Number(t.amount||0);if(!item.last||new Date(t.timestamp)>new Date(item.last))item.last=t.timestamp;map.set(key,item);}return [...map.values()].sort((a,b)=>b.total-a.total);}
  days(value:any){if(!value)return null;const today=new Date();today.setHours(0,0,0,0);const date=new Date(String(value)+'T00:00:00');return Math.ceil((date.getTime()-today.getTime())/86400000);}
  warnings(card:any){const result:string[]=[];const expiry=this.days(card.expiryDate),due=this.days(card.dueDate);if(this.status(card)!=='ACTIVE')result.push(this.status(card).toLowerCase());if(expiry!==null&&expiry<0)result.push('expired');else if(expiry!==null&&expiry<=30)result.push('expires in '+expiry+' days');if(due!==null&&due<0)result.push('payment overdue');else if(due!==null&&due<=14)result.push('due in '+due+' days');if(Number(card.cardLimit)>0&&Number(card.availableCredit)/Number(card.cardLimit)<=.1)result.push('low available credit');return result;}
  get attention(){return this.cards.map(card=>({card,warnings:this.warnings(card)})).filter(x=>x.warnings.length);}
  get filteredTransactions(){const q=this.search.toLowerCase().trim();return this.transactions.filter(t=>(this.statusFilter==='ALL'||String(t.status).toUpperCase()===this.statusFilter)&&(!q||[t.transactionId,t.cardNumber,t.merchantName,t.transactionType].some(v=>String(v??'').toLowerCase().includes(q))));}
  list(){if(this.section==='cards')return this.cards;if(this.section==='merchants')return this.merchants;if(this.section==='transactions')return this.filteredTransactions;if(this.section==='attention')return this.attention;return [];}
  paged(){const rows=this.list();const max=Math.max(1,Math.ceil(rows.length/this.pageSize));if(this.page>max)this.page=max;return rows.slice((this.page-1)*this.pageSize,this.page*this.pageSize);}
  pages(){return Math.max(1,Math.ceil(this.list().length/this.pageSize));}
  lastSix(value:any){return String(value||'').replace(/\D/g,'').slice(-6);}
  cardNumber(value:any,mask=false){const d=String(value||'').replace(/\D/g,'');if(!d)return 'Pending';return mask?'**** **** '+d.slice(-4):d.replace(/(.{4})(?=.)/g,'$1 ');}
  money(value:any){return new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',maximumFractionDigits:0}).format(Number(value||0));}
  date(value:any){return formatIndiaDateTime(value);}
  showMessage(text:string){this.message=text;this.cdr.detectChanges();window.setTimeout(()=>{this.message='';this.cdr.detectChanges();},3000);}
  openMerchant(merchant:any){if(merchant?.id!=null)this.router.navigate(['/my-account/merchants',merchant.id]);}
  openTransaction(transaction:any){const id=transaction?.transactionId??transaction?.id;if(id!=null)this.router.navigate(['/my-account/transactions',id]);}
  openTransactionDialog(){this.transactionError='';const card=this.eligibleCards[0],merchant=this.activeMerchants[0];this.transaction={cardId:card?String(card.creditId??card.id):'',merchantId:merchant?String(this.merchantId(merchant)):'',transactionType:'PURCHASE',amount:null,paymentMethod:'CHIP'};this.transactionOpen=true;}
  closeTransactionDialog(){this.transactionOpen=false;this.transactionError='';}
  async createTransaction(){this.transactionError='';const card=this.selectedTransactionCard;const merchant=this.activeMerchants.find((item:any)=>String(this.merchantId(item))===String(this.transaction.merchantId));const amount=Number(this.transaction.amount||0);if(!card||!merchant){this.transactionError='Select an active, unexpired card and an active merchant.';return;}if(amount<=0){this.transactionError='Enter a transaction amount greater than zero.';return;}if(['PURCHASE','AUTHORIZATION'].includes(this.transaction.transactionType)&&amount>this.selectedAvailable()){this.transactionError='Amount exceeds available credit of '+this.money(this.selectedAvailable())+'.';return;}try{await this.api.request('auth','/api/customer-portal/transactions',{method:'POST',body:JSON.stringify(this.transaction)});this.transactionOpen=false;this.showMessage('Transaction recorded successfully.');await this.refresh();}catch(e:any){this.transactionError=e?.message||'Unable to record this transaction.';}finally{this.cdr.detectChanges();}}
  async submitRequest(){this.actionError='';if(!this.canRequestCard){this.actionError=this.requestLimitMessage;this.cdr.detectChanges();return;}try{await this.api.request('auth','/api/customer-portal/requests',{method:'POST',body:JSON.stringify(this.request)});this.requestOpen=false;this.showMessage('Card request sent to your manager.');await this.refresh();}catch(e:any){this.actionError=e?.message||'Unable to send card request.';this.cdr.detectChanges();}}
  async block(){if(!this.blockTarget)return;this.actionError='';try{await this.api.request('auth','/api/customer-portal/cards/'+this.blockTarget.creditId+'/block',{method:'POST'});this.blockTarget=null;this.showMessage('Card blocked immediately. New transactions are disabled.');await this.refresh();}catch(e:any){this.actionError=e?.message||'Unable to block this card.';}}
  openCard(card:any){this.router.navigate(['/my-account/cards',card.creditId]);}
  isExpired(card:any){const remaining=this.days(card?.expiryDate);return remaining!==null&&remaining<0;}
  openRenew(card:any){this.renewTarget=card;this.renew={expiryDate:'',dueDate:''};this.actionError='';}
  async renewCard(){if(!this.renewTarget)return;this.actionError='';try{await this.api.request('auth','/api/customer-portal/cards/'+this.renewTarget.creditId+'/renew',{method:'POST',body:JSON.stringify(this.renew)});this.renewTarget=null;this.showMessage('Card renewed successfully. The old physical card is inactive.');await this.refresh();}catch(e:any){this.actionError=e?.message||'Unable to renew this card.';}}
}

