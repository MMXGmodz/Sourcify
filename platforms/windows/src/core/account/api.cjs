'use strict';
const {jsonResponse}=require('../auth/google.cjs');
function cloudUrl(value){const u=new URL(value);if(u.protocol!=='https:'||u.username||u.password||u.search||u.hash||u.pathname!=='/')throw new Error('Cloud-Adresse muss eine HTTPS-Origin sein.');return u.origin;}
function profile(value){
  if(!value||typeof value.id!=='string'||!/^[A-Za-z0-9:_-]{1,200}$/.test(value.id)||value.provider!=='google'||typeof value.googleSubject!=='string'||!/^\d{1,255}$/.test(value.googleSubject))throw new Error('Ungültiges Cloud-Account-Profil.');
  let avatar='';try{const u=new URL(value.avatarUrl);if(u.protocol==='https:'&&/(^|\.)(googleusercontent\.com|ggpht\.com)$/.test(u.hostname)&&!u.username&&!u.password)avatar=u.href;}catch{}
  return {id:value.id,provider:'google',googleSubject:value.googleSubject,email:String(value.email||'').slice(0,254),displayName:String(value.displayName||'Google Account').slice(0,150),avatarUrl:avatar};
}
function session(value){
  if(!value||typeof value.accessToken!=='string'||!value.accessToken||value.accessToken.length>16384||typeof value.refreshToken!=='string'||!value.refreshToken||value.refreshToken.length>16384||!Number.isFinite(value.expiresAt)||value.expiresAt<=Date.now())throw new Error('Ungültige Cloud-Session.');
  return {...profileEnvelope(value),accessToken:value.accessToken,refreshToken:value.refreshToken,expiresAt:value.expiresAt};
}
function profileEnvelope(value){return {user:profile(value.user)};}
// This is the documented shared adapter contract, not an existing deployed API.
class AccountAPI {
  constructor(origin,{fetchFn=fetch}={}){this.origin=cloudUrl(origin);this.fetch=fetchFn;}
  async request(route,{method='GET',body,token,signal}={}){
    if(!/^\/v1\/[a-zA-Z0-9/_-]+$/.test(route))throw new Error('Ungültige Cloud-Route.');
    return jsonResponse(await this.fetch(this.origin+route,{method,headers:{Accept:'application/json',...(body?{'Content-Type':'application/json'}:{}),...(token?{Authorization:'Bearer '+token}:{})},body:body?JSON.stringify(body):undefined,signal:signal?AbortSignal.any([signal,AbortSignal.timeout(20000)]):AbortSignal.timeout(20000),redirect:'error'}),2*1024*1024);
  }
  async challenge(){const r=await this.request('/v1/auth/google/challenge',{method:'POST',body:{platform:'windows'}});if(typeof r.nonce!=='string'||r.nonce.length<16||r.nonce.length>256||typeof r.challengeId!=='string'||r.challengeId.length>256)throw new Error('Ungültige Cloud-Anmelde-Challenge.');return r;}
  async exchange(idToken,challengeId,device){return session(await this.request('/v1/auth/google',{method:'POST',body:{idToken,challengeId,device}}));}
  async refresh(refreshToken){return session(await this.request('/v1/auth/refresh',{method:'POST',body:{refreshToken}}));}
  async logout(token){return this.request('/v1/auth/logout',{method:'POST',token,body:{}});}
}
module.exports={AccountAPI,cloudUrl,profile,session};
