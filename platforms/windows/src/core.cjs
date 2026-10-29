'use strict';
const fs = require('node:fs');
const path = require('node:path');
const {spawn} = require('node:child_process');
const {randomUUID} = require('node:crypto');
const YOUTUBE_ID = /^[A-Za-z0-9_-]{11}$/;
const LOCAL_ID = /^local:[0-9a-f-]{36}$/;
function validId(id) { return typeof id === 'string' && (YOUTUBE_ID.test(id) || LOCAL_ID.test(id)); }
function youtubeId(id) { if (!YOUTUBE_ID.test(id || '')) throw new Error('Ungültiger Videoeintrag.'); return id; }
function cleanTrack(entry) {
  if (!entry || !YOUTUBE_ID.test(entry.id || '')) return null;
  const thumb = entry.thumbnail || entry.thumbnails?.at(-1)?.url || `https://i.ytimg.com/vi/${entry.id}/hqdefault.jpg`;
  return {id: entry.id, kind: 'youtube', title: String(entry.title || 'Unbekannter Titel').slice(0, 300), artist: String(entry.artist || entry.channel || entry.uploader || 'YouTube').slice(0, 200), album: String(entry.album || '').slice(0,200), duration: Number.isFinite(Number(entry.duration)) ? Math.min(86400,Math.max(0,Number(entry.duration))) : 0, thumbnail: /^https:\/\//.test(thumb) ? thumb : ''};
}
function normalized(s) {return String(s).toLowerCase().normalize('NFKD').replace(/[\u0300-\u036f]/g, '').replace(/\b(official|music|video|audio|lyrics|topic|hd|4k)\b/g,' ').replace(/[^\p{L}\p{N}]+/gu,' ').trim();}
function canvasScore(track, candidate) {
  const title = normalized(track.title), artist = normalized(track.artist), hay = normalized(candidate.title + ' ' + candidate.artist);
  const words = title.split(' ').filter(w=>w.length>1);
  const matched = words.filter(w=>hay.split(' ').includes(w)).length;
  if (!words.length || matched / words.length < .7) return -1;
  const artistWords = artist.split(' ').filter(w=>w.length>1);
  if (artistWords.length && !artistWords.some(w=>hay.includes(w))) return -1;
  const unwanted = /\b(cover|karaoke|live|remix|reaction|sped up|slowed)\b/i;
  if (unwanted.test(candidate.title) && !unwanted.test(track.title)) return -1;
  let score = matched / words.length * 10;
  if (/official.*video|music video/i.test(candidate.title)) score += 5;
  if (/lyric|audio|visualizer/i.test(candidate.title)) score -= 3;
  if (track.duration && candidate.duration && Math.abs(track.duration-candidate.duration) > 90) score -= 5;
  if (candidate.id === track.id) score += 1;
  return score;
}
function clipBounds(duration, length=8) {
  if (!Number.isFinite(duration) || duration <= 0) return {start:0,end:0};
  const span = Math.min(duration, length), start = Math.max(0, (duration-span)/2);
  return {start,end:start+span};
}
function parseRange(header, size) {
  if (!header) return {start:0,end:size-1,partial:false};
  const m=/^bytes=(\d*)-(\d*)$/.exec(header);
  if (!m || (!m[1] && !m[2]) || size<=0) return null;
  let start=m[1] ? Number(m[1]) : Math.max(0,size-Number(m[2]));
  let end=m[1] && m[2] ? Math.min(size-1,Number(m[2])) : size-1;
  if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start> end || start>=size || start<0) return null;
  return {start,end,partial:true};
}
function safeMediaUrl(url) {
  try {const u=new URL(url);return u.protocol==='https:' && /(^|\.)googlevideo\.com$/i.test(u.hostname) && !u.username && !u.password;} catch {return false;}
}
function progressLine(line) {
  if (!line.startsWith('progress:')) return null;
  const parts = line.slice(9).split('|').map(Number);
  const [done,total,estimate,speed] = parts;
  const max = total || estimate;
  return {percent:max>0 ? Math.min(100,done/max*100) : null, speed:Number.isFinite(speed)?speed:0};
}
const defaults = () => ({tracks:{},favorites:[],queue:[],queueIndex:-1,recent:[],library:[],playlists:{},history:[],resume:null,downloads:{},canvases:{},preferences:{volume:.75,repeat:'off',shuffle:false,canvasEnabled:true,automaticCanvas:true,canvasZoom:1.35,canvasQuality:480,downloadDir:'',theme:'dark',dynamicBackground:true,backgroundBlur:.5,backgroundBrightness:.6,animations:true,showAlbum:true,showProgress:true,autoHideControls:true,miniAlwaysOnTop:true,minimizeToTray:false,startWithWindows:false,startMinimized:false}});
class Store {
  constructor(file) {
    this.file=file; this.state=defaults(); this.recovered=false;
    if (fs.existsSync(file)) {
      try {
        const data=JSON.parse(fs.readFileSync(file,'utf8'));
        if (!data || typeof data!=='object' || Array.isArray(data) || !data.tracks || typeof data.tracks!=='object') throw new Error('Invalid library');
        this.state={...this.state,...data,preferences:{...this.state.preferences,...data.preferences}};
        for (const key of ['favorites','queue','recent','library']) this.state[key]=(Array.isArray(data[key])?data[key]:[]).filter(id=>validId(id)&&this.state.tracks[id]);
        if(!Array.isArray(this.state.history))this.state.history=[];
        if(!this.state.history.length&&this.state.recent.length)this.state.history=this.state.recent.map((trackId,i)=>({id:randomUUID(),trackId,at:Date.now()-i}));
        if(!this.state.playlists||typeof this.state.playlists!=='object'||Array.isArray(this.state.playlists))this.state.playlists={};
        if (!Number.isInteger(this.state.queueIndex) || this.state.queueIndex>=this.state.queue.length || this.state.queueIndex<0) this.state.queueIndex=this.state.queue.length ? 0 : -1;
      } catch {fs.copyFileSync(file, file+'.corrupt-'+Date.now());this.recovered=true;}
    }
  }
  save(emit=true) {fs.mkdirSync(path.dirname(this.file),{recursive:true});const tmp=this.file+'.tmp';fs.writeFileSync(tmp,JSON.stringify(this.state));fs.renameSync(tmp,this.file);if(emit)this.onSave?.();}
  add(tracks) {for(const t of tracks) if(t&&validId(t.id)) this.state.tracks[t.id]={...this.state.tracks[t.id],...t};this.prune(tracks.map(t=>t?.id));this.save();}
  prune(extra=[]) {
    const s=this.state,keep=new Set([...extra,...s.favorites,...s.queue,...s.recent,...(s.library||[]),...Object.keys(s.downloads),...(s.history||[]).map(x=>x.trackId),...Object.values(s.playlists||{}).flatMap(x=>x.tracks),...Object.entries(s.canvases).flatMap(([id,c])=>[id,c.videoId]),s.resume?.id,s.remoteResume?.id]);
    const unused=Object.keys(s.tracks).filter(id=>s.tracks[id].kind!=='local'&&!keep.has(id));
    for(const id of unused.slice(0,Math.max(0,unused.length-500)))delete s.tracks[id];
  }
  act(action, payload={}) {
    const s=this.state; const id=payload.id;
    if (['favorite','enqueue','play'].includes(action) && (!validId(id)||!s.tracks[id])) throw new Error('Titel nicht gefunden.');
    switch(action) {
      case 'library': if(!validId(id)||!s.tracks[id])throw new Error('Titel nicht gefunden.');s.library=s.library.includes(id)?s.library.filter(x=>x!==id):[id,...s.library];break;
      case 'playlistCreate': {const name=String(payload.name||'').trim();if(!name||name.length>150)throw new Error('Bitte einen Playlist-Namen eingeben (max. 150 Zeichen).');const key=randomUUID();s.playlists[key]={name,tracks:[]};break;}
      case 'playlistRename': {const p=s.playlists[payload.playlistId],name=String(payload.name||'').trim();if(!p||!name||name.length>150)throw new Error('Ungültige Playlist.');p.name=name;break;}
      case 'playlistDelete': if(!s.playlists[payload.playlistId])throw new Error('Playlist nicht gefunden.');delete s.playlists[payload.playlistId];break;
      case 'playlistAdd': {const p=s.playlists[payload.playlistId];if(!p||!validId(id)||!s.tracks[id]||p.tracks.length>=10000)throw new Error('Ungültiger Playlist-Titel.');p.tracks.push(id);break;}
      case 'playlistRemove': {const p=s.playlists[payload.playlistId];if(!p||!Number.isInteger(payload.index)||!p.tracks[payload.index])throw new Error('Ungültige Playlist-Position.');p.tracks.splice(payload.index,1);break;}
      case 'resume': {if(!validId(id)||!s.tracks[id]||!Number.isFinite(payload.position)||payload.position<0||payload.position>86400)throw new Error('Ungültiger Playback-Status.');s.resume={id,position:payload.position,updatedAt:Date.now()};break;}
      case 'favorite': s.favorites=s.favorites.includes(id)?s.favorites.filter(x=>x!==id):[id,...s.favorites];break;
      case 'enqueue': s.queue.push(id); if(s.queueIndex<0)s.queueIndex=0;break;
      case 'play': {
        if (Array.isArray(payload.ids)) {const ids=payload.ids.filter(x=>validId(x)&&s.tracks[x]);const ix=ids.indexOf(id);if(ix>=0){s.queue=ids;s.queueIndex=ix;}else throw new Error('Titel nicht in Warteschlange.');}
        else {const ix=s.queue.indexOf(id);if(ix<0){s.queue.push(id);s.queueIndex=s.queue.length-1;}else s.queueIndex=ix;}
        s.recent=[id,...s.recent.filter(x=>x!==id)].slice(0,50);break;
      }
      case 'select': {const i=Number(payload.index);if(!Number.isInteger(i)||!s.queue[i])throw new Error('Ungültige Position.');s.queueIndex=i;s.recent=[s.queue[i],...s.recent.filter(x=>x!==s.queue[i])].slice(0,50);break;}
      case 'remove': {const i=Number(payload.index);if(!Number.isInteger(i)||!s.queue[i])throw new Error('Ungültige Position.');s.queue.splice(i,1);if(i<s.queueIndex)s.queueIndex--;else if(s.queueIndex>=s.queue.length)s.queueIndex=s.queue.length-1;break;}
      case 'move': {const from=Number(payload.from),to=Number(payload.to);if(!Number.isInteger(from)||!Number.isInteger(to)||!s.queue[from]||!s.queue[to])throw new Error('Ungültige Position.');const original=s.queueIndex;const [item]=s.queue.splice(from,1);s.queue.splice(to,0,item);if(original===from)s.queueIndex=to;else if(from<original&&to>=original)s.queueIndex--;else if(from>original&&to<=original)s.queueIndex++;break;}
      case 'clear':s.queue=[];s.queueIndex=-1;break;
      default: throw new Error('Unbekannte Aktion.');
    }
    if(action==='play'||action==='select'){const trackId=s.queue[s.queueIndex];s.history.unshift({id:randomUUID(),trackId,at:Date.now()});s.history=s.history.slice(0,10000);s.resume={id:trackId,position:0,updatedAt:Date.now()};}
    this.save();return s;
  }
}
function runEngine(executable, args, {nodePath=process.execPath, onLine, signal, timeout=90000}={}) {
  return new Promise((resolve,reject)=>{
    const child=spawn(executable,['--ignore-config','--no-warnings','--no-playlist','--js-runtimes',`node:${nodePath}`,...args],{windowsHide:true,env:{...process.env,ELECTRON_RUN_AS_NODE:'1'}});
    let out='',err='',pending='',finished=false;
    const end=(error,value)=>{if(finished)return;finished=true;clearTimeout(timer);signal?.removeEventListener('abort',abort);if(error)reject(error);else resolve(value);};
    const abort=()=>{child.kill();end(new Error('Vorgang abgebrochen.'));};
    const timer=setTimeout(()=>{child.kill();end(new Error('Zeitüberschreitung. Bitte Verbindung prüfen und erneut versuchen.'));},timeout);
    signal?.addEventListener('abort',abort,{once:true});if(signal?.aborted)abort();
    child.stdout.on('data',data=>{out+=data.toString();if(out.length>16*1024*1024){child.kill();end(new Error('Antwort zu groß.'));return;}pending+=data.toString();let i;while((i=pending.indexOf('\n'))>=0){onLine?.(pending.slice(0,i).trim());pending=pending.slice(i+1);}});
    child.stderr.on('data',data=>{err=(err+data.toString()).slice(-16000);});
    child.on('error',error=>end(new Error(error.code==='ENOENT'?'Die Wiedergabe-Komponente fehlt. Bitte Sourcify neu installieren.':error.message)));
    child.on('close',code=>{if(pending)onLine?.(pending.trim());if(code===0)end(null,out);else end(new Error(engineError(err)));});
  });
}
function engineError(stderr) {
  if (/sign in|not a bot|login|cookies/i.test(stderr)) return 'YouTube verlangt für diesen Titel eine Anmeldung. Versuche einen anderen Titel oder importiere eine eigene Musikdatei.';
  if (/unavailable|private|removed|copyright|not available/i.test(stderr)) return 'Dieser Titel ist nicht verfügbar. Versuche ein anderes Suchergebnis.';
  if (/403|forbidden/i.test(stderr)) return 'Der Musikdienst hat den Zugriff abgelehnt. Versuche einen anderen Titel; lokale Musik funktioniert weiterhin.';
  if (/timed out|resolve|connection|network/i.test(stderr)) return 'Keine Verbindung zum Musikdienst. Bitte Internetverbindung prüfen.';
  if (/unsupported|javascript|challenge|format/i.test(stderr)) return 'Dieser Stream konnte nicht aufgelöst werden. Versuche einen anderen Titel.';
  return 'Der Musikdienst antwortet derzeit nicht wie erwartet. Bitte erneut versuchen oder eigene Musik importieren.';
}
module.exports={validId,youtubeId,cleanTrack,canvasScore,clipBounds,parseRange,safeMediaUrl,progressLine,Store,runEngine,engineError,defaults};
