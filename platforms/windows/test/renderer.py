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
with sync_playwright() as pw:
 browser=pw.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox','--autoplay-policy=no-user-gesture-required'])
 page=browser.new_page(viewport={'width':1380,'height':900});errors=[];page.on('pageerror',lambda e:errors.append(str(e)));page.add_init_script(stub)
 page.goto(f'http://127.0.0.1:{server.server_port}/src/index.html');page.get_by_role('heading',name='Hier beginnt dein Sound.').wait_for()
 page.screenshot(path=str(ARTIFACTS/'Sourcify-Windows.png'))
 page.locator('#search-input').fill('Testsong');page.locator('#search-form').evaluate('(f)=>f.requestSubmit()');page.locator('.track-row').first.wait_for()
 assert page.locator('.track-row').count()==2
 assert page.locator('.track-info strong').first.inner_text()=='Testsong <script>bad()</script>'
 assert page.locator('.track-info script').count()==0
 page.locator('.track-row').first.locator('[data-action=favorite]').click();page.locator('[data-view=favorites]').click();assert page.locator('.track-row').count()==1
 page.locator('.track-info').first.click();page.wait_for_function('() => document.querySelector("audio").currentTime>.1')
 page.wait_for_function('() => document.querySelector("video").currentTime>=7.9 && document.querySelector("video").currentTime<16.1')
 assert page.locator('#canvas-video').evaluate('(v)=>getComputedStyle(v).objectFit')=='cover'
 assert page.locator('#canvas-video').evaluate('(v)=>v.muted')
 page.locator('#play').click();assert page.locator('audio').evaluate('(a)=>a.paused');assert page.locator('#canvas-video').evaluate('(v)=>v.paused')
 page.locator('#seek').evaluate('(s)=>{s.value=50;s.dispatchEvent(new Event("input"))}')
 assert 5.8<page.locator('audio').evaluate('(a)=>a.currentTime')<6.2
 page.locator('#play').click();page.wait_for_function('() => !document.querySelector("audio").paused')
 page.locator('#settings-button').click();assert page.locator('#setting-canvas').is_checked();page.locator('#setting-zoom').evaluate('(s)=>{s.value=1.75;s.dispatchEvent(new Event("input"));s.dispatchEvent(new Event("change"));}')
 assert page.locator('#zoom-label').inner_text()=='175 %';page.locator('#settings-dialog [data-close]').click()
 page.locator('#now-download').click();page.wait_for_function('() => document.querySelector("#now-download").classList.contains("selected")')
 page.locator('[data-view=downloads]').click();assert page.locator('.track-row').count()==1
 page.locator('.track-row [data-action=enqueue]').click();page.locator('#show-queue').click();assert page.locator('.track-row').count()==2
 page.locator('.track-row').nth(1).locator('[data-action=up]').click();page.locator('.track-row').nth(1).locator('[data-action=remove]').click();assert page.locator('.track-row').count()==1
 page.locator('[data-clear]').click();assert page.locator('audio').evaluate('(a)=>a.paused && !a.getAttribute("src")')
 page.locator('#credits-button').click();assert page.locator('#credits-dialog').inner_text().find('Vibecoded')>=0;assert page.locator('#credits-dialog').inner_text().find('MMXG')>=0
 assert not errors,errors
 page.locator('#credits-dialog [data-close]').click()
 for width,height in [(1000,680),(1150,720),(1600,900)]:
  page.set_viewport_size({'width':width,'height':height});assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
  for id in ['credits-button','play','canvas-file']:
   bounds=page.locator('#'+id).bounding_box();assert bounds['y']+bounds['height']<=height,(id,bounds)
 browser.close()
server.shutdown()
print('Renderer: search safety, audio, seek, pause, Canvas midpoint, favorites, queue, downloads, settings, credits and window sizes passed.')
