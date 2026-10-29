// Runs on Linux or Windows; preserves Electron's ASAR integrity resources.
const fs=require('node:fs'),path=require('node:path'),r=require('resedit');
module.exports=async function(context){
  if(context.electronPlatformName!=='win32')return;
  // WebGPU is not used; ANGLE, D3D11 and SwiftShader remain available.
  for(const optional of ['dxcompiler.dll','dxil.dll'])fs.rmSync(path.join(context.appOutDir,optional),{force:true});
  const locales=path.join(context.appOutDir,'locales');
  for(const name of fs.readdirSync(locales))if(!['de.pak','en-US.pak','en-GB.pak'].includes(name))fs.rmSync(path.join(locales,name));
  const versionParts=require('../package.json').version.split('.').map(Number);
  const file=path.join(context.appOutDir,'Sourcify Player.exe');
  const exe=r.NtExecutable.from(fs.readFileSync(file)),res=r.NtExecutableResource.from(exe);
  const icons=r.Data.IconFile.from(fs.readFileSync(path.join(__dirname,'..','assets','icon.ico'))).icons.map(x=>x.data);
  const groups=r.Resource.IconGroupEntry.fromEntries(res.entries);
  for(const group of groups)r.Resource.IconGroupEntry.replaceIconsForResource(res.entries,group.id,group.lang,icons);
  const version=r.Resource.VersionInfo.fromEntries(res.entries)[0];
  for(const language of version.getAllLanguagesForStringValues()){
    version.setFileVersion(...versionParts,0,language.lang);version.setProductVersion(...versionParts,0,language.lang);
    version.setStringValues(language,{CompanyName:'Sourcify',FileDescription:'Sourcify Player',ProductName:'Sourcify Player',InternalName:'Sourcify Player',OriginalFilename:'Sourcify Player.exe',LegalCopyright:'Copyright © 2026 Sourcify contributors'});
  }
  version.outputToResourceEntries(res.entries);res.outputResource(exe);fs.writeFileSync(file,Buffer.from(exe.generate()));
};
