'use strict';
const http=require('node:http'),os=require('node:os'),fs=require('node:fs'),path=require('node:path');
const {randomBytes,randomInt,randomUUID}=require('node:crypto');
const {validId}=require('./core.cjs');
function localAddress(address){return /^(127\.|10\.|192\.168\.|169\.254\.|172\.(1[6-9]|2\d|3[01])\.)/.test(String(address).replace(/^::ffff:/,''));}
function lanAddresses(){return [...new Set(Object.values(os.networkInterfaces()).flat().filter(x=>x?.family==='IPv4'&&!x.internal&&localAddress(x.address)).map(x=>x.address))];}
const safeName=name=>String(name||'Gast').normalize('NFC').replace(/[\x00-\x1f\x7f]/g,'').trim().slice(0,32)||'Gast';
const fail=(status,message)=>Object.assign(new Error(message),{status});
class Jam {
  constructor({getQueue,search,action,control,onChange=()=>{},hostName='Host'}){
    Object.assign(this,{getQueue,search,action,control,onChange,hostName:safeName(hostName)});
    this.server=null;this.members=new Map();this.entries=[];this.allowed=new Set();this.limits=new Map();this.controlAllowed=false;this.locked=false;this.playback={id:null,position:0,paused:true,duration:0,at:Date.now()};this.revision=0;this.pendingSearch=0;
  }
  async start(){
    if(this.server)return this.hostState();
    this.locked=false;this.controlAllowed=false;
    this.code=String(randomInt(100000,1000000));this.id=randomUUID();this.addresses=lanAddresses();
    const allowedHosts=new Set(['127.0.0.1','localhost',...this.addresses]);
    const server=http.createServer(async(req,res)=>{
      try {
        let host;try{host=new URL('http://'+req.headers.host);}catch{throw fail(403,'Ungültige Verbindung.');}
        if(!localAddress(req.socket.remoteAddress)||!allowedHosts.has(host.hostname)||host.port!==String(this.port))throw fail(403,'Jam ist nur im lokalen Netzwerk erreichbar.');
        if(req.headers.origin&&req.headers.origin!==host.origin)throw fail(403,'Verbindung nicht erlaubt.');
        if(req.headers['sec-fetch-site']==='cross-site')throw fail(403,'Verbindung nicht erlaubt.');
        const url=new URL(req.url,host.origin);
        const asset={'/':['index.html','text/html; charset=utf-8'],'/jam.js':['guest.js','text/javascript; charset=utf-8'],'/jam.css':['guest.css','text/css; charset=utf-8'],'/mark.svg':['../../assets/mark.svg','image/svg+xml']}[url.pathname];
        if(req.method==='GET'&&asset){res.writeHead(200,{'Content-Type':asset[1],'Cache-Control':'no-store','Content-Security-Policy':"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' https://i.ytimg.com; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'; object-src 'none'",'X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer'});res.end(fs.readFileSync(path.join(__dirname,'jam',asset[0])));return;}
        if(url.pathname==='/favicon.ico'){res.writeHead(204);res.end();return;}
        if(url.pathname==='/api/join'&&req.method==='POST'){
          this.rate('join:'+req.socket.remoteAddress,12,60000);const p=await this.body(req);
          if(p.code!==this.code)throw fail(401,'Der Sitzungscode stimmt nicht.');
          if(this.locked)throw fail(423,'Der Host hat neue Beitritte gesperrt.');
          this.prune();if(this.members.size>=20)throw fail(409,'Die Jam-Sitzung ist voll (20 Gäste).');
          const token=randomBytes(24).toString('hex'),member={id:randomUUID(),name:safeName(p.name),seen:Date.now(),lastSearch:0,searching:false};
          this.members.set(token,member);this.changed();this.json(res,200,{token,sessionId:this.id,memberId:member.id,state:this.snapshot(member.id)});return;
        }
        const token=String(req.headers.authorization||'').replace(/^Bearer /,'');const member=this.members.get(token);
        if(!member)throw fail(401,'Die Sitzung wurde beendet oder du wurdest entfernt. Bitte neu beitreten.');
        member.seen=Date.now();this.rate('member:'+token,90,60000);
        if(req.method==='GET'&&url.pathname==='/api/state'){this.json(res,200,this.snapshot(member.id));return;}
        if(req.method!=='POST')throw fail(404,'Nicht gefunden.');const p=await this.body(req);
        if(url.pathname==='/api/leave'){this.members.delete(token);this.changed();this.json(res,200,{left:true});return;}
        if(url.pathname==='/api/search'){
          if(typeof p.q!=='string'||!p.q.trim()||p.q.length>200)throw fail(400,'Bitte einen Suchbegriff mit höchstens 200 Zeichen eingeben.');
          if(member.searching||Date.now()-member.lastSearch<2000||this.pendingSearch>=2)throw fail(429,'Bitte kurz warten, bevor du erneut suchst.');
          member.searching=true;member.lastSearch=Date.now();this.pendingSearch++;
          try{const tracks=await this.search(p.q.trim());if(!this.members.has(token)||this.server!==server)throw fail(401,'Die Sitzung wurde beendet.');for(const t of tracks)if(validId(t.id))this.allowed.add(t.id);this.json(res,200,{tracks:tracks.map(this.publicTrack)});}finally{member.searching=false;this.pendingSearch--;}
          return;
        }
        if(url.pathname==='/api/add'){
          this.reconcile();if(!validId(p.id)||!this.allowed.has(p.id))throw fail(400,'Bitte wähle einen Titel aus den Suchergebnissen oder der Jam-Warteschlange.');
          if(this.entries.length>=300)throw fail(409,'Die Warteschlange ist voll.');
          this.action('enqueue',{id:p.id},member.id);this.json(res,200,this.snapshot(member.id));return;
        }
        if(url.pathname==='/api/remove'){
          this.reconcile();const index=this.entries.findIndex(e=>e.entryId===p.entryId),q=this.getQueue();
          if(index<0||index<=q.queueIndex||this.entries[index].addedBy!==member.id)throw fail(403,'Du kannst nur deine eigenen noch wartenden Songs entfernen.');
          this.action('remove',{index},member.id);this.json(res,200,this.snapshot(member.id));return;
        }
        if(url.pathname==='/api/control'){
          if(!this.controlAllowed)throw fail(403,'Die Wiedergabe wird vom Host gesteuert.');
          if(!['play','pause','next','previous','seek'].includes(p.action))throw fail(400,'Ungültige Steuerung.');
          if(p.action==='seek'&&(!Number.isFinite(p.position)||p.position<0||p.position>Math.max(this.playback.duration,0)))throw fail(400,'Ungültige Wiedergabeposition.');
          this.control({action:p.action,...(p.action==='seek'?{position:p.position}:{})});this.json(res,200,{accepted:true});return;
        }
        throw fail(404,'Nicht gefunden.');
      }catch(error){if(!res.destroyed&&!res.writableEnded)this.json(res,error.status||503,{error:error.status?error.message:'Der Vorgang konnte nicht abgeschlossen werden. '+error.message});}
    });
    server.headersTimeout=10000;server.requestTimeout=15000;server.keepAliveTimeout=5000;
    await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(0,'0.0.0.0',()=>{server.removeListener('error',reject);resolve();});});
    this.server=server;this.port=server.address().port;this.reconcile();this.timer=setInterval(()=>this.prune(),10000);this.timer.unref();this.changed();return this.hostState();
  }
  json(res,status,data){res.writeHead(status,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer'});res.end(JSON.stringify(data));}
  async body(req){
    if(!/^application\/json(?:;|$)/i.test(req.headers['content-type']||''))throw fail(415,'Bitte JSON senden.');
    const chunks=[];let size=0;for await(const chunk of req){size+=chunk.length;if(size>8192)throw fail(413,'Anfrage zu groß.');chunks.push(chunk);}
    try{const p=JSON.parse(Buffer.concat(chunks).toString('utf8'));if(!p||typeof p!=='object'||Array.isArray(p))throw Error();return p;}catch{throw fail(400,'Ungültige Anfrage.');}
  }
  rate(key,max,window){const now=Date.now();let slot=this.limits.get(key);if(!slot||slot.reset<=now){slot={count:0,reset:now+window};this.limits.set(key,slot);}if(++slot.count>max)throw fail(429,'Zu viele Anfragen. Bitte kurz warten.');}
  prune(){let changed=false;for(const [token,m]of this.members)if(Date.now()-m.seen>90000){this.members.delete(token);changed=true;}for(const [key,s]of this.limits)if(s.reset<=Date.now())this.limits.delete(key);if(changed)this.changed();}
  publicTrack(t){return {id:t.id,title:t.title,artist:t.artist,duration:Number(t.duration)||0,thumbnail:/^https:\/\/i\.ytimg\.com\//.test(t.thumbnail||'')?t.thumbnail:'',kind:t.kind};}
  reconcile(){
    const q=this.getQueue(),same=q.queue.length===this.entries.length&&q.queue.every((id,i)=>this.entries[i]?.id===id);
    if(!same){const pool=[...this.entries];this.entries=q.queue.map(id=>{const i=pool.findIndex(e=>e.id===id);return i<0?{entryId:randomUUID(),id,addedBy:'host'}:pool.splice(i,1)[0];});}
    for(const e of this.entries)this.allowed.add(e.id);
  }
  queueAction(action,p,actor='host'){
    if(!this.server)return;
    if(action==='enqueue')this.entries.push({entryId:randomUUID(),id:p.id,addedBy:actor});
    if(action==='remove')this.entries.splice(p.index,1);
    if(action==='move'){const [e]=this.entries.splice(p.from,1);this.entries.splice(p.to,0,e);}
    if(action==='clear')this.entries=[];
    this.reconcile();this.changed();
  }
  setPlayback(p){
    if(!p||typeof p!=='object')return;
    this.playback={id:validId(p.id)?p.id:null,position:Number.isFinite(p.position)?Math.max(0,p.position):0,duration:Number.isFinite(p.duration)?Math.max(0,p.duration):0,paused:!!p.paused,loading:!!p.loading,at:Date.now()};
  }
  snapshot(memberId){
    this.reconcile();const q=this.getQueue(),names=new Map([['host',this.hostName],...[...this.members.values()].map(m=>[m.id,m.name])]);
    const position=Math.min(this.playback.duration||Infinity,this.playback.position+(this.playback.paused?0:(Date.now()-this.playback.at)/1000));
    return {sessionId:this.id,revision:this.revision,hostName:this.hostName,locked:this.locked,controlAllowed:this.controlAllowed,memberId,participants:[{id:'host',name:this.hostName,host:true},...[...this.members.values()].map(m=>({id:m.id,name:m.name,host:false}))],queueIndex:q.queueIndex,queue:this.entries.map(e=>({...e,track:this.publicTrack(q.tracks[e.id]||{id:e.id,title:'Unbekannter Titel'}),addedByName:names.get(e.addedBy)||'Ehemaliger Gast'})),playback:{...this.playback,position}};
  }
  hostState(){if(!this.server)return {active:false};return {active:true,...this.snapshot('host'),code:this.code,links:this.addresses.map(ip=>`http://${ip}:${this.port}/#${this.code}`),localLink:`http://127.0.0.1:${this.port}/#${this.code}`};}
  settings(p){if(!this.server)throw new Error('Bitte zuerst eine Jam-Sitzung starten.');if(typeof p?.locked==='boolean')this.locked=p.locked;if(typeof p?.controlAllowed==='boolean')this.controlAllowed=p.controlAllowed;this.changed();return this.hostState();}
  kick(id){for(const [token,m]of this.members)if(m.id===id){this.members.delete(token);this.locked=true;this.changed();return this.hostState();}throw new Error('Teilnehmer nicht gefunden.');}
  changed(){this.revision++;this.onChange(this.hostState());}
  async stop(){
    const server=this.server;if(!server)return {active:false};this.server=null;clearInterval(this.timer);this.members.clear();this.entries=[];this.allowed.clear();this.limits.clear();this.onChange({active:false});
    await new Promise(resolve=>{server.close(resolve);server.closeAllConnections();});return {active:false};
  }
}
module.exports={Jam,localAddress,lanAddresses};
