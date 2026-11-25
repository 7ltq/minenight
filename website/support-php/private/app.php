<?php
declare(strict_types=1);
require_once __DIR__.'/applications.php';
require_once __DIR__.'/player-stats.php';
require_once __DIR__.'/community.php';
// Runs on the existing PHP-FPM hosting. This entire directory is outside the document root.
const CATEGORIES = ['ban-appeal'=>'Ban Appeal','player-report'=>'Player Report','bug-report'=>'Bug Report','general-support'=>'General Support','store-rank'=>'Store / Rank Support','media-application'=>'Media Application','staff-application'=>'Staff Application'];
class ApiError extends RuntimeException { public function __construct(public int $status, string $message) {parent::__construct($message);} }
function fail(int $status,string $message): never {throw new ApiError($status,$message);}
function output(array $body,int $status=200): never {http_response_code($status);header('Content-Type: application/json; charset=utf-8');echo json_encode($body,JSON_THROW_ON_ERROR|JSON_INVALID_UTF8_SUBSTITUTE);exit;}
function sql(string $query,array $args=[]): PDOStatement {global $db;$s=$db->prepare($query);$s->execute($args);return $s;}
function one(string $query,array $args=[]): ?array {$r=sql($query,$args)->fetch();return $r===false?null:$r;}
function randomToken(): string {return rtrim(strtr(base64_encode(random_bytes(32)),'+/','-_'),'=');}
function now(): float {return microtime(true);}
function setting(string $name,string $default=''): string {global $config;return (string)($config[$name]??$default);}
function ready(): bool {return setting('DISCORD_CLIENT_SECRET')!=='' && setting('DISCORD_BOT_TOKEN')!=='' && setting('DISCORD_CLIENT_ID')!=='';}
function cookie(string $name,string $value,int $age): void {setcookie($name,$value,['expires'=>time()+$age,'path'=>'/','secure'=>str_starts_with(setting('PUBLIC_URL'),'https:'),'httponly'=>true,'samesite'=>'Lax']);}
function redirect(string $url): never {header('Location: '.$url,true,303);exit;}
function body(): array {$b=json_decode(file_get_contents('php://input'),true);if(!is_array($b)||array_is_list($b))fail(422,'Send a valid JSON object.');return $b;}
function text(mixed $value,int $min,int $max,string $label): string {if(!is_string($value))fail(422,$label.' is required.');$v=trim($value);$len=mb_strlen($v);if($len<$min||$len>$max||str_contains($v,"\0"))fail(422,"$label must contain $min–$max characters.");return $v;}
function staffApplication(array $data): string {
    $age=text($data['staff_age']??null,1,3,'Age');
    if(!ctype_digit($age)||(int)$age<1||(int)$age>120)fail(422,'Enter your age in whole years.');
    $answers=['Age (years)'=>$age,'Previous staff experience'=>text($data['staff_experience']??null,2,1000,'Experience'),'Weekly availability'=>text($data['staff_availability']??null,2,1000,'Availability'),'Time zone'=>text($data['staff_timezone']??null,2,100,'Time zone')];
    foreach(['microphone'=>'Microphone','screenshare'=>'Can screen share','english'=>'Can communicate in English'] as $key=>$label){$v=$data['staff_'.$key]??'';if(!in_array($v,['yes','no'],true))fail(422,'Answer: '.$label);$answers[$label]=$v==='yes'?'Yes':'No';}
    $result="\n\nStaff Application\n";
    foreach($answers as $label=>$value)$result.=$label.': '.$value."\n";
    return $result;
}
function rate(string $bucket,int $count,int $seconds): void {if((int)one('SELECT count(*) n FROM limits WHERE bucket=? AND happened>?',[$bucket,now()-$seconds])['n'] >= $count)fail(429,'Please wait before trying again.');sql('INSERT INTO limits VALUES (?,?)',[$bucket,now()]);}
function discord(string $method,string $path,?array $data=null,?string $bearer=null,bool $form=false): array {
    $h=['Authorization: '.($bearer!==null?'Bearer '.$bearer:'Bot '.setting('DISCORD_BOT_TOKEN')),'User-Agent: MineNightSupport/1.0'];
    $ch=curl_init('https://discord.com/api/v10'.$path);$opts=[CURLOPT_RETURNTRANSFER=>true,CURLOPT_TIMEOUT=>12,CURLOPT_CONNECTTIMEOUT=>6,CURLOPT_FOLLOWLOCATION=>false,CURLOPT_CUSTOMREQUEST=>$method];
    if($data!==null){$h[]='Content-Type: '.($form?'application/x-www-form-urlencoded':'application/json');$opts[CURLOPT_POSTFIELDS]=$form?http_build_query($data):json_encode($data,JSON_THROW_ON_ERROR);}
    $opts[CURLOPT_HTTPHEADER]=$h;curl_setopt_array($ch,$opts);$raw=curl_exec($ch);$status=curl_getinfo($ch,CURLINFO_RESPONSE_CODE);curl_close($ch);
    $result=$raw!==false?json_decode($raw,true):null;
    if($status<200||$status>=300){if($status===429){$delay=max(1,min(86400,(float)($result['retry_after']??30)));sql('INSERT INTO meta(key,value,expires) VALUES (?,?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value,expires=excluded.expires',['discord-backoff','1',now()+$delay]);}throw new ApiError($status===404?404:503,'Discord could not verify this request. Please try again shortly.');}
    return is_array($result)?$result:[];
}
function membership(string $id): array {
    $backoff=one('SELECT * FROM meta WHERE key=?',['discord-backoff']);if($backoff&&$backoff['expires']>now())fail(503,'Discord is temporarily busy. Please try again shortly.');
    $guild=setting('DISCORD_GUILD_ID');$member=discord('GET',"/guilds/$guild/members/$id");
    $cache=one('SELECT * FROM meta WHERE key=?',['guild-metadata']);
    if(!$cache||$cache['expires']<now()){$meta=[discord('GET',"/guilds/$guild"),discord('GET',"/guilds/$guild/roles")];sql('INSERT INTO meta(key,value,expires) VALUES (?,?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value,expires=excluded.expires',['guild-metadata',json_encode($meta),now()+60]);}else{$meta=json_decode($cache['value'],true);}
    [$info,$roles]=$meta;$ids=$member['roles']??[];$staffRoles=array_filter(explode(',',setting('DISCORD_STAFF_ROLE_IDS')));$rankRoles=array_filter(explode(',',setting('DISCORD_RANK_ROLE_IDS')));
    $eligible=array_values(array_filter($roles,fn($r)=>in_array($r['id'],$ids,true)&&$r['id']!==$guild&&!($r['managed']??false)&&(!$rankRoles||in_array($r['id'],$rankRoles,true))));
    usort($eligible,fn($a,$b)=>($b['position']<=>$a['position'])?:strcmp($a['id'],$b['id']));$role=$eligible[0]??[];$owner=$info['owner_id']===$id;
    return ['rank'=>$owner?'Owner':($role['name']??'Member'),'color'=>$owner?0xf5bf55:($role['color']??0),'staff'=>$owner||count(array_intersect($ids,$staffRoles))>0];
}
function user(bool $mutation=false,bool $force=false,bool $verify=true): array {
    $u=one('SELECT u.*,s.csrf,s.expires FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token=?',[hash('sha256',$_COOKIE['mn_session']??'')]);
    if(!$u||$u['expires']<now())fail(401,'Please sign in with Discord again.');
    if($mutation&&((($_SERVER['HTTP_ORIGIN']??'')!==setting('PUBLIC_URL'))||!hash_equals($u['csrf'],$_SERVER['HTTP_X_CSRF_TOKEN']??'')))fail(403,'This request could not be verified. Reload and try again.');
    if($verify&&($force||$u['checked']<now()-15)){
        try{$m=membership($u['id']);}catch(ApiError $e){if($e->status===404){sql('DELETE FROM sessions WHERE user_id=?',[$u['id']]);fail(403,'Join the MineNight Discord before using support.');}throw $e;}
        $u=array_merge($u,$m);$u['checked']=now();sql('UPDATE users SET rank=?,color=?,staff=?,checked=? WHERE id=?',[$u['rank'],$u['color'],(int)$u['staff'],$u['checked'],$u['id']]);
    }$u['staff']=(bool)$u['staff'];return $u;
}
function ticket(string $id,array $u): array {$t=one('SELECT * FROM tickets WHERE id=?',[$id]);if(!$t||($t['creator']!==$u['id']&&!$u['staff']))fail(404,'Ticket not found.');return $t;}
function addMessage(string $id,array $u,string $text,string $kind='message'): int {global $db;sql('INSERT INTO messages(ticket_id,author,ign,discord_name,rank,color,kind,body,created) VALUES (?,?,?,?,?,?,?,?,?)',[$id,$u['id'],$u['ign'],$u['discord_name'],$u['rank'],$u['color'],$kind,$text,now()]);return (int)$db->lastInsertId();}
function files(): array {
    $out=[];foreach($_FILES as $item){$names=is_array($item['name'])?$item['name']:[$item['name']];foreach($names as $i=>$name){if($name==='')continue;$f=[];foreach(['name','tmp_name','error','size'] as $key)$f[$key]=is_array($item[$key])?$item[$key][$i]:$item[$key];$out[]=$f;}}
    if(count($out)>3)fail(422,'Attach up to three files.');$processed=[];
    foreach($out as $f){if($f['error']!==UPLOAD_ERR_OK||$f['size']<1||$f['size']>25*1024*1024)fail(413,'Each file must be between 1 byte and 25 MB.');$name=mb_substr(preg_replace('/[^\pL\pN_. ()-]/u','_',basename($f['name'])),-120);$ext=strtolower(pathinfo($name,PATHINFO_EXTENSION));$raw=file_get_contents($f['tmp_name']);$mime='';
        if(in_array($ext,['png','jpg','jpeg','gif','webp'],true)){$dims=@getimagesizefromstring($raw);if(!$dims||$dims[0]*$dims[1]>20000000)fail(422,'Image dimensions are too large or invalid.');$image=@imagecreatefromstring($raw);if(!$image)fail(422,'Invalid raster image.');ob_start();imagepng($image);$raw=ob_get_clean();$mime='image/png';$name=pathinfo($name,PATHINFO_FILENAME).'.png';}
        elseif($ext==='mp4'&&substr($raw,4,4)==='ftyp')$mime='video/mp4';elseif($ext==='webm'&&str_starts_with($raw,"\x1aE\xdf\xa3"))$mime='video/webm';else fail(422,'Use PNG, JPG, GIF, WebP, MP4 or WebM.');
        if(strlen($raw)>25*1024*1024)fail(413,'The processed image is too large.');$processed[]=['id'=>bin2hex(random_bytes(16)),'name'=>$name,'mime'=>$mime,'data'=>$raw];
    }return $processed;
}
function saveFiles(string $id,int $msg,array $files,array &$written): void {global $dataDir;$size=(int)(one('SELECT coalesce(sum(size),0) n FROM attachments WHERE ticket_id=?',[$id])['n']);if($size+array_sum(array_map(fn($f)=>strlen($f['data']),$files))>250*1024*1024)fail(413,'Ticket attachment quota reached.');foreach($files as $f){$stored=$f['id'].'.bin';$path=$dataDir.'/uploads/'.$stored;$written[]=$path;if(file_put_contents($path,$f['data'],LOCK_EX)===false)fail(503,'Attachment storage is unavailable.');chmod($path,0600);sql('INSERT INTO attachments VALUES (?,?,?,?,?,?,?)',[$f['id'],$msg,$id,$f['name'],$stored,$f['mime'],strlen($f['data'])]);}}
function dispatch(): void {global $dataDir;
    if(!ready())return;$lock=fopen($dataDir.'/notification.lock','c');if(!$lock||!flock($lock,LOCK_EX|LOCK_NB)){if($lock)fclose($lock);return;}
    try{$job=one('SELECT o.*,t.subject,t.category,t.created,u.ign FROM outbox o JOIN tickets t ON t.id=o.id JOIN users u ON u.id=t.creator WHERE o.sent IS NULL AND o.next_try<=? ORDER BY o.next_try LIMIT 1',[now()]);if(!$job)return;
        try{discord('POST','/channels/'.(in_array($job['category'],['staff-application','media-application'],true)?'1575110613967835136':setting('DISCORD_NOTIFICATION_CHANNEL_ID')).'/messages',['content'=>$job['category']==='staff-application'?'**New MineNight staff application**':'**New MineNight ticket**','allowed_mentions'=>['parse'=>[]],'nonce'=>substr(hash('sha256',$job['id']),0,20),'enforce_nonce'=>true,'embeds'=>[['title'=>mb_substr($job['subject'],0,120),'url'=>setting('PUBLIC_URL').'/support.html?ticket='.$job['id'],'description'=>CATEGORIES[$job['category']].' · '.$job['ign'],'color'=>0x5865f2]]]);sql('UPDATE outbox SET sent=?,attempts=attempts+1,last_status=200 WHERE id=?',[now(),$job['id']]);}
        catch(ApiError $e){$backoff=one('SELECT expires FROM meta WHERE key=?',['discord-backoff']);$delay=max((float)($backoff['expires']??0),now()+min(3600,2**min(12,$job['attempts']+2)));sql('UPDATE outbox SET attempts=attempts+1,next_try=?,last_status=? WHERE id=?',[$delay,$e->status,$job['id']]);}
    }finally{flock($lock,LOCK_UN);fclose($lock);}
}
function lifecycleTables(): void {
 global $db;
 $db->exec("CREATE TABLE IF NOT EXISTS dm_outbox(id TEXT PRIMARY KEY,recipient TEXT NOT NULL,payload TEXT NOT NULL,created REAL NOT NULL,next_try REAL NOT NULL DEFAULT 0,attempts INTEGER NOT NULL DEFAULT 0); CREATE TABLE IF NOT EXISTS upload_deletions(stored TEXT PRIMARY KEY);");
 sql('DELETE FROM dm_outbox WHERE created<?',[now()-7*86400]);
 sql("DELETE FROM dm_outbox WHERE payload LIKE '%\"title\":\"Ticket Deleted\"%'");
}
function queueTicketDm(array $t,array $u,string $event,string $reason): void {
 if($event==='deleted')return;
 $label=['closed'=>'closed','open'=>'reopened','deleted'=>'deleted'][$event];
 $description='Your MineNight ticket #'.strtoupper(substr($t['id'],-8)).' has been '.$label.'.';
 if($event==='deleted')$description.=" The conversation and attachments have been permanently removed from Support.";
 $description.="\nReason: ".$reason."\nTeam member: ".($u['ign']?:$u['discord_name']);
 $embed=['title'=>'Ticket '.ucfirst($label),'description'=>$description,'color'=>0x3478ed];
 if($event!=='deleted')$embed['url']=setting('PUBLIC_URL').'/support.html?ticket='.$t['id'];
 $id=bin2hex(random_bytes(12));$payload=['allowed_mentions'=>['parse'=>[]],'nonce'=>substr($id,0,20),'enforce_nonce'=>true,'embeds'=>[$embed]];
 sql('INSERT INTO dm_outbox(id,recipient,payload,created) VALUES (?,?,?,?)',[$id,$t['creator'],json_encode($payload,JSON_THROW_ON_ERROR),now()]);
}
function dispatchTicketDms(): void {
 if(!ready())return;
 try {
  $backoff=one("SELECT expires FROM meta WHERE key='discord-backoff'");if(($backoff['expires']??0)>now())return;
  $job=one('SELECT * FROM dm_outbox WHERE next_try<=? ORDER BY created LIMIT 1',[now()]);if(!$job)return;
  try {
   $channel=discord('POST','/users/@me/channels',['recipient_id'=>$job['recipient']]);
   discord('POST','/channels/'.$channel['id'].'/messages',json_decode($job['payload'],true,512,JSON_THROW_ON_ERROR));
   sql('DELETE FROM dm_outbox WHERE id=?',[$job['id']]);
  }catch(Throwable $e){sql('UPDATE dm_outbox SET attempts=attempts+1,next_try=? WHERE id=?',[now()+min(3600,2**min(12,$job['attempts']+3)),$job['id']]);}
 }catch(Throwable $e){error_log('MineNight ticket DM deferred (private payload omitted)');}
}
function purgeTicketUploads(): void {
 global $dataDir;
 foreach(sql('SELECT stored FROM upload_deletions LIMIT 100')->fetchAll() as $a){
  if(!preg_match('/^[a-f0-9]{32}\.bin$/D',$a['stored']))continue;
  $path=$dataDir.'/uploads/'.$a['stored'];
  if(!is_file($path)||@unlink($path))sql('DELETE FROM upload_deletions WHERE stored=?',[$a['stored']]);
 }
}

