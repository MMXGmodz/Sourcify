'use strict';
const $=id=>document.getElementById(id),esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
let token=sessionStorage.getItem('sourcify-jam-token'),sessionId=sessionStorage.getItem('sourcify-jam-session'),state,polling=false,timer,toasting;
const invitation=location.hash.slice(1);if(/^\d{6}$/.test(invitation))$('code').value=invitation;history.replaceState(null,'',location.pathname);
function toast(message){$('toast').textContent=message;$('toast').hidden=false;clearTimeout(toasting);toasting=setTimeout(()=>$('toast').hidden=true,6000);}
async function request(route,p){
  let response;try{response=await fetch('/api/'+route,{method:p?'POST':'GET',headers:{...(p?{'Content-Type':'application/json'}:{}),...(token?{Authorization:'Bearer '+token}:{})},body:p?JSON.stringify(p):undefined,signal:AbortSignal.timeout(route==='search'?100000:12000)});}catch{throw new Error('Der Host ist nicht erreichbar. Prüfe, ob ihr im selben WLAN seid und Jam noch läuft.');}
  const data=await response.json();if(!response.ok){const e=new Error(data.error||'Die Verbindung wurde unterbrochen.');e.status=response.status;throw e;}return data;
}
function clearSession(message){token=null;state=null;sessionId=null;sessionStorage.removeItem('sourcify-jam-token');sessionStorage.removeItem('sourcify-jam-session');$('session').hidden=true;$('join-panel').hidden=false;$('join-error').textContent=message||'';}
function cover(t){return t.thumbnail||'/mark.svg';}
function imageFallback(){for(const img of document.querySelectorAll('img[data-cover]:not([data-bound])')){img.dataset.bound='1';img.addEventListener('error',()=>img.src='/mark.svg',{once:true});}}
function render(s){
  state=s;$('join-panel').hidden=true;$('session').hidden=false;$('join-error').textContent='';$('session-title').textContent=`${s.hostName}s Jam`;$('connection').textContent=s.locked?'Verbunden · Neue Beitritte sind gesperrt':'Verbunden · Musik läuft beim Host';
  $('participants').innerHTML=s.participants.map(m=>`<span class="participant">${esc(m.name)}${m.host?' · Host':m.id===s.memberId?' · Du':''}</span>`).join('');
  const current=s.queue.find(e=>e.id===s.playback.id)||s.queue[s.queueIndex];$('now-title').textContent=current?.track.title||'Noch kein Song';$('now-artist').textContent=current?.track.artist||'Füge euren ersten Song hinzu.';
  $('play-status').textContent=s.playback.loading?'Titel lädt …':s.playback.paused?'Pausiert':'Wird abgespielt';$('controls').hidden=!s.controlAllowed;$('control-note').hidden=s.controlAllowed;$('play').textContent=s.playback.paused?'Abspielen':'Pausieren';$('play').disabled=s.playback.loading||!s.queue.length;
  $('queue-count').textContent=s.queue.length+' Songs';
  const signature=JSON.stringify([s.queue,s.queueIndex]);if($('queue').dataset.signature!==signature){$('queue').dataset.signature=signature;
    $('queue').innerHTML=s.queue.length?s.queue.map((e,i)=>`<div class="row ${i===s.queueIndex?'current':''}"><img data-cover src="${esc(cover(e.track))}" alt=""><div class="row-info"><strong>${esc(e.track.title)}</strong><small>${esc(e.track.artist)}</small><div class="added">${i===s.queueIndex?'Aktueller Titel · ':''}Von ${esc(e.addedByName)}</div></div>${e.addedBy===s.memberId&&i>s.queueIndex?`<button class="subtle" data-remove="${esc(e.entryId)}" aria-label="Eigenen Song entfernen">Entfernen</button>`:''}<button class="subtle" data-repeat="${esc(e.id)}" aria-label="Song erneut zur Warteschlange hinzufügen">+</button></div>`).join(''):'<p class="muted">Noch keine Songs in der Warteschlange.</p>';imageFallback();
  }
}
async function poll(){
  if(!token||polling)return;polling=true;
  try{const s=await request('state');if(!token)return;if(sessionId&&sessionId!==s.sessionId){clearSession('Der Host hat eine neue Sitzung gestartet.');return;}render(s);}
  catch(e){if(e.status===401)clearSession(e.message);else if(token)$('connection').textContent='Verbindung unterbrochen. Wir verbinden dich erneut …';}finally{polling=false;}
}
$('join-form').addEventListener('submit',async e=>{
  e.preventDefault();$('join').disabled=true;$('join-error').textContent='';
  try{const result=await request('join',{code:$('code').value.trim(),name:$('name').value.trim()});token=result.token;sessionId=result.sessionId;sessionStorage.setItem('sourcify-jam-token',token);sessionStorage.setItem('sourcify-jam-session',sessionId);render(result.state);}catch(e){$('join-error').textContent=e.message;}finally{$('join').disabled=false;}
});
$('leave').addEventListener('click',async()=>{try{await request('leave',{});}catch{}clearSession('Du hast die Jam-Sitzung verlassen.');});
$('search-form').addEventListener('submit',async e=>{
  e.preventDefault();const q=$('query').value.trim();if(!q)return;$('search-button').disabled=true;$('search-status').textContent='Musik wird gesucht …';$('results').replaceChildren();
  try{const result=await request('search',{q});if(!token)return;$('results').innerHTML=result.tracks.map(t=>`<div class="row"><img data-cover src="${esc(cover(t))}" alt=""><div class="row-info"><strong>${esc(t.title)}</strong><small>${esc(t.artist)}</small></div><button class="accent" data-add="${esc(t.id)}" aria-label="Song zur Warteschlange hinzufügen">+</button></div>`).join('');$('search-status').textContent=result.tracks.length?'Wähle einen Song für euren Jam.':'Keine Treffer. Versuche einen anderen Suchbegriff.';imageFallback();}
  catch(e){$('search-status').textContent=e.message;}finally{$('search-button').disabled=false;}
});
$('results').addEventListener('click',async e=>{const b=e.target.closest('[data-add]');if(!b)return;b.disabled=true;try{render(await request('add',{id:b.dataset.add}));toast('Song hinzugefügt.');}catch(e){toast(e.message);}finally{b.disabled=false;}});
$('queue').addEventListener('click',async e=>{const b=e.target.closest('[data-remove],[data-repeat]');if(!b)return;try{render(await request(b.dataset.remove?'remove':'add',b.dataset.remove?{entryId:b.dataset.remove}:{id:b.dataset.repeat}));}catch(e){toast(e.message);}});
for(const [id,action]of [['previous','previous'],['next','next'],['play',null]])$(id).addEventListener('click',async()=>{try{await request('control',{action:action||(state.playback.paused?'play':'pause')});await poll();}catch(e){toast(e.message);}});
setInterval(poll,2000);poll();
