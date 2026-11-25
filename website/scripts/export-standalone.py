"""Export the tested static pages as directly openable HTML companions."""
import base64
import mimetypes
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / 'dist'

def embedded(relative):
    path = DIST / relative
    mime = mimetypes.guess_type(path.name)[0] or 'application/octet-stream'
    return f'data:{mime};base64,' + base64.b64encode(path.read_bytes()).decode('ascii')

css = (DIST / 'styles.css').read_text(encoding='utf-8')
css = re.sub(r"url\('([^']+)'\)", lambda m: "url('" + embedded(m[1]) + "')", css)
js = (DIST / 'app.js').read_text(encoding='utf-8').replace('assets/icons.svg#', '#')
symbols = (DIST / 'assets/icons.svg').read_text(encoding='utf-8')
symbols = symbols.replace('<svg ', '<svg aria-hidden="true" style="position:absolute;width:0;height:0;overflow:hidden" ', 1)
for name in ['index.html', 'vote.html', 'support.html', 'store.html', 'legal-notice.html', 'privacy.html', 'terms.html', 'rules.html', 'leaderboard.html', 'players.html']:
    html = (DIST / name).read_text(encoding='utf-8')
    html = html.replace('<link rel="stylesheet" href="community.css">','<style>'+(DIST/'community.css').read_text(encoding='utf-8')+'</style>')
    html = html.replace('<script src="i18n.js" defer></script>','')
    html = html.replace('<script src="votes.js" defer></script>','')
    html = re.sub(r'  <link[^>]+rel="preload"[^>]*>\s*', '', html)
    html = html.replace('<link rel="stylesheet" href="styles.css">', '<style>\n' + css + '\n</style>')
    html = html.replace('<script src="app.js" defer></script>', '')
    if name in ['legal-notice.html','privacy.html','terms.html','rules.html']:
        html = html.replace('<link rel="stylesheet" href="legal.css">', '<style>' + (DIST/'legal.css').read_text(encoding='utf-8') + '</style>')
    if name == 'store.html':
        html = html.replace('<link rel="stylesheet" href="store.css">', '<style>' + (DIST/'store.css').read_text(encoding='utf-8') + '</style>')
        html = html.replace('<script src="store.js" defer></script>', '<script>' + (DIST/'store.js').read_text(encoding='utf-8') + '</script>')
    if name == 'support.html':
        html = re.sub(r'(support\.(?:css|js))\?v=[a-f0-9]+', r'\1', html)
        html = html.replace('<link rel="stylesheet" href="support.css">', '<style>\n' + (DIST / 'support.css').read_text(encoding='utf-8') + '\n</style>')
        html = html.replace('<script src="support.js" defer></script>', '')
    if name in ['leaderboard.html','players.html']:
        html = re.sub(r'(players\.(?:css|js))\?v=[a-f0-9]+', r'\1', html)
        html = html.replace('<link rel="stylesheet" href="players.css">', '<style>'+(DIST/'players.css').read_text(encoding='utf-8')+'</style>')
        html = html.replace('<script src="players.js" defer></script>', '<script>'+(DIST/'players.js').read_text(encoding='utf-8')+'</script>')
    html = html.replace('href="assets/brand/favicon-upload.png"', 'href="' + embedded('assets/brand/favicon-upload.png') + '"')
    def inline_img(match):
        tag = match[0]
        tag = re.sub(r'\s+(srcset|sizes)="[^"]*"', '', tag)
        return re.sub(r'src="([^"]+)"', lambda m: 'src="' + embedded(m[1]) + '"', tag)
    html = re.sub(r'<img\b[^>]*>', inline_img, html)
    html = html.replace('href="assets/icons.svg#', 'href="#')
    html = html.replace('<body id="top"', '<body id="top"', 1)
    html = re.sub(r'(<body[^>]*>)', lambda m: m[0] + '\n' + symbols, html, count=1)
    catalog=(DIST/'i18n-de.json').read_text(encoding='utf-8')
    locale_js=(DIST/'i18n.js').read_text(encoding='utf-8').replace("fetch(new URL('i18n-de.json',document.currentScript?.src||location.href),{cache:'no-cache'})",'Promise.resolve({ok:true,json:async()=>('+catalog+')})')
    html = html.replace('</body>','<script>'+locale_js+'</script></body>')
    if name=='vote.html':html=html.replace('</body>','<script>'+(DIST/'votes.js').read_text(encoding='utf-8')+'</script></body>')
    html = html.replace('</body>', '<script>\n' + js + '\n</script>\n</body>')
    if name == 'support.html':
        support_js = (DIST / 'support.js').read_text(encoding='utf-8')
        html = html.replace('</body>', '<script>\n' + support_js + '\n</script>\n</body>')
    (ROOT / name).write_text(html, encoding='utf-8')
    print('Exported directly openable', name)
