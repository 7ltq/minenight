(() => {
  'use strict';
  const key='minenight.language',records=new WeakMap(),attributes=new WeakMap();
  let language='en',dictionary={};
  try{language=localStorage.getItem(key)==='de'?'de':'en';}catch{}
  const normalize=text=>text.replace(/\s+/g,' ').trim();
  const excluded=node=>node.parentElement?.closest('script,style,code,textarea,[data-i18n-skip],.message-body,.message-discord,.message-meta strong,.ticket-list-item strong,#chat-title,#profile-name,.player-link,#support-identity strong,.discord-name,.rank-badge');
  function translate(value){
    if(language==='en')return value;
    const clean=normalize(value),translated=dictionary[clean];
    if(translated)return value.replace(clean,translated)||translated;
    for(const [pattern,replacement] of patterns){const match=clean.match(pattern);if(match)return replacement(...match.slice(1));}
    return value;
  }
  const patterns=[
    [/^(\d+) players online · Click to copy$/,n=>n+' Spieler online · Zum Kopieren klicken'],
    [/^Role delivery: (.*)$/,state=>'Rangvergabe: '+state.replace('complete','abgeschlossen').replace('queued','ausstehend').replace('failed','fehlgeschlagen')],
    [/^(\d+) players$/,n=>n+' Spieler'],
    [/^Leaderboard rank #(.*)$/,n=>'Ranglistenplatz #'+n],
    [/^Last server sync: (.*)$/,v=>'Letzte Serversynchronisierung: '+v.replace('Showing the last available snapshot.','Letzter verfügbarer Stand.').replace('Updates about once a minute.','Aktualisierung etwa jede Minute.')],
    [/^Ticket #(.*?) · Opened (.*)$/, (id,date)=>'Ticket #'+id+' · Erstellt '+date],
    [/^(\d+) file\(s\) selected$/,n=>n+' Datei(en) ausgewählt'],
    [/^Ticket closed · (.*)$/,reason=>'Ticket geschlossen · '+reason],
    [/^Decision: (accept|reject) · (reviewed|queued|complete|failed) · (.*)$/, (decision,state,reason)=>'Entscheidung: '+({accept:'angenommen',reject:'abgelehnt'})[decision]+' · '+({reviewed:'geprüft',queued:'ausstehend',complete:'abgeschlossen',failed:'fehlgeschlagen'})[state]+' · '+reason],
    [/^(.+) Minecraft skin$/,name=>name+' Minecraft-Skin'],
    [/^MineNight · (.*)$/,title=>'MineNight · '+(dictionary[title]||title)],
    [/^(.+) · MineNight Player$/,name=>name+' · MineNight-Spieler']
  ];
  function textNode(node){
    if(excluded(node))return;
    const previous=records.get(node),current=node.nodeValue;
    const original=previous&&current===previous.rendered?previous.original:current;
    const clean=normalize(original),replacement=translate(clean),rendered=replacement===clean?original:original.replace(/\S(?:[\s\S]*\S)?/,replacement);
    records.set(node,{original,rendered});if(current!==rendered)node.nodeValue=rendered;
  }
  function walk(root){
    if(root.nodeType===Node.TEXT_NODE){textNode(root);return;}
    if(root.nodeType!==Node.ELEMENT_NODE&&root.nodeType!==Node.DOCUMENT_NODE)return;
    const nodes=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);let node;while((node=nodes.nextNode()))textNode(node);
    const elements=[...(root.nodeType===Node.ELEMENT_NODE?[root]:[]),...root.querySelectorAll('[placeholder],[aria-label],[title],img[alt]')];
    for(const element of elements){
      if(element.closest('[data-i18n-skip]'))continue;
      const previous=attributes.get(element)||{};
      for(const attr of ['placeholder','aria-label','title','alt']){if(!element.hasAttribute(attr))continue;const current=element.getAttribute(attr),old=previous[attr],original=old&&old.rendered===current?old.original:current,rendered=translate(original);previous[attr]={original,rendered};if(current!==rendered)element.setAttribute(attr,rendered);}
      attributes.set(element,previous);
    }
  }
  function apply(){document.documentElement.lang=language;walk(document);if(picker)picker.value=language;}
  const picker=document.createElement('select');picker.className='language-picker';picker.setAttribute('aria-label','Language / Sprache');
  picker.innerHTML='<option value="en">EN · English</option><option value="de">DE · Deutsch</option>';picker.value=language;
  (document.querySelector('.nav-actions')||document.body).append(picker);
  picker.addEventListener('change',()=>{language=picker.value;try{localStorage.setItem(key,language);}catch{}apply();window.dispatchEvent(new Event('languagechange'));});
  new MutationObserver(changes=>{
    for(const change of changes){if(change.type==='characterData')textNode(change.target);else if(change.type==='attributes')walk(change.target);else for(const node of change.addedNodes)walk(node);}
  }).observe(document.documentElement,{subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:['placeholder','aria-label','title','alt']});
  window.MNI18n={translate,get language(){return language;}};
  fetch(new URL('i18n-de.json',document.currentScript?.src||location.href),{cache:'no-cache'}).then(r=>{if(!r.ok)throw Error();return r.json();}).then(data=>{dictionary=data;apply();}).catch(()=>{language='en';apply();});
  document.documentElement.lang=language;
})();
