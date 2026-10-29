'use strict';
const {youtubeId}=require('./core.cjs');
function clipBounds(duration,start){
  if(!Number.isFinite(duration)||duration<=0)return {start:0,end:0};
  const length=Math.min(8,duration),maximum=duration-length;
  return {start:Number.isFinite(start)?Math.min(maximum,Math.max(0,start)):maximum/2,end:(Number.isFinite(start)?Math.min(maximum,Math.max(0,start)):maximum/2)+length};
}
function selection(value,tracks){
  if(!value||typeof value!=='object')throw new Error('Ungültige Canvas-Auswahl.');
  const videoId=youtubeId(value.videoId);if(!tracks[videoId]||tracks[videoId].kind!=='youtube')throw new Error('Bitte ein Video aus den Suchergebnissen auswählen.');
  if(value.start!==undefined&&(!Number.isFinite(value.start)||value.start<0||value.start>86400))throw new Error('Ungültiger Canvas-Ausschnitt.');
  return {videoId,manual:true,...(value.start!==undefined?{start:value.start}:{})};
}
module.exports={clipBounds,selection};
