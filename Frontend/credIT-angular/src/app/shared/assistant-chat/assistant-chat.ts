import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, inject, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CreditApi } from '../../core/credit-api';
import { Session } from '../../core/session';
import { renderChatMarkdown } from './chat-markdown';

type ActionProposal={operation:string;payload:Record<string,unknown>;summary:string};
type ChatMessage={id:number;role:string;content:string;createdAt:string;proposal?:ActionProposal;actionResult?:string};
type Conversation={conversationId:string;title:string};
type ActionResult={success:boolean;message:string;operation:string;status:number};

@Component({selector:'app-assistant-chat',standalone:true,imports:[CommonModule,FormsModule],templateUrl:'./assistant-chat.html',styleUrl:'./assistant-chat.css'})
export class AssistantChat implements OnInit,OnDestroy {
  private readonly api=inject(CreditApi);
  private readonly session=inject(Session);
  private readonly cdr=inject(ChangeDetectorRef);
  private readonly router=inject(Router);
  private wordQueue:string[]=[];
  private wordTimer?:number;
  private completedMessage?:ChatMessage;
  private activeAssistant?:ChatMessage;

  format=renderChatMarkdown;
  pendingDelete='';
  open=false;
  loading=false;
  actionRunning=false;
  historyLoading=false;
  draft='';
  error='';
  messages:ChatMessage[]=[];
  conversations:Conversation[]=[];
  conversationId=this.newId();

  async ngOnInit(){await this.loadConversations();}
  ngOnDestroy(){if(this.wordTimer)window.clearTimeout(this.wordTimer);}

  async toggle(){
    this.open=!this.open;
    if(this.open){await this.loadConversations();await this.loadHistory(this.conversationId);}
  }

  async loadConversations(){
    try{
      this.conversations=await this.api.request<Conversation[]>('auth','/api/assistant/conversations');
      if(this.conversations.length&&!this.conversations.some(item=>item.conversationId===this.conversationId))this.conversationId=this.conversations[0].conversationId;
    }catch(error:any){this.error=error.message||'Could not load chat history.';}
    finally{this.cdr.detectChanges();}
  }

  async loadHistory(id:string){
    this.historyLoading=true;this.error='';this.conversationId=id;
    try{
      const history=await this.api.request<ChatMessage[]>('auth','/api/assistant/history?conversationId='+encodeURIComponent(id));
      this.messages=history.map(message=>this.decorate(message));
    }catch(error:any){this.error=error.message||'Could not load this conversation.';}
    finally{this.historyLoading=false;this.cdr.detectChanges();}
  }

  newConversation(){this.conversationId=this.newId();this.messages=[];this.draft='';this.error='';}

  async deleteConversation(){
    const id=this.pendingDelete;if(!id)return;this.error='';
    try{
      await this.api.request<void>('auth','/api/assistant/conversations/'+encodeURIComponent(id),{method:'DELETE'});
      this.conversations=this.conversations.filter(item=>item.conversationId!==id);this.pendingDelete='';
      if(this.conversationId===id)this.newConversation();
    }catch(error:any){this.error=error.message||'Could not delete this chat.';}
    finally{this.cdr.detectChanges();}
  }

