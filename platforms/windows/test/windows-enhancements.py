"""Real Electron/UI checks with a disposable local profile; no Google or backend claims."""
from pathlib import Path
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1]
ARTIFACTS=ROOT.parent/'artifacts'
with sync_playwright() as p:
 browser=p.chromium.connect_over_cdp('http://127.0.0.1:9223')
 page=next(x for x in browser.contexts[0].pages if x.url.endswith('/src/index.html'))
 page.reload();page.wait_for_selector('.track-row');page.evaluate('async()=>{const s=await window.sourcify.state();await window.sourcify.canvasTrim(s.recent[0],8);}');errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
 page.locator('.track-info').first.click();page.wait_for_function('()=>document.querySelector("audio").currentTime>.1')
 page.wait_for_function('()=>document.querySelector("#canvas-video").currentTime>=7.9')
 paused=page.locator('#audio').evaluate('(a)=>a.paused');assert not paused
 before=page.locator('#canvas-video').evaluate('(v)=>getComputedStyle(v).filter')
 page.locator('#canvas-frame').click(position={'x':120,'y':120})
 assert page.locator('body').evaluate('(b)=>b.classList.contains("canvas-focus")')
 page.wait_for_function('()=>getComputedStyle(document.querySelector("#canvas-video")).filter==="brightness(0.94)"')
 assert before!=page.locator('#canvas-video').evaluate('(v)=>getComputedStyle(v).filter')
 assert page.locator('#canvas-frame').bounding_box()['width']>=1300
 page.screenshot(path=str(ARTIFACTS/'Sourcify-Windows-Canvas-Gross.png'))
 page.locator('#canvas-frame').click(position={'x':120,'y':120});assert not page.locator('body').evaluate('(b)=>b.classList.contains("canvas-focus")')
 assert not page.locator('#audio').evaluate('(a)=>a.paused')
 page.locator('#canvas-trim-open').click();page.wait_for_function('()=>Number.isFinite(document.querySelector("#canvas-preview").duration)')
 assert page.locator('#canvas-video').evaluate('(v)=>v.paused')
 page.locator('#canvas-start').evaluate('(s)=>{s.value=4;s.dispatchEvent(new Event("input"));}')
 assert page.locator('#canvas-range-label').inner_text()=='0:04 – 0:12'
 page.screenshot(path=str(ARTIFACTS/'Sourcify-Windows-Canvas-Editor.png'))
 page.locator('#canvas-save').click();page.wait_for_function('()=>document.querySelector("#canvas-video").currentTime>=3.9 && document.querySelector("#canvas-video").currentTime<12')
 page.wait_for_function('()=>document.querySelector("#canvas-preview").getAttribute("src")===null')
 assert not page.locator('#audio').evaluate('(a)=>a.paused')
 page.keyboard.press('F11');page.wait_for_selector('#fullscreen-player:visible');assert page.locator('#canvas-video').evaluate('(v)=>v.paused')
 assert not page.locator('#audio').evaluate('(a)=>a.paused')
 assert page.evaluate('()=>window.sourcify.desktopState()')['fullscreen']
 page.locator('#full-play').click();assert page.locator('#audio').evaluate('(a)=>a.paused')
 assert page.locator('#full-title').inner_text()=='Lokaler Wiedergabetest'
 page.screenshot(path=str(ARTIFACTS/'Sourcify-Windows-Fullscreen-Neu.png'))
 page.keyboard.press('Escape');page.wait_for_selector('#fullscreen-player',state='hidden');assert not page.evaluate('()=>window.sourcify.desktopState()')['fullscreen']
 for existing in list(browser.contexts[0].pages):
  if existing.url.endswith('/mini.html'):existing.close()
 with browser.contexts[0].expect_page() as new_page:
  page.locator('#mini-button').click()
 mini=new_page.value
 mini.wait_for_url('**/mini.html')
 mini.wait_for_function('()=>document.querySelector("#title").textContent==="Lokaler Wiedergabetest"')
 assert mini.evaluate('typeof window.require')=='undefined'
 mini.locator('#play').click();page.wait_for_function('()=>!document.querySelector("#audio").paused')
 mini.screenshot(path=str(ARTIFACTS/'Sourcify-Windows-Mini-Neu.png'))
 page.bring_to_front();page.locator('#account-button').click()
 page.wait_for_selector('#google-login')
 assert page.locator('#account-dialog input[type=password]').count()==0
 assert page.locator('#account-dialog input[type=email]').count()==0
 assert 'Backend' in page.locator('#account-dialog').inner_text()
 info=page.evaluate('()=>window.sourcify.accountState()');assert 'accessToken' not in info and 'refreshToken' not in info
 page.screenshot(path=str(ARTIFACTS/'Sourcify-Windows-Google-Neu.png'))
 page.locator('#continue-offline').click()
 page.locator('[data-view=playlists]').click();page.locator('#create-playlist').click();page.locator('#playlist-name').fill('Gaming');page.locator('#playlist-form').evaluate('(f)=>f.requestSubmit()')
 page.wait_for_selector('.playlist-card');assert 'Gaming' in page.locator('.playlist-card').first.inner_text()
 assert not errors,errors
 print('PASS: real Electron F11/ESC, audio continuity, Canvas bright-focus toggle, local 8-second trim, preview decoder cleanup, Mini IPC, sandbox, account configuration/no password fields/no token exposure, local playlist.')
