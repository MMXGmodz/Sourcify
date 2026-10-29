"""Real native host with two independent browser guests; generated local music."""
from pathlib import Path
from playwright.sync_api import sync_playwright
ARTIFACTS=Path(__file__).resolve().parents[2]/'artifacts'
with sync_playwright() as pw:
 desktop=pw.chromium.connect_over_cdp('http://127.0.0.1:9223');host=desktop.contexts[0].pages[0];host.reload();host.locator('.track-info').first.wait_for();host.locator('.track-info').first.click();host.wait_for_function('()=>document.querySelector("audio").currentTime>.1');host.locator('#play').click()
 host.locator('#jam-top').click();host.locator('#jam-name').fill('MMXG');host.locator('#jam-start-form').evaluate('(f)=>f.requestSubmit()');host.locator('#jam-live').wait_for();jam=host.evaluate('()=>window.sourcify.jamState()');assert jam['active'];url=jam['localLink']
 browser=pw.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);errors=[]
 guests=[]
 for name in ['Lea <script>bad()</script>','Max']:
  context=browser.new_context(viewport={'width':390,'height':844});page=context.new_page();page.on('pageerror',lambda e:errors.append(str(e)));page.goto(url);page.locator('#name').fill(name);page.locator('#join-form').evaluate('(f)=>f.requestSubmit()');page.locator('#session').wait_for();guests.append(page)
 one,two=guests;host.wait_for_function('()=>document.querySelectorAll(".jam-member").length===3');assert one.locator('#controls').is_hidden()
 assert one.locator('#participants script').count()==0
 one.locator('#queue [data-repeat]').first.click();two.wait_for_function('()=>document.querySelectorAll("#queue .row").length===2');two.locator('#queue [data-repeat]').first.click();one.wait_for_function('()=>document.querySelectorAll("#queue .row").length===3');assert one.locator('#queue [data-remove]').count()==1;assert two.locator('#queue [data-remove]').count()==1
 one.locator('#queue [data-remove]').click();two.wait_for_function('()=>document.querySelectorAll("#queue .row").length===2');assert host.locator('#queue-count').inner_text()=='2'
 host.locator('#jam-control').check();one.locator('#controls').wait_for();one.locator('#play').click();host.wait_for_function('()=>!document.querySelector("audio").paused');one.wait_for_function('()=>document.querySelector("#play").textContent==="Pausieren"');one.locator('#play').click();host.wait_for_function('()=>document.querySelector("audio").paused')
 host.locator('#jam-locked').check();context=browser.new_context(viewport={'width':390,'height':844});third=context.new_page();third.goto(url);third.locator('#name').fill('Gesperrter Gast');third.locator('#join-form').evaluate('(f)=>f.requestSubmit()');third.wait_for_function('()=>document.querySelector("#join-error").textContent.includes("gesperrt")')
 assert two.evaluate('document.documentElement.scrollWidth<=innerWidth');two.screenshot(path=str(ARTIFACTS/'Sourcify-Jam-Guest.png'));host.screenshot(path=str(ARTIFACTS/'Sourcify-Jam-Host.png'))
 members=host.evaluate('()=>window.sourcify.jamState()')['participants'];guest=next(m for m in members if m['name'].startswith('Lea'));host.locator(f'[data-kick="{guest["id"]}"]').click();one.locator('#join-panel').wait_for();assert 'entfernt' in one.locator('#join-error').inner_text();two.wait_for_function('()=>document.querySelectorAll(".participant").length===2')
 host.locator('#jam-stop').click();host.locator('#jam-idle').wait_for();assert not host.evaluate('()=>window.sourcify.jamState()')['active'];assert host.locator('#jam-count').is_hidden()
 assert not errors,errors
 browser.close()
 print('Real Electron Jam: host, two browser guests, mobile layout, safe names, shared queue/ownership, controls, locking, removal and ending passed.')
