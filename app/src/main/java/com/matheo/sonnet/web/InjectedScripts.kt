package com.matheo.sonnet.web

/**
 * JavaScript injecté dans Jellyfin web (10.11) :
 *  1. repère la dernière requête PlaybackInfo (id, position de reprise, pistes choisies, jeton)
 *  2. quand le lecteur web s'ouvre (#/video), le coupe et passe la main au lecteur natif Sonnet
 *  3. transmet la session (serveur + jeton + utilisateur) pour les écrans natifs
 *  4. applique le thème violet selon la page (réglages / admin / partout)
 *
 * Écrit sans "$" pour rester une chaîne Kotlin brute sans gabarit.
 */
object InjectedScripts {

    val CORE: String = """
(function () {
  if (window.__sonnet) { try { window.__sonnetRoute && window.__sonnetRoute(); } catch (e) {} return; }
  var S = window.SonnetNative;
  if (!S) return;
  var state = { last: null, session: '', handing: false };
  window.__sonnet = state;

  function lowerHeaders(h) {
    var o = {};
    if (!h) return o;
    try {
      if (typeof Headers !== 'undefined' && h instanceof Headers) {
        h.forEach(function (v, k) { o[String(k).toLowerCase()] = v; });
      } else if (Array.isArray(h)) {
        h.forEach(function (p) { o[String(p[0]).toLowerCase()] = p[1]; });
      } else {
        Object.keys(h).forEach(function (k) { o[k.toLowerCase()] = h[k]; });
      }
    } catch (e) {}
    return o;
  }

  function tokenFrom(h) {
    var a = h['authorization'] || h['x-emby-authorization'] || '';
    var m = /Token="([^"]+)"/i.exec(a);
    if (m) return m[1];
    return h['x-emby-token'] || h['x-mediabrowser-token'] || null;
  }

  function apiToken() {
    try { if (window.ApiClient && ApiClient.accessToken) return ApiClient.accessToken(); } catch (e) {}
    return null;
  }

  function apiServer() {
    try { if (window.ApiClient && ApiClient.serverAddress) return ApiClient.serverAddress(); } catch (e) {}
    return null;
  }

  function record(url, method, headers, body) {
    try {
      if (!url || String(method || 'GET').toUpperCase() !== 'POST') return;
      var abs = new URL(String(url), location.href).href;
      var m = /^(.*?)\/Items\/([0-9a-fA-F-]{32,36})\/PlaybackInfo/i.exec(abs);
      if (!m) return;
      var b = {};
      if (typeof body === 'string') { try { b = JSON.parse(body) || {}; } catch (e) { b = {}; } }
      var q = new URL(abs).searchParams;
      var pick = function (name) {
        if (b[name] !== undefined && b[name] !== null) return b[name];
        var v = q.get(name);
        return v === null ? null : v;
      };
      var isPlayback = pick('IsPlayback');
      if (isPlayback === false || isPlayback === 'false') return;
      state.last = {
        itemId: m[2].replace(/-/g, ''),
        server: m[1],
        token: tokenFrom(lowerHeaders(headers)) || apiToken(),
        startTicks: Number(pick('StartTimeTicks') || 0),
        mediaSourceId: pick('MediaSourceId'),
        audio: pick('AudioStreamIndex'),
        sub: pick('SubtitleStreamIndex'),
        t: Date.now(),
        used: false
      };
    } catch (e) {}
  }

  // --- Écoute des requêtes (le SDK utilise XHR via axios, l'ancien client utilise fetch)
  try {
    var XO = XMLHttpRequest.prototype.open;
    var XS = XMLHttpRequest.prototype.send;
    var XH = XMLHttpRequest.prototype.setRequestHeader;
    XMLHttpRequest.prototype.open = function (method, url) {
      this.__sonnet = { m: method, u: url, h: {} };
      return XO.apply(this, arguments);
    };
    XMLHttpRequest.prototype.setRequestHeader = function (k, v) {
      if (this.__sonnet) this.__sonnet.h[String(k).toLowerCase()] = v;
      return XH.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function (body) {
      var s = this.__sonnet;
      if (s) record(s.u, s.m, s.h, body);
      return XS.apply(this, arguments);
    };
  } catch (e) {}

  try {
    var F = window.fetch;
    if (F) {
      window.fetch = function (input, init) {
        try {
          var url = typeof input === 'string' ? input : (input && input.url) || String(input);
          var method = (init && init.method) || (input && input.method) || 'GET';
          var headers = (init && init.headers) || (input && input.headers);
          record(url, method, headers, init && init.body);
        } catch (e) {}
        return F.apply(window, arguments);
      };
    }
  } catch (e) {}

  // --- Passage au lecteur natif quand Jellyfin ouvre son lecteur
  function leaveVideoRoute() {
    if ((location.hash || '').indexOf('#/video') === 0) history.back();
    setTimeout(function () {
      if ((location.hash || '').indexOf('#/video') === 0) history.back();
      state.handing = false;
    }, 1600);
  }

  function handoff() {
    var h = location.hash || '';
    if (h.indexOf('#/video') !== 0 || state.handing) return;
    var info = state.last;
    if (!info || info.used || Date.now() - info.t > 45000) return;
    info.used = true;
    var enabled = false;
    try { enabled = S.shouldIntercept(); } catch (e) {}
    if (!enabled) return;
    state.handing = true;
    try {
      document.querySelectorAll('video').forEach(function (v) {
        try { v.muted = true; v.pause(); } catch (e) {}
      });
    } catch (e) {}
    if (!info.token) info.token = apiToken();
    if (!info.server) info.server = apiServer();
    try { S.playVideo(JSON.stringify(info)); } catch (e) {}
    setTimeout(leaveVideoRoute, 200);
  }

  // --- Thème violet selon la page
  function theme() {
    var mode = window.__sonnetThemeMode || 0;
    var h = location.hash || '';
    var settings = /^#\/(dashboard|mypreferences|userprofile|configurationpage|metadata|quickconnect|wizard)/.test(h);
    var root = document.documentElement;
    if (!root || !root.classList) return;
    root.classList.toggle('sonnet-themed', mode === 2 || (mode === 1 && settings));
    root.classList.toggle('sonnet-admin', settings);
  }

  function route() { handoff(); theme(); }
  window.__sonnetRoute = route;

  window.__sonnetSetTheme = function (mode, css) {
    window.__sonnetThemeMode = mode;
    var el = document.getElementById('sonnet-theme');
    if (!el) {
      el = document.createElement('style');
      el.id = 'sonnet-theme';
      (document.head || document.documentElement).appendChild(el);
    }
    el.textContent = css;
    theme();
  };

  try {
    var PS = history.pushState;
    var RS = history.replaceState;
    history.pushState = function () { var r = PS.apply(this, arguments); setTimeout(route, 0); return r; };
    history.replaceState = function () { var r = RS.apply(this, arguments); setTimeout(route, 0); return r; };
  } catch (e) {}
  window.addEventListener('hashchange', route);
  window.addEventListener('popstate', route);
  setInterval(route, 400);

  // --- Session pour les écrans natifs (bibliothèques…)
  function session() {
    var token = apiToken();
    var server = apiServer();
    var user = null;
    try { if (window.ApiClient && ApiClient.getCurrentUserId) user = ApiClient.getCurrentUserId(); } catch (e) {}
    if (!token || !user) {
      try {
        var c = JSON.parse(localStorage.getItem('jellyfin_credentials') || '{}');
        var s = (c.Servers || []).filter(function (x) { return x.AccessToken && x.UserId; })[0];
        if (s) {
          token = s.AccessToken;
          user = s.UserId;
          server = server || s.ManualAddress || s.LocalAddress || s.RemoteAddress;
        }
      } catch (e) {}
    }
    if (!token || !user) {
      if (state.session !== '') { state.session = ''; try { S.onSession('{}'); } catch (e) {} }
      return;
    }
    var key = token + '|' + user + '|' + server;
    if (key === state.session) return;
    state.session = key;
    try { S.onSession(JSON.stringify({ server: server || location.origin, token: token, userId: user })); } catch (e) {}
  }
  setInterval(session, 3000);
  session();
  route();
})();
"""

