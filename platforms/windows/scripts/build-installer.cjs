'use strict';
// Use the checksum-verified cross-platform NSIS tool bundled by electron-builder.
const fs=require('node:fs'),path=require('node:path'),{spawn}=require('node:child_process');
const {getMakeNsisPath}=require('app-builder-lib/out/toolsets/windows');
(async()=>{
  const root=path.join(__dirname,'..','dist','win-unpacked'),files=[],dirs=[];
  function visit(dir,relative=''){for(const entry of fs.readdirSync(dir,{withFileTypes:true})){const name=relative?relative+'\\'+entry.name:entry.name;if(entry.isDirectory()){visit(path.join(dir,entry.name),name);dirs.push(name);}else files.push(name);}}
  visit(root);
  fs.writeFileSync(path.join(__dirname,'uninstall-files.nsh'),files.map(x=>`Delete "$INSTDIR\\${x}"`).concat(dirs.map(x=>`RMDir "$INSTDIR\\${x}"`)).join('\n')+'\n');
  const tool=await getMakeNsisPath();
  await new Promise((resolve,reject)=>{const child=spawn(tool.path,['-INPUTCHARSET','UTF8','-DAPP_VERSION='+require('../package.json').version,'installer.nsi'],{cwd:__dirname,env:{...process.env,...tool.env},stdio:'inherit',windowsHide:true});child.on('error',reject);child.on('exit',code=>code===0?resolve():reject(new Error(`NSIS exited ${code}`)));});
})().catch(error=>{console.error(error.message);process.exitCode=1;});
