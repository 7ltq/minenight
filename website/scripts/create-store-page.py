"""Build a browseable, purchase-disabled rank catalog with shared navigation."""
from pathlib import Path
import re
from html import escape

root=Path(__file__).resolve().parents[1]
home_path=root/'src/index.html'
home=home_path.read_text(encoding='utf-8')
def store_link(m):
    attrs=re.sub(r'\s+(?:type="button"|data-dialog="store")','',m[1])
    content=m[2].replace('Store <span class="nav-soon">Soon</span>','Store').replace('Coming Soon','View ranks')
    return '<a'+attrs+' href="store.html">'+content+'</a>'
home=re.sub(r'<button([^>]*data-dialog="store"[^>]*)>(.*?)</button>',store_link,home,flags=re.S)
home=home.replace('MineNight ranks are on their way. Join our Discord for announcements.','Explore VIP and MVP ranks, compare perks and choose your payment type. Sales are coming soon.')
home_path.write_text(home,encoding='utf-8')
header=home.split('  <main id="main"',1)[0].replace('MineNight · Home','MineNight · Store')
header=header.replace('href="index.html" aria-current="page"','href="index.html"').replace('href="#practice"','href="index.html#practice"').replace('href="#worlds"','href="index.html#worlds"')
header=header.replace('href="store.html"','href="store.html" aria-current="page"')
header=re.sub(r'<meta name="description"[^>]*>','<meta name="description" content="Compare MineNight VIP and MVP ranks, all perks, monthly subscriptions and lifetime prices. Sales coming soon.">',header)
header=header.replace('</head>','<link rel="stylesheet" href="store.css"><script src="store.js" defer></script></head>')
rows=[
 ('Exclusive rank in Minecraft & Discord','VIP','MVP'),
 ('Custom Armor Trims','Included','Included','/trim'),
 ('Toggle Server IP','Included','Included','/ip on/off'),
 ('Auto Accept Teleport Requests','Included','Included','/tpauto'),
 ('Host Battle Royales','1 per day','3 per day'),
 ('Host Events','1 per day','2 per day'),
 ('Queue Priority','Priority','Highest priority'),
 ('Chat Priority','Priority','Higher priority'),
 ('Tablist Priority','Priority','Higher priority'),
 ('Priority Support','—','Included'),
 ('Custom Nickname','—','Included','/nick'),
 ('Custom Rank Color','—','Included'),
 ('Playtime Statistics','—','Included','/playtime'),
 ('Custom Server Announcement','—','Every 2 days · Staff approval required'),
 ('Discord Promotion with an @here ping','—','Once per week · Staff approval required'),
]
table=''.join('<tr><th scope="row">'+escape(r[0])+(' <code>'+escape(r[3])+'</code>' if len(r)>3 else '')+'</th><td>'+escape(r[1])+'</td><td>'+escape(r[2])+'</td></tr>' for r in rows)
cards=''
for rank,price,summary in [('VIP','4.99','Your MineNight essentials, with daily hosting and priority access.'),('MVP','9.99','All VIP perks, higher limits and extra ways to make MineNight yours.')]:
    cards+=f'''<article class="rank-card {rank.lower()}"><p class="eyebrow">MINENIGHT RANK</p><h2>{rank}</h2><p>{summary}</p><div class="rank-price" aria-live="polite"><strong data-price="{rank}">€{price}</strong><span data-period>/month</span></div><p class="payment-note" data-payment-note>Monthly subscription · Renews every month</p><button type="button" class="rank-buy" disabled data-rank="{rank}" data-tebex-monthly-url="" data-tebex-lifetime-url="">Coming Soon</button><a class="rank-details" href="#compare">Compare all {rank} perks ↓</a></article>'''
main='''<main id="main" class="wrap store-main"><div class="store-heading"><p class="eyebrow ice-text">THE MINENIGHT STORE</p><h1>Your rank. Your MineNight.</h1><p>Explore VIP and MVP, compare every perk and find your fit.</p><p class="sales-note">Rank sales are coming soon. You can browse everything below.</p></div><fieldset class="billing-picker"><legend>Choose your payment type</legend><label><input type="radio" name="billing" value="monthly" checked> Monthly Subscription</label><label><input type="radio" name="billing" value="lifetime"> Lifetime</label></fieldset><section class="rank-grid" aria-label="Available ranks">'''+cards+'''</section><section id="compare" class="rank-comparison"><h2>Compare every perk</h2><p>MVP includes all VIP perks. The higher MVP limits replace the VIP limits.</p><div class="comparison-scroll" role="region" aria-label="VIP and MVP perk comparison" tabindex="0"><table><caption>VIP and MVP rank benefits</caption><thead><tr><th scope="col">Perk</th><th scope="col">VIP</th><th scope="col">MVP</th></tr></thead><tbody>'''+table+'''</tbody></table></div></section><section class="store-payment"><h2>Clear prices. Your choice.</h2><div class="comparison-scroll" role="region" aria-label="All rank prices" tabindex="0"><table><caption>Rank pricing in EUR</caption><thead><tr><th scope="col">Payment type</th><th scope="col">VIP</th><th scope="col">MVP</th></tr></thead><tbody><tr><th scope="row">Monthly Subscription <small>Recurring monthly payment</small></th><td>€4.99/month</td><td>€9.99/month</td></tr><tr><th scope="row">Lifetime <small>One-time payment</small></th><td>€20.00</td><td>€30.00</td></tr></tbody></table></div><p>Purchases are not available yet. When sales launch, purchases, subscriptions and payments will be handled entirely through Tebex.</p><!-- Show “Payments securely processed by Tebex” only when sales become available. --></section></main>'''
footer='  <footer'+home.split('  <footer',1)[1]
(root/'src/store.html').write_text(header+main+footer,encoding='utf-8')
print('Generated purchase-disabled VIP/MVP catalog.')
