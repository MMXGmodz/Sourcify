'use strict';
const {youtubeId,cleanTrack,safeMediaUrl}=require('./core.cjs');
// Shared by Electron and the PS5 companion; the existing extractor remains authoritative.
function musicApi(engine) {
  const cache=new Map(),pending=new Map(),searchCache=new Map(),searchPending=new Map();let generation=0,hits=0,misses=0;
  return {
    cacheInfo:()=>({streams:cache.size,searches:searchCache.size,inFlight:pending.size+searchPending.size,hits,misses}),
    clearCache:()=>{generation++;cache.clear();searchCache.clear();},
    async search(q,count=20) {
      if(typeof q!=='string'||!q.trim()||q.length>200)throw new Error('Bitte einen Suchbegriff mit höchstens 200 Zeichen eingeben.');
      if(!Number.isInteger(count)||count<1||count>50)throw new Error('Ungültige Anzahl.');
      const key=count+':'+q.trim(),old=searchCache.get(key);
      if(old&&old.expires>Date.now()){hits++;return structuredClone(old.results);}
      if(searchPending.has(key))return structuredClone(await searchPending.get(key));
      if(searchPending.size>=6)throw new Error('Zu viele Suchen gleichzeitig. Bitte kurz warten.');
      misses++;const epoch=generation;
      const request=(async()=>{const data=JSON.parse(await engine(['--flat-playlist','--dump-single-json',`ytsearch${count}:${q.trim()}`]));
        const results=(data.entries||[]).map(cleanTrack).filter(Boolean);if(epoch===generation){if(searchCache.size>=64)searchCache.delete(searchCache.keys().next().value);searchCache.set(key,{results,expires:Date.now()+180000});}return results;})();
      searchPending.set(key,request);try{return structuredClone(await request);}finally{searchPending.delete(key);}
    },
    async remote(id,video=false,maxVideoHeight=720) {
      youtubeId(id);if(video&&![360,480,720].includes(maxVideoHeight))throw new Error('Ungültige Canvas-Qualität.');const key=`${id}:${video}:${video?maxVideoHeight:0}`,old=cache.get(key);
      if(old&&old.expires>Date.now())return (hits++,old);
      if(pending.has(key))return pending.get(key);
      misses++;const epoch=generation;const request=(async()=>{
      const result=JSON.parse(await engine(['--dump-single-json','--skip-download','-f',video?`bestvideo[height<=${maxVideoHeight}][vcodec^=avc1][ext=mp4]/bestvideo[height<=${maxVideoHeight}][ext=mp4]/bestvideo[height<=${maxVideoHeight}]/best[height<=${maxVideoHeight}]`:'bestaudio[ext=m4a]/bestaudio',`https://www.youtube.com/watch?v=${id}`]));
      if(!safeMediaUrl(result.url))throw new Error('Für diesen Titel ist kein abspielbarer Stream verfügbar.');
      const expiry=Number(new URL(result.url).searchParams.get('expire'))*1000;
      const metadata=cleanTrack({...result,id});
      const item={url:result.url,headers:result.http_headers||{},duration:result.duration||0,metadata,expires:Math.min(Date.now()+30*60*1000,expiry?expiry-60000:Infinity)};
      if(cache.size>=200)cache.delete(cache.keys().next().value);
      if(epoch===generation)cache.set(key,item);return item;
      })();pending.set(key,request);try{return await request;}finally{pending.delete(key);}
    }
  };
}
module.exports={musicApi};
