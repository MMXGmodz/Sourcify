'use strict';
const fs=require('node:fs'),path=require('node:path'),{randomUUID}=require('node:crypto');
const {cleanTrack}=require('../../core.cjs');
const YT=/^[A-Za-z0-9_-]{11}$/;
const PREFS=new Set(['volume','repeat','shuffle','theme','canvasEnabled','automaticCanvas','canvasZoom','dynamicBackground','backgroundBlur','backgroundBrightness','animations','showAlbum','showProgress','autoHideControls']);
function atomic(file,value){fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file+'.tmp',JSON.stringify(value),{mode:0o600});fs.renameSync(file+'.tmp',file);}
function projection(s,deviceId){
  const result={};const ids=new Set([...(s.favorites||[]),...(s.library||[]),...(s.recent||[]),...(s.history||[]).map(x=>x.trackId),...Object.values(s.playlists||{}).flatMap(x=>x.tracks||[]),s.resume?.id].filter(id=>YT.test(id||'')));
  for(const id of ids){const t=cleanTrack(s.tracks[id]);if(t)result['track:'+id]=t;}
  for(const id of s.favorites||[])if(YT.test(id))result['favorite:'+id]=true;
  for(const id of s.library||[])if(YT.test(id))result['library:'+id]=true;
  for(const [id,p]of Object.entries(s.playlists||{}))if(!p.localOnly)result['playlist:'+id]={name:p.name,tracks:(p.tracks||[]).filter(id=>YT.test(id))};
  for(const e of s.history||[])if(YT.test(e.trackId))result['history:'+e.id]={trackId:e.trackId,at:e.at};
  for(const [key,value]of Object.entries(s.preferences||{}))if(PREFS.has(key))result['setting:'+key]=value;
  if(s.resume&&YT.test(s.resume.id))result['resume:'+deviceId]={id:s.resume.id,position:s.resume.position,updatedAt:s.resume.updatedAt,deviceId};
  for(const [id,d]of Object.entries(s.downloads||{}))if(YT.test(id))result['download:'+deviceId+':'+id]={trackId:id,at:d.at,deviceId};
  return result;
}
function validEntity(e){
  if(!e||typeof e.key!=='string'||e.key.length>300||!Number.isSafeInteger(e.revision)||e.revision<1||typeof e.deleted!=='boolean')throw new Error('Ungültige Sync-Änderung.');
  const [kind,id]=e.key.split(':');const v=e.value;
  const canonical=(['track','favorite','library'].includes(kind)&&YT.test(id)&&e.key===kind+':'+id)||(['playlist','history','resume'].includes(kind)&&/^[0-9a-f-]{36}$/.test(id)&&e.key===kind+':'+id)||(kind==='setting'&&PREFS.has(id)&&e.key===kind+':'+id)||(kind==='download'&&/^[0-9a-f-]{36}$/.test(id)&&YT.test(e.key.split(':')[2]||'')&&e.key.split(':').length===3);
  if(!canonical)throw new Error('Ungültiger Sync-Schlüssel.');
  if(e.deleted){if(!/^(track|favorite|library|playlist|history|setting|resume|download):/.test(e.key))throw new Error('Ungültiges Sync-Feld.');return e;}
  if(kind==='track'){if(!YT.test(id)||!cleanTrack(v)||v.id!==id)throw new Error('Ungültige Song-Metadaten.');}
  else if(kind==='favorite'||kind==='library'){if(!YT.test(id)||v!==true)throw new Error('Ungültiger Bibliothekseintrag.');}
  else if(kind==='playlist'){if(!/^[0-9a-f-]{36}$/.test(id)||!v||typeof v.name!=='string'||!v.name.trim()||v.name.length>150||!Array.isArray(v.tracks)||v.tracks.length>10000||v.tracks.some(id=>!YT.test(id)))throw new Error('Ungültige Playlist.');}
  else if(kind==='history'){if(!/^[0-9a-f-]{36}$/.test(id)||!v||!YT.test(v.trackId)||!Number.isSafeInteger(v.at)||v.at<0)throw new Error('Ungültiger Verlauf.');}
  else if(kind==='setting'){if(!PREFS.has(id)||!validPreference(id,v))throw new Error('Ungültige Cloud-Einstellung.');}
  else if(kind==='resume'){if(!/^[0-9a-f-]{36}$/.test(id)||!v||!YT.test(v.id)||!Number.isFinite(v.position)||v.position<0||v.position>86400||!Number.isSafeInteger(v.updatedAt)||v.deviceId!==id)throw new Error('Ungültiger Playback-Status.');}
  else if(kind==='download'){if(!v||!YT.test(v.trackId)||!Number.isSafeInteger(v.at)||typeof v.deviceId!=='string'||e.key!==`download:${v.deviceId}:${v.trackId}`)throw new Error('Ungültige Download-Metadaten.');}
  else throw new Error('Unbekanntes Sync-Feld.');
  return e;
}
function validPreference(key,v){
  if(['volume','backgroundBlur','backgroundBrightness'].includes(key))return typeof v==='number'&&Number.isFinite(v)&&v>=0&&v<=1;
  if(key==='canvasZoom')return typeof v==='number'&&Number.isFinite(v)&&v>=1&&v<=2;
  if(key==='repeat')return ['off','all','one'].includes(v);
  if(key==='theme')return ['dark','light','system'].includes(v);
  return typeof v==='boolean';
}
class SyncManager {
  constructor({file,store,api,getToken,deviceId,onStatus=()=>{},onRemote=()=>{}}){
    Object.assign(this,{file,store,api,getToken,deviceId,onStatus,onRemote});this.running=null;this.stopped=false;this.enabled=false;this.retryTimer=null;this.controller=new AbortController();this.abortSignal=this.controller.signal;
    this.data={version:1,cursor:0,entities:{},pending:[],conflicts:[],lastSync:null};
    if(fs.existsSync(file)){const d=JSON.parse(fs.readFileSync(file));if(d.version!==1||!Number.isSafeInteger(d.cursor)||d.cursor<0||!Array.isArray(d.pending)||!Array.isArray(d.conflicts)||!d.entities)throw new Error('Sync-Journal beschädigt. Bibliothek bleibt erhalten.');this.data=d;}
    this.snapshot=this.data.snapshot||projection(store.state,deviceId);this.status='offline';
  }
  info(){return {status:this.status,lastSync:this.data.lastSync,pending:this.data.pending.length,conflicts:this.data.conflicts.map(x=>({key:x.key,local:x.local,remote:x.remote})),enabled:this.enabled};}
  emit(status){this.status=status;this.onStatus(this.info());}
  persist(){atomic(this.file,this.data);}
  capture(){
    const next=projection(this.store.state,this.deviceId);
    if(this.enabled){for(const key of new Set([...Object.keys(this.snapshot),...Object.keys(next)])){
      if(JSON.stringify(this.snapshot[key])===JSON.stringify(next[key]))continue;
      this.data.pending=this.data.pending.filter(p=>p.key!==key);
      this.data.pending.push({id:randomUUID(),key,value:next[key]??null,deleted:!(key in next),baseRevision:this.data.entities[key]?.revision||0,timestamp:Date.now()});
    }this.data.snapshot=next;this.persist();this.schedule();}
    this.snapshot=next;
  }
  enable(){this.enabled=true;this.capture();this.emit('offline');this.schedule(0);}
  seed(){this.snapshot={};this.capture();}
  schedule(delay=1200){if(this.stopped||!this.enabled)return;if(this.retryTimer&&this.retryAt<=Date.now()+delay)return;clearTimeout(this.retryTimer);this.retryAt=Date.now()+delay;this.retryTimer=setTimeout(()=>{this.retryTimer=null;this.sync().catch(()=>{});},delay);this.retryTimer.unref?.();}
  async sync(){if(this.stopped||!this.enabled)return this.info();if(this.running)return this.running;this.running=this.perform().finally(()=>{this.running=null;});return this.running;}
  async perform(){
    if(this.data.conflicts.length){this.emit('conflict');return this.info();}
    this.emit('syncing');
    try{
      for(let page=0;page<100;page++){
        const batch=this.data.pending.slice(0,100),token=await this.getToken();
        if(this.stopped)return this.info();
        const reply=await this.api.request('/v1/sync',{method:'POST',token,body:{protocolVersion:1,deviceId:this.deviceId,cursor:this.data.cursor,operations:batch},signal:this.abortSignal});
        if(this.stopped)return this.info();
        if(!reply||!Number.isSafeInteger(reply.cursor)||reply.cursor<this.data.cursor||!Array.isArray(reply.changes)||reply.changes.length>1000||!Array.isArray(reply.acknowledged)||reply.acknowledged.some(id=>!batch.some(p=>p.id===id))||!Array.isArray(reply.conflicts||[]))throw new Error('Ungültige Sync-Antwort.');
        const changes=reply.changes.map(validEntity),conflicts=(reply.conflicts||[]).map(c=>{const op=batch.find(p=>p.id===c.operationId);if(!op||!c.remote||validEntity(c.remote).key!==op.key)throw new Error('Ungültiger Sync-Konflikt.');return {key:op.key,operationId:op.id,local:op,remote:c.remote};});
        if(changes.some(c=>c.revision>reply.cursor))throw new Error('Ungültige Sync-Revision.');
        this.data.pending=this.data.pending.filter(p=>!reply.acknowledged.includes(p.id));
        // A newer local edit causally follows our acknowledged upload of the same field.
        for(const sent of batch.filter(p=>reply.acknowledged.includes(p.id))){
          const accepted=changes.find(c=>c.key===sent.key&&c.deleted===sent.deleted&&JSON.stringify(c.value)===JSON.stringify(sent.value));
          if(accepted)for(const p of this.data.pending)if(p.key===sent.key&&p.baseRevision===sent.baseRevision)p.baseRevision=accepted.revision;
        }
        for(const e of changes)if(e.revision>(this.data.entities[e.key]?.revision||0))this.data.entities[e.key]=e;
        this.data.cursor=reply.cursor;this.data.conflicts=conflicts.filter(c=>this.data.pending.some(p=>p.id===c.operationId));this.applyRemote();this.persist();
        if(this.data.conflicts.length){this.emit('conflict');return this.info();}
        if(!reply.hasMore&&!this.data.pending.length){this.data.lastSync=Date.now();this.persist();this.emit('synced');this.schedule(30000);return this.info();}
        if(!reply.hasMore&&!reply.acknowledged.length&&batch.length)throw new Error('Cloud bestätigt Änderungen nicht.');
      }
      throw new Error('Sync benötigt weitere Seiten. Bitte erneut synchronisieren.');
    }catch(e){if(this.stopped)return this.info();this.emit(e.status===401?'expired':/offline|fetch|network|ENOTFOUND|ECONN|timed out|Zeitüberschreitung/i.test(e.message)?'offline':'error');if(e.status!==401)this.schedule(15000);throw e;}
  }
  applyRemote(){
    const s=this.store.state,blocked=new Set(this.data.pending.map(x=>x.key));
    for(const [key,e]of Object.entries(this.data.entities)){
      if(blocked.has(key))continue;const [kind,id]=key.split(':'),v=e.value;
      if(kind==='track'){if(!e.deleted)s.tracks[id]=cleanTrack(v);}
      else if(kind==='favorite'||kind==='library'){const list=kind==='favorite'?'favorites':'library';s[list]=(s[list]||[]).filter(x=>x!==id);if(!e.deleted)s[list].push(id);}
      else if(kind==='playlist'){const local=(s.playlists[id]?.tracks||[]).filter(x=>!YT.test(x));if(e.deleted){if(local.length)s.playlists[randomUUID()]={name:s.playlists[id].name+' (Lokale Dateien)',tracks:local,localOnly:true};delete s.playlists[id];}else s.playlists[id]={...v,tracks:[...v.tracks,...local]};}
      else if(kind==='history'){s.history=(s.history||[]).filter(x=>x.id!==id);if(!e.deleted)s.history.push({id,...v});}
      else if(kind==='setting'){if(!e.deleted)s.preferences[id]=v;}
      else if(kind==='resume'&&id!==this.deviceId&&!e.deleted&&(!s.remoteResume||s.remoteResume.updatedAt<v.updatedAt))s.remoteResume=v;
    }
    s.history=(s.history||[]).sort((a,b)=>b.at-a.at).slice(0,10000);s.recent=[...new Set(s.history.map(x=>x.trackId))].filter(id=>s.tracks[id]).slice(0,50);
    this.store.save(false);this.snapshot=projection(s,this.deviceId);this.data.snapshot=this.snapshot;this.onRemote();
  }
  resolve(choice){if(!['local','remote'].includes(choice))throw new Error('Ungültige Konfliktentscheidung.');for(const c of this.data.conflicts){this.data.entities[c.key]=c.remote;const p=this.data.pending.find(p=>p.id===c.operationId);if(!p)continue;if(choice==='local')p.baseRevision=c.remote.revision;else this.data.pending=this.data.pending.filter(x=>x.id!==p.id);}this.data.conflicts=[];this.applyRemote();this.persist();this.schedule(0);}
  stop(){this.stopped=true;this.controller.abort();clearTimeout(this.retryTimer);this.retryTimer=null;}
}
module.exports={SyncManager,projection,validEntity,validPreference,PREFS,atomic};
