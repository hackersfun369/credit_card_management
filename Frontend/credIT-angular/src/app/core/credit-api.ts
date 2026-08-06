import { Injectable, inject } from '@angular/core';
import { Session } from './session';

const endpoints={auth:'http://localhost:8085',customers:'http://localhost:8081',cards:'http://localhost:8082',transactions:'http://localhost:8083',merchants:'http://localhost:8084'} as const;
type Service=keyof typeof endpoints;

@Injectable({providedIn:'root'})
export class CreditApi {
  private readonly session=inject(Session);

  async request<T>(service:Service,path:string,init:RequestInit={}):Promise<T>{
    return this.perform<T>(service,path,init,false);
  }

  private async perform<T>(service:Service,path:string,init:RequestInit,hasRetried:boolean):Promise<T>{
    const publicAuth=service==='auth'&&path.startsWith('/api/auth/');
    if(!publicAuth&&!(await this.session.ensureAccessToken())){
      this.session.expire();
      throw new Error('Your session has expired. Please sign in again.');
    }

    const response=await this.fetch(service,path,init);
    const body=await response.text();

    if(response.status===401&&!publicAuth&&!hasRetried&&await this.session.refreshAccessToken()){
      return this.perform<T>(service,path,init,true);
    }
    if(response.status===401&&!publicAuth){
      this.session.expire();
      throw new Error('Your session has expired. Please sign in again.');
    }
    if(!response.ok)throw new Error(this.messageFrom(body,response.status));
    try{return body?JSON.parse(body) as T:null as T;}
    catch{return body as T;}
  }

  private async fetch(service:Service,path:string,init:RequestInit){
    const controller=new AbortController();
    const timeout=window.setTimeout(()=>controller.abort(),8000);
    try{
      const token=this.session.token;
      return await fetch(`${endpoints[service]}${path}`,{
        ...init,
        signal:init.signal||controller.signal,
        headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{}),...(init.headers||{})}
      });
    }catch(error:any){
      if(error?.name==='AbortError')throw new Error(`${service} service did not respond in time`);
      throw error;
    }finally{window.clearTimeout(timeout);}
  }

  private messageFrom(body:string,status:number){
    try{const parsed=JSON.parse(body);if(parsed?.message)return String(parsed.message);if(parsed?.error)return String(parsed.error);}catch{}
    return `Request failed (${status})`;
  }

  loadAll(){
    return Promise.allSettled([
      this.request('customers','/customer'),
      this.request('cards','/card'),
      this.request('merchants','/merchants'),
      this.request('transactions','/transactions')
    ]).then(results=>results.map(result=>result.status==='fulfilled'?result.value:[]));
  }
}
