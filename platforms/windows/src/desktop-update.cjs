'use strict';
function preferences(p, target) {
  for(const key of ['dynamicMusic','landscapeCover','lyricsEnabled'])if(typeof p[key]==='boolean')target[key]=p[key];
  for(const [key,min,max] of [['waveIntensity',0,100],['seekSeconds',1,20]])if(Number.isFinite(p[key]))target[key]=Math.round(Math.max(min,Math.min(max,p[key])));
  for(const [key,values]of [['sliderStyle',['standard','slim','dynamic']],['playStyle',['standard','metal']]])if(values.includes(p[key]))target[key]=p[key];
}
function editTrack(track,p) {
  if(!p||typeof p!=='object')throw new Error('Ungültige Songdaten.');
  if(p.reset){if(track.originalDisplay)Object.assign(track,track.originalDisplay);delete track.originalDisplay;delete track.accent;return;}
  const title=String(p.title||'').trim(),artist=String(p.artist||'').trim();
  if(!title||title.length>300||!artist||artist.length>200||p.accent&& !/^#[\da-f]{6}$/i.test(p.accent))throw new Error('Bitte gültigen Titel, Künstler und Farbe eingeben.');
  track.originalDisplay??={title:track.title,artist:track.artist};Object.assign(track,{title,artist,accent:p.accent||''});
}
function parseConcerts(data,artist,today=new Date().toISOString().slice(0,10)) {
  const norm=s=>String(s).normalize('NFKD').replace(/[\u0300-\u036f]/g,'').toLowerCase().trim();
  return (data.events||[]).filter(e=>!e.cancelled&&/^[\da-f]{8}(-[\da-f]{4}){3}-[\da-f]{12}$/i.test(e.id)&&/^\d{4}-\d{2}-\d{2}$/.test(e['life-span']?.begin)&&e['life-span'].begin>=today&&e.relations?.some(r=>['main performer','performer','guest performer','support act'].includes(r.type)&&norm(r.artist?.name)===norm(artist))).map(e=>({id:e.id,title:e.name,date:e['life-span'].begin,venue:e.relations.find(r=>r.type==='held at')?.place?.name||'',url:`https://musicbrainz.org/event/${e.id}`,poster:`https://eventartarchive.org/event/${e.id}/front-500`})).sort((a,b)=>a.date.localeCompare(b.date));
}
module.exports={preferences,editTrack,parseConcerts};
