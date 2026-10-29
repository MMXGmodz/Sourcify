'use strict';
const path=require('node:path');
function visibleBounds(bounds,displays){
  if(!bounds||![bounds.x,bounds.y,bounds.width,bounds.height].every(Number.isFinite))return undefined;
  const area=displays.find(d=>bounds.x<d.workArea.x+d.workArea.width&&bounds.x+bounds.width>d.workArea.x&&bounds.y<d.workArea.y+d.workArea.height&&bounds.y+bounds.height>d.workArea.y)?.workArea;
  if(!area)return undefined;const width=Math.min(Math.max(380,bounds.width),area.width),height=Math.min(Math.max(112,bounds.height),area.height);
  return {x:Math.round(Math.max(area.x,Math.min(bounds.x,area.x+area.width-width))),y:Math.round(Math.max(area.y,Math.min(bounds.y,area.y+area.height-height))),width:Math.round(width),height:Math.round(height)};
}
class DesktopIntegration {
  constructor({electron,window,getPrefs,setPrefs,getPlayback,command}){Object.assign(this,{electron,window,getPrefs,setPrefs,getPlayback,command});this.mini=null;this.tray=null;this.quitting=false;}
  install(){
    const w=this.window;
    w.webContents.on('before-input-event',(event,input)=>{if(input.type!=='keyDown')return;if(input.key==='F11'){event.preventDefault();this.fullscreen(!w.isFullScreen());}else if(input.key==='Escape'&&w.isFullScreen()&&!input.control&&!input.alt){event.preventDefault();this.fullscreen(false);}});
    for(const name of ['enter-full-screen','leave-full-screen'])w.on(name,()=>w.webContents.send('sourcify:fullscreen',w.isFullScreen()));
    w.on('close',e=>{if(!this.quitting&&this.getPrefs().minimizeToTray&&this.tray){e.preventDefault();w.hide();}});
    this.electron.app.on('before-quit',()=>{this.quitting=true;this.mini?.destroy();this.tray?.destroy();});this.configure();
  }
  fullscreen(value){this.open();this.window.setFullScreen(!!value);return this.window.isFullScreen();}
  open(){if(this.window.isMinimized())this.window.restore();this.window.show();this.window.focus();}
  configure(){
    const {Tray,Menu,app}=this.electron,p=this.getPrefs();
    if(p.minimizeToTray&&!this.tray){this.tray=new Tray(path.join(__dirname,'../../..','assets','icon.png'));this.tray.setToolTip('Sourcify Player');this.tray.on('click',()=>this.open());}
    if(!p.minimizeToTray&&this.tray){this.tray.destroy();this.tray=null;}
    this.mini?.setAlwaysOnTop(!!p.miniAlwaysOnTop);
    if(process.platform==='win32'&&app.isPackaged){app.setLoginItemSettings({name:'Sourcify Player',openAtLogin:!!p.startWithWindows,path:process.execPath,args:p.startMinimized?['--sourcify-minimized']:[]});}
    this.update();
  }
  update(){
    const s=this.getPlayback(),{Menu,nativeImage}=this.electron;
    if(this.mini&&!this.mini.isDestroyed())this.mini.webContents.send('sourcify:mini-state',s);
    const signature=JSON.stringify([s.track?.id,s.paused,!!this.tray]);if(signature===this.menuSignature)return;this.menuSignature=signature;
    this.tray?.setContextMenu(Menu.buildFromTemplate([{label:'Sourcify öffnen',click:()=>this.open()},{label:s.paused?'Abspielen':'Pausieren',click:()=>this.command('toggle')},{label:'Nächster Titel',click:()=>this.command('next')},{label:'Vorheriger Titel',click:()=>this.command('previous')},{type:'separator'},{label:'Fullscreen Player',click:()=>this.fullscreen(true)},{label:'Mini-Player',click:()=>this.openMini()},{type:'separator'},{label:'Beenden',click:()=>{this.quitting=true;this.electron.app.quit();}}]));
    if(process.platform==='win32')this.window.setThumbarButtons([{tooltip:'Vorheriger Titel',icon:nativeImage.createFromPath(path.join(__dirname,'../../..','assets','media-previous.png')),click:()=>this.command('previous')},{tooltip:s.paused?'Abspielen':'Pausieren',icon:nativeImage.createFromPath(path.join(__dirname,'../../..','assets',s.paused?'media-play.png':'media-pause.png')),click:()=>this.command('toggle')},{tooltip:'Nächster Titel',icon:nativeImage.createFromPath(path.join(__dirname,'../../..','assets','media-next.png')),click:()=>this.command('next')}]);
  }
  openMini(){
    if(this.mini&&!this.mini.isDestroyed()){this.mini.show();this.mini.focus();return true;}
    const {BrowserWindow,screen}=this.electron,bounds=visibleBounds(this.getPrefs().miniBounds,screen.getAllDisplays());
    this.mini=new BrowserWindow({width:440,height:128,minWidth:380,minHeight:112,maxHeight:180,...bounds,alwaysOnTop:!!this.getPrefs().miniAlwaysOnTop,title:'Sourcify Mini Player',backgroundColor:'#101a28',autoHideMenuBar:true,webPreferences:{preload:path.join(__dirname,'mini-preload.cjs'),contextIsolation:true,nodeIntegration:false,sandbox:true,webSecurity:true}});
    const mini=this.mini;mini.setMenu(null);mini.webContents.setWindowOpenHandler(()=>({action:'deny'}));mini.webContents.on('will-navigate',e=>e.preventDefault());mini.webContents.on('will-attach-webview',e=>e.preventDefault());mini.webContents.session.setPermissionRequestHandler((_wc,_p,cb)=>cb(false));
    mini.webContents.on('did-finish-load',()=>mini.webContents.send('sourcify:mini-state',this.getPlayback()));
    mini.on('close',()=>{if(!this.quitting)this.setPrefs({miniBounds:mini.getBounds()});});mini.on('closed',()=>{this.mini=null;});mini.loadFile(path.join(__dirname,'mini.html'));return true;
  }
}
module.exports={DesktopIntegration,visibleBounds};
