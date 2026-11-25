import json
import re


def check_browser(check, context, page, origin, root, player, owner, h, ho):
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    context.route('https://api.mcstatus.io/**', lambda route: route.fulfill(json={'online': True,'players':{'online':7}}))
    catalog = json.loads((root / 'src/i18n-de.json').read_text(encoding='utf-8'))
    changed = {key: value for key, value in catalog.items() if key != value and len(key) > 3}
    routes = ['index', 'support', 'leaderboard', 'players', 'vote', 'store', 'legal-notice', 'privacy', 'terms', 'rules']
    output = root / 'docs/community'
    output.mkdir(parents=True, exist_ok=True)
    for width in [1440, 390, 320]:
        page.set_viewport_size({'width': width, 'height': 900})
        for name in routes:
            page.goto(origin + '/' + name + '.html')
            picker = page.locator('.language-picker')
            picker.wait_for(state='attached')
            if width <= 600:
                page.locator('.menu-toggle').click()
            picker.select_option('de')
            page.wait_for_function("document.documentElement.lang==='de' && document.querySelector('.nav-links a').textContent==='Startseite'")
            page.wait_for_function("document.querySelector('[data-server-status]').textContent==='7 Spieler online · Zum Kopieren klicken'")
            check(page.evaluate('document.documentElement.scrollWidth<=innerWidth'), f'{name} fits {width}px in German')
            untranslated = page.evaluate('''keys => {
              const result=[], walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);
              let node;while(node=walker.nextNode()){
                const parent=node.parentElement;
                if(parent.closest('script,style,.message-body,.message-meta,.rank-badge,.player-link,[data-i18n-skip],#support-identity strong,.discord-name')||!parent.getClientRects().length)continue;
                const text=node.nodeValue.replace(/\\s+/g,' ').trim();if(keys.includes(text))result.push(text);
              }return result;
            }''', list(changed))
            check(not untranslated, f'{name} visible copy translates at {width}px: {untranslated[:3]}')
            if name=='store':check(page.locator('.rank-card').first.evaluate("el=>getComputedStyle(el).backgroundColor")=="rgb(152, 98, 14)",f'VIP matches homepage rank-card color at {width}px')
            if name=='leaderboard':check(page.locator('.stats-search-panel').evaluate("el=>getComputedStyle(el).backgroundColor")=="rgb(36, 100, 181)",f'Leaderboard uses homepage blue at {width}px')
            if name in ['support', 'leaderboard', 'store', 'vote']:
                if width <= 600:
                    page.locator('.menu-toggle').click()
                page.screenshot(path=str(output / f'{name}-{width}-de.png'), full_page=True)
        page.goto(origin + '/index.html')
        page.wait_for_function("document.querySelector('.nav-links a').textContent==='Startseite'")
        check(page.evaluate("localStorage.getItem('minenight.language')") == 'de', f'Language survives navigation at {width}px')
        if width <= 600:
            page.locator('.menu-toggle').click()
        page.locator('.language-picker').select_option('en')
        page.wait_for_function("document.querySelector('.nav-links a').textContent==='Home'")
        check('Ranks' in page.locator('.ranks').text_content(), f'English originals restore at {width}px')
    page.goto(origin + '/vote.html')
    page.locator('#latest-votes li').wait_for()
    check(page.locator('#latest-votes li').count() == 1, 'Vote page renders only the confirmed fixture event')
    check(page.locator('#latest-votes time').get_attribute('datetime') is not None, 'Confirmed vote includes machine-readable time')
    page.goto(origin + '/players.html?player=Alpha_One')
    page.locator('#player-skin').wait_for(state='visible')
    check(page.locator('#player-skin').get_attribute('src').endswith('/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/body'), 'Player profile requests full skin by UUID')
    page.screenshot(path=str(output / 'profile-skin.png'), full_page=True)
    ticket = player.post('/api/tickets', data={'category':'ban-appeal','subject':'Browser appeal review','body':'Please review this fixture ban.'}, headers=h)
    check(ticket.status_code == 201, 'Ban Appeal category creates tickets')
    ticket_id = ticket.json()['id']
    page.goto(origin + '/support.html?ticket=' + ticket_id)
    page.locator('#appeal-review button[value=accept]').wait_for()
    page.locator('#appeal-reason').fill('Evidence reviewed in the browser fixture.')
    page.locator('#appeal-review button[value=accept]').click()
    page.locator('#appeal-review button[value=unban]').wait_for()
    page.locator('#appeal-reason').fill('Appeal accepted after staff review.')
    page.locator('#appeal-review button[value=unban]').click()
    page.wait_for_function("document.querySelector('#appeal-review').textContent.includes('queued')")
    check(not page.locator('#appeal-review button[value=unban]').count(), 'Pending server unban cannot be clicked twice in the browser')
    page.screenshot(path=str(output / 'appeal-queued.png'), full_page=True)
    check(not errors, 'New pages and language switching produce no JavaScript exceptions: ' + repr(errors))
