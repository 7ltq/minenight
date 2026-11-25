<?php
const APPLICATION_ROLES=['helper'=>'1574779054781894738','mod'=>'1574779010821652490','admin'=>'1568004315933708430','media'=>'1575111720270045204'];
function applicationTables(): void {
 global $db;
 $db->exec("CREATE TABLE IF NOT EXISTS game_links(discord_id TEXT PRIMARY KEY,uuid TEXT NOT NULL UNIQUE,ign TEXT NOT NULL); CREATE TABLE IF NOT EXISTS game_codes(hash TEXT PRIMARY KEY,discord_id TEXT NOT NULL,expires REAL NOT NULL); CREATE TABLE IF NOT EXISTS application_grants(ticket TEXT PRIMARY KEY REFERENCES tickets(id) ON DELETE CASCADE,uuid TEXT NOT NULL,discord_id TEXT NOT NULL,role TEXT NOT NULL,owner TEXT NOT NULL,state TEXT NOT NULL DEFAULT 'queued',created REAL NOT NULL,updated REAL NOT NULL,error TEXT NOT NULL DEFAULT '');");
 sql('DELETE FROM game_codes WHERE expires<?',[now()]);
}
function applicationOwner(array $u): bool {
 $guild=setting('DISCORD_GUILD_ID');$info=discord('GET',"/guilds/$guild");
 if(($info['owner_id']??'')===$u['id'])return true;
 $m=discord('GET',"/guilds/$guild/members/".$u['id']);return in_array('1567978103853293578',$m['roles']??[],true);
}
function bridgeAuth(): void {
 $file=__DIR__.'/application-bridge.key';$secret=is_file($file)?trim(file_get_contents($file)):'';
 if(strlen($secret)<40||!hash_equals('Bearer '.$secret,$_SERVER['HTTP_X_MINENIGHT_BRIDGE']??$_SERVER['HTTP_AUTHORIZATION']??$_SERVER['REDIRECT_HTTP_AUTHORIZATION']??(function_exists('getallheaders')?(getallheaders()['Authorization']??getallheaders()['authorization']??''):'')))fail(401,'Bridge authentication required.');
}
function finishApplication(array $job): void {
 if($job['state']!=='game_done')return;
 try {
  discord('PUT','/guilds/'.setting('DISCORD_GUILD_ID').'/members/'.$job['discord_id'].'/roles/'.APPLICATION_ROLES[$job['role']]);
  sql("UPDATE application_grants SET state='complete',updated=?,error='' WHERE ticket=?",[now(),$job['ticket']]);
 }catch(Throwable $e){sql('UPDATE application_grants SET error=?,updated=? WHERE ticket=?',['Discord role delivery is pending. Check bot permissions and role hierarchy.',now(),$job['ticket']]);}
}
function applicationRoutes(string $path,string $method): void {
 global $db;
 if(str_starts_with($path,'/api/application-bridge/')) {
  bridgeAuth();
  if($path==='/api/application-bridge/link'&&$method==='POST') {
   $b=body();$code=text($b['code']??null,40,80,'Code');$uuid=strtolower(text($b['uuid']??null,36,36,'UUID'));$ign=text($b['ign']??null,3,16,'IGN');
   if(!preg_match('/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/D',$uuid)||!preg_match('/^[A-Za-z0-9_]{3,16}$/D',$ign))fail(422,'Invalid Minecraft identity.');
   $db->exec('BEGIN IMMEDIATE');$c=one('SELECT * FROM game_codes WHERE hash=? AND expires>?',[hash('sha256',$code),now()]);if(!$c)fail(422,'Link code expired or already used.');
   if(one('SELECT uuid FROM game_links WHERE discord_id=? OR uuid=?',[$c['discord_id'],$uuid]))fail(409,'An account is already linked. Contact the owner to change a verified link.');
   sql('INSERT INTO game_links VALUES (?,?,?)',[$c['discord_id'],$uuid,$ign]);sql('UPDATE users SET ign=? WHERE id=?',[$ign,$c['discord_id']]);sql('DELETE FROM game_codes WHERE discord_id=?',[$c['discord_id']]);$db->exec('COMMIT');output(['ok'=>true]);
  }
  if($path==='/api/application-bridge/jobs'&&$method==='GET') {
   sql("INSERT INTO meta(key,value,expires) VALUES ('application-bridge','online',?) ON CONFLICT(key) DO UPDATE SET expires=excluded.expires",[now()+20]);
   foreach(sql("SELECT * FROM application_grants WHERE state='game_done' AND updated<? LIMIT 10",[now()-10])->fetchAll() as $j)finishApplication($j);
   output(['jobs'=>sql("SELECT ticket,uuid,role FROM application_grants WHERE state='queued' ORDER BY created LIMIT 10")->fetchAll()]);
  }
  if($path==='/api/application-bridge/ack'&&$method==='POST') {
   $b=body();$job=one('SELECT * FROM application_grants WHERE ticket=?',[$b['ticket']??'']);if(!$job)fail(404,'Grant not found.');
   if($job['state']==='queued'){
    if(($b['ok']??false)===true){sql("UPDATE application_grants SET state='game_done',updated=? WHERE ticket=?",[now(),$job['ticket']]);$job['state']='game_done';finishApplication($job);}
    else sql("UPDATE application_grants SET state='failed',error=?,updated=? WHERE ticket=?",['Game rank delivery failed. Check server logs before retrying.',now(),$job['ticket']]);
   }output(['ok'=>true]);
  }fail(404,'Bridge route not found.');
 }
 if($path==='/api/game-link'&&$method==='GET'){$u=user();output(['link'=>one('SELECT uuid,ign FROM game_links WHERE discord_id=?',[$u['id']]),'bridge_online'=>(one("SELECT expires FROM meta WHERE key='application-bridge'")['expires']??0)>now()]);}
 if($path==='/api/game-link'&&$method==='POST'){$u=user(true,true);if(one('SELECT uuid FROM game_links WHERE discord_id=?',[$u['id']]))fail(409,'Your account is already linked.');rate('game-link:'.$u['id'],5,3600);$code=bin2hex(random_bytes(24));sql('DELETE FROM game_codes WHERE discord_id=?',[$u['id']]);sql('INSERT INTO game_codes VALUES (?,?,?)',[hash('sha256',$code),$u['id'],now()+600]);output(['command'=>'/mnperms link '.$code,'expires_in'=>600]);}
 if(preg_match('~^/api/applications/([a-f0-9]{24})$~D',$path,$m)){
  $u=user($method==='POST',true);$t=ticket($m[1],$u);if(!in_array($t['category'],['staff-application','media-application'],true))fail(422,'Not an application.');
  $owner=applicationOwner($u);$link=one('SELECT uuid,ign FROM game_links WHERE discord_id=?',[$t['creator']]);
  if($method==='GET')output(['can_accept'=>$owner,'linked'=>(bool)$link,'grant'=>one('SELECT role,state,error FROM application_grants WHERE ticket=?',[$t['id']]),'roles'=>$t['category']==='media-application'?['media']:['helper','mod','admin']]);
  if($method==='POST'){
   if(!$owner)fail(403,'Only the Owner can accept applications.');if($u['id']===$t['creator'])fail(403,'You cannot approve your own application.');if($t['status']!=='open')fail(409,'Reopen the application before accepting.');
   if(!$link)fail(409,'The applicant must verify their Minecraft account in-game first.');
   $b=body();$role=$b['role']??'';$allowed=$t['category']==='media-application'?['media']:['helper','mod','admin'];if(!in_array($role,$allowed,true))fail(422,'Choose an allowed application role.');
   $online=one("SELECT expires FROM meta WHERE key='application-bridge'");if(!$online||$online['expires']<now())fail(503,'The Minecraft application bridge is offline. No roles were changed.');
   $existing=one('SELECT * FROM application_grants WHERE ticket=?',[$t['id']]);if($existing)fail(409,'This application already has an acceptance record.');
   $db->exec('BEGIN IMMEDIATE');sql('INSERT INTO application_grants(ticket,uuid,discord_id,role,owner,created,updated) VALUES (?,?,?,?,?,?,?)',[$t['id'],$link['uuid'],$t['creator'],$role,$u['id'],now(),now()]);moderationAudit($t['id'],$u,'application_accepted','Approved role: '.$role);addMessage($t['id'],$u,'Application accepted for '.ucfirst($role).'. Role delivery is pending.');sql('UPDATE tickets SET updated=? WHERE id=?',[now(),$t['id']]);$db->exec('COMMIT');output(['ok'=>true,'state'=>'queued']);
  }
 }
}
