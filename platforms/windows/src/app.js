'use strict';
const api=window.sourcify,$=id=>document.getElementById(id);
const paths={people:'M9 3a3 3 0 1 0 0 6 3 3 0 0 0 0-6M3 21v-4a6 6 0 0 1 12 0v4M17 4a3 3 0 0 1 0 6m2 4a5 5 0 0 1 3 5v2',compass:'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18M16 8l-3 5-5 3 3-5z',heart:'M20.8 5.5c-2-2-5-2-6.9 0L12 7.4l-1.9-1.9c-1.9-2-4.9-2-6.9 0s-2 5 0 7L12 21l8.8-8.5c2-2 2-5 0-7',download:'M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5',queue:'M4 5h16M4 10h16M4 15h9m4 0 5 3-5 3z',settings:'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1z',spark:'M12 3l2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5z',search:'M10.5 3a7.5 7.5 0 1 0 0 15 7.5 7.5 0 0 0 0-15M16 16l5 5',plus:'M12 5v14M5 12h14',folder:'M3 7V4h6l3 3h9v13H3z',play:'M8 4l13 8-13 8z',pause:'M7 5h3v14H7zM14 5h3v14h-3z',previous:'M5 5v14M19 5 8 12l11 7z',next:'M19 5v14M5 5l11 7-11 7z',shuffle:'M3 6h3c4 0 7 12 11 12h4m-3-3 3 3-3 3M3 18h3c2 0 3-3 5-6m3-4c1-1 2-2 3-2h4m-3-3 3 3-3 3',repeat:'M3 8l3-3h12l3 3v4M3 8v4m0 4 3 3h12l3-3M3 16v-4m18 4v-4M3 8h5M21 16h-5',volume:'M3 9h4l5-4v14l-5-4H3zM16 8a6 6 0 0 1 0 8m3-11a10 10 0 0 1 0 14',video:'M3 5h12v14H3zM15 9l6-3v12l-6-3',close:'M6 6l12 12M18 6 6 18',check:'M5 12l4 4L20 5',up:'m6 15 6-6 6 6',down:'m6 9 6 6 6-6'};
function icon(name){return `<svg class="${name}" viewBox="0 0 24 24" aria-hidden="true"><path d="${paths[name]||paths.play}"></path></svg>`;}
for(const el of document.querySelectorAll('[data-icon]'))el.innerHTML=icon(el.dataset.icon);
function esc(s){return String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
function time(seconds){if(!Number.isFinite(seconds)||seconds<0)return '0:00';const n=Math.floor(seconds);return `${Math.floor(n/60)}:${String(n%60).padStart(2,'0')}`;}
let jamState={active:false};
let rowLimit=200;let state,view='home',searchIds=[],searchQuery='',searching=false,searchError='',searchTicket=0;
let currentId=null,playTicket=0,canvasTicket=0,loading=false,canvasBounds={start:0,end:0},canvasChosenStart=null,visible=true,toastTimer;
const progress=new Map(),audio=$('audio'),video=$('canvas-video');
function toast(message){$('toast').textContent=message;$('toast').hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').hidden=true,8000);}
async function safely(fn){try{return await fn();}catch(e){toast(e.message);return null;}}
function cover(t){return t?.thumbnail||'../assets/mark.svg';}
function images(root=document){for(const img of root.querySelectorAll('img:not([data-error-bound])')){img.dataset.errorBound='1';img.addEventListener('error',()=>{if(!img.src.endsWith('mark.svg'))img.src='../assets/mark.svg';});}}
function activeTrack(){return state?.tracks[currentId||state.queue[state.queueIndex]];}
function rows(ids,queue=false) {
  return `<div class="track-list">${ids.slice(0,rowLimit).map((id,i)=>{
    const t=state.tracks[id];if(!t)return '';const p=progress.get(id),running=state.activeDownloads.includes(id);
    return `<div class="track-row ${id===currentId?'current':''}" data-id="${esc(id)}" data-index="${i}">
      <button class="track-number" data-action="play" aria-label="Abspielen">${id===currentId&&!audio.paused?'♫':String(i+1).padStart(2,'0')}</button><img src="${esc(cover(t))}" alt="" loading="lazy">
      <button class="track-info" data-action="play"><strong>${esc(t.title)}</strong><span>${t.downloaded||t.kind==='local'?'<i class="offline-tag"></i>':''}${esc(t.artist)}</span>${running?`<div class="download-status">${p?.percent!=null?Math.round(p.percent)+' % geladen':'Download läuft …'}</div>`:''}</button>
      <span class="track-duration">${t.duration?time(t.duration):'—'}</span><div class="track-actions">
      <button class="icon-button ${state.favorites.includes(id)?'selected':''}" data-action="favorite" aria-label="${state.favorites.includes(id)?'Aus Favoriten entfernen':'Favorisieren'}">${icon('heart')}</button>
      ${queue?`<button class="icon-button" data-action="up" aria-label="Nach oben" ${i===0?'disabled':''}>${icon('up')}</button><button class="icon-button" data-action="down" aria-label="Nach unten" ${i===ids.length-1?'disabled':''}>${icon('down')}</button><button class="icon-button" data-action="remove" aria-label="Aus Warteschlange entfernen">${icon('close')}</button>`:`<button class="icon-button" data-action="enqueue" aria-label="Zur Warteschlange hinzufügen">${icon('plus')}</button>${t.kind==='youtube'?`<button class="icon-button ${t.downloaded?'selected':''}" data-action="${running?'cancel':'download'}" aria-label="${running?'Download abbrechen':t.downloaded?'Erneut herunterladen':'Herunterladen'}">${icon(running?'close':t.downloaded?'check':'download')}</button>`:''}`}</div></div>`;
  }).join('')}</div>${ids.length>rowLimit?'<button class="button subtle more-tracks" data-more-tracks>Weitere Titel anzeigen</button>':''}`;
}
function empty(kind,title,text,button=''){return `<div class="empty">${icon(kind)}<h3>${title}</h3><p>${text}</p>${button}</div>`;}
function render(){
  if(!state)return;
  if(['playlists','library','history'].includes(view)&&window.sourcifyUI){window.sourcifyUI.renderCollection(view);renderCurrent();return;}
  if(view==='jam'){$('favorite-count').textContent=state.favorites.length;$('queue-count').textContent=state.queue.length;renderJam();renderCurrent();return;}
  delete $('content').dataset.jamMounted;
  $('favorite-count').textContent=state.favorites.length;$('queue-count').textContent=state.queue.length;
  for(const b of document.querySelectorAll('[data-view]'))b.classList.toggle('active',b.dataset.view===view||(b.dataset.view==='home'&&view==='search'));
  let html='';
  if(view==='home'){
    html=`<section class="hero"><span class="eyebrow">SOURCIFY. DEIN MUSIKRAUM.</span><h1>Dein Sound.<br><em>Dein Moment.</em></h1><p>Finde neue Musik, sammle deine Favoriten und nimm deine Songs überallhin mit.</p><div class="hero-art"><img src="../assets/mark.svg" alt=""></div></section><div class="shortcut-row"><button class="shortcut" data-go="favorites"><span>${icon('heart')}</span>Deine Favoriten</button><button class="shortcut" data-go="downloads"><span>${icon('download')}</span>Offline hören</button><button class="shortcut" data-go="queue"><span>${icon('queue')}</span>Dein nächster Song</button></div><div class="section-heading"><div><h2>Zuletzt gehört</h2><p>Deine Musik, immer einen Klick entfernt.</p></div><button class="text-button" data-import>Musik importieren</button></div>`;
    html+=state.recent.length?rows(state.recent):empty('spark','Hier beginnt dein Sound.','Suche oben nach einem Song oder Künstler. Eigene Musikdateien kannst du direkt importieren.',`<button class="button accent" data-import>${icon('plus')}Musik importieren</button>`);
  }else if(view==='search'){
    html=`<div class="section-heading"><div><span class="eyebrow">ENTDECKEN</span><h1>${esc(searchQuery)}</h1><p>${searching?'Suche im Musikdienst …':`${searchIds.length} Suchergebnisse`}</p></div></div>`;
    if(searching)html+='<div class="loading"><span class="spinner"></span>Musik wird gesucht …</div>';
    else if(searchError)html+=empty('search','Suche nicht verfügbar',esc(searchError),'<button class="button subtle" data-retry-search>Erneut versuchen</button>');
    else html+=searchIds.length?rows(searchIds):empty('search','Keine Treffer.','Versuche einen anderen Songtitel oder Künstler.');
  }else {
    const data={favorites:['DEINE SAMMLUNG','Favoriten','Songs, die bleiben.'],downloads:['OFFLINE HÖREN','Downloads','Heruntergeladene Songs und deine eigenen Musikdateien.'],queue:['DEIN NÄCHSTER SONG','Warteschlange','Deine Reihenfolge. Dein Flow.']}[view];
    const ids=view==='favorites'?state.favorites:view==='queue'?state.queue:[...new Set([...state.activeDownloads,...state.downloads,...Object.keys(state.tracks).filter(id=>state.tracks[id].kind==='local')])];
    html=`<div class="section-heading"><div><span class="eyebrow">${data[0]}</span><h1>${data[1]}</h1><p>${data[2]}</p></div>${ids.length&&view!=='queue'?'<button class="button accent" data-play-all>'+icon('play')+'Alle abspielen</button>':view==='queue'&&ids.length?'<button class="text-button" data-clear>Leeren</button>':''}</div>`;
    html+=ids.length?rows(ids,view==='queue'):empty(view==='favorites'?'heart':view==='queue'?'queue':'download',view==='favorites'?'Deine Favoriten warten.':view==='queue'?'Platz für deinen nächsten Song.':'Deine Musik zum Mitnehmen.',view==='favorites'?'Tippe bei einem Song auf das Herz, um ihn hier zu speichern.':view==='queue'?'Füge Songs mit dem Plus-Symbol hinzu. Hier kannst du sie auch umsortieren.':'Lade einen Song aus den Suchergebnissen herunter oder importiere eigene Musik.',view==='downloads'?'<button class="button accent" data-import>'+icon('plus')+'Musik importieren</button>':'');
  }
  if($('content').dataset.rendered!==html){$('content').innerHTML=html;$('content').dataset.rendered=html;}images($('content'));renderCurrent();
}
function renderCurrent(){
  const t=activeTrack();
  if(t){
    $('now-title').textContent=t.title;$('now-artist').textContent=t.artist;$('player-title').textContent=t.title;$('player-artist').textContent=t.artist;
    if($('player-cover').getAttribute('src')!==cover(t))$('player-cover').src=cover(t);
    if($('now-cover').getAttribute('src')!==cover(t))$('now-cover').src=cover(t);
    $('now-cover').style.display=t.thumbnail?'block':'none';$('canvas-frame').classList.add('has-track');
  }
  $('now-favorite').disabled=!t;$('canvas-file').disabled=!t;$('now-download').disabled=!t||t.kind==='local';
  $('now-favorite').classList.toggle('selected',!!t&&state.favorites.includes(t.id));$('now-download').classList.toggle('selected',!!t&&t.downloaded);
  $('shuffle').classList.toggle('selected',state.preferences.shuffle);$('shuffle').setAttribute('aria-pressed',String(state.preferences.shuffle));
  $('repeat').classList.toggle('selected',state.preferences.repeat!=='off');$('repeat-one').hidden=state.preferences.repeat!=='one';
  $('repeat').setAttribute('aria-label','Wiederholung '+({off:'aus',all:'alle Titel',one:'ein Titel'}[state.preferences.repeat]));
  $('volume').value=state.preferences.volume;audio.volume=state.preferences.volume;
  video.style.transform=`scale(${state.preferences.canvasZoom})`;
  $('play').innerHTML=loading?'<span class="spinner"></span>':icon(audio.paused?'play':'pause');$('play').setAttribute('aria-label',audio.paused?'Abspielen':'Pausieren');
  $('play-status').textContent=loading?'LÄDT':audio.paused?'BEREIT':'SPIELT';
  const next=state.tracks[state.queue[state.queueIndex+1]];
  $('next-track').innerHTML=next?`<img src="${esc(cover(next))}" alt=""><div><strong>${esc(next.title)}</strong><small>${esc(next.artist)}</small></div>`:'<span>Keine weiteren Titel in der Warteschlange.</span>';images();window.sourcifyUI?.update();
}
function visibleIds(){if(['playlists','library','history'].includes(view))return window.sourcifyUI?.collectionIds(view)||[];return view==='search'?searchIds:view==='favorites'?state.favorites:view==='queue'?state.queue:view==='downloads'?[...new Set([...state.downloads,...Object.keys(state.tracks).filter(id=>state.tracks[id].kind==='local')])]:state.recent;}
function navigate(next){rowLimit=200;view=next;render();}
async function doSearch(){
  const q=$('search-input').value.trim();if(!q)return;$('search-input').blur();searchQuery=q;view='search';searching=true;searchError='';const ticket=++searchTicket;render();$('search-button').textContent='Sucht …';
  try{const results=await api.search(q);if(ticket!==searchTicket)return;searchIds=results.map(t=>t.id);}catch(e){if(ticket!==searchTicket)return;searchIds=[];searchError=e.message;}finally{if(ticket===searchTicket){searching=false;$('search-button').textContent='Suchen';render();}}
}
function stopCanvas(){canvasTicket++;video.pause();video.removeAttribute('src');video.load();video.style.display='none';canvasBounds={start:0,end:0};canvasChosenStart=null;$('canvas-status').textContent=state?.preferences.canvasEnabled?'CANVAS':'CANVAS AUS';}
async function loadCanvas(id){
  stopCanvas();if(!state.preferences.canvasEnabled)return;
  const ticket=canvasTicket;$('canvas-status').textContent=state.preferences.automaticCanvas?'CANVAS WIRD GESUCHT':'CANVAS';
  try{const result=await api.canvas(id);if(ticket!==canvasTicket||currentId!==id)return;if(!result){$('canvas-status').textContent='COVER';return;}canvasChosenStart=Number.isFinite(result.start)?result.start:null;video.src=result.url;video.load();$('canvas-status').textContent=result.custom?'DEIN CANVAS':result.manual?'DEIN SUCHVIDEO':'AUTO CANVAS';}
  catch(e){if(ticket===canvasTicket){$('canvas-status').textContent='COVER';$('canvas-status').title=e.message;}}
}
async function startPlayback(id,{position=0}={}){
  const t=state.tracks[id];if(!t)return;
  const ticket=++playTicket;currentId=id;loading=true;audio.pause();audio.removeAttribute('src');audio.load();stopCanvas();render();
  try{
    const result=await api.audio(id);if(ticket!==playTicket)return;audio.src=result.url;audio.load();
    if(position>0)audio.addEventListener('loadedmetadata',()=>{if(ticket===playTicket)audio.currentTime=Math.min(position,audio.duration||position);},{once:true});
    await audio.play();if(ticket!==playTicket)return;loading=false;renderCurrent();loadCanvas(id);
    if('mediaSession' in navigator){navigator.mediaSession.metadata=new MediaMetadata({title:t.title,artist:t.artist,album:t.album||'',artwork:t.thumbnail?[{src:t.thumbnail}]:[]});navigator.mediaSession.playbackState='playing';}
  }catch(e){if(ticket===playTicket){loading=false;$('play-status').textContent='FEHLER';renderCurrent();toast(e.message==='NotSupportedError'?'Diese Musikdatei wird nicht unterstützt. Bitte MP3, M4A oder WAV versuchen.':e.message);}}
}
async function playId(id,ids){const result=await safely(()=>api.action('play',{id,...(ids?.includes(id)?{ids}: {})}));if(result){state=result;startPlayback(id);}}
async function select(index){const result=await safely(()=>api.action('select',{index}));if(result){state=result;startPlayback(state.queue[index]);}}
async function skip(direction=1,ended=false){
  if(!state.queue.length)return;
  if(direction<0&&audio.currentTime>3){audio.currentTime=0;return;}
  if(ended&&state.preferences.repeat==='one'){audio.currentTime=0;await safely(()=>audio.play());return;}
  let index=state.queueIndex+direction;
  if(state.preferences.shuffle&&direction>0&&state.queue.length>1){const options=state.queue.map((_,i)=>i).filter(i=>i!==state.queueIndex);index=options[Math.floor(Math.random()*options.length)];}
  if(index>=state.queue.length){if(state.preferences.repeat==='all')index=0;else{audio.pause();audio.currentTime=0;renderCurrent();return;}}
  if(index<0)index=state.preferences.repeat==='all'?state.queue.length-1:0;
  await select(index);
}
async function togglePlayback(){
  if(loading)return;if(!audio.src){const t=activeTrack();if(t)await playId(t.id);else toast('Suche einen Song oder importiere deine Musik.');return;}
  if(audio.paused)await safely(()=>audio.play());else audio.pause();
}
function stopPlayback(){++playTicket;currentId=null;loading=false;audio.pause();audio.removeAttribute('src');audio.load();stopCanvas();$('now-title').textContent='Dein nächster Lieblingssong.';$('now-artist').textContent='Wähle Musik oder importiere deine Dateien.';$('player-title').textContent='Sourcify Player';$('player-artist').textContent='Dein Sound. Dein Moment.';$('player-cover').src='../assets/mark.svg';$('now-cover').style.display='none';$('canvas-frame').classList.remove('has-track');render();}
function syncVideo(){if(!document.body.classList.contains('fullscreen-mode')&&!window.sourcifyCanvasEditor?.editing&&!audio.paused&&visible&&!document.hidden&&video.readyState>=2){video.play().catch(()=>{});}else video.pause();}
async function importMusic(){const tracks=await safely(()=>api.importMusic());if(tracks?.length){navigate('downloads');toast(`${tracks.length} Musikdatei${tracks.length===1?'':'en'} importiert.`);}}
async function download(id){const result=await safely(()=>api.download(id));if(result?.started){progress.set(id,{percent:null});toast('Download gestartet.');}}
$('search-form').addEventListener('submit',e=>{e.preventDefault();doSearch();});
$('jam-top').addEventListener('click',()=>navigate('jam'));
$('navigation').addEventListener('click',e=>{const b=e.target.closest('[data-view]');if(b)navigate(b.dataset.view);});
document.querySelector('.brand').addEventListener('click',e=>{e.preventDefault();navigate('home');});
$('content').addEventListener('click',async e=>{
  const b=e.target.closest('button');if(!b)return;
  if(b.hasAttribute('data-more-tracks')){rowLimit+=200;render();return;}
  if(b.dataset.go)return navigate(b.dataset.go);
  if(b.hasAttribute('data-import'))return importMusic();
  if(b.hasAttribute('data-retry-search'))return doSearch();
  if(b.hasAttribute('data-play-all'))return playId(visibleIds()[0],visibleIds());
  if(b.hasAttribute('data-clear')){await safely(()=>api.action('clear'));stopPlayback();return;}
  const row=b.closest('[data-id]');if(!row)return;const id=row.dataset.id,index=Number(row.dataset.index),a=b.dataset.action;
  if(a==='play')return view==='queue'?select(index):playId(id,visibleIds());
  if(a==='playlistRemove')return safely(()=>api.action('playlistRemove',{playlistId:window.sourcifyUI.selectedPlaylist,index}));
  if(a==='playlist')return window.sourcifyUI?.addToPlaylist(id);
  if(a==='favorite')return safely(()=>api.action('favorite',{id}));
  if(a==='enqueue'){await safely(()=>api.action('enqueue',{id}));toast('Zur Warteschlange hinzugefügt.');return;}
  if(a==='download')return download(id);if(a==='cancel')return safely(()=>api.cancelDownload(id));
  if(a==='up'||a==='down')return safely(()=>api.action('move',{from:index,to:index+(a==='up'?-1:1)}));
  if(a==='remove'){
    const wasCurrent=index===state.queueIndex,wasPlaying=!audio.paused;
    const result=await safely(()=>api.action('remove',{index}));
    if(result&&wasCurrent){if(!result.queue.length)stopPlayback();else if(wasPlaying)startPlayback(result.queue[result.queueIndex]);else{stopPlayback();renderCurrent();}}
  }
});
for(const id of ['import-top','import-sidebar'])$(id).addEventListener('click',importMusic);
for(const id of ['show-queue','footer-queue'])$(id).addEventListener('click',()=>navigate('queue'));
$('now-favorite').addEventListener('click',()=>safely(()=>api.action('favorite',{id:activeTrack().id})));
$('now-download').addEventListener('click',()=>download(activeTrack().id));
$('canvas-file').addEventListener('click',async()=>{const t=activeTrack();const result=await safely(()=>api.importCanvas(t.id));if(result&&currentId===t.id)loadCanvas(t.id);if(result&&!state.preferences.canvasEnabled)toast('Aktiviere Canvas in den Einstellungen, um dein Video anzuzeigen.');});
$('play').addEventListener('click',togglePlayback);$('previous').addEventListener('click',()=>skip(-1));$('next').addEventListener('click',()=>skip());
$('shuffle').addEventListener('click',()=>safely(()=>api.preferences({shuffle:!state.preferences.shuffle})));
$('repeat').addEventListener('click',()=>safely(()=>api.preferences({repeat:({off:'all',all:'one',one:'off'})[state.preferences.repeat]})));
$('volume').addEventListener('input',()=>{audio.volume=Number($('volume').value);});$('volume').addEventListener('change',()=>safely(()=>api.preferences({volume:Number($('volume').value)})));
$('seek').addEventListener('input',()=>{if(Number.isFinite(audio.duration))audio.currentTime=Number($('seek').value)/100*audio.duration;});
audio.addEventListener('timeupdate',()=>{$('position').textContent=time(audio.currentTime);$('duration').textContent=time(audio.duration);$('seek').value=Number.isFinite(audio.duration)&&audio.duration?audio.currentTime/audio.duration*100:0;});
for(const event of ['play','pause'])audio.addEventListener(event,()=>{renderCurrent();syncVideo();if('mediaSession'in navigator)navigator.mediaSession.playbackState=audio.paused?'paused':'playing';});
audio.addEventListener('ended',()=>skip(1,true));
audio.addEventListener('error',()=>{if(!audio.getAttribute('src'))return;loading=false;renderCurrent();toast('Der Titel konnte nicht abgespielt werden. Drücke Abspielen für einen neuen Versuch oder wähle einen anderen Titel.');audio.removeAttribute('src');audio.load();});
video.addEventListener('loadedmetadata',()=>{if(!Number.isFinite(video.duration)||video.duration<=0)return;const length=Math.min(8,video.duration);const start=canvasChosenStart===null?Math.max(0,(video.duration-length)/2):Math.min(video.duration-length,Math.max(0,canvasChosenStart));canvasBounds={start,end:start+length};video.currentTime=canvasBounds.start;video.style.display='block';syncVideo();});
video.addEventListener('timeupdate',()=>{if(canvasBounds.end&&video.currentTime>=canvasBounds.end-.08)video.currentTime=canvasBounds.start;});
video.addEventListener('ended',()=>{video.currentTime=canvasBounds.start;syncVideo();});
video.addEventListener('error',()=>{video.style.display='none';$('canvas-status').textContent='COVER';});
document.addEventListener('visibilitychange',syncVideo);api.onVisibility(v=>{visible=v;syncVideo();});
$('settings-button').addEventListener('click',()=>{const p=state.preferences;$('setting-canvas').checked=p.canvasEnabled;$('setting-auto').checked=p.automaticCanvas;$('setting-zoom').value=p.canvasZoom;$('zoom-label').textContent=Math.round(p.canvasZoom*100)+' %';$('download-folder').textContent=p.downloadDir;$('reset-canvas').disabled=!activeTrack()?.customCanvas;$('settings-dialog').showModal();});
$('credits-button').addEventListener('click',()=>$('credits-dialog').showModal());$('licenses-button').addEventListener('click',()=>$('licenses-dialog').showModal());
for(const b of document.querySelectorAll('[data-close]'))b.addEventListener('click',()=>b.closest('dialog').close());
for(const [id,key] of [['setting-canvas','canvasEnabled'],['setting-auto','automaticCanvas']])$(id).addEventListener('change',async()=>{await safely(()=>api.preferences({[key]:$(id).checked}));if(currentId)loadCanvas(currentId);});
$('setting-zoom').addEventListener('input',()=>{const value=Number($('setting-zoom').value);$('zoom-label').textContent=Math.round(value*100)+' %';video.style.transform=`scale(${value})`;});
$('setting-zoom').addEventListener('change',()=>safely(()=>api.preferences({canvasZoom:Number($('setting-zoom').value)})));
$('reset-canvas').addEventListener('click',async()=>{const t=activeTrack();if(!t)return;await safely(()=>api.resetCanvas(t.id));$('reset-canvas').disabled=true;if(currentId)loadCanvas(currentId);toast('Eigenes Canvas zurückgesetzt.');});
$('choose-folder').addEventListener('click',async()=>{const dir=await safely(()=>api.chooseFolder());if(dir)$('download-folder').textContent=dir;});$('open-folder').addEventListener('click',()=>safely(()=>api.openDownloads()));
document.addEventListener('keydown',e=>{if(e.code==='Space'&&!['INPUT','TEXTAREA','BUTTON'].includes(document.activeElement.tagName)&&!document.querySelector('dialog[open]')){e.preventDefault();togglePlayback();}});
if('mediaSession'in navigator){for(const [action,fn] of [['play',()=>{if(audio.paused)togglePlayback();}],['pause',()=>audio.pause()],['nexttrack',()=>skip()],['previoustrack',()=>skip(-1)],['seekto',d=>{audio.currentTime=d.seekTime;}],['seekbackward',d=>{audio.currentTime=Math.max(0,audio.currentTime-(d.seekOffset||10));}],['seekforward',d=>{audio.currentTime=Math.min(audio.duration,audio.currentTime+(d.seekOffset||10));}]])try{navigator.mediaSession.setActionHandler(action,fn);}catch{}}
api.onEngine?.(status=>{if(status==='loading')toast('Wiedergabekomponente wird einmalig geladen (17 MB) …');if(status==='ready')toast('Wiedergabekomponente bereit.');});
api.onJam?.(s=>{jamState=s;renderJamBadge();if(view==='jam')renderJam();});
api.onJamControl?.(command=>{if(command.action==='play'){if(audio.paused)togglePlayback();}else if(command.action==='pause')audio.pause();else if(command.action==='next')skip(1);else if(command.action==='previous')skip(-1);else if(command.action==='seek'&&Number.isFinite(audio.duration))audio.currentTime=Math.min(command.position,audio.duration);});
setInterval(()=>{if(jamState.active)api.jamPlayback?.({id:currentId,position:audio.currentTime||0,duration:Number.isFinite(audio.duration)?audio.duration:0,paused:audio.paused,loading}).catch(()=>{});},1000);
api.onState(s=>{state=s;render();});
api.onDownload(p=>{progress.set(p.id,p);if(p.status==='complete')toast('Download abgeschlossen. Der Song ist jetzt offline verfügbar.');if(p.status==='error')toast(p.message);if(p.status==='cancelled')toast('Download abgebrochen.');if(view==='downloads'||view==='search')render();});
(async()=>{try{state=await api.state();if(api.jamState)jamState=await api.jamState();renderJamBadge();render();if(state.recovered)toast('Die Musikbibliothek wurde zurückgesetzt. Eine Sicherung der beschädigten Datei wurde gespeichert.');}catch(e){$('content').innerHTML=empty('settings','Sourcify konnte nicht starten.',esc(e.message));}})();

function renderJamBadge(){
  $('jam-count').hidden=!jamState.active;$('jam-count').textContent=jamState.active?jamState.participants.length:0;$('jam-top').classList.toggle('jam-live',jamState.active);
}
function renderJam(){
  const content=$('content');
  for(const b of document.querySelectorAll('[data-view]'))b.classList.toggle('active',b.dataset.view==='jam');
  if(!content.dataset.jamMounted){
    content.innerHTML=`<section class="hero jam-hero"><span class="eyebrow">DEIN SOUND. EUER MOMENT.</span><h1>Ein Jam.<br><em>Euer Sound.</em></h1><p>Gemeinsam Songs auswählen und über deinen PC hören. Deine Freunde treten im selben WLAN über ihren Browser bei.</p><div class="hero-art">${icon('people')}</div></section>
    <div id="jam-idle"><section class="jam-card"><h2>Deinen Jam starten</h2><p>Teile einen Einladungslink und den Sitzungscode. Jeder bringt seine Lieblingssongs mit.</p><form id="jam-start-form" class="jam-form"><input id="jam-name" aria-label="Dein Host-Name" maxlength="32" placeholder="Dein Name" value="Host" required><button id="jam-start" class="button accent">${icon('people')}Jam starten</button></form></section><section class="jam-card"><h2>Bei Freunden mitmachen</h2><p>Füge den Einladungslink des Hosts ein. Die Gast-Seite öffnet sich in deinem Browser.</p><form id="jam-join-form" class="jam-form"><input id="jam-invite" aria-label="Jam-Einladungslink" placeholder="http://192.168.1.10:12345/#123456" required><button class="button subtle">Beitreten</button></form></section></div>
    <div id="jam-live" hidden><section class="jam-card"><div class="section-heading"><div><span class="eyebrow">IHR SEID LIVE</span><h2 id="jam-title">Dein Jam</h2></div><button id="jam-stop" class="button subtle">Jam beenden</button></div><div class="jam-code"><span>SITZUNGSCODE</span><strong id="jam-code"></strong></div><div class="jam-form"><select id="jam-links" aria-label="Einladungslink auswählen"></select><button id="jam-copy" class="button accent">Link kopieren</button></div><p id="jam-link-note"></p><label class="setting"><span><strong>Gäste dürfen die Wiedergabe steuern</strong><small>Abspielen, Pause und Titelwechsel für alle freigeben.</small></span><input id="jam-control" type="checkbox"></label><label class="setting"><span><strong>Neue Beitritte sperren</strong><small>Die aktuelle Gruppe bleibt in der Sitzung.</small></span><input id="jam-locked" type="checkbox"></label></section><section class="jam-card"><div class="section-heading"><h2>Wer ist dabei?</h2><span id="jam-members-count" class="eyebrow"></span></div><div id="jam-members"></div></section><section class="jam-card"><div class="section-heading"><h2>Eure Warteschlange</h2><button id="jam-show-queue" class="text-button">Reihenfolge bearbeiten</button></div><div id="jam-shared-queue"></div></section></div>`;
    content.dataset.jamMounted='1';
    $('jam-start-form').addEventListener('submit',async e=>{e.preventDefault();$('jam-start').disabled=true;const result=await safely(()=>api.jamStart($('jam-name').value.trim()));$('jam-start').disabled=false;if(result){jamState=result;renderJamBadge();renderJam();}});
    $('jam-join-form').addEventListener('submit',e=>{e.preventDefault();safely(()=>api.jamOpen($('jam-invite').value.trim()));});
    $('jam-stop').addEventListener('click',()=>safely(()=>api.jamStop()));
    $('jam-copy').addEventListener('click',async()=>{const ok=await safely(()=>api.jamCopy(Number($('jam-links').value)));if(ok)toast('Einladungslink kopiert. Mit Freunden im selben WLAN teilen.');});
    $('jam-control').addEventListener('change',()=>safely(()=>api.jamSettings({controlAllowed:$('jam-control').checked})));
    $('jam-locked').addEventListener('change',()=>safely(()=>api.jamSettings({locked:$('jam-locked').checked})));
    $('jam-members').addEventListener('click',async e=>{const b=e.target.closest('[data-kick]');if(b){const result=await safely(()=>api.jamKick(b.dataset.kick));if(result)toast('Gast entfernt. Neue Beitritte sind jetzt gesperrt.');}});
    $('jam-show-queue').addEventListener('click',()=>navigate('queue'));
  }
  $('jam-idle').hidden=jamState.active;$('jam-live').hidden=!jamState.active;
  if(jamState.active){
    $('jam-title').textContent=jamState.hostName+'s Jam';$('jam-code').textContent=jamState.code;$('jam-control').checked=jamState.controlAllowed;$('jam-locked').checked=jamState.locked;
    const signature=JSON.stringify(jamState.links);if($('jam-links').dataset.links!==signature){$('jam-links').dataset.links=signature;$('jam-links').innerHTML=jamState.links.map((url,i)=>`<option value="${i}">${esc(url)}</option>`).join('');}
    $('jam-copy').disabled=!jamState.links.length;$('jam-links').hidden=!jamState.links.length;$('jam-link-note').textContent=jamState.links.length?'Gäste brauchen nur den Link und einen Browser. Die Musik läuft über deinen PC.':'Kein lokaler Netzwerkadapter gefunden. Verbinde deinen PC mit dem WLAN und starte Jam erneut.';
    $('jam-members-count').textContent=jamState.participants.length+' DABEI';$('jam-members').innerHTML=jamState.participants.map(m=>`<div class="jam-member"><span class="jam-avatar">${esc(m.name.slice(0,1).toUpperCase())}</span><div><strong>${esc(m.name)}</strong><small>${m.host?'Host · Dein PC':'Gast'}</small></div>${m.host?'<span class="eyebrow">DU</span>':`<button class="icon-button" data-kick="${esc(m.id)}" aria-label="Gast entfernen">${icon('close')}</button>`}</div>`).join('');
    $('jam-shared-queue').innerHTML=jamState.queue.length?jamState.queue.map((e,i)=>`<div class="jam-queue-row ${i===jamState.queueIndex?'current':''}"><span>${i===jamState.queueIndex?'♫':i+1}</span><div><strong>${esc(e.track.title)}</strong><small>${esc(e.track.artist)} · Von ${esc(e.addedByName)}</small></div></div>`).join(''):'<p>Suche einen Song und füge ihn über das Plus-Symbol hinzu.</p>';
  }
}
