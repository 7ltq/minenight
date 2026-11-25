"""Check the rank catalog without contacting a payment provider."""
import asyncio,json
from pathlib import Path
from playwright.async_api import async_playwright
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/store';OUT.mkdir(exist_ok=True)
async def main():
 async with async_playwright() as p:
  browser=await p.chromium.launch(executable_path=r'C:\Program Files\Google\Chrome\Application\chrome.exe',headless=True)
  results=[]
  for width in [1440,390,320]:
   page=await browser.new_page(viewport={'width':width,'height':1000})
   errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
   await page.route('https://api.mcstatus.io/**',lambda r:r.fulfill(json={'online':False}))
   await page.goto('http://127.0.0.1:4176/store.html')
   assert await page.locator('.rank-buy:disabled').count()==2
   assert await page.locator('#compare tbody tr').count()==15
   assert await page.locator('[data-price="VIP"]').inner_text()=='€4.99'
   await page.locator('input[value="lifetime"]').check()
   assert await page.locator('[data-price="VIP"]').inner_text()=='€20.00'
   assert await page.locator('[data-price="MVP"]').inner_text()=='€30.00'
   assert 'One-time payment' in await page.locator('.payment-note').first.inner_text()
   await page.locator('input[value="monthly"]').check()
   assert await page.locator('[data-price="MVP"]').inner_text()=='€9.99'
   assert 'Renews every month' in await page.locator('.payment-note').first.inner_text()
   assert await page.evaluate('document.documentElement.scrollWidth<=innerWidth')
   assert not errors,errors
   assert not await page.locator('a[href*="tebex"],form[action]').count()
   await page.screenshot(path=str(OUT/f'store-{width}.png'),full_page=True)
   results.append({'width':width,'pricing_toggle':'pass','disabled_checkout':'pass','overflow':False,'errors':errors})
   await page.close()
  for name in ['index.html','vote.html','support.html']:
   page=await browser.new_page()
   await page.route('https://api.mcstatus.io/**',lambda r:r.fulfill(json={'online':False}))
   await page.route('**/api/session',lambda r:r.fulfill(json={'available':False,'user':None}))
   await page.goto('http://127.0.0.1:4176/'+name)
   link=page.locator('.nav-actions a[href="store.html"]')
   assert await link.count()==1
   await link.click();assert page.url.endswith('/store.html')
   await page.close()
  await browser.close()
  (OUT/'verification.json').write_text(json.dumps({'status':'pass','cases':results,'navigation_pages':3},indent=2))
  print('Store: 3 layouts, both payment options, all perks, disabled checkout and 3 navigation routes passed.')
asyncio.run(main())
