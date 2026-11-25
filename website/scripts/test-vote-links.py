"""Regression: browser checkboxes or forged storage cannot claim completed votes."""
import asyncio
import hashlib
import json
from pathlib import Path
from playwright.async_api import async_playwright

ROOT=Path(__file__).resolve().parents[1]
EXPECTED=['https://minecraft.buzz/vote/minenight','https://minecraftservers.org/vote/696400','https://minecraft-mp.com/server/367126/vote/','https://www.minecraftiplist.com/server/minenight-45795']

async def main():
    cases=[]
    async with async_playwright() as p:
        browser=await p.chromium.launch(executable_path=r'C:\Program Files\Google\Chrome\Application\chrome.exe',headless=True)
        for width in [1440,390]:
            context=await browser.new_context(viewport={'width':width,'height':900},reduced_motion='reduce')
            await context.route('https://api.mcstatus.io/**',lambda r:r.fulfill(status=200,content_type='application/json',body='{"online":true}'))
            await context.route('https://minecraft.buzz/vote/**',lambda r:r.fulfill(status=200,content_type='text/html',body='<title>Vote destination test</title>'))
            await context.add_init_script('''const d=new Date();localStorage.setItem('minenight.vote-checklist.v1',JSON.stringify({day:`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`,sites:['buzz','org','mp','ip']}));''')
            page=await context.new_page()
            await page.goto('http://127.0.0.1:4173/vote.html',wait_until='networkidle')
            vote_list=page.locator('.vote-list')
            assert await vote_list.locator('input[type=checkbox],[role=checkbox],[data-vote-count],[data-vote-label],.is-marked').count()==0
            assert 'voted' not in (await vote_list.inner_text()).lower()
            links=await page.locator('.vote-site').evaluate_all('(els)=>els.map(e=>({href:e.href,target:e.target,rel:e.rel}))')
            assert [link['href'] for link in links]==EXPECTED
            assert all(link['target']=='_blank' and 'noopener' in link['rel'] for link in links)
            logos=await page.locator('.site-logo').evaluate_all('(els)=>els.map(e=>({src:e.getAttribute("src"),loaded:e.complete&&e.naturalWidth>0}))')
            assert len(logos)==4 and all(i['loaded'] for i in logos)
            original_text=await vote_list.inner_text()
            await page.locator('.vote-site').first.focus()
            async with context.expect_page() as opened:
                await page.keyboard.press('Enter')
            popup=await opened.value
            await popup.wait_for_load_state()
            await popup.close()
            await page.reload(wait_until='networkidle')
            assert await vote_list.inner_text()==original_text
            assert await vote_list.locator('input,[role=checkbox],.is-marked').count()==0
            assert await page.evaluate('document.documentElement.scrollWidth<=innerWidth')
            await vote_list.screenshot(path=str(ROOT/f'qa/index-refresh/after/screenshots/vote-list-{width}.png'))
            cases.append({'width':width,'no_manual_vote_controls':True,'forged_browser_state_ignored':True,'link_and_reload_do_not_claim_a_vote':True,'logos':logos})
            await context.close()
        await browser.close()
    assert 'localStorage' not in (ROOT/'src/app.js').read_text(encoding='utf-8')
    report={'status':'pass','no_manual_vote_controls':True,'cases':cases,'dist_sha256':{f.relative_to(ROOT/'dist').as_posix():hashlib.sha256(f.read_bytes()).hexdigest() for f in (ROOT/'dist').rglob('*') if f.is_file()}}
    (ROOT/'docs/index-refresh/vote-links-verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print('Vote links pass: four icons/URLs, keyboard opening, no manual controls, no browser vote claims.')

asyncio.run(main())
