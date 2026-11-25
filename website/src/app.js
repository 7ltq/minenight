(() => {
  'use strict';
  const ip = 'minenight.de';
  const menuButton = document.querySelector('.menu-toggle');
  const navigation = document.querySelector('#navigation');
  const dialog = document.querySelector('.info-dialog');
  const toast = document.querySelector('.toast');
  let toastTimer;
  let dialogOpener;

  const dialogCopy = {
    copy: { icon: 'copy', kicker: 'JOIN MINENIGHT', title: 'Your next match awaits.', body: '<p>Select the address below and copy it with your device\'s copy command.</p><label class="copy-label" for="manual-server-ip">Server address</label><input class="copy-address" id="manual-server-ip" type="text" value="minenight.de" readonly spellcheck="false" autocomplete="off"><p class="join-instructions">In Minecraft Java Edition, choose <strong>Multiplayer</strong>, then <strong>Add Server</strong>, paste the address and join.</p>' },
    store: { icon: 'bag', kicker: 'THE MINENIGHT STORE', title: 'Good things are coming.', body: '<p><strong>Store Coming Soon.</strong> MineNight ranks are on their way.</p><p>Purchases are not available yet. Join our <a href="https://discord.com/invite/mXjqNYF4b6" target="_blank" rel="noopener noreferrer">Discord community</a> for store announcements.</p>' },
    legal: { icon: "shield", kicker: "MINENIGHT", title: "Legal Notice", body: '<p>Operator information and official contact details: <a href="legal-notice.html">Legal Notice (Impressum)</a>.</p><p>Contact: <a href="mailto:contact@minenight.de">contact@minenight.de</a>.</p>' },
    privacy: { icon: "shield", kicker: "YOUR VISIT", title: "Privacy Policy", body: '<p>Read our <a href="privacy.html">Privacy Policy</a> for hosting, Discord sign-in, cookies, player data, Support and your rights.</p><p>Privacy contact: <a href="mailto:contact@minenight.de">contact@minenight.de</a>.</p>' }
  };

  function setMenu(open, restoreFocus = false) {
    if (!menuButton || !navigation) return;
    menuButton.setAttribute('aria-expanded', String(open));
    menuButton.setAttribute('aria-label', open ? 'Close navigation' : 'Open navigation');
    menuButton.querySelector('use').setAttribute('href', `assets/icons.svg#${open ? 'close' : 'menu'}`);
    navigation.classList.toggle('is-open', open);
    if (restoreFocus) menuButton.focus();
  }
  menuButton?.addEventListener('click', () => setMenu(menuButton.getAttribute('aria-expanded') !== 'true'));
  navigation?.querySelectorAll('a').forEach(link => link.addEventListener('click', () => {
    setMenu(false);
    if (!link.getAttribute('href')?.startsWith('#')) return;
    const target = document.getElementById(link.hash.slice(1));
    if (!target) return;
    if (!target.hasAttribute('tabindex')) {
      target.setAttribute('tabindex', '-1');
      target.addEventListener('blur', () => target.removeAttribute('tabindex'), {once: true});
    }
    target.focus({preventScroll: true});
  }));
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && !dialog?.open && menuButton?.getAttribute('aria-expanded') === 'true') setMenu(false, true);
  });
  matchMedia('(min-width: 601px)').addEventListener('change', event => { if (event.matches) setMenu(false); });

  function notify(message) {
    clearTimeout(toastTimer);
    toast.textContent = message;
    toast.classList.add('show');
    toastTimer = setTimeout(() => toast.classList.remove('show'), 3500);
  }

  async function copyIP() {
    let copied = false;
    const previousFocus = document.activeElement;
    try {
      await navigator.clipboard.writeText(ip);
      copied = true;
    } catch {
      const field = document.createElement('textarea');
      field.value = ip;
      field.setAttribute('readonly', '');
      field.setAttribute('aria-label', 'MineNight server IP');
      Object.assign(field.style, {position: 'fixed', top: '-1000px', left: '0'});
      document.body.append(field);
      field.select();
      try { copied = document.execCommand('copy'); } catch { /* The IP stays visible when clipboard access is unavailable. */ }
      field.remove();
      previousFocus?.focus({preventScroll: true});
    }
    if (copied) notify('Server IP copied: minenight.de');
    else openDialog('copy', previousFocus);
  }
  document.querySelectorAll('[data-copy-ip]').forEach(button => button.addEventListener('click', copyIP));

  function openDialog(key, opener) {
    const content = dialogCopy[key];
    if (!content || !dialog) return;
    dialogOpener = opener;
    document.querySelector('#dialog-title').textContent = content.title;
    document.querySelector('#dialog-kicker').textContent = content.kicker;
    document.querySelector('#dialog-content').innerHTML = content.body;
    dialog.querySelector('.dialog-symbol use').setAttribute('href', `assets/icons.svg#${content.icon}`);
    dialog.showModal();
    const address = dialog.querySelector('.copy-address');
    if (address) {
      address.addEventListener('mouseup', event => event.preventDefault());
      address.addEventListener('click', () => address.select());
      address.focus();
      address.select();
    } else dialog.querySelector('.dialog-close').focus();
  }
  document.querySelectorAll('[data-dialog]').forEach(button => button.addEventListener('click', () => openDialog(button.dataset.dialog, button)));
  dialog?.querySelectorAll('.dialog-close, .dialog-done').forEach(button => button.addEventListener('click', () => dialog.close()));
  dialog?.addEventListener('click', event => {
    if (event.target !== dialog) return;
    const bounds = dialog.getBoundingClientRect();
    if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) dialog.close();
  });
  dialog?.addEventListener('close', () => dialogOpener?.focus({preventScroll: true}));
  dialog?.addEventListener('keydown', event => {
    if (event.key !== 'Tab') return;
    const controls = [...dialog.querySelectorAll('button, a[href], input, select, textarea, [tabindex="0"]')].filter(control => !control.disabled && control.getClientRects().length);
    const first = controls[0], last = controls[controls.length - 1];
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  });

  // A failed third-party request never changes the address or invents a player count.
  const status = document.querySelector('[data-server-status]');
  async function updateServerStatus() {
    if (!status || document.hidden) return;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 6000);
    try {
      const response = await fetch('https://api.mcstatus.io/v2/status/java/minenight.de', {signal: controller.signal, credentials: 'omit', referrerPolicy: 'no-referrer'});
      if (!response.ok) throw new Error('Status unavailable');
      const data = await response.json();
      status.textContent = data.online === true ? `${Number.isFinite(data.players?.online) ? data.players.online + ' players online · ' : ''}Click to copy` : 'Server currently offline · Click to copy';
    } catch { status.textContent = 'Click to copy the server IP'; }
    finally { clearTimeout(timeout); }
  }
  updateServerStatus();
  setInterval(updateServerStatus, 90000);
  document.querySelectorAll('[data-year]').forEach(element => { element.textContent = String(new Date().getFullYear()); });
})();
