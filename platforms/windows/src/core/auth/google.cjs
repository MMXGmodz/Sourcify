'use strict';
const {randomBytes,createHash,timingSafeEqual,createPublicKey,verify}=require('node:crypto');
const http=require('node:http');
const AUTHORIZATION='https://accounts.google.com/o/oauth2/v2/auth';
const TOKEN='https://oauth2.googleapis.com/token';
const JWKS='https://www.googleapis.com/oauth2/v3/certs';
function equal(a,b){return typeof a==='string'&&typeof b==='string'&&Buffer.byteLength(a)===Buffer.byteLength(b)&&timingSafeEqual(Buffer.from(a),Buffer.from(b));}
async function jsonResponse(response,limit=128*1024){
  const chunks=[];let size=0;
  if(response.body){for await(const chunk of response.body){size+=chunk.length;if(size>limit)throw new Error('Antwort überschreitet die Größenbegrenzung.');chunks.push(Buffer.from(chunk));}}
  let data;try{data=JSON.parse(Buffer.concat(chunks).toString('utf8'));}catch{throw new Error('Ungültige Serverantwort.');}
  if(!response.ok){const e=new Error(response.status===401?'Anmeldung abgelaufen.':'Anmeldung oder Cloud-Anfrage fehlgeschlagen.');e.status=response.status;throw e;}return data;
}
function validateIdToken(token,{keys,clientId,nonce,now=Date.now()}){
  if(typeof token!=='string'||token.length>32*1024)throw new Error('Ungültiges Google-ID-Token.');
  const parts=token.split('.');if(parts.length!==3)throw new Error('Ungültiges Google-ID-Token.');
  let head,claims;try{head=JSON.parse(Buffer.from(parts[0],'base64url'));claims=JSON.parse(Buffer.from(parts[1],'base64url'));}catch{throw new Error('Ungültiges Google-ID-Token.');}
  const jwk=keys?.find(k=>k.kid===head.kid&&k.kty==='RSA'&&(!k.use||k.use==='sig'));
  if(head.alg!=='RS256'||!jwk||!verify('RSA-SHA256',Buffer.from(parts.slice(0,2).join('.')),createPublicKey({key:jwk,format:'jwk'}),Buffer.from(parts[2],'base64url')))throw new Error('Google-Signatur ungültig.');
  const audiences=Array.isArray(claims.aud)?claims.aud:[claims.aud];
  if(!['https://accounts.google.com','accounts.google.com'].includes(claims.iss)||!audiences.includes(clientId)||((audiences.length>1||claims.azp!==undefined)&&claims.azp!==clientId)||!Number.isFinite(claims.exp)||claims.exp<=now/1000||!Number.isFinite(claims.iat)||claims.iat>now/1000+60||!equal(claims.nonce,nonce)||typeof claims.sub!=='string'||!/^\d{1,255}$/.test(claims.sub))throw new Error('Google-Identität konnte nicht bestätigt werden.');
  return claims;
}
class GoogleLogin {
  constructor({clientId,openBrowser,fetchFn=fetch,timeout=180000}){this.clientId=clientId;this.openBrowser=openBrowser;this.fetch=fetchFn;this.timeout=timeout;this.pending=null;}
  cancel(){this.pending?.abort();}
  async login(nonce){
    if(!/^[A-Za-z0-9_-]+\.apps\.googleusercontent\.com$/.test(this.clientId||''))throw new Error('Google-Desktop-Client-ID fehlt oder ist ungültig.');
    if(typeof nonce!=='string'||nonce.length<16||nonce.length>256)throw new Error('Google-Anmelde-Challenge fehlt.');
    if(this.pending)throw new Error('Eine Anmeldung läuft bereits.');
    const controller=new AbortController();this.pending=controller;
    const state=randomBytes(32).toString('base64url'),verifier=randomBytes(48).toString('base64url');
    const challenge=createHash('sha256').update(verifier).digest('base64url');
    let server,timer;
    try{
      let resolveCode,rejectCode;
      const codePromise=new Promise((resolve,reject)=>{resolveCode=resolve;rejectCode=reject;});
      // Install a rejection handler before opening the browser: cancellation may be immediate.
      codePromise.catch(()=>{});
      server=http.createServer((req,res)=>{
        const port=server.address()?.port;
        res.setHeader('Cache-Control','no-store');res.setHeader('Content-Security-Policy',"default-src 'none'");res.setHeader('Content-Type','text/plain; charset=utf-8');
        if(req.method!=='GET'||req.headers.host!==`127.0.0.1:${port}`){res.writeHead(400).end('Ungültiger Callback.');return;}
        let url;try{url=new URL(req.url,`http://127.0.0.1:${port}`);}catch{res.writeHead(400).end('Ungültiger Callback.');return;}
        if(url.pathname!=='/oauth/callback'){res.writeHead(404).end('Nicht gefunden.');return;}
        if(url.searchParams.getAll('state').length!==1||!equal(url.searchParams.get('state'),state)||url.searchParams.getAll('code').length>1||url.searchParams.getAll('error').length>1){res.writeHead(400).end('Anmeldestatus stimmt nicht überein.');return;}
        if(url.searchParams.has('error')){res.writeHead(200).end('Anmeldung abgebrochen. Zurück zu Sourcify.');rejectCode(new Error('Google-Anmeldung abgebrochen.'));return;}
        const code=url.searchParams.get('code');if(!code||code.length>4096){res.writeHead(400).end('Anmeldecode fehlt.');return;}
        res.writeHead(200).end('Google hat geantwortet. Sourcify prüft jetzt die Anmeldung. Du kannst dieses Fenster schließen.');resolveCode(code);
      });
      server.headersTimeout=5000;server.requestTimeout=10000;
      controller.signal.addEventListener('abort',()=>rejectCode(new Error('Google-Anmeldung abgebrochen.')),{once:true});
      await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(0,'127.0.0.1',resolve);});
      const redirect=`http://127.0.0.1:${server.address().port}/oauth/callback`;
      const url=new URL(AUTHORIZATION);url.search=new URLSearchParams({client_id:this.clientId,redirect_uri:redirect,response_type:'code',scope:'openid email profile',state,nonce,code_challenge:challenge,code_challenge_method:'S256',prompt:'select_account'}).toString();
      timer=setTimeout(()=>rejectCode(new Error('Google-Anmeldung hat zu lange gedauert.')),this.timeout);
      await this.openBrowser(url.href);
      const code=await codePromise;
      const tokens=await jsonResponse(await this.fetch(TOKEN,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({client_id:this.clientId,code,code_verifier:verifier,grant_type:'authorization_code',redirect_uri:redirect}),signal:AbortSignal.any([controller.signal,AbortSignal.timeout(20000)]),redirect:'error'}));
      const certs=await jsonResponse(await this.fetch(JWKS,{signal:AbortSignal.any([controller.signal,AbortSignal.timeout(15000)]),redirect:'error'}));
      const claims=validateIdToken(tokens.id_token,{keys:certs.keys,clientId:this.clientId,nonce});
      return {idToken:tokens.id_token,subject:claims.sub};
    }finally{clearTimeout(timer);server?.close();server?.closeAllConnections();this.pending=null;}
  }
}
module.exports={GoogleLogin,validateIdToken,jsonResponse,equal};
