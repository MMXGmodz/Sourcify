'use strict';
const fs=require('node:fs/promises'),path=require('node:path'),crypto=require('node:crypto');
async function prepareEngine({file,url,hash,fetchFn,onStatus=()=>{},timeout=120000}) {
  const digest=data=>crypto.createHash('sha256').update(data).digest('hex');
  try {const cached=await fs.readFile(file);if(digest(cached)===hash)return file;}catch{}
  const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),timeout),temp=file+'.download';
  onStatus('loading');
  try {
    const response=await fetchFn(url,{signal:controller.signal});
    if(!response.ok||!response.body)throw new Error('Engine download failed');
    const chunks=[];let size=0;
    for await(const chunk of response.body){size+=chunk.byteLength;if(size>32*1024*1024)throw new Error('Engine too large');chunks.push(Buffer.from(chunk));}
    const data=Buffer.concat(chunks);if(digest(data)!==hash)throw new Error('Engine checksum mismatch');
    await fs.mkdir(path.dirname(file),{recursive:true});await fs.writeFile(temp,data);await fs.rename(temp,file);onStatus('ready');return file;
  } catch {
    await fs.rm(temp,{force:true}).catch(()=>{});onStatus('error');
    throw new Error('Die Wiedergabekomponente konnte nicht geladen werden. Bitte Internetverbindung prüfen und erneut versuchen. Eigene Musikdateien funktionieren weiterhin.');
  } finally {clearTimeout(timer);}
}
module.exports={prepareEngine};
