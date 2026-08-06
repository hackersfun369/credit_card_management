import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
export interface SessionUser { username: string; role: string; expiresAt: string; }
@Injectable({ providedIn: 'root' })
export class Session {
  private readonly router = inject(Router); private expiryTimer?: number;
  get token() { return localStorage.getItem('credit-token'); }
  get user(): SessionUser | null { try { return JSON.parse(localStorage.getItem('credit-user') || 'null'); } catch { return null; } }
  get authenticated() { return !!this.token && !this.expired(); }
  expired() { const payload=this.payload(); return !payload?.exp || payload.exp * 1000 <= Date.now(); }
  start() { if (this.token && this.expired()) this.expire(); else this.scheduleExpiry(); }
  save(response:any) { const token=response?.accessToken; if(!token) throw new Error('The sign-in service did not return an access token.'); localStorage.setItem('credit-token',token); localStorage.setItem('credit-user',JSON.stringify({username:response?.username||this.payload(token)?.sub||'Admin Manager',role:response?.role||'ADMIN',expiresAt:response?.expiresAt||''})); this.scheduleExpiry(); }
  clear() { if(this.expiryTimer) window.clearTimeout(this.expiryTimer); this.expiryTimer=undefined; localStorage.removeItem('credit-token'); localStorage.removeItem('credit-user'); }
  logout() { this.clear(); this.router.navigateByUrl('/login'); }
  expire(returnUrl=this.router.url) { this.clear(); this.router.navigate(['/login'],{queryParams:{returnUrl:returnUrl && !returnUrl.startsWith('/login') ? returnUrl : '/'}}); }
  private scheduleExpiry() { if(this.expiryTimer) window.clearTimeout(this.expiryTimer); const exp=this.payload()?.exp; if(!exp) return; const delay=Math.max(0,exp*1000-Date.now()); const refreshIn=delay-60_000; this.expiryTimer=window.setTimeout(()=>refreshIn>0 ? this.refresh() : this.expire(),Math.max(0,refreshIn)); }
  private async refresh() { const token=this.token; if(!token) return this.expire(); try { const response=await fetch('http://localhost:8085/api/auth/refresh',{method:'POST',headers:{Authorization:`Bearer ${token}`}}); if(!response.ok) throw new Error('Refresh failed'); this.save(await response.json()); } catch { this.expire(); } }
  private payload(token=this.token||''):any { try { return JSON.parse(atob(token.split('.')[1].replace(/-/g,'+').replace(/_/g,'/'))); } catch { return null; } }
}