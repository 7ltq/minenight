"""Exercise narrow layouts, enlarged text and genuinely unavailable clipboard access."""
import asyncio
import hashlib
import json
from pathlib import Path
from playwright.async_api import async_playwright

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'qa' / 'usability-pass' / 'after'
REPORT = ROOT / 'docs' / 'usability-pass'
CASES = [(320, 844, 1), (360, 844, 1), (600, 900, 1), (601, 900, 1),
         (1051, 900, 1), (390, 844, 2), (720, 1024, 2),
         (1440, 900, 2), (1920, 1080, 2)]

async def main():
    OUT.mkdir(parents=True, exist_ok=True)
    REPORT.mkdir(parents=True, exist_ok=True)
    evidence = {'cases': [], 'interactions': {}}
    async with async_playwright() as p:
        browser = await p.chromium.launch(executable_path=r'C:\Program Files\Google\Chrome\Application\chrome.exe', headless=True)
        for width, height, scale in CASES:
            page = await browser.new_page(viewport={'width': width, 'height': height}, reduced_motion='reduce')
            await page.route('https://api.mcstatus.io/**', lambda route: route.fulfill(status=200, content_type='application/json', body='{"online":true}'))
            for file, label in [('index.html', 'home'), ('vote.html', 'vote')]:
                await page.goto('http://127.0.0.1:4173/' + file, wait_until='networkidle')
                await page.evaluate('(scale) => { document.documentElement.style.fontSize = `${scale * 100}%`; document.querySelectorAll("img").forEach(i => i.loading = "eager"); }', scale)
                await page.wait_for_function('() => [...document.images].every(i => i.complete && i.naturalWidth > 0)')
                await page.evaluate('document.fonts.ready')
                audit = await page.evaluate('''() => {
                    const clipped = [];
                    for (const card of document.querySelectorAll('.promo,.vote-hero,.vote-site')) {
                        const bounds = card.getBoundingClientRect();
                        for (const caption of card.querySelectorAll('.card-copy,.card-kicker,.copy-pill,.hero-copy,h3,.site-domain,p,.vote-site-cta')) {
                            const walker = document.createTreeWalker(caption, NodeFilter.SHOW_TEXT);
                            while (walker.nextNode()) {
                                if (!walker.currentNode.textContent.trim()) continue;
                                const range = document.createRange(); range.selectNodeContents(walker.currentNode);
                                for (const r of range.getClientRects()) {
                                    if (!r.width || !r.height) continue;
                                    if (r.left < bounds.left - 2 || r.top < bounds.top - 2 || r.right > bounds.right + 2 || r.bottom > bounds.bottom + 2)
                                        clipped.push({card: card.className, text: walker.currentNode.textContent.trim()});
                                }
                            }
                        }
                    }
                    return {width: innerWidth, scrollWidth: document.documentElement.scrollWidth,
                            textClipping: clipped, loaded: [...document.images].every(i => i.complete && i.naturalWidth > 0)};
                }''')
                audit.update(page=label, viewport=f'{width}x{height}', text_scale=scale)
                evidence['cases'].append(audit)
                assert audit['scrollWidth'] <= width and not audit['textClipping'] and audit['loaded'], audit
                if (width, scale) in [(320, 1), (720, 2), (390, 2)]:
                    await page.screenshot(path=str(OUT / f'{label}-{width}-text{scale * 100}.png'), full_page=True)
                print(label, width, f'text {scale * 100}%', 'PASS', flush=True)
            await page.close()

        page = await browser.new_page(viewport={'width': 390, 'height': 844}, reduced_motion='reduce')
        await page.route('https://api.mcstatus.io/**', lambda route: route.fulfill(status=200, content_type='application/json', body='{"online":true}'))
        for file in ['index.html', 'vote.html']:
            await page.goto('http://127.0.0.1:4173/' + file, wait_until='networkidle')
            await page.locator('.menu-toggle').click()
            targets = await page.locator('.menu-toggle,.nav-actions button,.footer-top nav a,.footer-top nav button').evaluate_all('(els) => els.map(e => ({text: e.textContent.trim() || e.getAttribute("aria-label"), width: e.getBoundingClientRect().width, height: e.getBoundingClientRect().height}))')
            assert all(t['width'] >= 44 and t['height'] >= 44 for t in targets), targets
            evidence['interactions']['touch_targets_' + file] = targets

        await page.goto('http://127.0.0.1:4173/', wait_until='networkidle')
        await page.locator('.menu-toggle').click()
        await page.locator('.nav-links a[href="#practice"]').focus()
        await page.keyboard.press('Enter')
        assert await page.evaluate('document.activeElement.id') == 'practice'
        assert await page.locator('.menu-toggle').get_attribute('aria-expanded') == 'false'
        await page.keyboard.press('Tab')
        assert await page.locator('#practice [data-copy-ip]').evaluate('(e) => e === document.activeElement')
        evidence['interactions']['anchor_keyboard_focus'] = 'PASS: mobile Practice link focuses its section; Tab reaches its copy button'

        for width, scale, failure in [(390, 1, 'false'), (1440, 1, 'throw'), (320, 2, 'false')]:
            await page.set_viewport_size({'width': width, 'height': 844})
            await page.goto('http://127.0.0.1:4173/', wait_until='networkidle')
            await page.evaluate('''({scale, failure}) => {
                document.documentElement.style.fontSize = `${scale * 100}%`;
                Object.defineProperty(navigator, 'clipboard', {configurable: true, value: {writeText: async () => { throw Error('denied'); }}});
                document.execCommand = () => { if (failure === 'throw') throw Error('denied'); return false; };
            }''', {'scale': scale, 'failure': failure})
            opener = page.locator('.promo.play')
            await opener.click()
            field = page.locator('.copy-address')
            await field.wait_for(state='visible')
            assert await field.input_value() == 'minenight.de'
            assert await field.evaluate('(e) => e.readOnly && e === document.activeElement && e.selectionStart === 0 && e.selectionEnd === e.value.length')
            assert await field.evaluate('(e) => e.scrollWidth <= e.clientWidth + 2'), 'The whole server address must fit in the copy field'
            for _ in range(6):
                await page.keyboard.press('Tab')
                assert await page.evaluate('document.querySelector("dialog").contains(document.activeElement)')
            await field.click()
            assert await field.evaluate('(e) => e.selectionEnd - e.selectionStart === e.value.length')
            assert await page.locator('dialog').evaluate('(d) => d.scrollWidth <= d.clientWidth + 2')
            await page.screenshot(path=str(OUT / f'manual-copy-{width}-text{scale * 100}.png'))
            await page.keyboard.press('Escape')
            assert not await page.locator('dialog').evaluate('(d) => d.open')
            assert await opener.evaluate('(e) => e === document.activeElement')
            evidence['interactions'][f'blocked_clipboard_{width}_{scale}'] = 'PASS: selected readonly IP, keyboard containment, no dialog overflow, Escape and opener focus restored'
        await browser.close()
    evidence['status'] = 'pass'
    evidence['dist_sha256'] = {str(f.relative_to(ROOT / 'dist')).replace('\\', '/'): hashlib.sha256(f.read_bytes()).hexdigest() for f in (ROOT / 'dist').rglob('*') if f.is_file()}
    (REPORT / 'verification.json').write_text(json.dumps(evidence, indent=2), encoding='utf-8')
    print('All additional usability checks passed.', flush=True)

asyncio.run(main())