    /** Feuille de style violette. Ne touche qu'aux couleurs d'accent, jamais à la mise en page. */
    fun themeCss(accent: String, extra: String): String = """
html.sonnet-themed { --sonnet-accent: $accent; --sonnet-accent-soft: color-mix(in srgb, $accent 24%, transparent); }
html.sonnet-themed .button-submit,
html.sonnet-themed .emby-button.button-submit,
html.sonnet-themed .raised.button-submit,
html.sonnet-themed .btnSave,
html.sonnet-themed .MuiButton-containedPrimary,
html.sonnet-themed .MuiFab-primary { background-color: var(--sonnet-accent) !important; color: #fff !important; }
html.sonnet-themed .MuiButton-textPrimary,
html.sonnet-themed .MuiButton-outlinedPrimary,
html.sonnet-themed .MuiLink-root,
html.sonnet-themed .MuiTab-root.Mui-selected,
html.sonnet-themed .MuiCircularProgress-root,
html.sonnet-themed .MuiSvgIcon-colorPrimary,
html.sonnet-themed .MuiCheckbox-root.Mui-checked,
html.sonnet-themed .MuiRadio-root.Mui-checked,
html.sonnet-themed .MuiInputLabel-root.Mui-focused,
html.sonnet-themed .MuiFormLabel-root.Mui-focused,
html.sonnet-themed .button-link,
html.sonnet-themed .emby-tab-button-active,
html.sonnet-themed .inputLabelFocused,
html.sonnet-themed .selectLabelFocused,
html.sonnet-themed .textareaLabelFocused,
html.sonnet-themed .navMenuOption-selected .navMenuOptionIcon { color: var(--sonnet-accent) !important; }
html.sonnet-themed .MuiButton-outlinedPrimary { border-color: var(--sonnet-accent) !important; }
html.sonnet-themed .MuiSwitch-switchBase.Mui-checked { color: var(--sonnet-accent) !important; }
html.sonnet-themed .MuiSwitch-switchBase.Mui-checked + .MuiSwitch-track,
html.sonnet-themed .MuiTabs-indicator,
html.sonnet-themed .MuiLinearProgress-bar,
html.sonnet-themed .MuiSlider-thumb,
html.sonnet-themed .MuiSlider-track,
html.sonnet-themed .MuiChip-colorPrimary,
html.sonnet-themed .MuiBadge-colorPrimary,
html.sonnet-themed .itemProgressBarForeground,
html.sonnet-themed .playedIndicator,
html.sonnet-themed .countIndicator,
html.sonnet-themed .sliderBubble { background-color: var(--sonnet-accent) !important; }
html.sonnet-themed .MuiSlider-track,
html.sonnet-themed .MuiSlider-thumb { border-color: var(--sonnet-accent) !important; }
html.sonnet-themed .MuiOutlinedInput-root.Mui-focused .MuiOutlinedInput-notchedOutline,
html.sonnet-themed .emby-input:focus,
html.sonnet-themed .emby-textarea:focus,
html.sonnet-themed .emby-select-withcolor:focus { border-color: var(--sonnet-accent) !important; }
html.sonnet-themed .emby-checkbox:checked + span + .checkboxOutline { background-color: var(--sonnet-accent) !important; border-color: var(--sonnet-accent) !important; }
html.sonnet-themed .emby-checkbox:focus + span + .checkboxOutline { border-color: var(--sonnet-accent) !important; }
html.sonnet-themed .emby-radio:checked + span:before { border-color: var(--sonnet-accent) !important; }
html.sonnet-themed .emby-radio:checked + span:after { background-color: var(--sonnet-accent) !important; }
html.sonnet-themed .emby-toggle:checked + .toggleButton,
html.sonnet-themed .emby-toggle:checked { background-color: var(--sonnet-accent) !important; }
html.sonnet-themed .MuiListItemButton-root.Mui-selected,
html.sonnet-themed .MuiMenuItem-root.Mui-selected,
html.sonnet-themed .navMenuOption-selected { background-color: var(--sonnet-accent-soft) !important; }
html.sonnet-themed .mdl-slider::-webkit-slider-thumb { background: var(--sonnet-accent) !important; }
html.sonnet-themed.sonnet-admin body,
html.sonnet-themed.sonnet-admin .MuiDrawer-paper,
html.sonnet-themed.sonnet-admin .MuiAppBar-root,
html.sonnet-themed.sonnet-admin .skinHeader { background-color: #0a0612 !important; }
html.sonnet-themed.sonnet-admin .MuiPaper-root:not(.MuiAppBar-root):not(.MuiDrawer-paper),
html.sonnet-themed.sonnet-admin .paperList,
html.sonnet-themed.sonnet-admin .cardBox { background-color: #150c26 !important; }
$extra
"""
}
