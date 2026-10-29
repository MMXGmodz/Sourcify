'use strict';
const electron=require('electron');
const {app,BrowserWindow,ipcMain,dialog,protocol,net,shell,screen,clipboard,safeStorage}=electron;
const {AccountManager}=require('./core/account/manager.cjs');
const {CredentialStore}=require('./platform/windows/auth/credentials.cjs');
const {DesktopIntegration}=require('./platform/windows/integration.cjs');
const fs=require('node:fs');
const path=require('node:path');
const {randomUUID,randomBytes}=require('node:crypto');
const {Readable}=require('node:stream');
const {Store,youtubeId,cleanTrack,canvasScore,parseRange,safeMediaUrl,progressLine,runEngine}=require('./core.cjs');
const {isIP}=require('node:net');
const {Jam,localAddress}=require('./jam.cjs');
const {prepareEngine}=require('./bootstrap.cjs');
const {musicApi}=require('./music-api.cjs');
const {selection:canvasSelection}=require('./canvas.cjs');
const {ImageCache}=require('./core/cache/images.cjs');
protocol.registerSchemesAsPrivileged([{scheme:'sourcify-media',privileges:{standard:true,secure:true,supportFetchAPI:true,stream:true,bypassCSP:false}}]);
app.setName('Sourcify Player');
let win,store,guestStore,jam,jamStarting,accounts,desktop,profileEpoch=0;
let playback={track:null,paused:true,position:0,duration:0};
const coverCache=new Map(),imagePending=new Map();let imageCache,coverGeneration=0;
const media=new Map(), jobs=new Map();
let enginePromise;
async function executable(){
  if(process.platform!=='win32')return path.join(__dirname,'..','vendor','yt-dlp');
  if(!app.isPackaged)return path.join(__dirname,'..','vendor','yt-dlp.exe');
  if(!enginePromise){
    enginePromise=prepareEngine({file:path.join(app.getPath('userData'),'components','yt-dlp-2026.08.19.exe'),url:'https://github.com/yt-dlp/yt-dlp/releases/download/2026.08.19/yt-dlp.exe',hash:'66674953fe251b89f4d08c5f0e35e0728679bd67ab3d7d05c0562af101dd3e7a',fetchFn:(url,options)=>net.fetch(url,options),onStatus:status=>{if(win&&!win.isDestroyed())win.webContents.send('sourcify:engine',status);}}).catch(error=>{enginePromise=null;throw error;});
  }
  return enginePromise;
}
const engine=async(args,opts={})=>runEngine(await executable(),args,{nodePath:process.execPath,...opts});
function track(id) {const t=store.state.tracks[id];if(!t)throw new Error('Titel nicht gefunden.');return t;}
function publicState() {
  const s=store.state;
  const tracks=Object.fromEntries(Object.entries(s.tracks).map(([id,t])=>[id,{id:t.id,kind:t.kind,title:t.title,artist:t.artist,album:t.album||'',duration:t.duration,thumbnail:t.thumbnail||'',downloaded:!!s.downloads[id],customCanvas:!!s.canvases[id]?.path}]));
  return {tracks,favorites:s.favorites,library:s.library,playlists:s.playlists,history:s.history,resume:s.resume,remoteResume:s.remoteResume,queue:s.queue,queueIndex:s.queueIndex,recent:s.recent,preferences:s.preferences,downloads:Object.keys(s.downloads),activeDownloads:[...jobs.keys()],recovered:store.recovered};
}
function notify(){if(win&&!win.isDestroyed())win.webContents.send('sourcify:state',publicState());desktop?.update();}
function downloadEvent(data){if(win&&!win.isDestroyed())win.webContents.send('sourcify:download',data);}
function token(item) {
  const key=randomBytes(24).toString('hex');media.set(key,item);
  if(media.size>500)media.delete(media.keys().next().value);
  return `sourcify-media://${key}/stream`;
}
const music=musicApi(engine),{remote,search}=music;
function localPath(id) {
  const t=track(id),download=store.state.downloads[id];
  if(download?.path&&fs.existsSync(download.path))return download.path;
  if(t.kind==='local'&&t.path&&fs.existsSync(t.path))return t.path;
  if(t.kind==='local')throw new Error('Die Musikdatei wurde verschoben oder gelöscht. Bitte erneut importieren.');
  return null;
}
function typeFor(file) {
  return {'.mp3':'audio/mpeg','.m4a':'audio/mp4','.mp4':'video/mp4','.webm':'video/webm','.opus':'audio/ogg','.ogg':'audio/ogg','.wav':'audio/wav','.flac':'audio/flac','.mov':'video/quicktime','.mkv':'video/x-matroska'}[path.extname(file).toLowerCase()]||'application/octet-stream';
}
async function mediaResponse(request) {
  try {
    if(!['GET','HEAD'].includes(request.method))return new Response(null,{status:405});
    const item=media.get(new URL(request.url).hostname);
    if(!item)return new Response(null,{status:404});
    if(item.path) {
      const size=fs.statSync(item.path).size,r=parseRange(request.headers.get('range'),size);
      if(!r)return new Response(null,{status:416,headers:{'Content-Range':`bytes */${size}`}});
      const headers={'Content-Type':typeFor(item.path),'Accept-Ranges':'bytes','Content-Length':String(Math.max(0,r.end-r.start+1))};
      if(r.partial)headers['Content-Range']=`bytes ${r.start}-${r.end}/${size}`;
      const stream=request.method==='HEAD'||!size?null:Readable.toWeb(fs.createReadStream(item.path,{start:r.start,end:r.end}));
      return new Response(stream,{status:r.partial?206:200,headers});
    }
    const headers={...item.headers};if(request.headers.get('range'))headers.Range=request.headers.get('range');
    let url=item.url;
    for(let i=0;i<4;i++) {
      if(!safeMediaUrl(url))return new Response(null,{status:403});
      const response=await net.fetch(url,{method:request.method,headers,redirect:'manual',signal:request.signal,bypassCustomProtocolHandlers:true});
      if(response.status>=300&&response.status<400){const next=response.headers.get('location');if(!next)return response;url=new URL(next,url).href;await response.body?.cancel();continue;}
      return response;
    }
    return new Response(null,{status:502});
  } catch {return new Response(null,{status:404});}
}
function register(name,fn){ipcMain.handle('sourcify:'+name,async(event,...args)=>{
  if(event.sender!==win?.webContents||event.senderFrame!==win.webContents.mainFrame)return {error:'Zugriff nicht erlaubt.'};
  const epoch=profileEpoch;try{const result=await fn(...args);if(epoch!==profileEpoch&&!['accountLogin','accountLogout','accountMigration'].includes(name))throw new Error('Account wurde gewechselt.');return result;}catch(error){return {error:error.message||'Der Vorgang ist fehlgeschlagen.'};}
});}
function queueAction(action,p,actor='host'){store.act(action,p);jam?.queueAction(action,p,actor);notify();}
async function cachedImage(url){
  if(typeof url!=='string'||url.length>4096)throw new Error('Ungültiges Bild.');
  const u=new URL(url);if(u.protocol!=='https:'||u.username||u.password||!/(^|\.)(ytimg\.com|googleusercontent\.com|ggpht\.com)$/.test(u.hostname))throw new Error('Bildquelle nicht erlaubt.');
  if(coverCache.has(url))return coverCache.get(url);
  const cached=imageCache.get(url);if(cached)return cached;
  const owner=imageCache,epoch=coverGeneration,key=epoch+':'+url;if(imagePending.has(key))return imagePending.get(key);
  const pending=(async()=>{const r=await net.fetch(url,{signal:AbortSignal.timeout(15000),redirect:'error'});
  const type=r.headers.get('content-type')?.split(';')[0];if(!r.ok||!['image/jpeg','image/png','image/webp'].includes(type))throw new Error('Bild nicht verfügbar.');
  const chunks=[];let size=0;for await(const chunk of r.body){size+=chunk.length;if(size>4*1024*1024)throw new Error('Bild zu groß.');chunks.push(Buffer.from(chunk));}
  const bytes=Buffer.concat(chunks),result='data:'+type+';base64,'+bytes.toString('base64');if(epoch===coverGeneration&&owner===imageCache){try{owner.put(url,bytes,type);}catch{}coverCache.set(url,result);if(coverCache.size>12)coverCache.delete(coverCache.keys().next().value);}return result;})();imagePending.set(key,pending);try{return await pending;}finally{imagePending.delete(key);}
}
function desktopCommand(action){if(win&&!win.isDestroyed())win.webContents.send('sourcify:desktop-control',action);}
function handlers() {
  register('accountState',()=>accounts.info());
  register('accountConfigure',c=>accounts.configure(c));
  register('accountLogin',()=>accounts.login());
  register('accountCancel',()=>{accounts.cancel();return true;});
  register('accountLogout',()=>accounts.logout());
  register('accountMigration',choice=>accounts.migrate(choice));
  register('syncNow',()=>{if(!accounts.sync?.enabled)throw new Error('Cloud-Sync ist nicht aktiviert.');return accounts.sync.sync();});
  register('syncResolve',choice=>{if(!accounts.sync)throw new Error('Nicht angemeldet.');accounts.sync.resolve(choice);return accounts.info();});
  register('devices',()=>{if(!accounts.user)throw new Error('Nicht angemeldet.');return accounts.devices();});
  register('removeDevice',id=>accounts.removeDevice(id));
  register('logoutOthers',()=>accounts.logoutOthers());
  register('googleAccount',()=>shell.openExternal('https://myaccount.google.com/'));
  register('fullscreen',value=>{desktop.fullscreen(value);return true;});
  register('miniPlayer',()=>desktop.openMini());
  register('image',url=>cachedImage(url));
  register('cacheState',()=>({images:imageCache.info(),music:music.cacheInfo()}));
  register('clearCache',()=>{coverGeneration++;coverCache.clear();imageCache.clear();music.clearCache();store.prune();store.save();return true;});
  register('canvasPreview',async id=>{const owner=store;const t=track(id);if(t.kind!=='youtube')throw new Error('Bitte ein Suchvideo auswählen.');const item=await remote(id,true,store.state.preferences.canvasQuality||480);if(owner!==store)throw new Error('Account wurde gewechselt.');return {url:token(item),duration:item.duration,videoId:id};});
  register('canvasSet',(id,p)=>{track(id);store.state.canvases[id]=canvasSelection(p,store.state.tracks);store.save();notify();return true;});
  register('canvasTrim',(id,start)=>{track(id);if(!Number.isFinite(start)||start<0||start>86400||!store.state.canvases[id])throw new Error('Ungültiger Canvas-Ausschnitt.');store.state.canvases[id].start=start;store.save();notify();return true;});
  register('soundSettings',()=>{if(process.platform!=='win32')throw new Error('Windows-Audioeinstellungen sind auf diesem System nicht verfügbar.');return shell.openExternal('ms-settings:sound');});
  register('desktopState',()=>({fullscreen:win.isFullScreen(),windows:process.platform==='win32',autoStartAvailable:process.platform==='win32'&&app.isPackaged}));
  register('playbackState',p=>{
    if(!p||typeof p.paused!=='boolean'||!Number.isFinite(p.position)||p.position<0||p.position>86400||!Number.isFinite(p.duration)||p.duration<0||p.duration>86400)throw new Error('Ungültiger Playback-Status.');
    const t=store.state.tracks[p.id];playback={track:t?{id:t.id,title:t.title,artist:t.artist,album:t.album||'',thumbnail:t.thumbnail||''}:null,paused:p.paused,position:p.position,duration:p.duration};desktop.update();return true;
  });
  register('lyrics',async id=>{
    const t=track(id),url=new URL('https://lrclib.net/api/get');url.search=new URLSearchParams({track_name:t.title,artist_name:t.artist,...(t.album?{album_name:t.album}:{}),...(t.duration?{duration:String(Math.round(t.duration))}:{})}).toString();
    const r=await net.fetch(url.href,{headers:{'User-Agent':'Sourcify Player/Windows'},signal:AbortSignal.timeout(15000),redirect:'error'});if(r.status===404)return {text:'Für diesen Titel sind keine Lyrics verfügbar.'};
    const data=await require('./core/auth/google.cjs').jsonResponse(r,256*1024);return {text:String(data.plainLyrics||data.syncedLyrics||(data.instrumental?'Instrumental':'Keine Lyrics verfügbar.')).slice(0,120000)};
  });
  ipcMain.handle('sourcify:mini-command',(event,action)=>{
    if(event.sender!==desktop.mini?.webContents||event.senderFrame!==event.sender.mainFrame||!['toggle','next','previous','open'].includes(action))return false;
    if(action==='open')desktop.open();else desktopCommand(action);return true;
  });

  register('state',()=>publicState());
  register('search',async q=>{const owner=store;const results=await search(q);if(owner!==store)throw new Error('Account wurde gewechselt.');store.add(results);notify();return results;});
  register('action',(action,p)=>{queueAction(action,p);return publicState();});
  register('jamState',()=>jam.hostState());
  register('jamStart',async name=>{
    if(jamStarting)return jamStarting;
    if(!jam.server){if(typeof name!=='string'||name.length>32)throw new Error('Bitte einen Namen mit höchstens 32 Zeichen wählen.');jam.hostName=name.replace(/[\x00-\x1f\x7f]/g,'').trim()||'Host';}
    jamStarting=jam.start();try{return await jamStarting;}finally{jamStarting=null;}
  });
  register('jamStop',()=>jam.stop());
  register('jamSettings',p=>jam.settings(p));
  register('jamKick',id=>jam.kick(id));
  register('jamPlayback',p=>{jam.setPlayback(p);return true;});
  register('jamCopy',index=>{const links=jam.hostState().links;if(!Number.isInteger(index)||!links?.[index])throw new Error('Kein Einladungslink verfügbar.');clipboard.writeText(links[index]);return true;});
  register('jamOpen',async link=>{let u;try{u=new URL(link);}catch{throw new Error('Bitte einen gültigen Jam-Einladungslink eingeben.');}
    if(u.protocol!=='http:'||isIP(u.hostname)!==4||!localAddress(u.hostname)||!u.port||u.username||u.password||u.pathname!=='/'||u.search||!/^#\d{6}$/.test(u.hash))throw new Error('Bitte den vollständigen Jam-Link eines Hosts im selben WLAN verwenden.');
    await shell.openExternal(u.href);return true;});
  register('audio',async id=>{
    const owner=store;const t=track(id),file=localPath(id);
    if(file)return {url:token({path:file}),duration:t.duration,offline:true};
    const item=await remote(id);if(owner!==store)throw new Error('Account wurde gewechselt.');if(item.duration&&!t.duration){t.duration=item.duration;store.save();notify();}
    return {url:token(item),duration:item.duration,offline:false};
  });
  register('canvas',async id=>{
    const owner=store,t=track(id),s=store.state;if(!s.preferences.canvasEnabled)return null;
    const saved=s.canvases[id];
    if(saved?.path) {
      if(!fs.existsSync(saved.path))throw new Error('Das Canvas-Video wurde verschoben oder gelöscht. Bitte erneut auswählen.');
      return {url:token({path:saved.path}),custom:true,start:saved.start};
    }
    if(!s.preferences.automaticCanvas&&!saved?.manual)return null;
    let candidate=saved?.videoId;
    if(!candidate){
      const results=await search(`${t.artist} ${t.title} official music video`.slice(0,200),8);
      const ranked=results.map(c=>({c,score:canvasScore(t,c)})).filter(x=>x.score>=0).sort((a,b)=>b.score-a.score);
      if(!ranked.length)return null;candidate=ranked[0].c.id;
    }
    const item=await remote(candidate,true,s.preferences.canvasQuality||480);if(owner!==store)throw new Error('Account wurde gewechselt.');
    s.canvases[id]={...saved,videoId:candidate};store.save();
    return {url:token(item),duration:item.duration,custom:false,manual:!!saved?.manual,start:saved?.start,videoId:candidate};
  });
  register('importMusic',async()=>{
    const result=await dialog.showOpenDialog(win,{title:'Musik importieren',properties:['openFile','multiSelections'],filters:[{name:'Musik',extensions:['mp3','m4a','aac','ogg','opus','flac','wav','webm']}]});
    if(result.canceled)return [];
    const tracks=result.filePaths.map(file=>{
      const existing=Object.values(store.state.tracks).find(t=>t.path===file);if(existing)return existing;
      const base=path.parse(file).name,split=base.indexOf(' - ');
      return {id:'local:'+randomUUID(),kind:'local',title:split>0?base.slice(split+3):base,artist:split>0?base.slice(0,split):'Eigene Musik',duration:0,thumbnail:'',path:file};
    });
    store.add(tracks);for(const t of tracks)store.state.recent=[t.id,...store.state.recent.filter(x=>x!==t.id)].slice(0,50);store.save();notify();return tracks.map(({path,...rest})=>rest);
  });
  register('importCanvas',async id=>{
    track(id);const result=await dialog.showOpenDialog(win,{title:'Canvas-Video auswählen',properties:['openFile'],filters:[{name:'Videos (MP4 empfohlen)',extensions:['mp4','webm','mov','mkv']}]});
    if(result.canceled)return false;store.state.canvases[id]={path:result.filePaths[0]};store.save();notify();return true;
  });
  register('resetCanvas',id=>{track(id);delete store.state.canvases[id];store.save();notify();return true;});
  register('preferences',p=>{
    if(!p||typeof p!=='object')throw new Error('Ungültige Einstellung.');
    const prefs=store.state.preferences;
    for(const key of ['shuffle','canvasEnabled','automaticCanvas','dynamicBackground','animations','showAlbum','showProgress','autoHideControls','miniAlwaysOnTop','minimizeToTray','startWithWindows','startMinimized'])if(typeof p[key]==='boolean')prefs[key]=p[key];
    if(['off','all','one'].includes(p.repeat))prefs.repeat=p.repeat;
    if(typeof p.volume==='number'&&Number.isFinite(p.volume))prefs.volume=Math.min(1,Math.max(0,p.volume));
    if([360,480,720].includes(p.canvasQuality))prefs.canvasQuality=p.canvasQuality;
    if(typeof p.canvasZoom==='number'&&Number.isFinite(p.canvasZoom))prefs.canvasZoom=Math.min(2,Math.max(1,p.canvasZoom));
    for(const key of ['backgroundBlur','backgroundBrightness'])if(typeof p[key]==='number'&&Number.isFinite(p[key]))prefs[key]=Math.min(1,Math.max(0,p[key]));
    if(['dark','light','system'].includes(p.theme))prefs.theme=p.theme;
    store.save();desktop?.configure();notify();return publicState();
  });
  register('chooseFolder',async()=>{const r=await dialog.showOpenDialog(win,{title:'Download-Ordner wählen',properties:['openDirectory','createDirectory']});if(r.canceled)return null;store.state.preferences.downloadDir=r.filePaths[0];store.save();notify();return r.filePaths[0];});
  register('openDownloads',async()=>{const dir=store.state.preferences.downloadDir;fs.mkdirSync(dir,{recursive:true});const error=await shell.openPath(dir);if(error)throw new Error('Der Download-Ordner konnte nicht geöffnet werden.');return true;});
  register('cancelDownload',id=>{jobs.get(id)?.abort();return true;});
  register('download',id=>{
    const owner=store;const t=track(id);if(t.kind!=='youtube')throw new Error('Diese Datei ist bereits auf deinem PC.');youtubeId(id);
    if(jobs.has(id))return {started:false};if(jobs.size>=2)throw new Error('Zwei Downloads laufen bereits. Bitte kurz warten.');
    const dir=store.state.preferences.downloadDir;fs.mkdirSync(dir,{recursive:true});
    const controller=new AbortController();jobs.set(id,controller);notify();let finalPath;
    engine(['-f','bestaudio[ext=m4a]/bestaudio','--newline','--progress','--progress-template','download:progress:%(progress.downloaded_bytes)s|%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|%(progress.speed)s','--print','after_move:done:%(filepath)j','-o',path.join(dir,`${id}.%(ext)s`),`https://www.youtube.com/watch?v=${id}`],{
      signal:controller.signal,timeout:30*60*1000,onLine:line=>{
        const progress=progressLine(line);if(progress)downloadEvent({id,status:'running',...progress});
        if(line.startsWith('done:')){try{finalPath=JSON.parse(line.slice(5));}catch{}}
      }
    }).then(()=>{
      if(owner!==store||controller.signal.aborted)throw new Error('Download abgebrochen.');
      if(!finalPath||path.dirname(path.resolve(finalPath))!==path.resolve(dir)||!path.basename(finalPath).startsWith(id+'.')||!fs.existsSync(finalPath))throw new Error('Der Download wurde nicht vollständig gespeichert.');
      store.state.downloads[id]={path:finalPath,at:Date.now()};store.save();downloadEvent({id,status:'complete',percent:100});
    }).catch(error=>downloadEvent({id,status:controller.signal.aborted?'cancelled':'error',message:error.message})).finally(()=>{if(jobs.get(id)===controller)jobs.delete(id);notify();});
    return {started:true};
  });
}
function createWindow(){
  const area=screen.getPrimaryDisplay().workAreaSize;
  win=new BrowserWindow({width:Math.min(1380,area.width),height:Math.min(900,area.height),minWidth:1000,minHeight:680,backgroundColor:'#090E19',title:'Sourcify Player',icon:path.join(__dirname,'..','assets','icon.png'),autoHideMenuBar:true,webPreferences:{preload:path.join(__dirname,'preload.cjs'),contextIsolation:true,nodeIntegration:false,sandbox:true,webSecurity:true}});
  win.setMenu(null);win.webContents.setWindowOpenHandler(()=>({action:'deny'}));win.webContents.on('will-navigate',event=>event.preventDefault());
  win.webContents.on('will-attach-webview',event=>event.preventDefault());
  for(const name of ['minimize','hide'])win.on(name,()=>win.webContents.send('sourcify:visibility',false));
  for(const name of ['restore','show'])win.on(name,()=>win.webContents.send('sourcify:visibility',true));
  win.loadFile(path.join(__dirname,'index.html'));
}
if(!app.requestSingleInstanceLock())app.quit();
else {
  app.on('second-instance',()=>{if(win){if(win.isMinimized())win.restore();win.focus();}});
  app.whenReady().then(()=>{
    store=new Store(path.join(app.getPath('userData'),'library.json'));
    if(!store.state.preferences.downloadDir){store.state.preferences.downloadDir=path.join(app.getPath('music'),'Sourcify');store.save();}
    jam=new Jam({getQueue:()=>store.state,search:async q=>{const found=await search(q,10);store.add(found);notify();return found;},action:queueAction,control:command=>{if(win&&!win.isDestroyed())win.webContents.send('sourcify:jam-control',command);},onChange:state=>{if(win&&!win.isDestroyed())win.webContents.send('sourcify:jam',state);}});
    protocol.handle('sourcify-media',mediaResponse);
    guestStore=store;imageCache=new ImageCache(path.join(app.getPath('userData'),'cache','images'));
    accounts=new AccountManager({root:app.getPath('userData'),guest:guestStore,credentials:new CredentialStore(path.join(app.getPath('userData'),'auth','session.bin'),safeStorage),fetchFn:(url,options)=>net.fetch(url,options),openBrowser:url=>shell.openExternal(url),onProfile:async target=>{
      if(target===store)return;profileEpoch++;for(const c of jobs.values())c.abort();jobs.clear();media.clear();coverCache.clear();coverGeneration++;music.clearCache();await jam.stop();store=target;imageCache=new ImageCache(path.join(path.dirname(store.file),'cache','images'));playback={track:null,paused:true,position:0,duration:0};
      if(win&&!win.isDestroyed())win.webContents.send('sourcify:profile',publicState());desktop?.configure();notify();
    },onChange:(info,remote)=>{if(win&&!win.isDestroyed())win.webContents.send('sourcify:account',info);if(remote)notify();}});
    handlers();createWindow();
    desktop=new DesktopIntegration({electron,window:win,getPrefs:()=>store.state.preferences,setPrefs:p=>{Object.assign(store.state.preferences,p);store.save();},getPlayback:()=>playback,command:desktopCommand});desktop.install();
    if(store.state.preferences.startMinimized||process.argv.includes('--sourcify-minimized')){if(store.state.preferences.minimizeToTray)win.hide();else win.minimize();}
    accounts.restore().catch(()=>{});
    win.webContents.session.setPermissionRequestHandler((_wc,_permission,callback)=>callback(false));
  }).catch(error=>{dialog.showErrorBox('Sourcify konnte nicht starten',error.message);app.quit();});
  app.on('window-all-closed',()=>app.quit());
  app.on('before-quit',()=>{accounts?.cancel();accounts?.sync?.stop();jam?.stop().catch(()=>{});for(const controller of jobs.values())controller.abort();});
}
