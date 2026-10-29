'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),r=require('resedit'),asar=require('@electron/asar');
const dir=path.join(__dirname,'..','dist','win-unpacked'),archive=path.join(dir,'resources','app.asar');
const executable=r.NtExecutable.from(fs.readFileSync(path.join(dir,'Sourcify Player.exe'))),res=r.NtExecutableResource.from(executable);
const record=JSON.parse(Buffer.from(res.entries.find(x=>x.type==='INTEGRITY').bin))[0];
const hash=crypto.createHash('sha256').update(asar.getRawHeader(archive).headerString).digest('hex');
if(hash!==record.value)throw new Error('ASAR header integrity mismatch');
function verifyDirectory(dir,relative=''){for(const e of fs.readdirSync(dir,{withFileTypes:true})){const name=relative?relative+'/'+e.name:e.name;if(e.isDirectory())verifyDirectory(path.join(dir,e.name),name);else{const source=fs.readFileSync(path.join(dir,e.name)),packaged=asar.extractFile(archive,'src/'+name);if(!source.equals(packaged))throw new Error('Stale packaged file: '+name);}}}
verifyDirectory(path.join(__dirname,'..','src'));
const version=r.Resource.VersionInfo.fromEntries(res.entries)[0].getStringValues({lang:1033,codepage:1200});
if(version.ProductVersion!==require('../package.json').version+'.0')throw new Error('Version mismatch');
if(version.ProductName!=='Sourcify Player'||version.CompanyName!=='Sourcify')throw new Error('Branding mismatch');
for(const name of ['README-Sourcify.txt','LICENSE-Sourcify.txt','THIRD-PARTY.txt','engine-NOTICES.txt','yt-dlp-LICENSE.txt'])if(!fs.existsSync(path.join(dir,'resources',name)))throw new Error('Missing notice: '+name);
console.log('Packaged source, Electron integrity, Windows branding, bootstrap and license notices verified.');
