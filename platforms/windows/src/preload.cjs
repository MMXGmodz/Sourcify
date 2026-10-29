'use strict';
const {contextBridge,ipcRenderer}=require('electron');
const invoke=(name,...args)=>ipcRenderer.invoke('sourcify:'+name,...args).then(result=>{if(result?.error)throw new Error(result.error);return result;});
contextBridge.exposeInMainWorld('sourcify',Object.freeze({
  accountState:()=>invoke('accountState'),accountConfigure:c=>invoke('accountConfigure',c),accountLogin:()=>invoke('accountLogin'),accountCancel:()=>invoke('accountCancel'),accountLogout:()=>invoke('accountLogout'),accountMigration:choice=>invoke('accountMigration',choice),syncNow:()=>invoke('syncNow'),syncResolve:choice=>invoke('syncResolve',choice),devices:()=>invoke('devices'),removeDevice:id=>invoke('removeDevice',id),logoutOthers:()=>invoke('logoutOthers'),googleAccount:()=>invoke('googleAccount'),
  canvasPreview:id=>invoke('canvasPreview',id),canvasSet:(id,p)=>invoke('canvasSet',id,p),canvasTrim:(id,start)=>invoke('canvasTrim',id,start),cacheState:()=>invoke('cacheState'),clearCache:()=>invoke('clearCache'),
  fullscreen:value=>invoke('fullscreen',value),miniPlayer:()=>invoke('miniPlayer'),desktopState:()=>invoke('desktopState'),playbackState:p=>invoke('playbackState',p),image:url=>invoke('image',url),lyrics:id=>invoke('lyrics',id),soundSettings:()=>invoke('soundSettings'),
  onAccount:fn=>{const h=(_,s)=>fn(s);ipcRenderer.on('sourcify:account',h);return()=>ipcRenderer.removeListener('sourcify:account',h);},
  onProfile:fn=>{const h=(_,s)=>fn(s);ipcRenderer.on('sourcify:profile',h);return()=>ipcRenderer.removeListener('sourcify:profile',h);},
  onFullscreen:fn=>{const h=(_,s)=>fn(s);ipcRenderer.on('sourcify:fullscreen',h);return()=>ipcRenderer.removeListener('sourcify:fullscreen',h);},
  onDesktopControl:fn=>{const h=(_,s)=>fn(s);ipcRenderer.on('sourcify:desktop-control',h);return()=>ipcRenderer.removeListener('sourcify:desktop-control',h);},
  jamState:()=>invoke('jamState'),jamStart:name=>invoke('jamStart',name),jamStop:()=>invoke('jamStop'),jamSettings:p=>invoke('jamSettings',p),jamKick:id=>invoke('jamKick',id),jamPlayback:p=>invoke('jamPlayback',p),jamCopy:i=>invoke('jamCopy',i),jamOpen:url=>invoke('jamOpen',url),
  onJam:fn=>{const h=(_,s)=>fn(s);ipcRenderer.on('sourcify:jam',h);return()=>ipcRenderer.removeListener('sourcify:jam',h);},
  onJamControl:fn=>{const h=(_,s)=>fn(s);ipcRenderer.on('sourcify:jam-control',h);return()=>ipcRenderer.removeListener('sourcify:jam-control',h);},
  state:()=>invoke('state'),search:q=>invoke('search',q),action:(a,p)=>invoke('action',a,p),
  audio:id=>invoke('audio',id),canvas:id=>invoke('canvas',id),importMusic:()=>invoke('importMusic'),
  importCanvas:id=>invoke('importCanvas',id),resetCanvas:id=>invoke('resetCanvas',id),
  download:id=>invoke('download',id),cancelDownload:id=>invoke('cancelDownload',id),
  preferences:p=>invoke('preferences',p),chooseFolder:()=>invoke('chooseFolder'),openDownloads:()=>invoke('openDownloads'),
  onEngine:fn=>{const handler=(_,s)=>fn(s);ipcRenderer.on('sourcify:engine',handler);return()=>ipcRenderer.removeListener('sourcify:engine',handler);},
  onState:fn=>{const handler=(_,s)=>fn(s);ipcRenderer.on('sourcify:state',handler);return()=>ipcRenderer.removeListener('sourcify:state',handler);},
  onDownload:fn=>{const handler=(_,s)=>fn(s);ipcRenderer.on('sourcify:download',handler);return()=>ipcRenderer.removeListener('sourcify:download',handler);},
  onVisibility:fn=>{const handler=(_,s)=>fn(s);ipcRenderer.on('sourcify:visibility',handler);return()=>ipcRenderer.removeListener('sourcify:visibility',handler);}
}));