function cleanup(): void {global $db,$dataDir;
    $lock=fopen($dataDir.'/maintenance.lock','c');if(!$lock||!flock($lock,LOCK_EX|LOCK_NB)){if($lock)fclose($lock);return;}
    try{$last=one('SELECT expires FROM meta WHERE key=?',['maintenance']);if($last&&$last['expires']>now())return;$db->exec('BEGIN IMMEDIATE');$old=sql("SELECT id FROM tickets WHERE status='closed' AND closed<?",[now()-86400*(int)setting('TICKET_RETENTION_DAYS','90')])->fetchAll();$paths=[];foreach($old as $t){foreach(sql('SELECT stored FROM attachments WHERE ticket_id=?',[$t['id']])->fetchAll() as $a)$paths[]=$a['stored'];sql('DELETE FROM tickets WHERE id=?',[$t['id']]);}sql('DELETE FROM sessions WHERE expires<?',[now()]);sql('DELETE FROM oauth WHERE expires<?',[now()]);sql('DELETE FROM limits WHERE happened<?',[now()-86400]);sql('INSERT INTO meta(key,value,expires) VALUES (?,?,?) ON CONFLICT(key) DO UPDATE SET expires=excluded.expires',['maintenance','1',now()+3600]);$db->exec('COMMIT');foreach($paths as $name)@unlink($dataDir.'/uploads/'.$name);
    }catch(Throwable $e){if($db->inTransaction())$db->rollBack();}finally{flock($lock,LOCK_UN);fclose($lock);}
}
function route(): never {global $db,$dataDir;
    $path=parse_url($_SERVER['REQUEST_URI'],PHP_URL_PATH);$method=$_SERVER['REQUEST_METHOD'];communityRoutes($path,$method);playerStatsRoutes($path,$method);applicationRoutes($path,$method);
    if($path==='/api/session'&&$method==='GET'){if(empty($_COOKIE['mn_session']))output(['available'=>ready(),'user'=>null,'categories'=>CATEGORIES,'retention_days'=>(int)setting('TICKET_RETENTION_DAYS','90')]);$u=user();output(['available'=>ready(),'user'=>array_intersect_key($u,array_flip(['id','discord_name','ign','rank','color','staff'])),'csrf'=>$u['csrf'],'categories'=>CATEGORIES,'retention_days'=>(int)setting('TICKET_RETENTION_DAYS','90')]);}
    if($path==='/auth/discord'&&$method==='GET'){if(!ready())redirect('/support.html?auth=unavailable');$state=randomToken();$verifier=randomToken().randomToken();$db->exec('BEGIN IMMEDIATE');rate('login:'.($_SERVER['REMOTE_ADDR']??''),20,600);sql('INSERT INTO oauth VALUES (?,?,?)',[hash('sha256',$state),$verifier,now()+600]);$db->exec('COMMIT');cookie('mn_oauth',$state,600);redirect('https://discord.com/oauth2/authorize?'.http_build_query(['client_id'=>setting('DISCORD_CLIENT_ID'),'redirect_uri'=>setting('PUBLIC_URL').'/auth/discord/callback','response_type'=>'code','scope'=>'identify','state'=>$state,'code_challenge'=>rtrim(strtr(base64_encode(hash('sha256',$verifier,true)),'+/','-_'),'='),'code_challenge_method'=>'S256']));}
    if($path==='/auth/discord/callback'&&$method==='GET'){$state=$_GET['state']??'';if(!$state||!hash_equals($_COOKIE['mn_oauth']??'',$state))fail(400,'Sign-in expired. Please start again.');$db->exec('BEGIN IMMEDIATE');$auth=one('SELECT * FROM oauth WHERE state=?',[hash('sha256',$state)]);sql('DELETE FROM oauth WHERE state=?',[hash('sha256',$state)]);$db->exec('COMMIT');if(!$auth||$auth['expires']<now()||($_GET['iss']??'https://discord.com')!=='https://discord.com')fail(400,'Sign-in expired. Please start again.');if(isset($_GET['error'])||empty($_GET['code']))redirect('/support.html?auth=cancelled');
        try{$token=discord('POST','/oauth2/token',['grant_type'=>'authorization_code','code'=>$_GET['code'],'redirect_uri'=>setting('PUBLIC_URL').'/auth/discord/callback','client_id'=>setting('DISCORD_CLIENT_ID'),'client_secret'=>setting('DISCORD_CLIENT_SECRET'),'code_verifier'=>$auth['verifier']],null,true);$identity=discord('GET','/users/@me',null,$token['access_token']);unset($token);$m=membership($identity['id']);}catch(ApiError $e){redirect('/support.html?auth='.($e->status===404?'membership':'retry'));}
        $token=randomToken();$csrf=randomToken();$db->exec('BEGIN IMMEDIATE');sql('INSERT INTO users(id,discord_name) VALUES (?,?) ON CONFLICT(id) DO UPDATE SET discord_name=excluded.discord_name',[$identity['id'],$identity['global_name']??$identity['username']]);sql('UPDATE users SET rank=?,color=?,staff=?,checked=? WHERE id=?',[$m['rank'],$m['color'],(int)$m['staff'],now(),$identity['id']]);sql('DELETE FROM sessions WHERE token=?',[hash('sha256',$_COOKIE['mn_session']??'')]);sql('INSERT INTO sessions VALUES (?,?,?,?)',[hash('sha256',$token),$identity['id'],$csrf,now()+7*86400]);$db->exec('COMMIT');cookie('mn_oauth','',-3600);cookie('mn_session',$token,7*86400);redirect('/support.html');
    }
    if($path==='/api/logout'&&$method==='POST'){user(true,false,false);sql('DELETE FROM sessions WHERE token=?',[hash('sha256',$_COOKIE['mn_session']??'')]);cookie('mn_session','',-3600);output(['ok'=>true]);}
    if($path==='/api/profile'&&$method==='POST'){$u=user(true,true);$ign=text(body()['ign']??null,3,16,'Minecraft username');if(!preg_match('/^[A-Za-z0-9_]{3,16}$/D',$ign))fail(422,'Enter a Java Minecraft username.');sql('UPDATE users SET ign=? WHERE id=?',[$ign,$u['id']]);output(['ok'=>true]);}
    if($path==='/api/tickets'&&$method==='GET'){$u=user();$scope=$_GET['scope']??'mine';$status=$_GET['status']??'all';if(!in_array($scope,['mine','team'],true)||!in_array($status,['all','open','closed'],true))fail(422,'Invalid filter.');if($scope==='team'&&!$u['staff'])fail(403,'Team access required.');$where=['t.updated<?'];$args=[(float)($_GET['before']??1e20)];if($scope==='mine'){$where[]='t.creator=?';$args[]=$u['id'];}if($status!=='all'){$where[]='t.status=?';$args[]=$status;}$rows=sql('SELECT t.*,u.ign FROM tickets t JOIN users u ON u.id=t.creator WHERE '.implode(' AND ',$where).' ORDER BY t.updated DESC LIMIT 51',$args)->fetchAll();output(['tickets'=>array_slice($rows,0,50),'more'=>count($rows)>50]);}
    if($path==='/api/tickets'&&$method==='POST'){$u=user(true,true);if(!$u['ign'])fail(422,'Save your Minecraft username first.');$category=$_POST['category']??'';if(!isset(CATEGORIES[$category]))fail(422,'Choose a category.');$subject=text($_POST['subject']??null,4,120,'Subject');$body=text($_POST['body']??null,10,6000,'Description');$media='';if($category==='media-application'){$media=text($_POST['media_url']??null,15,500,'TikTok link');$url=parse_url($media);if(!$url||($url['scheme']??'')!=='https'||!in_array($url['host']??'',['tiktok.com','www.tiktok.com','vm.tiktok.com','vt.tiktok.com'],true)||isset($url['user'])||isset($url['port'])||($_POST['media_confirm']??'')!=='yes')fail(422,'Supply a TikTok link and confirm 2,500 views with minenight.de visible on screen.');$body.="\n\nTikTok: $media\nApplicant confirms 2,500+ views and minenight.de visible on screen. Pending team review.";}if($category==='staff-application')$body.=staffApplication($_POST);$files=files();$id=bin2hex(random_bytes(12));$now=now();$written=[];try{$db->exec('BEGIN IMMEDIATE');rate('create:'.$u['id'],5,3600);if((int)one("SELECT count(*) n FROM tickets WHERE creator=? AND status='open'",[$u['id']])['n']>=3)fail(429,'You already have three open tickets.');sql('INSERT INTO tickets(id,creator,category,subject,created,updated,media_url) VALUES (?,?,?,?,?,?,?)',[$id,$u['id'],$category,$subject,$now,$now,$media]);$msg=addMessage($id,$u,$body);saveFiles($id,$msg,$files,$written);sql('INSERT INTO outbox(id) VALUES (?)',[$id]);$db->exec('COMMIT');}catch(Throwable $e){if($db->inTransaction())$db->rollBack();foreach($written as $p)@unlink($p);throw $e;}dispatch();output(['id'=>$id],201);}
    if(preg_match('~^/api/tickets/([a-f0-9]{24})(?:/(messages|status|events))?$~D',$path,$match)){$id=$match[1];$action=$match[2]??'';$u=user(in_array($method,['POST','DELETE'],true),in_array($method,['POST','DELETE'],true));$t=ticket($id,$u);
        if($method==='GET'&&$action===''){$before=(int)($_GET['before']??PHP_INT_MAX);$rows=sql('SELECT * FROM messages WHERE ticket_id=? AND id<? ORDER BY id DESC LIMIT 101',[$id,$before])->fetchAll();$messages=array_reverse(array_slice($rows,0,100));foreach($messages as &$msg)$msg['attachments']=sql('SELECT id,name,mime,size FROM attachments WHERE message_id=?',[$msg['id']])->fetchAll();$note=one('SELECT sent FROM outbox WHERE id=?',[$id]);output(['ticket'=>$t,'messages'=>$messages,'has_older'=>count($rows)>100,'can_manage'=>$u['staff'],'notification'=>!empty($note['sent'])?'sent':'queued']);}
        if($method==='DELETE'&&$action===''){
         if(!$u['staff'])fail(403,'Only the MineNight team can delete tickets.');
         $b=body();$reason=text($b['reason']??null,3,1000,'Deletion reason');
         $db->exec('BEGIN IMMEDIATE');$t=ticket($id,$u);
         if($t['status']!=='closed')fail(409,'Close this ticket before deleting it.');
         if(one("SELECT ticket FROM appeal_reviews WHERE ticket=? AND state='queued'",[$id]))fail(409,'Wait for the queued unban before deleting this ticket.');
         if(one("SELECT ticket FROM application_grants WHERE ticket=? AND state IN ('queued','game_done')",[$id]))fail(409,'Wait for pending application role delivery before deleting this ticket.');
         foreach(sql('SELECT stored FROM attachments WHERE ticket_id=?',[$id])->fetchAll() as $a)sql('INSERT OR IGNORE INTO upload_deletions VALUES (?)',[$a['stored']]);
         // Permanent deletion intentionally sends no Discord DM.
         moderationAudit($id,$u,'ticket_deleted',$reason);sql('DELETE FROM tickets WHERE id=?',[$id]);$db->exec('COMMIT');purgeTicketUploads();dispatchTicketDms();output(['ok'=>true,'dm'=>'queued_or_sent']);
        }
        if($method==='POST'&&$action==='messages'){$body=text($_POST['body']??'',0,6000,'Message');$files=files();if($body===''&&!$files)fail(422,'Write a message or attach a file.');$written=[];try{$db->exec('BEGIN IMMEDIATE');$t=ticket($id,$u);if($t['status']!=='open')fail(409,'This ticket is closed.');rate('message:'.$u['id'],30,60);$msg=addMessage($id,$u,$body);saveFiles($id,$msg,$files,$written);sql('UPDATE tickets SET updated=? WHERE id=?',[now(),$id]);$db->exec('COMMIT');}catch(Throwable $e){if($db->inTransaction())$db->rollBack();foreach($written as $p)@unlink($p);throw $e;}output(['ok'=>true,'id'=>$msg],201);}
        if($method==='POST'&&$action==='status'){if(!$u['staff'])fail(403,'Only the MineNight team can close or reopen tickets.');$b=body();$status=$b['status']??'';if(!in_array($status,['open','closed'],true))fail(422,'Invalid status.');$reason=text($b['reason']??null,3,1000,'Reason');$db->exec('BEGIN IMMEDIATE');$t=ticket($id,$u);if($t['status']===$status)fail(409,'Ticket already has this status.');$now=now();sql('UPDATE tickets SET status=?,updated=?,closed=?,reason=? WHERE id=?',[$status,$now,$status==='closed'?$now:null,$reason,$id]);addMessage($id,$u,$reason,$status==='closed'?'closed':'reopened');moderationAudit($id,$u,'ticket_'.$status,$reason);queueTicketDm($t,$u,$status,$reason);$db->exec('COMMIT');dispatchTicketDms();output(['ok'=>true,'dm'=>'queued_or_sent']);}
        if($method==='GET'&&$action==='events'){if(isset($_SERVER['HTTP_ORIGIN'])&&$_SERVER['HTTP_ORIGIN']!==setting('PUBLIC_URL'))fail(403,'Invalid origin.');header('Content-Type: text/event-stream');header('X-Accel-Buffering: no');header('Cache-Control: no-store');while(ob_get_level())ob_end_flush();set_time_limit(30);$end=now()+20;$last=null;do{try{$current=user();$t=ticket($id,$current);$latest=(int)one('SELECT coalesce(max(id),0) n FROM messages WHERE ticket_id=?',[$id])['n'];$revision=$latest.':'.$t['status'];if($revision!==$last){echo "event: change\ndata: ".json_encode(['last_id'=>$latest,'status'=>$t['status']])."\n\n";$last=$revision;}else echo ": heartbeat\n\n";flush();}catch(ApiError $e){echo "event: access-lost\ndata: {}\n\n";flush();exit;}if(connection_aborted())exit;usleep(1000000);}while(now()<$end);exit;}
    }
    if($method==='GET'&&preg_match('~^/api/attachments/([a-f0-9]{32})$~D',$path,$match)){$u=user();$a=one('SELECT * FROM attachments WHERE id=?',[$match[1]]);if(!$a)fail(404,'Attachment not found.');ticket($a['ticket_id'],$u);$file=$dataDir.'/uploads/'.$a['stored'];if(!is_file($file))fail(404,'Attachment unavailable.');header('Content-Type: '.$a['mime']);header('Content-Disposition: inline; filename="attachment"; filename*=UTF-8\'\''.rawurlencode($a['name']));header("Content-Security-Policy: default-src 'none'; sandbox");header('Content-Length: '.filesize($file));readfile($file);exit;}
    fail(404,'Route not found.');
}
try {
    ini_set('display_errors','0');header('Cache-Control: no-store');header('X-Content-Type-Options: nosniff');header('X-Frame-Options: DENY');header('Referrer-Policy: no-referrer');
    if(!is_file(__DIR__.'/config.php'))output(['available'=>false,'user'=>null,'categories'=>CATEGORIES,'retention_days'=>90],503);
    $config=require __DIR__.'/config.php';$origin=rtrim(setting('PUBLIC_URL'),'/');$host=parse_url($origin,PHP_URL_HOST);if(PHP_SAPI!=='cli'&&strtolower(explode(':',$_SERVER['HTTP_HOST']??'')[0])!==strtolower($host))fail(400,'Invalid host.');
    if((int)($_SERVER['CONTENT_LENGTH']??0)>32*1024*1024)fail(413,'Request exceeds 32 MB.');
    $dataDir=__DIR__.'/data';if(!is_dir($dataDir.'/uploads')&&!mkdir($dataDir.'/uploads',0700,true)&&!is_dir($dataDir.'/uploads'))fail(503,'Support storage unavailable.');
    $db=new PDO('sqlite:'.$dataDir.'/tickets.sqlite3',null,null,[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC]);$db->exec('PRAGMA foreign_keys=ON; PRAGMA busy_timeout=10000; PRAGMA journal_mode=WAL;');$db->exec(file_get_contents(__DIR__.'/schema.sql'));$db->exec('CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY,value TEXT NOT NULL,expires REAL NOT NULL)');applicationTables();playerStatsTables();communityTables();lifecycleTables();purgeTicketUploads();chmod($dataDir.'/tickets.sqlite3',0600);cleanup();dispatch();dispatchTicketDms();if(PHP_SAPI==='cli')exit;route();
} catch(ApiError $e){if(isset($db)&&$db->inTransaction())$db->rollBack();output(['detail'=>$e->getMessage()],$e->status);}catch(Throwable $e){if(isset($db)&&$db->inTransaction())$db->rollBack();error_log('MineNight support request failed (private payload omitted)');output(['detail'=>'Support is temporarily unavailable. Please try again shortly.'],503);}
