"""Generate Support with the same header/footer as Home and Vote."""
from pathlib import Path
import hashlib

root = Path(__file__).resolve().parents[1]
home = (root / 'src/index.html').read_text(encoding='utf-8')
header = home.split('  <main id="main"', 1)[0]
header = header.replace('MineNight · Home', 'MineNight · Support')
start = header.index('  <meta name="description"')
end = header.index('\n', start)
header = header[:start] + '  <meta name="description" content="Get help from the MineNight team. Player reports, bugs, store support and media applications in one private conversation.">' + header[end:]
header = header.replace('href="index.html" aria-current="page"', 'href="index.html"')
header = header.replace('href="#practice"', 'href="index.html#practice"').replace('href="#worlds"', 'href="index.html#worlds"')
header = header.replace('href="support.html"', 'href="support.html" aria-current="page"')
header = header.replace('<body id="top">', '<body id="top" class="support-page">')
header = header.replace('</head>', '  <link rel="stylesheet" href="support.css">\n  <script src="support.js" defer></script>\n</head>')
for asset in ['support.css', 'support.js']:
    version = hashlib.sha256((root / 'src' / asset).read_bytes()).hexdigest()[:12]
    header = header.replace(asset + '"', asset + '?v=' + version + '"')
footer = '  <footer' + home.split('  <footer', 1)[1]
main = (root / 'scripts/templates/support-main.html').read_text(encoding='utf-8')
(root / 'src/support.html').write_text(header + main + footer, encoding='utf-8')
print('Generated support.html with shared MineNight navigation.')
