<?php
// Public gameplay snapshots only: no Discord IDs, IPs, chat or moderation data.
function playerStatsTables(): void {
 global $db;
 $db->exec('CREATE TABLE IF NOT EXISTS player_stats(uuid TEXT PRIMARY KEY,ign TEXT NOT NULL COLLATE NOCASE,kills INTEGER NOT NULL,deaths INTEGER NOT NULL,playtime_seconds INTEGER NOT NULL,updated INTEGER NOT NULL); CREATE INDEX IF NOT EXISTS stats_name ON player_stats(ign); CREATE INDEX IF NOT EXISTS stats_kills ON player_stats(kills DESC); CREATE INDEX IF NOT EXISTS stats_deaths ON player_stats(deaths DESC); CREATE INDEX IF NOT EXISTS stats_time ON player_stats(playtime_seconds DESC);');
}
function publicPlayer(array $p): array {
 return ['uuid'=>$p['uuid'],'name'=>$p['ign'],'kills'=>(int)$p['kills'],'deaths'=>(int)$p['deaths'],'playtime_seconds'=>(int)$p['playtime_seconds'],'updated'=>(int)$p['updated'],'victory_royales'=>null,'event_wins'=>null];
}
function syncPlayerStats(): never {
 global $db;
 bridgeAuth();$b=body();$players=$b['players']??null;
 if(!is_array($players)||count($players)>200)fail(422,'Supply at most 200 player snapshots.');
 $captured=$b['captured_at']??null;
 if(!is_int($captured)||abs(time()-$captured)>600)fail(422,'Snapshot timestamp is invalid or expired.');
 $valid=[];
 foreach($players as $p){
  if(!is_array($p))fail(422,'Invalid player snapshot.');
  $uuid=strtolower((string)($p['uuid']??''));$name=$p['name']??'';
  if(!preg_match('/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/D',$uuid)||!is_string($name)||!preg_match('/^[A-Za-z0-9_]{3,16}$/D',$name))fail(422,'Invalid player identity.');
  foreach(['kills','deaths','playtime_seconds'] as $key)if(!isset($p[$key])||!is_int($p[$key])||$p[$key]<0||$p[$key]>1000000000000)fail(422,'Invalid gameplay statistic.');
  $valid[]=[$uuid,$name,$p['kills'],$p['deaths'],$p['playtime_seconds'],$captured];
 }
 $db->exec('BEGIN IMMEDIATE');
 foreach($valid as $v)sql('INSERT INTO player_stats VALUES (?,?,?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET ign=excluded.ign,kills=excluded.kills,deaths=excluded.deaths,playtime_seconds=excluded.playtime_seconds,updated=excluded.updated WHERE excluded.updated>=player_stats.updated',$v);
 sql("INSERT INTO meta(key,value,expires) VALUES ('player-stats-sync',?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value,expires=excluded.expires WHERE CAST(excluded.value AS INTEGER)>=CAST(meta.value AS INTEGER)",[(string)$captured,now()+180]);
 $db->exec('COMMIT');output(['ok'=>true,'received'=>count($valid)]);
}
function playerStatsRoutes(string $path,string $method): void {
 if($path==='/api/application-bridge/stats'&&$method==='POST')syncPlayerStats();
 if($method!=='GET')return;
 $sync=one("SELECT value,expires FROM meta WHERE key='player-stats-sync'");
 $meta=['last_sync'=>$sync?(int)$sync['value']:null,'stale'=>!$sync||$sync['expires']<now()];
 if($path==='/api/leaderboard'){
  $metric=$_GET['metric']??'kills';$columns=['kills'=>'kills','deaths'=>'deaths','playtime'=>'playtime_seconds'];
  if(!isset($columns[$metric]))fail(422,'Choose kills, deaths or playtime.');
  $column=$columns[$metric];$rows=sql("SELECT * FROM player_stats ORDER BY $column DESC,ign COLLATE NOCASE,uuid LIMIT 50")->fetchAll();
  $players=[];$previous=null;$rank=0;foreach($rows as $i=>$row){if($previous!==$row[$column])$rank=$i+1;$previous=$row[$column];$players[]=[...publicPlayer($row),'rank'=>$rank];}
  output(['metric'=>$metric,'players'=>$players,'total'=>(int)one('SELECT count(*) n FROM player_stats')['n'],...$meta]);
 }
 if($path==='/api/players'){
  $q=trim($_GET['q']??'');if(!preg_match('/^[A-Za-z0-9_]{1,16}$/D',$q))fail(422,'Enter a Minecraft username (1–16 letters, numbers or underscores).');
  $prefix=str_replace(['!','_'],['!!','!_'],$q).'%';
  $rows=sql("SELECT * FROM player_stats WHERE ign LIKE ? ESCAPE '!' ORDER BY CASE WHEN ign=? COLLATE NOCASE THEN 0 ELSE 1 END,ign COLLATE NOCASE,uuid LIMIT 20",[$prefix,$q])->fetchAll();
  output(['players'=>array_map('publicPlayer',$rows),...$meta]);
 }
 if(preg_match('~^/api/players/([A-Za-z0-9_-]{3,36})$~D',$path,$m)){
  $player=one('SELECT * FROM player_stats WHERE uuid=? OR ign=? COLLATE NOCASE ORDER BY updated DESC LIMIT 1',[$m[1],$m[1]]);
  if(!$player)fail(404,'This player has no synchronized MineNight statistics yet.');
  $ranks=[];foreach(['kills','deaths','playtime_seconds'] as $column)$ranks[$column]=1+(int)one("SELECT count(*) n FROM player_stats WHERE $column>?",[$player[$column]])['n'];
  output(['player'=>publicPlayer($player),'ranks'=>$ranks,...$meta]);
 }
}
