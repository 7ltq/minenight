"""Exercise the real PHP backend with a separate, test-only Discord adapter copy."""
import os, json, shutil, subprocess, tempfile, time, urllib.parse, sys, io, hashlib
from pathlib import Path
import sqlite3
import httpx
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
(ROOT/'docs/support').mkdir(parents=True,exist_ok=True)
TMP=ROOT/'support-data/php-tests'
TMP.mkdir(parents=True,exist_ok=True)
web=TMP/'website';private=TMP/'minenight-support'
web.mkdir(exist_ok=True);private.mkdir(exist_ok=True)
app=(ROOT/'support-php/private/app.php').read_text(encoding='utf-8')
app=app.replace('function discord(', 'function real_discord(',1)
fixture='''
function discord(string $method,string $path,?array $data=null,?string $bearer=null,bool $form=false): array {
 if($method==='PUT'&&str_contains($path,'/roles/'))return [];
 if($path==='/users/@me/channels')return ['id'=>'dm-test'];
 if($path==='/channels/dm-test/messages'){if(is_file(__DIR__.'/block-dms'))throw new ApiError(503,'DM blocked');file_put_contents(__DIR__.'/dm-sent.json',json_encode($data));return ['id'=>'dm-message'];}
 if($path==='/oauth2/token')return ['access_token'=>$data['code']];
 if($path==='/users/@me')return ['id'=>$bearer,'username'=>$bearer==='999'?'7ltq':'Player'];
 if(str_contains($path,'/members/'))return ['roles'=>str_ends_with($path,'/999')?['200']:[]];
 if(str_ends_with($path,'/roles'))return [['id'=>'200','name'=>'Owner','position'=>99,'color'=>100]];
 if($path==='/guilds/1567974541039247540')return ['owner_id'=>'999'];
 if(str_ends_with($path,'/messages')){file_put_contents(__DIR__.'/sent.json',json_encode($data));file_put_contents(__DIR__.'/sent-route.txt',$path);return ['id'=>'test'];}
 throw new ApiError(404,'Fixture missing endpoint');
}
'''
app=app.replace('function real_discord(',fixture+'\nfunction real_discord(',1)
app=app.replace('$end=now()+20;', '$end=now()+1;')
(private/'app.php').write_text(app,encoding='utf-8')
shutil.copy2(ROOT/'support-php/private/community.php',private/'community.php')
shutil.copy2(ROOT/'support-php/private/applications.php',private/'applications.php')
shutil.copy2(ROOT/'support-php/private/player-stats.php',private/'player-stats.php')
(private/'application-bridge.key').write_text('a'*64)
shutil.copy2(ROOT/'support-php/private/schema.sql',private/'schema.sql')
shutil.copy2(ROOT/'support-php/public/support-api.php',web/'support-api.php')
config={'PUBLIC_URL':'http://127.0.0.1:4175','DISCORD_CLIENT_ID':'fixture','DISCORD_CLIENT_SECRET':'fixture','DISCORD_BOT_TOKEN':'fixture','DISCORD_GUILD_ID':'1567974541039247540','DISCORD_NOTIFICATION_CHANNEL_ID':'1568160766916567051','DISCORD_STAFF_ROLE_IDS':'200'}
(private/'config.php').write_text("<?php return json_decode('"+json.dumps(config)+"',true);",encoding='utf-8')
(web/'router.php').write_text("<?php require __DIR__.'/support-api.php';",encoding='utf-8')
shutil.rmtree(private/'data',ignore_errors=True)
args=[os.environ.get('MINENIGHT_PHP',str(ROOT/'qa/php-runtime/php.exe')),'-n','-d','extension_dir='+str(Path(os.environ.get('MINENIGHT_PHP',str(ROOT/'qa/php-runtime/php.exe'))).parent/'ext')]
for extension in ['pdo_sqlite','curl','mbstring','gd']:args+=['-d','extension='+extension]
args+=['-S','127.0.0.1:4175','-t',str(web),str(web/'router.php')]
log=open(TMP/'server.log','w',encoding='utf-8')
server=subprocess.Popen(args,stdout=log,stderr=log,cwd=ROOT)
origin='http://127.0.0.1:4175'
checks=[]
def check(condition,label):
 assert condition,label
 checks.append(label)
