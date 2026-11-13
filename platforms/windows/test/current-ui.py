import runpy
from pathlib import Path
source=(Path(__file__).parent/'renderer.py').read_text()
source=source.replace('window.sourcify={state:',"window.sourcify={lyrics:async()=>({text:'First line\\nSecond line',available:true}),concerts:async()=>[],canvasPreview:()=>blob('canvas.webm'),openLink:async()=>true,state:")
checks='''
 page.evaluate("window.sourcify.action('play',{id:'abcdefghi01'})")
 page.evaluate("document.querySelector('#play').click()")
 page.locator('.current-tools button',has_text='Player-Einstellungen').click()
 page.locator('[data-pref="sliderStyle"]').select_option('dynamic')
 page.locator('[data-pref="playStyle"]').select_option('metal')
 page.wait_for_function('()=>document.body.classList.contains("metal-play")')
 assert page.locator('[data-pref="waveIntensity"]').get_attribute('max')=='100'
 page.locator('.current-settings').evaluate('(e)=>e.closest("dialog").close()')
 page.locator('.current-tools button',has_text='Lyrics').click()
 page.locator('.current-lyrics pre').filter(has_text='First line').wait_for()
 assert page.locator('.current-lyrics img').count()==1
 page.locator('.current-lyrics').evaluate('(e)=>e.closest("dialog").close()')
 page.locator('.current-tools button',has_text='Konzerte').click()
 page.get_by_text('Keine kommenden Konzerte gefunden.',exact=True).wait_for()
 page.locator('.event-list').evaluate('(e)=>e.closest("dialog").close()')
 page.locator('.current-tools button',has_text='Zu Video wechseln').click()
 page.locator('.video-results button').first.click()
 page.wait_for_function('()=>document.querySelector("#music-video").readyState>=2')
 assert page.locator('#music-video').evaluate('(v)=>v.muted')
 page.locator('.current-tools button',has_text='Zu Audio wechseln').click()
 assert page.locator('#music-video').is_hidden()
 page.screenshot(path=str(ARTIFACTS/'Sourcify-Windows-1.3.png'))
 assert not errors,errors
'''
source=source.replace(' browser.close()',checks+' browser.close()')
exec(compile(source,str(Path(__file__).parent/'renderer.py'),'exec'),{'__file__':str(Path(__file__).parent/'renderer.py')})
