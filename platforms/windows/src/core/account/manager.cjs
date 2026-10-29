'use strict';
const fs=require('node:fs'),path=require('node:path'),{createHash,randomUUID}=require('node:crypto');
const {Store,defaults}=require('../../core.cjs');
const {AccountAPI,cloudUrl,profile}=require('./api.cjs');
const {GoogleLogin}=require('../auth/google.cjs');
const {SyncManager,atomic}=require('../sync/manager.cjs');
function namespace(origin,userId,subject=''){return createHash('sha256').update(origin+'\0'+userId+'\0'+subject).digest('hex');}
function mergeLibrary(target,source){
  target.tracks={...source.tracks,...target.tracks};
  for(const key of ['favorites','library','recent'])target[key]=[...new Set([...(target[key]||[]),...(source[key]||[])])];
  target.playlists={...(source.playlists||{}),...(target.playlists||{})};
  target.history=[...(target.history||[]),...(source.history||[])].filter((e,i,a)=>a.findIndex(x=>x.id===e.id)===i).sort((a,b)=>b.at-a.at).slice(0,10000);
  target.preferences={...target.preferences,...source.preferences};
  // Files stay on this PC; only explicit migration associates these references with the account.
  target.downloads={...source.downloads,...target.downloads};target.canvases={...source.canvases,...target.canvases};
}
class AccountManager {
  constructor({root,guest,credentials,openBrowser,fetchFn=fetch,onProfile=()=>{},onChange=()=>{}}){
    Object.assign(this,{root,guest,credentials,openBrowser,fetchFn,onProfile,onChange});this.user=null;this.auth=null;this.api=null;this.sync=null;this.loginFlow=null;this.loggingIn=false;this.generation=0;this.error='';this.refreshing=null;
    this.configFile=path.join(root,'cloud-config.json');this.config={clientId:'',apiOrigin:''};
    if(fs.existsSync(this.configFile)){try{const c=JSON.parse(fs.readFileSync(this.configFile));if(c.clientId&&c.apiOrigin)this.config={clientId:c.clientId,apiOrigin:cloudUrl(c.apiOrigin)};}catch{this.error='Cloud-Konfiguration ungültig.';}}
    this.deviceFile=path.join(root,'device.json');let device;try{device=JSON.parse(fs.readFileSync(this.deviceFile));}catch{}
    this.device={id:device?.id&&/^[0-9a-f-]{36}$/.test(device.id)?device.id:randomUUID(),name:device?.name||require('node:os').hostname().slice(0,80),platform:'windows'};atomic(this.deviceFile,this.device);
  }
  info(){return {user:this.user,configured:!!(this.config.clientId&&this.config.apiOrigin),config:this.config,secureStorage:this.credentials.available(),loggingIn:this.loggingIn,error:this.error,device:this.device,migration:this.migration?{favorites:this.guest.state.favorites.length,playlists:Object.keys(this.guest.state.playlists||{}).length,history:(this.guest.state.history||[]).length}:null,sync:this.sync?.info()||{status:'offline',enabled:false,pending:0,conflicts:[]},avatar:null};}
  emit(){this.onChange(this.info());}
  configure(c){if(this.user||this.loggingIn)throw new Error('Vor einer Cloud-Konfigurationsänderung bitte abmelden.');if(!c||!/^[A-Za-z0-9_-]+\.apps\.googleusercontent\.com$/.test(c.clientId||''))throw new Error('Eine öffentliche Google-Desktop-Client-ID ist erforderlich.');this.config={clientId:c.clientId,apiOrigin:cloudUrl(c.apiOrigin)};atomic(this.configFile,this.config);this.error='';this.emit();return this.info();}
  async restore(){
    try{
      const a=this.credentials.load();if(!a)return;
      if(a.origin!==this.config.apiOrigin||a.clientId!==this.config.clientId||typeof a.refreshToken!=='string'||!a.refreshToken||typeof a.accessToken!=='string'||!a.accessToken||!Number.isFinite(a.expiresAt))throw new Error('Gespeicherte Session passt nicht zur Cloud-Konfiguration.');
      a.user=profile(a.user);this.auth=a;this.api=new AccountAPI(a.origin,{fetchFn:this.fetchFn});await this.activate(a.user);
      
    }catch(e){this.error='Automatische Anmeldung konnte nicht wiederhergestellt werden.';this.auth=null;this.user=null;this.sync?.stop();this.sync=null;await this.onProfile(this.guest);this.emit();}
  }
  async login(){
    if(!this.info().configured)throw new Error('Google-Desktop-Client-ID und gemeinsames Sourcify-Cloud-Backend fehlen.');
    if(!this.credentials.available())throw new Error('Sicherer Credential Store fehlt. Anmeldung wird nicht unsicher gespeichert.');
    if(this.user||this.loggingIn)throw new Error('Bereits angemeldet oder Anmeldung läuft.');
    this.loggingIn=true;this.error='';const generation=this.generation;this.emit();
    try{
      const api=new AccountAPI(this.config.apiOrigin,{fetchFn:this.fetchFn}),challenge=await api.challenge();
      if(generation!==this.generation)throw new Error('Anmeldung abgebrochen.');
      this.loginFlow=new GoogleLogin({clientId:this.config.clientId,fetchFn:this.fetchFn,openBrowser:this.openBrowser});
      const identity=await this.loginFlow.login(challenge.nonce);
      const a=await api.exchange(identity.idToken,challenge.challengeId,this.device);
      if(a.user.googleSubject!==identity.subject||generation!==this.generation)throw new Error('Google-Identität stimmt nicht mit dem Cloud-Account überein.');
      this.auth={...a,origin:this.config.apiOrigin,clientId:this.config.clientId};this.credentials.save(this.auth);this.api=api;await this.activate(a.user);return this.info();
    }catch(e){this.error=e.message;throw e;}finally{this.loggingIn=false;this.loginFlow=null;this.emit();}
  }
  cancel(){this.generation++;this.loginFlow?.cancel();}
  async activate(user){
    this.sync?.stop();this.user=user;this.profileRoot=path.join(this.root,'accounts',namespace(this.config.apiOrigin,user.id,user.googleSubject));
    const file=path.join(this.profileRoot,'profile.json');let meta;try{meta=JSON.parse(fs.readFileSync(file));}catch{}
    const target=new Store(path.join(this.profileRoot,'library.json'));if(!target.state.preferences.downloadDir)target.state.preferences.downloadDir=path.join(this.guest.state.preferences.downloadDir,'Accounts',path.basename(this.profileRoot).slice(0,16));
    this.migration=!meta?.initialized&&(this.guest.state.favorites.length||Object.keys(this.guest.state.playlists||{}).length||this.guest.state.recent.length||Object.keys(this.guest.state.tracks).length)>0;
    await this.onProfile(target);this.store=target;
    this.sync=new SyncManager({file:path.join(this.profileRoot,'sync.json'),store:target,api:this.api,deviceId:this.device.id,getToken:()=>this.token(),onRemote:()=>this.onChange(this.info(),true),onStatus:()=>this.emit()});
    target.onSave=()=>this.sync.capture();
    if(!this.migration&&!meta){atomic(file,{initialized:true,syncEnabled:true});}if(!this.migration&&meta?.syncEnabled!==false)this.sync.enable();this.emit();
  }
  async migrate(choice){
    if(!this.user||!this.migration||!['merge','cloud','local'].includes(choice))throw new Error('Ungültige Bibliotheksübernahme.');
    // The original guest library is never overwritten or deleted.
    if(choice==='merge'||choice==='local'){mergeLibrary(this.store.state,this.guest.state);this.store.save(false);}
    this.migration=false;atomic(path.join(this.profileRoot,'profile.json'),{initialized:true,syncEnabled:choice!=='local'});
    if(choice!=='local'){this.sync.enable();if(choice==='merge')this.sync.seed();await this.sync.sync();}this.emit();return this.info();
  }
  async token(){
    if(!this.auth)throw new Error('Nicht angemeldet.');if(this.auth.expiresAt>Date.now()+60000)return this.auth.accessToken;
    if(!this.refreshing){const current=this.auth,generation=this.generation;this.refreshing=this.api.refresh(current.refreshToken).then(a=>{
      if(generation!==this.generation||!this.auth)throw new Error('Account getrennt.');
      if(a.user.id!==current.user.id||a.user.googleSubject!==current.user.googleSubject){const e=new Error('Session-Account hat sich geändert.');e.status=401;throw e;}
      this.auth={...a,origin:current.origin,clientId:current.clientId};this.credentials.save(this.auth);this.user=a.user;this.emit();return a.accessToken;
    }).catch(async e=>{if(e.status===401&&generation===this.generation)await this.logout(false);throw e;}).finally(()=>{this.refreshing=null;});}return this.refreshing;
  }
  async logout(revoke=true){
    const old=this.auth,api=this.api;this.cancel();this.sync?.stop();if(this.store)this.store.onSave=null;this.sync=null;this.credentials.clear();this.auth=null;this.user=null;this.api=null;this.migration=false;this.error='';await this.onProfile(this.guest);this.emit();
    if(revoke&&old)try{await api.logout(old.accessToken);}catch{this.error='Lokal abgemeldet. Die Server-Session konnte offline nicht widerrufen werden.';this.emit();}return this.info();
  }
  async devices(){const r=await this.api.request('/v1/devices',{token:await this.token()});if(!Array.isArray(r.devices)||r.devices.length>100||r.devices.some(d=>typeof d.id!=='string'||!/^[0-9a-f-]{36}$/.test(d.id)||typeof d.name!=='string'||d.name.length>80||typeof d.platform!=='string'||!Number.isFinite(d.lastSeen)))throw new Error('Ungültige Geräteliste.');return r.devices;}
  async removeDevice(id){if(!/^[0-9a-f-]{36}$/.test(id))throw new Error('Ungültiges Gerät.');await this.api.request('/v1/devices/'+id,{method:'DELETE',token:await this.token()});if(id===this.device.id)await this.logout(false);}
  async logoutOthers(){return this.api.request('/v1/devices/revoke-others',{method:'POST',token:await this.token(),body:{deviceId:this.device.id}});}
}
module.exports={AccountManager,namespace,mergeLibrary};