def login(c,identity):
 r=c.get('/auth/discord');q=urllib.parse.parse_qs(urllib.parse.urlsplit(r.headers['location']).query)
 check(q['code_challenge_method']==['S256'],'PKCE enabled')
 callback='/auth/discord/callback?'+urllib.parse.urlencode({'state':q['state'][0],'code':identity,'iss':'https://discord.com'})
 assert c.get(callback).status_code==303
 u=c.get('/api/session').json();h={'Origin':origin,'X-CSRF-Token':u['csrf']}
 assert c.post('/api/profile',json={'ign':'7ltq' if identity=='999' else 'PlayerIGN'},headers=h).status_code==200
 return h,callback
try:
 for _ in range(100):
  try:
   if httpx.get(origin+'/api/session').status_code==200:break
  except httpx.HTTPError:time.sleep(.05)
 else:raise RuntimeError('PHP server did not start')
 with httpx.Client(base_url=origin) as player,httpx.Client(base_url=origin) as owner,httpx.Client(base_url=origin) as other:
  h,callback=login(player,'111');ho,_=login(owner,'999');hx,_=login(other,'222')
  check(player.get(callback).status_code==400,'OAuth state cannot be reused')
  check(player.post('/api/profile',json={'ign':'Forged'}).status_code==403,'CSRF required')
  staff={'category':'staff-application','subject':'Staff application','body':'I would like to help the MineNight team.','staff_age':'16','staff_experience':'Previous moderation experience','staff_availability':'Five hours per week','staff_timezone':'Europe/Berlin','staff_microphone':'yes','staff_screenshare':'yes','staff_english':'yes'}
  bad={**staff,'staff_age':'not an age'}
  check(other.post('/api/tickets',data=bad,headers=hx).status_code==422,'Staff age is validated on the server')
  missing={**staff};del missing['staff_english']
  check(other.post('/api/tickets',data=missing,headers=hx).status_code==422,'All staff answers are required on the server')
  application=other.post('/api/tickets',data=staff,headers=hx)
  check(application.status_code==201,'Complete staff application is accepted')
  check((private/'sent-route.txt').read_text()=='/channels/1575110613967835136/messages','Staff notification uses the applications channel')
  aid=application.json()['id']
  detail=owner.get('/api/tickets/'+aid).json()
  check('Weekly availability: Five hours per week' in detail['messages'][0]['body'],'Staff answers are saved in the private conversation')
  check(player.get('/api/tickets/'+aid).status_code==404,'Staff applications are private from other players')
  bridge={'Authorization':'Bearer '+'a'*64}
  check(other.get('/api/application-bridge/jobs').status_code==401,'Game bridge rejects unauthenticated callers')
  check(other.post('/api/applications/'+aid,json={'role':'admin'},headers=hx).status_code==403,'Non-owner cannot approve an application')
  check(owner.post('/api/applications/'+aid,json={'role':'helper'},headers=ho).status_code==409,'An unverified IGN cannot receive an application rank')
  link=other.post('/api/game-link',json={},headers=hx).json()['command'].split()[-1]
  identity={'code':link,'uuid':'12345678-1234-1234-1234-123456789012','ign':'VerifiedPlayer'}
  check(other.post('/api/application-bridge/link',json=identity,headers=bridge).status_code==200,'Authenticated game bridge verifies Minecraft UUID')
  check(other.post('/api/application-bridge/link',json=identity,headers=bridge).status_code==422,'Game linking code can only be used once')
  check(owner.post('/api/applications/'+aid,json={'role':'owner'},headers=ho).status_code==422,'Owner and unlisted ranks cannot be selected')
  check(owner.post('/api/applications/'+aid,json={'role':'helper'},headers=ho).status_code==503,'Offline game bridge blocks acceptance')
  check(other.get('/api/application-bridge/jobs',headers=bridge).status_code==200,'Authenticated game worker reports readiness')
  check(owner.post('/api/applications/'+aid,json={'role':'helper'},headers=ho).status_code==200,'Only Owner can accept a verified application')
  check(owner.post('/api/applications/'+aid,json={'role':'admin'},headers=ho).status_code==409,'Duplicate acceptance cannot upgrade a queued role')
  jobs=other.get('/api/application-bridge/jobs',headers=bridge).json()['jobs']
  check(jobs[0]['uuid']==identity['uuid'] and jobs[0]['role']=='helper','Game job targets verified UUID with fixed role')
  check(other.post('/api/application-bridge/ack',json={'ticket':aid,'ok':True},headers=bridge).status_code==200,'Game delivery acknowledgement triggers Discord role')
  check(owner.get('/api/applications/'+aid).json()['grant']['state']=='complete','Both roles complete after game acknowledgement')
  snapshots={'captured_at':int(time.time()),'players':[{'uuid':'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','name':'Alpha_One','kills':25,'deaths':9,'playtime_seconds':7200},{'uuid':'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','name':'BetaTwo','kills':25,'deaths':11,'playtime_seconds':3600}]}
  check(player.post('/api/application-bridge/stats',json=snapshots).status_code==401,'Public callers cannot forge player statistics')
  check(player.post('/api/application-bridge/stats',json=snapshots,headers=bridge).status_code==200,'Authenticated vanilla snapshots are synchronized')
  public=httpx.get(origin+'/api/leaderboard?metric=kills').json()
  check(public['total']==2 and public['players'][0]['name']=='Alpha_One','Public leaderboard is deterministic and needs no login')
  check(public['players'][0]['rank']==public['players'][1]['rank']==1,'Tied players share a consistent leaderboard rank')
  check(httpx.get(origin+'/api/leaderboard?metric=deaths').json()['players'][0]['name']=='BetaTwo','Deaths sorting uses real counters')
  check(httpx.get(origin+'/api/leaderboard?metric=playtime').json()['players'][0]['playtime_seconds']==7200,'Playtime values use seconds')
  profile=httpx.get(origin+'/api/players/alpha_one').json()
  check(profile['player']['kills']==25 and profile['player']['victory_royales'] is None and profile['player']['event_wins'] is None,'Profiles expose real counters and inactive future counters')
  check('discord_id' not in profile['player'] and 'ip' not in profile['player'],'Public player profiles contain no Discord or network identifiers')
  check(httpx.get(origin+'/api/players?q=Alpha_').json()['players'][0]['name']=='Alpha_One','Underscores in player search are treated literally')
  check(httpx.get(origin+'/api/players/UnknownPlayer').status_code==404,'Unknown players are not fabricated')
  check(httpx.get(origin+'/api/leaderboard?metric=DROP').status_code==422,'Leaderboard sorting is restricted to approved columns')
  check(httpx.get(origin+'/api/players?q=%25').status_code==422,'Search does not accept SQL wildcards')
  older={**snapshots,'captured_at':snapshots['captured_at']-60,'players':[{**snapshots['players'][0],'kills':1}]}
  player.post('/api/application-bridge/stats',headers=bridge,json=older)
  check(httpx.get(origin+'/api/players/Alpha_One').json()['player']['kills']==25,'Delayed snapshots cannot overwrite newer counters')
  invalid={**snapshots,'players':[{**snapshots['players'][0],'kills':-1}]}
  check(player.post('/api/application-bridge/stats',headers=bridge,json=invalid).status_code==422,'Negative counters are rejected')
  from community_checks import check_community
  check_community(check,player,owner,other,h,ho,hx,bridge,ROOT)
  data={'category':'general-support','subject':'Need support','body':'An actual PHP integration test.'}
  r=player.post('/api/tickets',data=data,headers=h);assert r.status_code==201,r.text;tid=r.json()['id']
  check(other.get('/api/tickets/'+tid).status_code==404,'Another player cannot read ticket')
  check(other.get('/api/tickets/'+tid+'/events').status_code==404,'Another player cannot stream ticket')
  check(other.get('/api/tickets?scope=team').status_code==403,'Nonstaff cannot see team inbox')
  check(owner.get('/api/tickets?scope=team').json()['tickets'][0]['id']==tid,'Staff sees team inbox')
  r=owner.post('/api/tickets/'+tid+'/messages',data={'body':'Owner response'},headers=ho);assert r.status_code==201,r.text
  msg=player.get('/api/tickets/'+tid).json()['messages'][-1]
  check(msg['rank']=='Owner' and msg['ign']=='7ltq','Owner badge comes from trusted identity')
  out=io.BytesIO();Image.new('RGB',(40,30),'blue').save(out,'PNG')
  r=player.post('/api/tickets/'+tid+'/messages',data={'body':'Two files'},headers=h,files=[('files[]',('first.png',out.getvalue(),'image/png')),('files[]',('second.png',out.getvalue(),'image/png'))]);assert r.status_code==201,r.text
  attachments=player.get('/api/tickets/'+tid).json()['messages'][-1]['attachments']
  check(len(attachments)==2,'Multiple PHP multipart attachments persist')
  check(owner.get('/api/attachments/'+attachments[0]['id']).status_code==200,'Staff can fetch private attachment')
  check(other.get('/api/attachments/'+attachments[0]['id']).status_code==404,'Other player cannot fetch attachment')
  check(player.post('/api/tickets/'+tid+'/messages',headers=h,files={'files':('evil.svg',b'<svg/>','image/svg+xml')}).status_code==422,'SVG uploads rejected')
  route='/api/tickets/'+tid+'/status'
  check(player.post(route,headers=h,json={'status':'closed','reason':'Fake close'}).status_code==403,'Player cannot close tickets')
  check(owner.post(route,headers=ho,json={'status':'closed','reason':''}).status_code==422,'Close reason required')
  check(owner.request('DELETE','/api/tickets/'+tid,headers=ho,json={'reason':'Too early.'}).status_code==409,'Open tickets cannot be deleted')
  check(owner.post(route,headers=ho,json={'status':'closed','reason':'Resolved.'}).status_code==200,'Staff can close ticket')
  dm=json.loads((private/'dm-sent.json').read_text());check(dm['embeds'][0]['title']=='Ticket Closed' and 'Resolved.' in dm['embeds'][0]['description'],'Closing sends the creator a DM with the correct status and reason')
  check(player.post('/api/tickets/'+tid+'/messages',headers=h,data={'body':'Closed write'}).status_code==409,'Closed ticket rejects messages')
  check(owner.post(route,headers=ho,json={'status':'open','reason':'Follow up.'}).status_code==200,'Staff can reopen ticket')
  check('event: change' in player.get('/api/tickets/'+tid+'/events').text,'Authenticated live event stream works')
  check(json.loads((private/'dm-sent.json').read_text())['embeds'][0]['title']=='Ticket Reopened','Reopening sends the creator a DM')
  (private/'block-dms').write_text('test')
  check(owner.post(route,headers=ho,json={'status':'closed','reason':'Ready for deletion.'}).status_code==200,'Blocked Discord DMs do not prevent closing')
  con=sqlite3.connect(private/'data/tickets.sqlite3');check(con.execute('SELECT count(*) FROM dm_outbox').fetchone()[0]>0,'Blocked DMs remain queued for retry');con.close()
  (private/'block-dms').unlink()
  con=sqlite3.connect(private/'data/tickets.sqlite3');con.execute('UPDATE dm_outbox SET next_try=0');con.commit();con.close();owner.get('/api/session')
  check(player.request('DELETE','/api/tickets/'+tid,headers=h,json={'reason':'Unauthorized.'}).status_code==403,'Players cannot delete tickets')
  check(owner.request('DELETE','/api/tickets/'+tid,json={'reason':'No CSRF.'}).status_code==403,'Deletion requires CSRF protection')
  result=owner.request('DELETE','/api/tickets/'+tid,headers=ho,json={'reason':'Integration test completed.'})
  check(result.status_code==200,'Staff can permanently delete a closed ticket')
  check(owner.get('/api/tickets/'+tid).status_code==404 and player.get('/api/tickets/'+tid).status_code==404,'Deleted conversation cannot be read by staff or its creator')
  check(owner.get('/api/attachments/'+attachments[0]['id']).status_code==404,'Deleted attachments cannot be downloaded')
  check(not (private/'data/uploads'/ (attachments[0]['id']+'.bin')).exists(),'Attachment bytes are removed from disk')
  con=sqlite3.connect(private/'data/tickets.sqlite3');check(con.execute('SELECT count(*) FROM messages WHERE ticket_id=?',(tid,)).fetchone()[0]==0,'Ticket messages are removed by cascade');con.close()
  dm=json.loads((private/'dm-sent.json').read_text());check(dm['embeds'][0]['title']=='Ticket Closed','Permanent deletion sends no additional Discord DM')
  sent=json.loads((private/'sent.json').read_text());check(sent['allowed_mentions']=={'parse':[]},'Discord notification disables mentions')
  check(tid in sent['embeds'][0]['url'],'Discord notification links to ticket')
  check(player.post('/api/tickets',headers=h,data=data|{'category':'media-application'}).status_code==422,'Media requires evidence and confirmation')
  ui=player.post('/api/tickets',headers=h,data=data|{'subject':'Browser deletion test'}).json()['id']
  from playwright.sync_api import sync_playwright
  with sync_playwright() as pw:
   browser=pw.chromium.launch(executable_path=r'C:\Program Files\Google\Chrome\Application\chrome.exe',headless=True)
   context=browser.new_context(viewport={'width':1440,'height':1000})
   context.add_cookies([{'name':k,'value':v,'domain':'127.0.0.1','path':'/'} for k,v in owner.cookies.items()])
   def assets(route):
    path=urllib.parse.urlsplit(route.request.url).path.lstrip('/')
    file=ROOT/'dist'/path
    if file.is_file():route.fulfill(path=str(file))
    else:route.continue_()
   context.route(origin+'/**',assets)
   page=context.new_page();page.goto(origin+'/support.html?ticket='+ui)
   page.locator('#ticket-chat').wait_for(state='visible')
   check(not page.locator('#delete-ticket').is_visible(),'Permanent-delete button is hidden while ticket is open')
   page.locator('#manage-ticket').click();page.locator('#status-reason').fill('Browser test resolved.');page.locator('#status-submit').click()
   page.locator('#delete-ticket').wait_for(state='visible');page.locator('#delete-ticket').click()
   page.locator('#status-reason').fill('Browser deletion test completed.')
   page.screenshot(path=str(ROOT/'docs/support/delete-confirmation.png'))
   check('cannot be reopened' in page.locator('#status-help').inner_text(),'Deletion dialog clearly states permanence')
   page.locator('#status-submit').click();page.locator('#ticket-chat').wait_for(state='hidden')
   check(owner.get('/api/tickets/'+ui).status_code==404,'Browser delete confirmation removes the real PHP ticket')
   check('permanently deleted' in page.locator('#support-notice').inner_text(),'Browser confirms successful deletion')
   for width in [1440,390,320]:
    page.set_viewport_size({'width':width,'height':900});page.goto(origin+'/leaderboard.html')
    page.locator('#leaderboard-rows tr').first.wait_for();check(page.locator('#leaderboard-rows tr').count()==2,'Leaderboard renders synchronized players at '+str(width))
    check(page.locator('.stats-tabs button:disabled').count()==2,'Future leaderboards are visibly Coming Soon at '+str(width))
    check(page.evaluate('document.documentElement.scrollWidth<=innerWidth'),'Stats pages fit viewport '+str(width))
    page.locator('[data-metric=deaths]').click();page.wait_for_function("document.querySelector('#leaderboard-rows tr a')?.textContent==='BetaTwo'")
    page.locator('#player-query').fill('Alpha_');page.locator('#player-search button').click();page.locator('.search-result').click()
    page.locator('#player-profile').wait_for(state='visible');check(page.locator('#profile-name').inner_text()=='Alpha_One','Search links to a real player profile at '+str(width))
    check(page.locator('#profile-playtime').inner_text()=='2h 0m','Player profile formats real playtime at '+str(width))
    page.screenshot(path=str(ROOT/'docs/support'/('player-profile-'+str(width)+'.png')),full_page=True)
   from community_browser_checks import check_browser
   check_browser(check,context,page,origin,ROOT,player,owner,h,ho)
   browser.close()
  check(player.post('/api/logout',headers=h).status_code==200,'Logout succeeds')
  check(player.get('/api/tickets/'+tid).status_code==401,'Logout removes access')
 evidence={'status':'pass','checks':checks,'external_discord':'isolated test adapter only',
           'source_sha256':{str(p.relative_to(ROOT)).replace('\\','/'):hashlib.sha256(p.read_bytes()).hexdigest()
                            for p in (ROOT/'support-php').rglob('*') if p.is_file() and p.name!='config.php'}}
 (ROOT/'docs/support/php-verification.json').write_text(json.dumps(evidence,indent=2),encoding='utf-8')
 print('PHP backend:',len(checks),'checks passed')
finally:
 server.terminate();server.wait(timeout=5);log.close()
