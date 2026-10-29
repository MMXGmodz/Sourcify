'use strict';
const {contextBridge,ipcRenderer}=require('electron');
contextBridge.exposeInMainWorld('sourcifyMini',Object.freeze({command:action=>ipcRenderer.invoke('sourcify:mini-command',action),onState:fn=>{ipcRenderer.on('sourcify:mini-state',(_,s)=>fn(s));}}));
