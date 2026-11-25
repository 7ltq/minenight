"""Check legal routes, footer reachability, layout and passive cookie behavior."""
import asyncio,json,os
from pathlib import Path
from playwright.async_api import async_playwright
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'docs/legal';OUT.mkdir(exist_ok=True)
BASE=os.getenv('LEGAL_TEST_ORIGIN','http://127.0.0.1:4176/')
PAGES=['legal-notice.html','privacy.html','terms.html','rules.html']
async def main():
 async with async_playwright() as p:
  browser=await p.chromium.launch(executable_path=r'C:\Program Files\Google\Chrome\Application\chrome.exe',headless=True)
  checks=[]
  for width,scale in [(1440,1),(390,1),(320,1),(390,2)]:
   ctx=await browser.new_context(viewport={'width':width,'height':900})
   for name in PAGES:
    page=await ctx.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
    await page.route('https://api.mcstatus.io/**',lambda r:r.fulfill(json={'online':False}))
    r=await page.goto(BASE+name);assert r.status==200
    await page.evaluate('(s)=>document.documentElement.style.fontSize=(s*100)+"%"',scale)
    assert await page.evaluate('document.documentElement.scrollWidth<=innerWidth'),(name,width,scale)
    assert await page.locator('h1').count()==1
    assert await page.locator('main a[href="mailto:contact@minenight.de"]').count()>0
    for target in PAGES:assert await page.locator('footer a[href="'+target+'"]').count()==1
    assert 'Not an official Minecraft service.' in await page.locator('footer').inner_text()
    assert not errors,errors
    if width in [1440,390] and scale==1:await page.screenshot(path=str(OUT/f'{name}-{width}.png'),full_page=True)
    checks.append({'page':name,'width':width,'text_scale':scale,'pass':True})
    await page.close()
   assert await ctx.cookies()==[], 'Unrequested cookies on public legal pages'
   await ctx.close()
  for name in ['index.html','vote.html','support.html','store.html']:
   page=await browser.new_page()
   await page.route('https://api.mcstatus.io/**',lambda r:r.fulfill(json={'online':False}))
   await page.route('**/api/session',lambda r:r.fulfill(json={'available':False,'user':None}))
   await page.goto(BASE+name)
   for target in PAGES:assert await page.locator('footer a[href="'+target+'"]').count()==1
   await page.locator('footer a[href="privacy.html"]').click()
   assert page.url.endswith('/privacy.html')
   await page.close()
  await browser.close()
  (OUT/('live-verification.json' if BASE.startswith('https:') else 'verification.json')).write_text(json.dumps({'origin':BASE,'status':'pass','layout_cases':checks,'public_cookie_check':'none before sign-in','footer_routes':4},indent=2))
  print('Legal pages: 16 layout/text cases, 4 footer routes and public cookie checks passed.')
asyncio.run(main())
