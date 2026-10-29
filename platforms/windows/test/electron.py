"""Checks the real Electron IPC and media protocol in a temporary local profile."""
from pathlib import Path
from playwright.sync_api import sync_playwright
ARTIFACTS=Path(__file__).resolve().parents[2]/"artifacts";ARTIFACTS.mkdir(exist_ok=True)
with sync_playwright() as pw:
 browser=pw.chromium.connect_over_cdp('http://127.0.0.1:9223')
 page=browser.contexts[0].pages[0];page.reload();page.wait_for_selector('.track-row');errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
 assert page.evaluate('typeof window.require')=='undefined'
 state=page.evaluate('()=>window.sourcify.state()');
 for id in state['favorites']:page.evaluate('(id)=>window.sourcify.action("favorite",{id})',id)
 state=page.evaluate('()=>window.sourcify.state()');assert all('path' not in t for t in state['tracks'].values())
 page.locator('.track-info').first.click();page.wait_for_function('()=>document.querySelector("audio").currentTime>.2')
 page.wait_for_function('()=>document.querySelector("video").currentTime>7.9')
 assert page.locator('audio').evaluate('(a)=>a.src.startsWith("sourcify-media://")')
 page.locator('#play').click();page.locator('#seek').evaluate('(s)=>{s.value=75;s.dispatchEvent(new Event("input"));}')
 page.wait_for_function('()=>document.querySelector("audio").currentTime>8.8')
 assert page.locator('#canvas-video').evaluate('(v)=>v.paused')
 page.locator('#now-favorite').click();page.wait_for_function('()=>document.querySelector("#favorite-count").textContent!=="0"')
 page.locator('[data-view=favorites]').click();assert page.locator('.track-row').count()==1
 page.locator('#credits-button').click();assert page.locator('#credits-dialog').inner_text().find('Vibecoded')>=0;page.locator('#credits-dialog [data-close]').click()
 assert not errors,errors
 page.screenshot(path=str(ARTIFACTS/'Sourcify-Windows-Playback.png'))
 print('Real Electron: sandbox, preload IPC, private paths, local audio/video, pause, seek/ranges, favorites and credits passed.')
