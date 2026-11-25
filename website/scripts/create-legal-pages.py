"""Generate direct legal routes and keep shared footer links synchronized."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
src=root/'src';p=src/'index.html';home=p.read_text(encoding='utf-8')
links='<a href="vote.html">Vote</a><a href="legal-notice.html">Legal Notice</a><a href="privacy.html">Privacy</a><a href="terms.html">Terms of Service</a><a href="rules.html">Rules</a><a href="mailto:contact@minenight.de">Contact</a><a href="support.html">Support</a>'
home=re.sub(r'(<nav aria-label="Footer navigation">).*?(</nav>)',lambda m:m[1]+links+m[2],home,flags=re.S)
home=home.replace('Not affiliated with Mojang AB or Microsoft.','Not an official Minecraft service. Not approved by or associated with Mojang or Microsoft.')
p.write_text(home,encoding='utf-8')
app_path=src/'app.js'
app=app_path.read_text(encoding='utf-8').splitlines()
for i,line in enumerate(app):
 if line.strip().startswith('legal:'):
  app[i]='    legal: { icon: "shield", kicker: "MINENIGHT", title: "Legal Notice", body: \'<p>Operator information and official contact details: <a href="legal-notice.html">Legal Notice (Impressum)</a>.</p><p>Contact: <a href="mailto:contact@minenight.de">contact@minenight.de</a>.</p>\' },'
 if line.strip().startswith('privacy:'):
  app[i]='    privacy: { icon: "shield", kicker: "YOUR VISIT", title: "Privacy Policy", body: \'<p>Read our <a href="privacy.html">Privacy Policy</a> for hosting, Discord sign-in, cookies, player data, Support and your rights.</p><p>Privacy contact: <a href="mailto:contact@minenight.de">contact@minenight.de</a>.</p>\' }'
app_path.write_text('\n'.join(app)+'\n',encoding='utf-8')
header=home.split('  <main id="main"',1)[0].replace('href="index.html" aria-current="page"','href="index.html"').replace('href="#practice"','href="index.html#practice"').replace('href="#worlds"','href="index.html#worlds"')
header=header.replace('</head>','<link rel="stylesheet" href="legal.css"></head>')
footer='  <footer'+home.split('  <footer',1)[1]
for slug,title in [('legal-notice','Legal Notice (Impressum)'),('privacy','Privacy Policy'),('terms','Terms of Service'),('rules','Server Rules')]:
 head=header.replace('MineNight · Home','MineNight · '+title)
 head=re.sub(r'<meta name="description"[^>]*>','<meta name="description" content="MineNight '+title+'. Official contact: contact@minenight.de.">',head)
 content=(root/'scripts/templates'/f'{slug}.html').read_text(encoding='utf-8')
 main=f'<main id="main" class="wrap legal-main" tabindex="-1"><p class="eyebrow ice-text">MINENIGHT · INFORMATION</p><h1>{title}</h1><p class="legal-updated">Last updated: 25 November 2026</p><article aria-label="{title}">{content}</article></main>'
 (src/f'{slug}.html').write_text(head+main+footer,encoding='utf-8')
print('Generated four legal pages and official footer contact links.')
