"""Renderer integration checks using synthetic local media; never product demo content."""
import http.server,threading,json
from pathlib import Path
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1]
ARTIFACTS=ROOT.parent/'artifacts';ARTIFACTS.mkdir(exist_ok=True)
class Quiet(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*args,**kw):super().__init__(*args,directory=str(ROOT),**kw)
 def log_message(self,*args):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Quiet)
threading.Thread(target=server.serve_forever,daemon=True).start()
stub=r'''
(()=>{
let listeners=[],downloadListeners=[];
const tracks={abcdefghi01:{id:'abcdefghi01',kind:'youtube',title:'Testsong <script>bad()</script>',artist:'Testkünstler',duration:12,thumbnail:''},abcdefghi02:{id:'abcdefghi02',kind:'youtube',title:'Zweiter Testsong',artist:'Studio',duration:12,thumbnail:''}};
let s={tracks,favorites:[],queue:[],queueIndex:-1,recent:[],downloads:[],activeDownloads:[],preferences:{volume:.4,repeat:'off',shuffle:false,canvasEnabled:true,automaticCanvas:true,canvasZoom:1.35,downloadDir:'C:\\Music\\Sourcify'}};
const clone=()=>structuredClone(s),emit=()=>listeners.forEach(fn=>fn(clone()));
const blob=async name=>({url:URL.createObjectURL(await(await fetch('/test/fixtures/'+name)).blob())});
window.sourcify={state:async()=>clone(),onState:fn=>listeners.push(fn),onDownload:fn=>downloadListeners.push(fn),onVisibility:()=>{},
 search:async()=>Object.values(tracks),audio:()=>blob('tone.wav'),canvas:()=>blob('canvas.webm'),
 action:async(a,p={})=>{const id=p.id;if(a==='favorite')s.favorites=s.favorites.includes(id)?s.favorites.filter(x=>x!==id):[id,...s.favorites];if(a==='enqueue'){s.queue.push(id);if(s.queueIndex<0)s.queueIndex=0;}if(a==='play'){if(p.ids){s.queue=[...p.ids];s.queueIndex=s.queue.indexOf(id);}else{if(!s.queue.includes(id))s.queue.push(id);s.queueIndex=s.queue.indexOf(id);}s.recent=[id,...s.recent.filter(x=>x!==id)];}if(a==='select')s.queueIndex=p.index;
 if(a==='move'){const current=s.queueIndex;const [item]=s.queue.splice(p.from,1);s.queue.splice(p.to,0,item);if(current===p.from)s.queueIndex=p.to;else if(p.from<current&&p.to>=current)s.queueIndex--;else if(p.from>current&&p.to<=current)s.queueIndex++;}
 if(a==='remove'){s.queue.splice(p.index,1);if(s.queueIndex>=s.queue.length)s.queueIndex=s.queue.length-1;}if(a==='clear'){s.queue=[];s.queueIndex=-1;}emit();return clone();},
 preferences:async p=>{Object.assign(s.preferences,p);emit();return clone();},importMusic:async()=>[],importCanvas:async()=>true,resetCanvas:async()=>true,
 download:async id=>{s.activeDownloads.push(id);emit();setTimeout(()=>{s.activeDownloads=[];s.downloads.push(id);s.tracks[id].downloaded=true;downloadListeners.forEach(fn=>fn({id,status:'complete',percent:100}));emit();},100);return {started:true};},cancelDownload:async()=>true,chooseFolder:async()=>null,openDownloads:async()=>true};
})();
'''

manual=r"""
(()=>{
let savedStart;const original=window.sourcify.canvas;
window.sourcify.canvas=async id=>({...await original(id),start:savedStart});
window.sourcify.canvasPreview=async id=>({url:URL.createObjectURL(await(await fetch('/test/fixtures/canvas.webm')).blob()),videoId:id});
window.sourcify.canvasSet=async(id,value)=>{window.savedCanvas={id,...value};savedStart=value.start;return true;};
window.sourcify.canvasTrim=async(id,start)=>{savedStart=start;return true;};
})();
"""
with sync_playwright() as pw:
 browser=pw.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox','--autoplay-policy=no-user-gesture-required'])
 page=browser.new_page(viewport={'width':1380,'height':900});errors=[];page.on('pageerror',lambda e:errors.append(str(e)));page.add_init_script(stub);page.add_init_script(manual)
 page.goto(f'http://127.0.0.1:{server.server_port}/src/index.html');page.locator('#search-input').fill('Song');page.locator('#search-form').evaluate('(f)=>f.requestSubmit()');page.locator('.track-info').first.click();page.wait_for_function('()=>document.querySelector("audio").currentTime>.1')
 page.locator('#canvas-search-open').click();page.locator('#canvas-query').fill('correct music video');page.locator('#canvas-search-form').evaluate('(f)=>f.requestSubmit()');page.locator('[data-canvas-result]').nth(1).click()
 page.wait_for_function('()=>Number.isFinite(document.querySelector("#canvas-preview").duration)');page.locator('#canvas-start').evaluate('(s)=>{s.value=2.5;s.dispatchEvent(new Event("input"));}')
 page.locator('#canvas-save').click();page.wait_for_function('()=>document.querySelector("#canvas-video").currentTime>=2.4 && document.querySelector("#canvas-video").currentTime<10.5')
 assert page.evaluate('window.savedCanvas.videoId')=='abcdefghi02';assert page.evaluate('window.savedCanvas.start')==2.5
 page.keyboard.press('F11');page.wait_for_selector('#fullscreen-player:visible');assert not page.locator('#audio').evaluate('(a)=>a.paused')
 page.keyboard.press('Escape');page.wait_for_selector('#fullscreen-player',state='hidden');assert not errors,errors
 browser.close()
server.shutdown()
print('PASS: browser fixture UI selects another searched video, saves a 2.5–10.5s Canvas range and renders it; F11/ESC preserves audio. Search fixture is not live YouTube.')
