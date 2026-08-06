import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';

export interface SessionUser { username: string; role: string; expiresAt: string; customerId?: number|string; }

@Injectable({ providedIn: 'root' })
export class Session {
  private readonly router=inject(Router);
  private expiryTimer?:number;
  private refreshInFlight?:Promise<boolean>;

  get token(){return localStorage.getItem('credit-token');}
  get refreshToken(){return localStorage.getItem('credit-refresh-token');}
  get user():SessionUser|null{try{return JSON.parse(localStorage.getItem('credit-user')||'null');}catch{return null;}}
  get authenticated(){return !!this.token&&!this.expired();}

  expired(){const payload=this.payload();return !payload?.exp||payload.exp*1000<=Date.now();}

  start(){
    if(!this.token)return;
    void this.ensureAccessToken().then(valid=>{if(!valid)this.expire();});
  }

  save(response:any){
    const token=response?.accessToken;
    if(!token)throw new Error('The sign-in service did not return an access token.');
    localStorage.setItem('credit-token',token);
    if(response?.refreshToken)localStorage.setItem('credit-refresh-token',response.refreshToken);
    localStorage.setItem('credit-user',JSON.stringify({username:response?.username||this.payload(token)?.sub||'Admin Manager',role:response?.role||'MANAGER',customerId:response?.customerId||this.payload(token)?.customerId,expiresAt:response?.expiresAt||''}));
    this.scheduleExpiry();
  }

  clear(){
    if(this.expiryTimer)window.clearTimeout(this.expiryTimer);
    this.expiryTimer=undefined;
    localStorage.removeItem('credit-token');
    localStorage.removeItem('credit-refresh-token');
    localStorage.removeItem('credit-user');
  }

  logout(){this.clear();this.router.navigateByUrl('/login');}
  expire(returnUrl=this.router.url){this.clear();this.router.navigate(['/login'],{queryParams:{returnUrl:returnUrl&&!returnUrl.startsWith('/login')?returnUrl:'/'}});}

  async ensureAccessToken(){
    const token=this.token;
    if(!token)return false;
    const exp=this.payload(token)?.exp;
    if(!exp)return false;
    if(exp*1000-Date.now()>45_000)return true;
    return this.refreshAccessToken();
  }

  async refreshAccessToken(){
    if(!this.refreshInFlight)this.refreshInFlight=this.refreshInternal().finally(()=>this.refreshInFlight=undefined);
    return this.refreshInFlight;
  }

  private scheduleExpiry(){
    if(this.expiryTimer)window.clearTimeout(this.expiryTimer);
    const exp=this.payload()?.exp;
    if(!exp)return;
    const refreshIn=Math.max(0,exp*1000-Date.now()-60_000);
    this.expiryTimer=window.setTimeout(()=>{
      void this.refreshAccessToken().then(valid=>valid?this.scheduleExpiry():this.expire());
    },refreshIn);
  }

  private async refreshInternal(){
    const token=this.refreshToken||this.token;
    if(!token)return false;
    try{
      const response=await fetch('http://localhost:8085/api/auth/refresh',{method:'POST',headers:{Authorization:'Bearer '+token}});
      if(!response.ok)return false;
      this.save(await response.json());
      return true;
    }catch{return false;}
  }

  private payload(token=this.token||''):any{
    try{return JSON.parse(atob(token.split('.')[1].replace(/-/g,'+').replace(/_/g,'/')));}
    catch{return null;}
  }
}
