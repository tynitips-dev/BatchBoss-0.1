/* BatchBoss marketing website: optional GA4 analytics, loaded only after consent. */
(function () {
  'use strict';
  const key = 'batchboss_analytics_consent';
  const id = 'G-DKPHWXB2CD';
  function enable() {
    if (window.__batchbossGaStarted) return;
    window.__batchbossGaStarted = true;
    window.dataLayer = window.dataLayer || [];
    window.gtag = function () { window.dataLayer.push(arguments); };
    window.gtag('js', new Date());
    window.gtag('config', id, { send_page_view: true });
    const script = document.createElement('script');
    script.async = true;
    script.src = 'https://www.googletagmanager.com/gtag/js?id=' + encodeURIComponent(id);
    document.head.appendChild(script);
  }
  try {
    if (localStorage.getItem(key) === 'yes') { enable(); return; }
    if (localStorage.getItem(key) === 'no') return;
  } catch (_) { return; }
  function showConsent() {
    const box = document.createElement('div');
    box.setAttribute('role', 'region');
    box.setAttribute('aria-label', 'Optional website analytics');
    box.style.cssText = 'position:fixed;bottom:16px;left:16px;right:16px;margin:auto;max-width:590px;background:#14213c;color:white;padding:18px;border-radius:14px;z-index:99999;box-shadow:0 12px 36px #0005;font:inherit';
    const title = document.createElement('strong');
    title.textContent = 'Help us improve BatchBoss';
    const message = document.createElement('p');
    message.textContent = 'May we collect anonymous website-usage statistics? You can continue without accepting.';
    const actions = document.createElement('div');
    actions.style.cssText = 'display:flex;gap:10px;justify-content:flex-end';
    for (const option of [{label:'Not now', value:'no'}, {label:'Allow analytics', value:'yes'}]) {
      const button = document.createElement('button');
      button.type = 'button';
      button.textContent = option.label;
      button.style.cssText = 'padding:9px 14px;cursor:pointer;border-radius:7px';
      button.addEventListener('click', function () {
        try { localStorage.setItem(key, option.value); } catch (_) {}
        box.remove();
        if (option.value === 'yes') enable();
      });
      actions.appendChild(button);
    }
    box.append(title, message, actions);
    document.body.appendChild(box);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', showConsent, {once:true});
  else showConsent();
})();