  async send(){
    const message=this.draft.trim();if(!message||this.loading)return;
    this.loading=true;this.error='';this.draft='';this.wordQueue=[];this.completedMessage=undefined;
    const user:ChatMessage={id:Date.now(),role:'user',content:message,createdAt:new Date().toISOString()};
    const assistant:ChatMessage={id:-Date.now(),role:'assistant',content:'',createdAt:new Date().toISOString()};
    this.activeAssistant=assistant;this.messages=[...this.messages,user,assistant];this.cdr.detectChanges();
    try{
      const token=this.session.token;if(!token)throw new Error('Your session has expired. Please sign in again.');
      const response=await fetch('http://localhost:8085/api/assistant/chat/stream',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},body:JSON.stringify({conversationId:this.conversationId,message})});
      if(!response.ok||!response.body)throw new Error(await this.message(response));
      const reader=response.body.getReader(),decoder=new TextDecoder();let buffer='';
      while(true){
        const next=await reader.read();if(next.done)break;
        buffer+=decoder.decode(next.value,{stream:true});
        const events=buffer.split('\n\n');buffer=events.pop()||'';
        for(const raw of events)this.handleEvent(raw,assistant);
      }
      if(!assistant.content&&!this.wordQueue.length)this.messages=this.messages.filter(item=>item!==assistant);
    }catch(error:any){
      this.messages=this.messages.filter(item=>item!==assistant&&item!==user);this.draft=message;this.error=error.message||'Message could not be sent.';this.loading=false;this.cdr.detectChanges();
    }
  }

  async executeProposal(message:ChatMessage){
    const proposal=message.proposal;if(!proposal||this.actionRunning)return;
    this.actionRunning=true;this.error='';
    try{
      const result=await this.api.request<ActionResult>('auth','/api/assistant/actions/execute',{method:'POST',body:JSON.stringify({operation:proposal.operation,payload:proposal.payload,confirmed:true})});
      if(!result.success)throw new Error(result.message||'The operation could not be completed.');
      message.actionResult=result.message;
      message.proposal=undefined;
      this.messages=[...this.messages];
      await this.persistActionResult(message);
      void this.refreshActivePage();
    }catch(error:any){this.error=error.message||'The operation could not be completed.';}
    finally{this.actionRunning=false;this.cdr.detectChanges();}
  }

  private async persistActionResult(message:ChatMessage){
    if(!message.id||message.id<1||!message.actionResult)return;
    try{
      const saved=await this.api.request<ChatMessage>('auth','/api/assistant/conversations/'+encodeURIComponent(this.conversationId)+'/messages/'+message.id+'/action-result',{method:'POST',body:JSON.stringify({result:message.actionResult})});
      message.content=saved.content;
    }catch{
      // The completed result remains visible in this session even if history persistence is unavailable.
    }
  }
  private async refreshActivePage(){
    const currentUrl=this.router.url;
    try{
      await this.router.navigateByUrl('/__refresh', {skipLocationChange:true});
      await this.router.navigateByUrl(currentUrl, {replaceUrl:true});
    }catch{
      // The mutation succeeded even if the current page refresh cannot be completed.
    }
  }
  dismissProposal(message:ChatMessage){message.proposal=undefined;this.messages=[...this.messages];}

  private handleEvent(raw:string,assistant:ChatMessage){
    const event=(raw.match(/^event:\s*(.+)$/m)||[])[1];
    const data=(raw.match(/^data:\s*(.+)$/m)||[])[1];if(!data)return;
    try{
      const parsed=JSON.parse(data);
      if(event==='started')this.conversationId=parsed.conversationId;
      if(event==='delta')this.enqueueWords(parsed.text||'',assistant);
      if(event==='complete'){this.conversationId=parsed.conversationId;this.completedMessage=this.decorate(parsed.message);this.finishIfReady(assistant);void this.loadConversations();}
      if(event==='error')throw new Error(parsed.message||'Streaming failed.');
    }catch(error:any){if(event==='error'){this.error=error.message;this.loading=false;}}
    finally{this.cdr.detectChanges();}
  }

  private decorate(message:ChatMessage):ChatMessage{
    if(message.role!=='assistant')return message;
    const match=message.content.match(/<credence_action>\s*([\s\S]*?)\s*<\/credence_action>/i);
    if(!match)return message;
    try{
      const parsed=JSON.parse(match[1]);
      if(!this.validProposal(parsed))return {...message,content:message.content.replace(match[0],'').trim()};
      return {...message,content:message.content.replace(match[0],'').trim(),proposal:{operation:parsed.operation,payload:parsed.payload,summary:parsed.summary}};
    }catch{return {...message,content:message.content.replace(match[0],'').trim()};}
  }

  private validProposal(value:any):value is ActionProposal{
    const allowed=['create_customer','update_customer','patch_customer','delete_customer','create_card','update_card','patch_card','block_card','activate_card','activate_and_create_add_on','renew_card','delete_card','create_merchant','update_merchant','patch_merchant','delete_merchant','create_transaction','update_transaction_status','delete_transaction'];
    return !!value&&allowed.includes(value.operation)&&value.payload&&typeof value.payload==='object'&&typeof value.summary==='string';
  }

  private enqueueWords(text:string,assistant:ChatMessage){
    this.wordQueue.push(...(text.match(/\S+\s*/g)||[]));this.activeAssistant=assistant;
    if(!this.wordTimer)this.writeNextWord();
  }

  private writeNextWord(){
    const assistant=this.activeAssistant;const next=this.wordQueue.shift();
    if(assistant&&next){assistant.content+=next;this.messages=[...this.messages];this.cdr.detectChanges();}
    if(this.wordQueue.length){this.wordTimer=window.setTimeout(()=>this.writeNextWord(),18);return;}
    this.wordTimer=undefined;if(assistant)this.finishIfReady(assistant);
  }

  private finishIfReady(assistant:ChatMessage){
    if(this.wordQueue.length||this.wordTimer||!this.completedMessage)return;
    this.messages=this.messages.map(item=>item===assistant?this.completedMessage!:item);this.completedMessage=undefined;this.loading=false;this.cdr.detectChanges();
  }

  private async message(response:Response){
    const text=await response.text();try{return JSON.parse(text).message||'Streaming request failed.';}catch{return 'Streaming request failed.';}
  }

  onKeydown(event:KeyboardEvent){if(event.key==='Enter'&&!event.shiftKey){event.preventDefault();void this.send();}}
  private newId(){return typeof crypto!=='undefined'&&crypto.randomUUID?crypto.randomUUID():'chat-'+Date.now()+'-'+Math.random().toString(36).slice(2);}
}
