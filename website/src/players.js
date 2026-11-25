(() => {
 'use strict';
 const $=s=>document.querySelector(s), root=$('[data-stats-page]');if(!root)return;
 const number=n=>Number(n).toLocaleString(document.documentElement.lang);
 const duration=s=>{const minutes=Math.floor(s/60),hours=Math.floor(minutes/60);return hours?number(hours)+'h '+minutes%60+'m':minutes+'m';};
 function message(text){$('#stats-message').textContent=text;$('#stats-message').hidden=!text;}
 async function api(path,signal){const r=await fetch(path,{signal,credentials:'omit',cache:'no-store'});let d;try{d=await r.json();}catch{throw Error('Player statistics are temporarily unavailable. Please try again.');}if(!r.ok)throw Error(d.detail||'Statistics could not be loaded.');return d;}
 function freshness(d){$('#stats-freshness').textContent=d.last_sync?'Last server sync: '+new Date(d.last_sync*1000).toLocaleString(document.documentElement.lang)+(d.stale?' · Showing the last available snapshot.':' · Updates about once a minute.'):'Waiting for the first server statistics sync.';}
 function playerLink(p){const a=document.createElement('a');a.href='players.html?player='+encodeURIComponent(p.name);const image=document.createElement('img');image.src='api/skins/'+encodeURIComponent(p.uuid)+'/head';image.alt='';image.width=36;image.height=36;image.loading='lazy';image.className='skin-head';image.addEventListener('error',()=>image.remove(),{once:true});a.classList.add('player-link');a.append(image,document.createTextNode(p.name));return a;}
 let searchRequest,leaderboardRequest,profileRequest;
 $('#player-search').addEventListener('submit',async event=>{
  event.preventDefault();const q=$('#player-query').value.trim();if(!/^[A-Za-z0-9_]{1,16}$/.test(q)){message('Enter a Minecraft username using letters, numbers or underscores.');return;}
  searchRequest?.abort();const request=new AbortController();searchRequest=request;message('Searching players…');$('#search-results').replaceChildren();
  try{const d=await api('api/players?q='+encodeURIComponent(q),request.signal);if(searchRequest!==request)return;freshness(d);message(d.players.length?'':'No matching MineNight players found. Try another username.');for(const p of d.players){const a=playerLink(p);a.className='search-result';$('#search-results').append(a);}if(root.dataset.statsPage==='players'&&d.players.length===1){history.replaceState(null,'','players.html?player='+encodeURIComponent(d.players[0].name));await profile(d.players[0].name);}}
  catch(error){if(error.name!=='AbortError')message(error.message);}
 });
 async function leaderboard(metric){
  leaderboardRequest?.abort();const request=new AbortController();leaderboardRequest=request;
  const titles={kills:'Most kills',deaths:'Most deaths',playtime:'Most time played'},labels={kills:'Kills',deaths:'Deaths',playtime:'Playtime'};
  $('#leaderboard-title').textContent=titles[metric];$('#value-heading').textContent=labels[metric];$('#leaderboard-rows').replaceChildren();$('#leaderboard-empty').hidden=true;
  for(const button of document.querySelectorAll('[data-metric]'))button.setAttribute('aria-pressed',String(button.dataset.metric===metric));message('Loading leaderboard…');
  try{const d=await api('api/leaderboard?metric='+metric,request.signal);if(leaderboardRequest!==request)return;freshness(d);message('');$('#player-total').textContent=number(d.total)+' players';$('#leaderboard-empty').hidden=!!d.players.length;
   d.players.forEach((p,index)=>{const row=document.createElement('tr'),rank=document.createElement('td'),name=document.createElement('td'),value=document.createElement('td');rank.textContent='#'+(p.rank||index+1);name.append(playerLink(p));value.textContent=metric==='playtime'?duration(p.playtime_seconds):number(p[metric]);row.append(rank,name,value);$('#leaderboard-rows').append(row);});
  }catch(error){if(error.name!=='AbortError')message(error.message);}
 }
 async function profile(name){
  profileRequest?.abort();const request=new AbortController();profileRequest=request;message('Loading player profile…');$('#player-profile').hidden=true;
  try{const d=await api('api/players/'+encodeURIComponent(name),request.signal);if(profileRequest!==request)return;const p=d.player;freshness(d);message('');$('#player-profile').hidden=false;$('#profile-placeholder').hidden=true;$('#profile-name').textContent=p.name;const skin=$('#player-skin');skin.hidden=false;skin.src='api/skins/'+encodeURIComponent(p.uuid)+'/body';skin.alt=p.name+' Minecraft skin';skin.onerror=()=>{skin.hidden=true;};$('#profile-kills').textContent=number(p.kills);$('#profile-deaths').textContent=number(p.deaths);$('#profile-playtime').textContent=duration(p.playtime_seconds);for(const [id,key] of [['kills','kills'],['deaths','deaths'],['playtime','playtime_seconds']])$('#rank-'+id).textContent='Leaderboard rank #'+number(d.ranks[key]);document.title=p.name+' · MineNight Player';}
  catch(error){if(error.name!=='AbortError'){message(error.message);$('#profile-placeholder').hidden=false;}}
 }
 for(const button of document.querySelectorAll('[data-metric]'))button.addEventListener('click',()=>leaderboard(button.dataset.metric));
 if(root.dataset.statsPage==='leaderboard')leaderboard('kills');
 else{const name=new URLSearchParams(location.search).get('player');if(name&&/^[A-Za-z0-9_]{3,16}$/.test(name)){$('#player-query').value=name;profile(name);}else $('#stats-freshness').textContent='Search for a player to load their latest server snapshot.';}
})();
