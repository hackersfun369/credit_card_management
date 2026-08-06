import { Component, inject } from '@angular/core';
import { Router, RouterOutlet, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs';
import { Shell } from '../core/shell/shell';
import { Session } from '../core/session';
import { AssistantChat } from '../shared/assistant-chat/assistant-chat';
@Component({selector:'app-root',standalone:true,imports:[RouterOutlet,Shell,AssistantChat],templateUrl:'./app.html',styleUrl:'./app.css'})
export class App {
  private readonly router=inject(Router); readonly session=inject(Session);
  onAuthPage=false;
  constructor(){ this.session.start(); this.onAuthPage=this.router.url.startsWith('/login'); this.router.events.pipe(filter(event=>event instanceof NavigationEnd)).subscribe((event:NavigationEnd)=>this.onAuthPage=event.urlAfterRedirects.startsWith('/login')); }
}