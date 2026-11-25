<?php
declare(strict_types=1);
function communityTables(): void {
 global $db;
 $db->exec("CREATE TABLE IF NOT EXISTS ban_snapshot(uuid TEXT PRIMARY KEY,ign TEXT NOT NULL COLLATE NOCASE,fingerprint TEXT NOT NULL); CREATE TABLE IF NOT EXISTS appeal_reviews(ticket TEXT PRIMARY KEY,uuid TEXT NOT NULL,fingerprint TEXT NOT NULL,decision TEXT NOT NULL,state TEXT NOT NULL DEFAULT 'reviewed',reason TEXT NOT NULL,staff TEXT NOT NULL,created REAL NOT NULL); CREATE TABLE IF NOT EXISTS moderation_audit(id INTEGER PRIMARY KEY, ticket TEXT NOT NULL,staff TEXT NOT NULL,staff_name TEXT NOT NULL,action TEXT NOT NULL,reason TEXT NOT NULL,created REAL NOT NULL); CREATE TABLE IF NOT EXISTS confirmed_votes(id TEXT PRIMARY KEY,uuid TEXT NOT NULL,ign TEXT NOT NULL,platform TEXT NOT NULL,confirmed INTEGER NOT NULL); CREATE INDEX IF NOT EXISTS votes_recent ON confirmed_votes(confirmed DESC);");
}
function moderationAudit(string $ticket,array $staff,string $action,string $reason): void {
 sql('INSERT INTO moderation_audit(ticket,staff,staff_name,action,reason,created) VALUES (?,?,?,?,?,?)',[$ticket,$staff['id'],$staff['discord_name'],$action,$reason,now()]);
}
function communityRoutes(string $path,string $method): void {
 global $db;
 if($method==='GET'&&preg_match('~^/api/skins/([a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12})/(head|body)$~D',$path,$m)){
  global $dataDir;
  if(!one('SELECT uuid FROM player_stats WHERE uuid=?',[$m[1]])&&!one('SELECT uuid FROM confirmed_votes WHERE uuid=? LIMIT 1',[$m[1]]))fail(404,'Player not found.');
  $dir=$dataDir.'/skins';if(!is_dir($dir))mkdir($dir,0700,true);$file=$dir.'/'.$m[1].'-'.$m[2].'.png';
  if(!is_file($file)||filemtime($file)<time()-21600){
   $lock=fopen($file.'.lock','c');
   if($lock&&flock($lock,LOCK_EX|LOCK_NB)){
    try{
     if(!is_file($file)||filemtime($file)<time()-21600){
      rate('skins:'.($_SERVER['REMOTE_ADDR']??''),150,60);$kind=$m[2]==='head'?'avatar':'body';$size=$m[2]==='head'?64:256;
      $ch=curl_init('https://mc-heads.net/'.$kind.'/'.$m[1].'/'.$size);$raw='';curl_setopt_array($ch,[CURLOPT_CONNECTTIMEOUT=>3,CURLOPT_TIMEOUT=>6,CURLOPT_FOLLOWLOCATION=>false,CURLOPT_WRITEFUNCTION=>function($ch,$data)use(&$raw){if(strlen($raw)+strlen($data)>1048576)return 0;$raw.=$data;return strlen($data);}]);curl_exec($ch);$status=curl_getinfo($ch,CURLINFO_RESPONSE_CODE);curl_close($ch);
      if($status===200&&str_starts_with($raw,"\x89PNG\r\n\x1a\n")){$tmp=$file.'.tmp';file_put_contents($tmp,$raw,LOCK_EX);chmod($tmp,0600);rename($tmp,$file);}
     }
    }finally{flock($lock,LOCK_UN);fclose($lock);}
   }elseif($lock)fclose($lock);
  }
  if(!is_file($file))fail(503,'Skin temporarily unavailable.');
  $etag='"'.hash_file('sha256',$file).'"';header('Cache-Control: public, max-age=3600, stale-while-revalidate=21600');header('ETag: '.$etag);
  if(($_SERVER['HTTP_IF_NONE_MATCH']??'')===$etag){http_response_code(304);exit;}
  header('Content-Type: image/png');header('Content-Length: '.filesize($file));readfile($file);exit;
 }
 if($path==='/api/moderation-audit'&&$method==='GET'){$u=user(false,true);if(!$u['staff'])fail(403,'Team access required.');$before=filter_var($_GET['before']??PHP_INT_MAX,FILTER_VALIDATE_INT);if($before===false||$before<1)fail(422,'Invalid audit cursor.');output(['actions'=>sql('SELECT * FROM moderation_audit WHERE id<? ORDER BY id DESC LIMIT 50',[$before])->fetchAll()]);}
 if($path==='/api/votes'&&$method==='GET')output(['votes'=>sql('SELECT uuid,ign AS name,platform,confirmed FROM confirmed_votes ORDER BY confirmed DESC,id LIMIT 20')->fetchAll()]);
 if(str_starts_with($path,'/api/community-bridge/')){
  bridgeAuth();
  if($path==='/api/community-bridge/sync'&&$method==='POST'){
   $b=body();$bans=$b['bans']??null;$votes=$b['votes']??[];
   if(!is_array($bans)||count($bans)>100000||!is_array($votes)||count($votes)>100)fail(422,'Invalid bridge batch.');
   foreach($bans as $p)if(!is_array($p)||!is_string($p['uuid']??null)||!is_string($p['name']??null)||!preg_match('/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/D',$p['uuid']??'')||!preg_match('/^[A-Za-z0-9_]{3,16}$/D',$p['name']??''))fail(422,'Invalid player identity.');
   foreach($bans as $p)if(!preg_match('/^[a-f0-9]{64}$/D',$p['fingerprint']??''))fail(422,'Invalid ban identity.');
   foreach($votes as $v)if(!is_array($v)||!is_string($v['id']??null)||!is_string($v['name']??null)||!is_string($v['uuid']??'')||!preg_match('/^[A-Za-z0-9_]{3,16}$/D',$v['name']??'')||(($v['uuid']??'')!==''&&!preg_match('/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/D',$v['uuid']))||!preg_match('/^[a-f0-9]{64}$/D',$v['id']??'')||!is_int($v['confirmed']??null)||$v['confirmed']<=0||$v['confirmed']>time()+300||!is_string($v['platform']??null)||mb_strlen($v['platform'])>100)fail(422,'Invalid confirmed vote.');
   $db->exec('BEGIN IMMEDIATE');sql('DELETE FROM ban_snapshot');foreach($bans as $p)sql('INSERT INTO ban_snapshot VALUES (?,?,?)',[$p['uuid'],$p['name'],$p['fingerprint']]);
   foreach($votes as $v)sql('INSERT OR IGNORE INTO confirmed_votes VALUES (?,?,?,?,?)',[$v['id'],$v['uuid']??'',$v['name'],$v['platform'],$v['confirmed']]);
   sql("INSERT INTO meta(key,value,expires) VALUES ('community-bridge','online',?) ON CONFLICT(key) DO UPDATE SET expires=excluded.expires",[now()+90]);
   $db->exec('COMMIT');output(['ok'=>true,'jobs'=>sql("SELECT ticket,uuid,fingerprint,reason,staff FROM appeal_reviews WHERE state='queued' ORDER BY created LIMIT 10")->fetchAll()]);
  }
  if($path==='/api/community-bridge/ack'&&$method==='POST'){
   $b=body();$db->exec('BEGIN IMMEDIATE');$job=one('SELECT * FROM appeal_reviews WHERE ticket=?',[$b['ticket']??'']);if(!$job)fail(404,'Appeal not found.');
   if($job['state']==='queued'){
    $state=($b['ok']??false)===true?'complete':'failed';sql('UPDATE appeal_reviews SET state=? WHERE ticket=?',[$state,$job['ticket']]);
    $u=one('SELECT * FROM users WHERE id=?',[$job['staff']]);moderationAudit($job['ticket'],$u,'unban_'.$state,$job['reason']);
    if(one('SELECT id FROM tickets WHERE id=?',[$job['ticket']]))addMessage($job['ticket'],$u,$job['reason'],'unban_'.$state);
   }$db->exec('COMMIT');output(['ok'=>true]);
  }fail(404,'Bridge route not found.');
 }
 if(!preg_match('~^/api/appeals/([a-f0-9]{24})$~D',$path,$m))return;
 $u=user($method==='POST',true);$t=ticket($m[1],$u);if($t['category']!=='ban-appeal')fail(422,'Not a ban appeal.');
 $can=$u['staff']&&$u['id']!==$t['creator'];
 if($method==='GET'){
  $review=one('SELECT decision,state,reason,created FROM appeal_reviews WHERE ticket=?',[$t['id']]);
  output(['can_review'=>$can,'review'=>$review,'audit'=>$u['staff']?sql('SELECT staff_name,action,reason,created FROM moderation_audit WHERE ticket=? ORDER BY id',[$t['id']])->fetchAll():[]]);
 }
 if($method!=='POST')fail(405,'Method not allowed.');if(!$can)fail(403,'Authorized staff must review another player’s appeal.');
 $b=body();$action=$b['action']??'';$reason=text($b['reason']??null,3,1000,'Reason');
 if(!in_array($action,['accept','reject','unban'],true))fail(422,'Choose an appeal action.');
 $db->exec('BEGIN IMMEDIATE');$review=one('SELECT * FROM appeal_reviews WHERE ticket=?',[$t['id']]);
 if($action==='unban'){
  if(!$review||$review['decision']!=='accept'||$review['state']!=='reviewed')fail(409,'Accept the appeal before requesting an unban.');
  if((one("SELECT expires FROM meta WHERE key='community-bridge'")['expires']??0)<now())fail(503,'The Minecraft bridge is offline.');
  sql("UPDATE appeal_reviews SET state='queued',reason=?,staff=? WHERE ticket=?",[$reason,$u['id'],$t['id']]);
 }else{
  if($review)fail(409,'This appeal has already been reviewed.');
  $creator=one('SELECT ign FROM users WHERE id=?',[$t['creator']]);$link=one('SELECT uuid FROM game_links WHERE discord_id=?',[$t['creator']]);
  $ban=$link?one('SELECT * FROM ban_snapshot WHERE uuid=?',[$link['uuid']]):one('SELECT * FROM ban_snapshot WHERE ign=? COLLATE NOCASE',[$creator['ign']]);
  if($action==='accept'&&(!$ban||(one("SELECT expires FROM meta WHERE key='community-bridge'")['expires']??0)<now()))fail(409,'No current synchronized ban matches this player. Check their Minecraft username and the server connection.');
  sql('INSERT INTO appeal_reviews(ticket,uuid,fingerprint,decision,reason,staff,created) VALUES (?,?,?,?,?,?,?)',[$t['id'],$ban['uuid']??'',$ban['fingerprint']??'',$action,$reason,$u['id'],now()]);
 }
 moderationAudit($t['id'],$u,$action,$reason);addMessage($t['id'],$u,$reason,'appeal_'.$action);sql('UPDATE tickets SET updated=? WHERE id=?',[now(),$t['id']]);$db->exec('COMMIT');output(['ok'=>true]);
}
