// Test entry point: uses an isolated, temporary profile; production has no test mode.
const {app}=require('electron'),fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const profile=fs.mkdtempSync(path.join(os.tmpdir(),'sourcify-electron-test-'));
app.setPath('userData',profile);
const id='local:94c89bde-5e67-4b7a-b17c-0d80b556367b';
fs.writeFileSync(path.join(profile,'library.json'),JSON.stringify({tracks:{[id]:{id,kind:'local',title:'Lokaler Wiedergabetest',artist:'Sourcify Test',duration:12,path:path.join(__dirname,'fixtures','tone.wav')}},favorites:[],queue:[id],queueIndex:0,recent:[id],downloads:{},canvases:{[id]:{path:path.join(__dirname,'fixtures','canvas.webm')}},preferences:{automaticCanvas:false}}));
app.on('will-quit',()=>fs.rmSync(profile,{recursive:true,force:true}));
require('../src/main.cjs');
