"""Keep the original vote.html route and shared MineNight chrome in sync."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
home = (root / 'src' / 'index.html').read_text(encoding='utf-8')
header = home.split('  <main id="main"',1)[0]
header = header.replace('</head>', '<script src="votes.js" defer></script>\n</head>')
header = header.replace('MineNight · Home', 'Vote for MineNight · Support your server')
header = header.replace('Find your next fight on MineNight. Crystal &amp; Anchor PvP, practice bots and five RTP worlds. Join minenight.de. Battle Royale coming soon.', 'Vote for MineNight on four Minecraft server lists. Help new players discover our Crystal PvP community.')
header = header.replace('Find your next fight on MineNight. Crystal & Anchor PvP, practice bots and five RTP worlds. Join minenight.de. Battle Royale coming soon.', 'Vote for MineNight on four Minecraft server lists. Help new players discover our Crystal PvP community.')
header = header.replace('<body id="top">', '<body id="top" class="vote-page">')
header = header.replace('href="index.html" aria-current="page"', 'href="index.html"')
header = header.replace('href="vote.html">Vote', 'href="vote.html" aria-current="page">Vote')
header = header.replace('href="#practice"', 'href="index.html#practice"').replace('href="#worlds"', 'href="index.html#worlds"')
header = header.replace('MineNight — Crystal & Anchor PvP practice, training bots and five RTP worlds. Join minenight.de. Battle Royale and ranks coming soon.', 'Vote for MineNight on four Minecraft server lists. Help new players discover our Crystal PvP community.')
footer = '  <footer' + home.split('  <footer',1)[1]
play = re.search(r'<button class="promo play"(.*?)</button>', home, re.S).group(1)
main = '''  <main id="main" class="site-content wrap" tabindex="-1">
    <nav class="breadcrumb" aria-label="Breadcrumb"><a href="index.html">MineNight</a><span aria-hidden="true">/</span><span aria-current="page">Vote</span></nav>
    <section class="vote-hero" aria-labelledby="vote-title"><img src="assets/art/vote.png" width="1400" height="1100" fetchpriority="high" alt="Crey6712 leaping with an emerald above gold blocks and a diamond trail"><div class="hero-copy"><p class="eyebrow ice-text">YOUR SERVER. YOUR SUPPORT.</p><h1 id="vote-title">A small vote.<br>A bigger community.</h1><p class="muted">Every vote helps new players find MineNight. Take a moment to put your server on the map.</p><div class="vote-summary"><span><svg class="icon" aria-hidden="true"><use href="assets/icons.svg#globe"/></svg>4 server lists</span><span><svg class="icon" aria-hidden="true"><use href="assets/icons.svg#person"/></svg>Your Minecraft username</span></div></div></section>
    <section class="vote-list" aria-labelledby="vote-sites-title"><div class="vote-page-heading vote-list-heading"><h2 id="vote-sites-title">Vote sites</h2><p class="vote-provider-count">4 voting sites</p></div><div class="voting-grid">
'''
sites = [
 ('buzz','Minecraft.buzz','minecraft-buzz.png','https://minecraft.buzz/vote/minenight'),
 ('org','MinecraftServers','minecraftservers.png','https://minecraftservers.org/vote/696400'),
 ('mp','Minecraft-MP','minecraft-mp.png','https://minecraft-mp.com/server/367126/vote/'),
 ('ip','MinecraftIPList','minecraftiplist.ico','https://www.minecraftiplist.com/server/minenight-45795')
]
for number, (key, title, logo, url) in enumerate(sites, 1):
    title_markup = title.replace("MinecraftServers", "Minecraft<wbr>Servers").replace("MinecraftIPList", "Minecraft<wbr>IPList")
    main += f'''      <div class="vote-row"><a class="vote-site" href="{url}" target="_blank" rel="noopener noreferrer" aria-label="Vote for MineNight on {title}, opens in a new tab"><img class="site-logo" src="assets/vote/{logo}" width="32" height="32" alt=""><span class="site-number" aria-hidden="true">#{number}</span><h3>{title_markup}</h3><span class="vote-action" aria-hidden="true">Vote</span></a></div>
'''
main += '''    </div><p class="vote-link-note">Open a site to cast your vote. Voting is handled on the provider's website.</p></section>
    <section class="latest-votes stats-panel" aria-labelledby="latest-votes-title"><h2 id="latest-votes-title">Latest Votes</h2><p id="votes-status" role="status">Loading confirmed votes…</p><ul id="latest-votes"></ul></section>\n    <section class="vote-help" aria-labelledby="help-title"><div><p class="eyebrow ice-text">GOOD TO KNOW</p><h2 id="help-title">A little help<br>before you vote.</h2><p>Voting is handled by each listing website. Use your exact Minecraft username and follow its instructions.</p></div><div class="vote-faq"><details><summary>How do I vote?</summary><p>Open a voting site above, enter your Minecraft Java username and complete that site's voting steps. Then come back here to visit the other lists.</p></details><details><summary>How often can I vote?</summary><p>Each server list sets its own voting interval. Check the message on that website to find out when you can vote again.</p></details><details><summary>Do I need a MineNight account?</summary><p>No website account is needed. Minecraft account features on MineNight are Coming Soon. A voting service may have its own sign-in requirements.</p></details><details><summary>A voting site won't open. What can I do?</summary><p>Some lists use a security check or may be temporarily unavailable. Try again later or contact the MineNight team on Discord. You can still use the other voting links.</p></details></div></section>
    <section aria-label="Join MineNight"><button class="promo play"''' + play + '''      </button></section>
  </main>
'''
(root / 'src' / 'vote.html').write_text(header+main+footer,encoding='utf-8')
print('Created src/vote.html')
