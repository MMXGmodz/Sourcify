'use strict';
const fs=require('node:fs'),path=require('node:path'),{createHash}=require('node:crypto');
const TYPES=new Set(['image/jpeg','image/png','image/webp']);
class ImageCache {
  constructor(root,{limit=32*1024*1024,ttl=30*86400000}={}){this.root=root;this.limit=limit;this.ttl=ttl;}
  key(url){return createHash('sha256').update(url).digest('hex');}
  get(url){try{const key=this.key(url),metaFile=path.join(this.root,key+'.json');if(!fs.lstatSync(metaFile).isFile()||fs.statSync(metaFile).size>1024)return null;const meta=JSON.parse(fs.readFileSync(metaFile));if(!TYPES.has(meta.type)||!Number.isFinite(meta.expires)||meta.expires<Date.now())return null;const file=path.join(this.root,key+'.img'),stat=fs.lstatSync(file);if(!stat.isFile()||stat.size>4*1024*1024)return null;const bytes=fs.readFileSync(file);fs.utimesSync(file,new Date(),new Date());return 'data:'+meta.type+';base64,'+bytes.toString('base64');}catch{return null;}}
  put(url,bytes,type){
    if(!TYPES.has(type)||bytes.length>4*1024*1024)throw new Error('Ungültiges Cache-Bild.');fs.mkdirSync(this.root,{recursive:true});
    const key=this.key(url),file=path.join(this.root,key+'.img');fs.writeFileSync(file+'.tmp',bytes,{mode:0o600});fs.renameSync(file+'.tmp',file);fs.writeFileSync(path.join(this.root,key+'.json'),JSON.stringify({type,expires:Date.now()+this.ttl}),{mode:0o600});this.trim();
  }
  entries(){try{return fs.readdirSync(this.root).filter(n=>/^[0-9a-f]{64}\.img$/.test(n)).map(name=>{const s=fs.statSync(path.join(this.root,name));return {name,size:s.size,at:s.mtimeMs};});}catch{return [];}}
  trim(){const entries=this.entries().sort((a,b)=>a.at-b.at);let total=entries.reduce((n,e)=>n+e.size,0);while((total>this.limit||entries.length>256)&&entries.length){const e=entries.shift();fs.rmSync(path.join(this.root,e.name),{force:true});fs.rmSync(path.join(this.root,e.name.replace('.img','.json')),{force:true});total-=e.size;}}
  info(){const e=this.entries();return {files:e.length,bytes:e.reduce((n,x)=>n+x.size,0),limit:this.limit};}
  clear(){for(const e of this.entries()){fs.rmSync(path.join(this.root,e.name),{force:true});fs.rmSync(path.join(this.root,e.name.replace('.img','.json')),{force:true});}}
}
module.exports={ImageCache};
