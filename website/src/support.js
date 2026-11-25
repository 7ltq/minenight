(() => {
  'use strict';
  const $ = s => document.querySelector(s);
  const notice = $('#support-notice');
  const requestedApplication = new URLSearchParams(location.search).get('apply');
  const applicationChoiceKey = 'minenight.application-choice';
  function applicationChoice(value) {
    try { if (value !== undefined) value ? sessionStorage.setItem(applicationChoiceKey,value) : sessionStorage.removeItem(applicationChoiceKey); return sessionStorage.getItem(applicationChoiceKey); } catch { return requestedApplication; }
  }
  if (['staff','media'].includes(requestedApplication)) applicationChoice(requestedApplication);
  let session, ticketId, ticketData, stream, scope = 'mine', listBefore = null, lastMessage = 0;
  let refreshTimer, listTimer, pollTimer, connectTimer, listBusy = false, chatBusy = false;
  function stopLive() {
    stream?.close(); clearInterval(pollTimer); clearTimeout(connectTimer);
  }
  const date = n => new Date(n * 1000).toLocaleString(document.documentElement.lang, {dateStyle:'medium', timeStyle:'short'});
  function node(tag, className, content) {
    const e = document.createElement(tag);
    if (className) e.className = className;
    if (content !== undefined) e.textContent = content;
    return e;
  }
  function say(message) { notice.textContent = message; notice.hidden = !message; }
  async function api(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (options.method && options.method !== 'GET') headers.set('X-CSRF-Token', session?.csrf || '');
    const response = await fetch(path, {...options, headers, credentials:'same-origin', cache:'no-store'});
    let data;
    try { data = await response.json(); } catch { throw new Error('Support is temporarily unavailable. Please try again shortly.'); }
    if (!response.ok) {
      if (response.status === 401) { stopLive(); say('Your session has expired. Sign in with Discord again.'); }
      const error = new Error(typeof data.detail === 'string' ? data.detail : 'Please check your information and try again.'); error.status = response.status; throw error;
    }
    return data;
  }
  const json = data => ({method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(data)});
  function multipart(form) {
    const data = new FormData(form);
    const files = data.getAll('files');
    data.delete('files');
    for (const file of files) if (file.name) data.append('files[]', file);
    return data;
  }
  async function submit(form, work, label) {
    const button = form.querySelector('[type=submit]');
    const old = button.textContent;
    const error = form.querySelector('.form-error');
    error.textContent = '';
    button.disabled = true;
    button.textContent = label;
    try { await work(); } catch (e) { error.textContent = e.message; }
    finally { button.disabled = false; button.textContent = old; }
  }
  function badge(rank) { return node('span', 'rank-badge', rank || 'Member'); }
  function identity() {
    const user = session.user;
    const target = $('#support-identity');
    target.replaceChildren(badge(user.rank), node('strong', '', user.ign || user.discord_name), node('span', 'discord-name', `Discord · ${user.discord_name}`));
    $('#tab-team').hidden = !user.staff;
  }
  async function boot() {
    if (location.protocol === 'file:') {
      $('#support-loading').hidden = true;
      $('#support-login').hidden = false;
      $('#discord-login').href = 'http://127.0.0.1:4173/support.html';
      $('#discord-login').lastChild.textContent = 'Open local Support';
      say('Open the running MineNight website to sign in securely and use tickets. Start.ps1 opens it on this computer.');
      return;
    }
    try {
      session = await api('api/session');
      $('#retention-days').textContent = session.retention_days;
      $('#support-loading').hidden = true;
      $('#support-login').hidden = !!session.user;
      if (!session.available) {
        say('Support sign-in is not available yet. Please check back soon.');
        $('#discord-login').setAttribute('aria-disabled', 'true');
        $('#discord-login').removeAttribute('href');
      }
      if (!session.user) return;
      identity();
      $('#game-link-panel').hidden = false;
      let account = {link:null};
      try { account = await api('api/game-link'); } catch(e) { $('#game-link-status').textContent = e.message; }
      $('#game-link-status').textContent = account.link ? 'Verified Minecraft account: ' + account.link.ign : 'Your Minecraft account has not been verified yet.';
      $('#game-link-start').hidden = !!account.link;
      $('#profile-ign').value = session.user.ign;
      if (!session.user.ign) { $('#support-profile').hidden = false; return; }
      $('#support-profile').hidden = true;
      $('#support-workspace').hidden = false;
      await listTickets();
      if (!listTimer) listTimer = setInterval(() => { if (!document.hidden) listTickets().catch(e => say(e.message)); }, 15000);
      const requested = new URLSearchParams(location.search).get('ticket');
      if (requested && /^[a-f0-9]{24}$/.test(requested)) await openTicket(requested, false);
      else { const apply = requestedApplication || applicationChoice(); if (apply === 'staff' || apply === 'media') { newTicket(); applicationChoice(null); $('#create-ticket-form').elements.category.value = apply + '-application'; $('#create-ticket-form').dispatchEvent(new Event('change')); $('#ticket-subject').value = apply === 'staff' ? 'Staff Application' : 'Media Application'; $('#create-title').scrollIntoView({block:'start'}); } }
    } catch (e) {
      $('#support-loading').hidden = true;
      $('#support-login').hidden = false;
      say(e.message);
    }
  }
  async function listTickets(append = false) {
    if (listBusy) return;
    listBusy = true;
    try {
      const filter = $('#ticket-filter').value;
      const data = await api(`api/tickets?scope=${scope}&status=${filter}${append && listBefore ? '&before=' + listBefore : ''}`);
      const list = $('#ticket-list');
      if (!append) list.replaceChildren();
      for (const ticket of data.tickets) {
        const link = node('a', 'ticket-list-item');
        link.href = `support.html?ticket=${ticket.id}`;
        link.dataset.id = ticket.id;
        link.setAttribute('aria-current', String(ticket.id === ticketId));
        link.append(node('span', 'ticket-status' + (ticket.status === 'closed' ? ' is-closed' : ''), ticket.status), node('strong', '', ticket.subject), node('small', '', `${session.categories[ticket.category]} · ${ticket.ign}`));
        link.addEventListener('click', e => { e.preventDefault(); openTicket(ticket.id).catch(error => say(error.message)); });
        list.append(link);
      }
      if (!list.children.length) list.append(node('p', 'support-empty', scope === 'team' ? 'No tickets in this inbox.' : 'No tickets yet. Open one to get started.'));
      if (data.tickets.length) listBefore = data.tickets.at(-1).updated;
      $('#more-tickets').hidden = !data.more;
    } finally { listBusy = false; }
  }
  function renderMessage(message) {
    const article = node('article', 'chat-message' + (message.kind !== 'message' ? ' message-event' : ''));
    article.dataset.messageId = message.id;
    const meta = node('div', 'message-meta');
    const time = node('time', '', date(message.created));
    time.dateTime = new Date(message.created * 1000).toISOString();
    meta.append(badge(message.rank), node('strong', '', message.ign || message.discord_name), time);
    article.append(meta);
    const moderationLabels={appeal_accept:'Ban appeal accepted',appeal_reject:'Ban appeal rejected',appeal_unban:'Unban requested',unban_complete:'Unban confirmed by the Minecraft server.',unban_failed:'Unban failed: the ban changed or the server could not execute it. Review the current ban.'};
    if (message.kind !== 'message') article.append(node('p', 'support-fine', moderationLabels[message.kind] || (message.kind === 'closed' ? 'Closed this ticket' : 'Reopened this ticket')));
    article.append(node('p', 'message-body', message.body));
    article.append(node('span', 'message-discord', `Discord · ${message.discord_name}`));
    if (message.attachments.length) {
      const group = node('div', 'message-attachments');
      for (const attachment of message.attachments) {
        const box = node('div', 'attachment');
        const url = 'api/attachments/' + encodeURIComponent(attachment.id);
        if (attachment.mime.startsWith('image/')) {
          const image = node('img'); image.src = url; image.alt = attachment.name; image.loading = 'lazy';
          box.append(image);
        } else if (attachment.mime.startsWith('video/')) {
          const video = node('video'); video.src = url; video.controls = true; video.preload = 'metadata';
          video.setAttribute('aria-label', attachment.name); box.append(video);
        }
        const link = node('a', '', `${attachment.name} · ${(attachment.size / 1024 / 1024).toFixed(1)} MB`);
        link.href = url; link.target = '_blank'; link.rel = 'noopener noreferrer';
        box.append(link); group.append(box);
      }
      article.append(group);
    }
    return article;
  }
  function renderTicket(data, older = false) {
    const log = $('#chat-messages');
    const nearEnd = log.scrollHeight - log.scrollTop - log.clientHeight < 100;
    const oldHeight = log.scrollHeight;
    const oldTop = log.scrollTop;
    if (older) {
      const present = new Set([...log.children].map(x => x.dataset.messageId));
      const fragment = document.createDocumentFragment();
      data.messages.filter(m => !present.has(String(m.id))).forEach(m => fragment.append(renderMessage(m)));
      log.prepend(fragment); log.scrollTop = oldTop + log.scrollHeight - oldHeight;
      $('#older-messages').hidden = !data.has_older;
      return;
    }
    ticketData = data;
    const ticket = data.ticket;
    $('#chat-category').textContent = session.categories[ticket.category];
    $('#chat-title').textContent = ticket.subject;
    $('#chat-reference').textContent = `Ticket #${ticket.id.slice(-8).toUpperCase()} · Opened ${date(ticket.created)}`;
    const status = $('#chat-status'); status.textContent = ticket.status; status.classList.toggle('is-closed', ticket.status === 'closed');
    $('#closed-note').hidden = ticket.status !== 'closed';
    $('#closed-note').textContent = 'Ticket closed · ' + ticket.reason;
    $('#reply-form').hidden = ticket.status !== 'open';
    $('#manage-ticket').hidden = !data.can_manage;
    $('#delete-ticket').hidden = !data.can_manage || ticket.status !== 'closed';
    $('#manage-ticket').textContent = ticket.status === 'open' ? 'Close ticket' : 'Reopen ticket';
    // Preserve older history and append/update the server's latest messages.
    const present = new Set([...log.children].map(x => x.dataset.messageId));
    for (const m of data.messages) if (!present.has(String(m.id))) log.append(renderMessage(m));
    const newest = data.messages.at(-1)?.id || 0;
    if (newest > lastMessage && lastMessage) $('#chat-update').textContent = 'New message in this ticket.';
    if (nearEnd || !lastMessage) log.scrollTop = log.scrollHeight;
    lastMessage = Math.max(lastMessage, newest);
    if (!log.dataset.hasHistory) $('#older-messages').hidden = !data.has_older;
    say('');
  }
  async function refreshTicket() {
    if (!ticketId || chatBusy) return;
    const requested = ticketId;
    chatBusy = true;
    try {
      const data = await api('api/tickets/' + requested);
      if (ticketId === requested) { renderTicket(data); await applicationReview(data.ticket); await appealReview(data.ticket); }
    } catch(e) { if (e.status === 404 && ticketId === requested) { newTicket(); await listTickets(); say('This ticket has been deleted or is no longer available.'); } else throw e; } finally { chatBusy = false; }
  }
  async function openTicket(id, push = true) {
    stopLive(); clearTimeout(refreshTimer);
    ticketId = id; ticketData = null; lastMessage = 0;
    $('#chat-messages').replaceChildren(); delete $('#chat-messages').dataset.hasHistory;
    $('#ticket-create').hidden = true; $('#ticket-chat').hidden = false;
    $('#reply-form').reset(); $('#reply-file-count').textContent = '';
    if (push) history.pushState(null, '', '?ticket=' + id);
    await refreshTicket();
    if (ticketId !== id) return;
    $('#chat-title').setAttribute('tabindex', '-1'); $('#chat-title').focus({preventScroll:true});
    document.querySelectorAll('.ticket-list-item').forEach(a => a.setAttribute('aria-current', String(a.dataset.id === id)));
    stream = new EventSource('api/tickets/' + id + '/events');
    const poll = () => {
      stream?.close(); clearTimeout(connectTimer);
      if (ticketId !== id) return;
      clearInterval(pollTimer);
      const update = async () => {
        if (ticketId !== id || document.hidden) return;
        try { await refreshTicket(); if (ticketId === id) $('#live-state').textContent = 'Live conversation · updates every 3 seconds'; }
        catch { if (ticketId === id) $('#live-state').textContent = 'Connection interrupted · retrying'; }
      };
      update(); pollTimer = setInterval(update, 3000);
    };
    connectTimer = setTimeout(poll, 8000);
    stream.onopen = () => { clearTimeout(connectTimer); $('#live-state').textContent = 'Live conversation'; };
    stream.addEventListener('change', () => {
      clearTimeout(refreshTimer);
      refreshTimer = setTimeout(() => refreshTicket().catch(e => say(e.message)), 80);
    });
    stream.addEventListener('access-lost', () => {
      stopLive(); $('#live-state').textContent = 'Sign-in or access needs refreshing';
      $('#reply-form').hidden = true; $('#manage-ticket').hidden = true;
      say('Ticket access could not be verified. Reload to continue.');
      refreshTicket().catch(e => say(e.message));
    });
    stream.onerror = poll;
  }
  function newTicket() {
    stopLive(); ticketId = null; ticketData = null;
    $('#ticket-chat').hidden = true; $('#ticket-create').hidden = false;
    history.pushState(null, '', 'support.html');
    $('#create-title').setAttribute('tabindex', '-1'); $('#create-title').focus();
  }
  function checkFiles(input) {
    const files = [...input.files];
    if (files.length > 3 || files.some(f => f.size > 25 * 1024 * 1024) || files.reduce((n, f) => n + f.size, 0) > 31 * 1024 * 1024)
      throw new Error('Choose up to 3 files, at most 25 MB each and 31 MB combined (leaving room for your message).');
  }
  $('#profile-form').addEventListener('submit', e => {
    e.preventDefault(); submit(e.currentTarget, async () => {
      await api('api/profile', json({ign:$('#profile-ign').value}));
      await boot();
    }, 'Saving…');
  });
  $('#edit-ign').addEventListener('click', () => { $('#support-profile').hidden = false; $('#profile-ign').focus(); });
  $('#logout').addEventListener('click', async () => {
    try { await api('api/logout', {method:'POST'}); stopLive(); location.href = 'support.html'; } catch (e) { say(e.message); }
  });
  $('#new-ticket').addEventListener('click', newTicket);
  $('#ticket-filter').addEventListener('change', () => listTickets().catch(e => say(e.message)));
  for (const mode of ['mine','team']) $('#tab-' + mode).addEventListener('click', () => {
    scope = mode;
    for (const m of ['mine','team']) $('#tab-' + m).setAttribute('aria-pressed', String(mode === m));
    listTickets().catch(e => say(e.message));
  });
  $('#more-tickets').addEventListener('click', () => listTickets(true).catch(e => say(e.message)));
  let reviewTicket = null, reviewChecked = 0;
  async function appealReview(ticket) {
    let box=$('#appeal-review');
    if(!box){box=node('section','appeal-review');box.id='appeal-review';$('#chat-messages').before(box);}
    box.hidden=ticket.category!=='ban-appeal';if(box.hidden)return;
    const requested=ticket.id,data=await api('api/appeals/'+ticket.id);if(ticketId!==requested)return;
    const signature=JSON.stringify(data);if(box.dataset.revision===signature)return;box.dataset.revision=signature;box.replaceChildren();
    box.append(node('h3','','Ban appeal review'));
    if(data.review)box.append(node('p','',`Decision: ${data.review.decision} · ${data.review.state} · ${data.review.reason}`));
    if(data.can_review&&(!data.review||data.review.decision==='accept'&&data.review.state==='reviewed')){
      const form=node('form'),label=node('label','','Moderation reason'),reason=node('textarea');reason.id='appeal-reason';label.htmlFor=reason.id;reason.required=true;reason.minLength=3;reason.maxLength=1000;form.append(label,reason);
      const actions=data.review?['unban']:['accept','reject'];
      for(const action of actions){const button=node('button','button',({accept:'Accept appeal',reject:'Reject appeal',unban:'Unban player'})[action]);button.type='submit';button.value=action;form.append(button);}
      const error=node('p','form-error');error.setAttribute('role','alert');form.append(error);
      form.addEventListener('submit',async event=>{event.preventDefault();const buttons=[...form.querySelectorAll('button')];buttons.forEach(b=>b.disabled=true);try{await api('api/appeals/'+requested,json({action:event.submitter.value,reason:reason.value}));delete box.dataset.revision;await refreshTicket();}catch(e){error.textContent=e.message;}finally{buttons.forEach(b=>b.disabled=false);}});box.append(form);
    }
    if(data.audit.length){const history=node('details'),title=node('summary','','Moderation history');history.append(title);const labels={accept:'Ban appeal accepted',reject:'Ban appeal rejected',unban:'Unban requested',unban_complete:'Unban confirmed by the Minecraft server.',unban_failed:'Unban failed'};for(const entry of data.audit){const row=node('p'),staff=node('span','',entry.staff_name),reason=node('span','',entry.reason);staff.dataset.i18nSkip='';reason.dataset.i18nSkip='';row.append(staff,document.createTextNode(' · '),node('span','',labels[entry.action]||entry.action),document.createTextNode(' · '+date(entry.created)+' · '),reason);history.append(row);}box.append(history);}
  }
  async function applicationReview(ticket) {
    const panel = $('#application-review');
    if (!['staff-application','media-application'].includes(ticket.category)) { panel.hidden = true; return; }
    if (reviewTicket === ticket.id && Date.now() - reviewChecked < 15000) return;
    const changed = reviewTicket !== ticket.id;
    if (changed) { $('#application-state').textContent = 'Loading application review…'; $('#application-accept').hidden = true; $('#application-role').hidden = true; }
    panel.hidden = false;
    let data;
    try { data = await api('api/applications/' + ticket.id); }
    catch(e) { if (ticketId === ticket.id) $('#application-state').textContent = e.message; return; }
    reviewTicket = ticket.id; reviewChecked = Date.now();
    if (ticketId !== ticket.id) return;
    panel.hidden = false;
    $('#application-state').textContent = data.grant ? 'Role delivery: ' + data.grant.state + (data.grant.error ? ' — ' + data.grant.error : '') : data.linked ? 'Minecraft account verified. Ready for Owner review.' : 'The applicant must verify their Minecraft account before acceptance.';
    const select = $('#application-role'); const chosen = select.value; select.replaceChildren(...data.roles.map(role => { const option = node('option','',role[0].toUpperCase() + role.slice(1)); option.value = role; return option; }));
    if (!changed && data.roles.includes(chosen)) select.value = chosen;
    select.hidden = !data.can_accept;
    document.querySelector('label[for=application-role]').hidden = !data.can_accept;
    $('#application-accept').hidden = !data.can_accept;
    $('#application-accept').disabled = !data.linked || !!data.grant || ticket.status !== 'open';
  }
  $('#application-accept').addEventListener('click', async () => {
    const button = $('#application-accept'); button.disabled = true;
    try { await api('api/applications/' + ticketId, json({role:$('#application-role').value})); reviewChecked = 0; await refreshTicket(); }
    catch (e) { $('#application-state').textContent = e.message; button.disabled = false; }
  });
  $('#game-link-start').addEventListener('click', async () => {
    try { const data = await api('api/game-link',json({})); $('#game-link-command').textContent = data.command; $('#game-link-status').textContent = 'Run this command while connected to minenight.de. It expires in 10 minutes. Reload this page after linking.'; }
    catch(e) { $('#game-link-status').textContent = e.message; }
  });
  $('#create-ticket-form').addEventListener('change', () => {
    const media = $('#create-ticket-form').elements.category.value === 'media-application';
    $('#media-fields').hidden = !media;
    const staff = $('#create-ticket-form').elements.category.value === 'staff-application';
    $('#staff-fields').hidden = !staff; $('#staff-fields').disabled = !staff;
    $('#media-url').required = media; $('#media-confirm').required = media;
  });
  $('#create-ticket-form').addEventListener('submit', e => {
    e.preventDefault(); const form = e.currentTarget;
    submit(form, async () => {
      checkFiles($('#ticket-files'));
      const data = await api('api/tickets', {method:'POST', body:multipart(form)});
      form.reset(); $('#staff-fields').hidden = true; $('#staff-fields').disabled = true; $('#media-fields').hidden = true; $('#media-url').required = false; $('#media-confirm').required = false;
      await listTickets(); await openTicket(data.id);
    }, 'Creating ticket…');
  });
  $('#reply-files').addEventListener('change', () => { $('#reply-file-count').textContent = `${$('#reply-files').files.length} file(s) selected`; });
  $('#reply-form').addEventListener('submit', e => {
    e.preventDefault(); const form = e.currentTarget;
    submit(form, async () => {
      checkFiles($('#reply-files'));
      await api('api/tickets/' + ticketId + '/messages', {method:'POST', body:multipart(form)});
      form.reset(); $('#reply-file-count').textContent = '';
      await refreshTicket(); await listTickets(); $('#reply-body').focus();
    }, 'Sending…');
  });
  $('#older-messages').addEventListener('click', async () => {
    try {
      const before = $('#chat-messages').firstElementChild?.dataset.messageId;
      const data = await api('api/tickets/' + ticketId + '?before=' + before);
      renderTicket(data, true); $('#chat-messages').dataset.hasHistory = 'true';
    } catch (e) { say(e.message); }
  });
  const statusDialog = $('#status-dialog');
  let deletingTicket = false;
  $('#delete-ticket').addEventListener('click', () => {
    deletingTicket = true; $('#status-title').textContent = 'Delete ticket permanently?'; $('#status-submit').textContent = 'Delete permanently';
    $('#status-help').textContent = 'This permanently removes the conversation and all attachments. It cannot be reopened. No Discord DM is sent for permanent deletion. Granted ranks are not removed.';
    $('#status-form').reset(); $('#status-form .form-error').textContent = ''; statusDialog.showModal(); $('#status-reason').focus();
  });
  $('#manage-ticket').addEventListener('click', () => {
    deletingTicket = false; $('#status-help').textContent = 'The reason and your name will appear in the conversation. The creator will be notified by Discord DM if their settings allow it.';
    const label = ticketData.ticket.status === 'open' ? 'Close ticket' : 'Reopen ticket';
    $('#status-title').textContent = label; $('#status-submit').textContent = label;
    $('#status-form').reset(); $('#status-form .form-error').textContent = '';
    statusDialog.showModal(); $('#status-reason').focus();
  });
  $('#status-cancel').addEventListener('click', () => statusDialog.close());
  statusDialog.addEventListener('close', () => (ticketId ? $('#manage-ticket') : $('#new-ticket')).focus());
  $('#status-form').addEventListener('submit', e => {
    e.preventDefault(); submit(e.currentTarget, async () => {
      if (deletingTicket) {
        await api('api/tickets/' + ticketId, {...json({reason:$('#status-reason').value}),method:'DELETE'});
        newTicket(); statusDialog.close(); await listTickets(); say('Ticket permanently deleted.'); return;
      }
      await api('api/tickets/' + ticketId + '/status', json({status:ticketData.ticket.status === 'open' ? 'closed' : 'open', reason:$('#status-reason').value}));
      statusDialog.close(); await refreshTicket(); await listTickets();
    }, 'Saving…');
  });
  window.addEventListener('popstate', () => {
    const id = new URLSearchParams(location.search).get('ticket');
    if (id) openTicket(id, false).catch(e => say(e.message)); else newTicket();
  });
  window.addEventListener('pagehide', () => { stopLive(); clearInterval(listTimer); });
  const auth = new URLSearchParams(location.search).get('auth');
  if (auth) {
    say(({cancelled:'Discord sign-in was cancelled. You can try again below.', membership:'Join the MineNight Discord, then sign in again.', retry:'Discord sign-in could not be completed. Please try again.', unavailable:'Support sign-in is not available yet.'})[auth] || 'Please try signing in again.');
    history.replaceState(null, '', 'support.html');
  }
  boot();
})();
