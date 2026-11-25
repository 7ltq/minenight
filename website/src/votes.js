(() => {
  'use strict';
  const status=document.querySelector('#votes-status'),list=document.querySelector('#latest-votes');
  if(!status||!list)return;
  fetch('api/votes',{credentials:'omit',cache:'no-store'}).then(async response=>{
    if(!response.ok)throw Error();
    const data=await response.json();
    status.textContent=data.votes.length?'Only confirmed server votes are shown.':'No confirmed votes yet.';
    for(const vote of data.votes){
      const row=document.createElement('li'),link=document.createElement('a'),image=document.createElement('img'),platform=document.createElement('span'),time=document.createElement('time');
      link.href='players.html?player='+encodeURIComponent(vote.name);if(vote.uuid)image.src='api/skins/'+encodeURIComponent(vote.uuid)+'/head';image.width=36;image.height=36;image.alt='';image.loading='lazy';image.onerror=()=>image.remove();if(vote.uuid)link.append(image);link.append(document.createTextNode(vote.name));link.className='player-link';
      platform.textContent=vote.platform||'Unknown platform';time.dateTime=new Date(vote.confirmed*1000).toISOString();
      const update=()=>{time.textContent=new Date(vote.confirmed*1000).toLocaleString(document.documentElement.lang,{dateStyle:'medium',timeStyle:'short'});};update();window.addEventListener('languagechange',update);
      row.append(link,platform,time);list.append(row);
    }
  }).catch(()=>{status.textContent='Confirmed votes are temporarily unavailable. Please try again later.';});
})();
