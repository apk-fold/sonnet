/*
 * Sonnet FX — couche d'effets injectée dans Jellyfin web et dans le Bridge Hub.
 * Animations GSAP, retour tactile, raccourcis « tirer vers le bas », double-tap favori,
 * glisser un épisode, thèmes sombre / AMOLED / clair.
 * Tout est réglable depuis Réglages › Options Sonnet : l'appli appelle window.__sonnetFxInit(config).
 * Dépend de window.gsap (gsap.min.js, chargé juste avant).
 */
(function () {
  'use strict';
  var W = window;
  if (W.__sonnetFxInit) return; // déjà chargé dans ce contexte : seul l'appel d'init suivra

  var D = document;
  var G = W.gsap;
  var cfg = {
    mode: 'jellyfin', anim: true, press: true, haptics: true, pull: true,
    dbl: true, swipe: true, max: false, theme: 0, accent: '#7F77DD'
  };
  var started = false;
  var PULL = 86;

  // ------------------------------------------------------------------ utilitaires
  function $(sel, root) { return (root || D).querySelector(sel); }
  function reduced() {
    try { return W.matchMedia && W.matchMedia('(prefers-reduced-motion: reduce)').matches; } catch (e) { return false; }
  }
  function animOn() { return !!(cfg.anim && G && !reduced()); }
  function haptic(kind) {
    if (!cfg.haptics) return;
    try { if (W.SonnetFx && W.SonnetFx.haptic) W.SonnetFx.haptic(kind || 'light'); } catch (e) {}
  }
  function isJf() { return cfg.mode === 'jellyfin'; }
  function maxOn() { return animOn() && !!cfg.max; }
  function hexRgb(h) {
    var m = /^#?([0-9a-f]{6})$/i.exec(h || '');
    if (!m) return [127, 119, 221];
    var n = parseInt(m[1], 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  }
  function hash() { return W.location.hash || ''; }
  function onVideo() { return hash().indexOf('#/video') === 0; }

  // ------------------------------------------------------------------ styles + thème
  var CSS = [
    ':root{--sfx-accent:#7F77DD}',
    'html.sfx-amoled,html.sfx-amoled body,html.sfx-amoled .backgroundContainer,html.sfx-amoled .skinBody,',
    'html.sfx-amoled .skinHeader,html.sfx-amoled .mainDrawer,html.sfx-amoled .MuiDrawer-paper,',
    'html.sfx-amoled .MuiAppBar-root,html.sfx-amoled .pageContainer,html.sfx-amoled .libraryPage,',
    'html.sfx-amoled .dialog{background-color:#000!important}',
    'html.sfx-amoled .MuiPaper-root:not(.MuiAppBar-root):not(.MuiDrawer-paper),html.sfx-amoled .paperList,',
    'html.sfx-amoled .cardBox,html.sfx-amoled .cardImageContainer{background-color:#080808!important}',
    'html.sfx-light{filter:invert(1) hue-rotate(180deg);background:#fff}',
    'html.sfx-light img,html.sfx-light video,html.sfx-light canvas,html.sfx-light picture,',
    'html.sfx-light .cardImageContainer,html.sfx-light .itemBackdrop,html.sfx-light .backdropImage,',
    'html.sfx-light .detailImageContainer,html.sfx-light .sfx-keep,html.sfx-light [style*="background-image"]',
    '{filter:invert(1) hue-rotate(180deg)}',
    '.sfx-pull{position:fixed;left:50%;top:0;z-index:2147483000;margin-left:-60px;width:120px;padding:8px 0;',
    'text-align:center;font:600 13px system-ui,sans-serif;color:#fff;background:var(--sfx-accent);',
    'border-radius:0 0 18px 18px;opacity:0;visibility:hidden;pointer-events:none;box-shadow:0 6px 24px rgba(0,0,0,.45)}',
    '.sfx-pull.ready{filter:brightness(1.18)}',
    '.sfx-sheet-bg{position:fixed;inset:0;z-index:2147483100;background:rgba(0,0,0,.55);visibility:hidden}',
    '.sfx-sheet{position:fixed;left:0;right:0;bottom:0;z-index:2147483200;background:#15102b;color:#f1eeff;',
    'border-radius:22px 22px 0 0;padding:14px 14px calc(18px + env(safe-area-inset-bottom));visibility:hidden;',
    'font-family:system-ui,sans-serif;box-shadow:0 -12px 40px rgba(0,0,0,.5)}',
    '.sfx-sheet h3{margin:2px 6px 10px;font-size:15px;font-weight:600;opacity:.75}',
    '.sfx-sheet .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(92px,1fr));gap:10px}',
    '.sfx-sheet button{all:unset;box-sizing:border-box;display:flex;flex-direction:column;align-items:center;',
    'gap:6px;padding:14px 6px;border-radius:16px;background:rgba(255,255,255,.07);font-size:13px;text-align:center;cursor:pointer}',
    '.sfx-sheet button b{font-size:22px;line-height:1;color:var(--sfx-accent)}',
    '.sfx-toast{position:fixed;left:50%;bottom:calc(86px + env(safe-area-inset-bottom));z-index:2147483300;',
    'transform:translateX(-50%);padding:10px 18px;border-radius:22px;background:#1c1638;color:#fff;',
    'font:500 14px system-ui,sans-serif;border:1px solid var(--sfx-accent);opacity:0;visibility:hidden;',
    'pointer-events:none;white-space:nowrap}',
    '.sfx-bg{position:fixed;left:0;top:0;width:100vw;height:100vh;z-index:2147482000;pointer-events:none;mix-blend-mode:screen;opacity:.85}',
    '.sfx-rip{position:fixed;z-index:2147483250;pointer-events:none;border-radius:50%;width:160px;height:160px;margin:-80px 0 0 -80px;',
    'background:radial-gradient(circle,var(--sfx-accent) 0%,transparent 68%);opacity:0;visibility:hidden}',
    '.sfx-spark{position:fixed;z-index:2147483300;pointer-events:none;width:9px;height:9px;margin:-4px 0 0 -4px;border-radius:50%;',
    'background:var(--sfx-accent);box-shadow:0 0 12px var(--sfx-accent);opacity:0;visibility:hidden}',
    '.sfx-mask{display:inline-block;overflow:hidden;vertical-align:top;line-height:1.15}',
    '.sfx-ch{display:inline-block;will-change:transform}',
    '.sfx-heart{position:fixed;z-index:2147483300;pointer-events:none;font-size:72px;line-height:1;',
    'color:var(--sfx-accent);opacity:0;visibility:hidden;margin:-36px 0 0 -36px}'
  ].join('\n');

  function ensureStyle() {
    if ($('#sonnet-fx-style')) return;
    var el = D.createElement('style');
    el.id = 'sonnet-fx-style';
    el.textContent = CSS;
    (D.head || D.documentElement).appendChild(el);
  }

  function applyTheme() {
    var root = D.documentElement;
    if (!root) return;
    root.classList.toggle('sfx-amoled', cfg.theme === 1);
    root.classList.toggle('sfx-light', cfg.theme === 2);
    root.style.setProperty('--sfx-accent', cfg.accent);
  }

  // ------------------------------------------------------------------ toast
  var toastEl = null, toastTl = null;
  function toast(text) {
    if (!D.body) return;
    if (!toastEl || !toastEl.parentNode) {
      toastEl = D.createElement('div');
      toastEl.className = 'sfx-toast';
      D.body.appendChild(toastEl);
    }
    toastEl.textContent = text;
    if (!G || !animOn()) {
      toastEl.style.opacity = 1; toastEl.style.visibility = 'visible';
      setTimeout(function () { toastEl.style.opacity = 0; toastEl.style.visibility = 'hidden'; }, 1600);
      return;
    }
    if (toastTl) toastTl.kill();
    toastTl = G.timeline()
      .set(toastEl, { autoAlpha: 0, y: 16, xPercent: -50, x: 0 })
      .to(toastEl, { autoAlpha: 1, y: 0, duration: 0.28, ease: 'back.out(2)' })
      .to(toastEl, { autoAlpha: 0, y: 8, duration: 0.25, ease: 'power2.in' }, '+=1.4');
  }

  // ------------------------------------------------------------------ API Jellyfin (favori / vu)
  function creds() {
    try {
      var c = W.ApiClient;
      if (!c) return null;
      var t = c.accessToken(), u = c.getCurrentUserId(), s = c.serverAddress();
      if (!t || !u || !s) return null;
      return { token: t, user: u, server: String(s).replace(/\/+$/, '') };
    } catch (e) { return null; }
  }
  function call(method, path) {
    var c = creds();
    if (!c) return Promise.reject(new Error('no-session'));
    return fetch(c.server + path, {
      method: method,
      headers: { 'X-Emby-Token': c.token, 'Authorization': 'MediaBrowser Token="' + c.token + '"' }
    }).then(function (r) { if (!r.ok) throw new Error('http ' + r.status); return r; });
  }
  function userData(id) {
    var c = creds();
    return call('GET', '/Items?Ids=' + encodeURIComponent(id) + '&UserId=' + encodeURIComponent(c.user) + '&Fields=')
      .then(function (r) { return r.json(); })
      .then(function (j) { return (j.Items && j.Items[0] && j.Items[0].UserData) || {}; });
  }
  function setFlag(kind, id, on) {
    var c = creds();
    var first = (kind === 'fav' ? '/UserFavoriteItems/' : '/UserPlayedItems/') + id + '?userId=' + encodeURIComponent(c.user);
    var legacy = '/Users/' + c.user + (kind === 'fav' ? '/FavoriteItems/' : '/PlayedItems/') + id;
    return call(on ? 'POST' : 'DELETE', first).catch(function () { return call(on ? 'POST' : 'DELETE', legacy); });
  }
  function toggle(kind, id) {
    return userData(id).then(function (ud) {
      var now = kind === 'fav' ? !!ud.IsFavorite : !!ud.Played;
      return setFlag(kind, id, !now).then(function () { return !now; });
    });
  }
  function toggleFavorite(id, x, y) {
    toggle('fav', id).then(function (on) {
      haptic('heavy');
      toast(on ? '♥  Ajouté aux favoris' : 'Retiré des favoris');
      if (on) { heart(x, y); burst(x, y, 16); }
    }).catch(function () { toast('Action impossible (session ?)'); });
  }
  function togglePlayed(id) {
    toggle('played', id).then(function (on) {
      haptic('heavy');
      toast(on ? '✓  Marqué comme vu' : 'Marqué comme non vu');
    }).catch(function () { toast('Action impossible (session ?)'); });
  }

  function heart(x, y) {
    if (!D.body) return;
    var el = D.createElement('div');
    el.className = 'sfx-heart';
    el.textContent = '♥';
    el.style.left = x + 'px'; el.style.top = y + 'px';
    D.body.appendChild(el);
    if (!animOn()) { setTimeout(function () { el.remove(); }, 400); return; }
    G.timeline({ onComplete: function () { el.remove(); } })
      .fromTo(el, { autoAlpha: 0, scale: 0.2 }, { autoAlpha: 1, scale: 1.25, duration: 0.3, ease: 'back.out(3)' })
      .to(el, { y: -40, autoAlpha: 0, scale: 1, duration: 0.45, ease: 'power2.in' }, '+=0.12');
  }

  // ------------------------------------------------------------------ effets « max »
  function burst(x, y, n) {
    if (!maxOn() || !D.body) return;
    n = n || 14;
    for (var i = 0; i < n; i++) {
      var sp = D.createElement('span');
      sp.className = 'sfx-spark';
      sp.style.left = x + 'px'; sp.style.top = y + 'px';
      D.body.appendChild(sp);
      var a = (i / n) * Math.PI * 2 + Math.random() * 0.6;
      var d = G.utils.random(40, 110);
      G.fromTo(sp, { x: 0, y: 0, scale: G.utils.random(0.7, 1.5), autoAlpha: 1 }, {
        x: Math.cos(a) * d, y: Math.sin(a) * d, scale: 0, autoAlpha: 0,
        duration: G.utils.random(0.55, 1.0), ease: 'power3.out',
        onComplete: (function (el) { return function () { el.remove(); }; })(sp)
      });
    }
  }
  function ripple(x, y) {
    if (!maxOn() || !D.body) return;
    var r = D.createElement('span');
    r.className = 'sfx-rip';
    r.style.left = x + 'px'; r.style.top = y + 'px';
    D.body.appendChild(r);
    G.fromTo(r, { scale: 0.1, autoAlpha: 0.55 }, { scale: 2.2, autoAlpha: 0, duration: 0.7, ease: 'power2.out', onComplete: function () { r.remove(); } });
  }

  // Fond aurore : halos qui dérivent + constellation de particules (canvas, mélange « screen »)
  var bg = null;
  function stopBg() {
    if (!bg) return;
    try { G.ticker.remove(bg.tick); } catch (e) {}
    W.removeEventListener('resize', bg.resize);
    if (bg.cv && bg.cv.parentNode) bg.cv.remove();
    bg = null;
  }
  function startBg() {
    if (bg || !D.body || !G) return;
    var cv = D.createElement('canvas');
    cv.className = 'sfx-bg';
    D.body.appendChild(cv);
    var ctx = cv.getContext('2d');
    if (!ctx) { cv.remove(); return; }
    var w = 0, h = 0, scale = 0.5;
    function resize() {
      w = Math.max(160, Math.round((W.innerWidth || 400) * scale));
      h = Math.max(160, Math.round((W.innerHeight || 800) * scale));
      cv.width = w; cv.height = h;
    }
    resize();
    W.addEventListener('resize', resize);
    var rgb = hexRgb(cfg.accent);
    var blobs = [];
    for (var i = 0; i < 3; i++) blobs.push({ ph: Math.random() * 6.28, sp: 0.12 + Math.random() * 0.12, r: 0.55 + Math.random() * 0.3, k: i });
    var P = [];
    for (var j = 0; j < 38; j++) P.push({ x: Math.random(), y: Math.random(), vx: (Math.random() - 0.5) * 0.012, vy: -0.004 - Math.random() * 0.012, r: 0.6 + Math.random() * 1.4, ph: Math.random() * 6.28 });
    function tick(time, dt) {
      if (D.hidden || onVideo()) { cv.style.visibility = 'hidden'; return; }
      cv.style.visibility = 'visible';
      rgb = hexRgb(cfg.accent);
      ctx.clearRect(0, 0, w, h);
      for (var b = 0; b < blobs.length; b++) {
        var o = blobs[b];
        var cx = (0.5 + 0.42 * Math.sin(time * o.sp + o.ph + o.k)) * w;
        var cy = (0.5 + 0.42 * Math.cos(time * o.sp * 0.8 + o.ph * 1.7)) * h;
        var rad = o.r * Math.max(w, h);
        var g = ctx.createRadialGradient(cx, cy, 0, cx, cy, rad);
        var tint = [rgb[0], rgb[1], rgb[2]];
        if (o.k === 1) tint = [rgb[2], rgb[0], rgb[1]];
        if (o.k === 2) tint = [Math.min(255, rgb[0] + 60), Math.min(255, rgb[1] + 20), Math.min(255, rgb[2] + 90)];
        g.addColorStop(0, 'rgba(' + tint.join(',') + ',0.16)');
        g.addColorStop(1, 'rgba(' + tint.join(',') + ',0)');
        ctx.fillStyle = g;
        ctx.fillRect(0, 0, w, h);
      }
      var step = (dt || 16) / 16;
      var k;
      for (k = 0; k < P.length; k++) {
        var p = P[k];
        p.x += p.vx * 0.05 * step; p.y += p.vy * 0.05 * step;
        if (p.x < -0.02) p.x = 1.02; else if (p.x > 1.02) p.x = -0.02;
        if (p.y < -0.02) p.y = 1.02; else if (p.y > 1.02) p.y = -0.02;
      }
      ctx.lineWidth = 0.6;
      for (k = 0; k < P.length; k++) {
        var a = P[k];
        for (var m = k + 1; m < P.length; m++) {
          var c = P[m];
          var ddx = (a.x - c.x) * w, ddy = (a.y - c.y) * h, d2 = ddx * ddx + ddy * ddy;
          if (d2 < 2600) {
            ctx.strokeStyle = 'rgba(' + rgb.join(',') + ',' + ((1 - d2 / 2600) * 0.35).toFixed(3) + ')';
            ctx.beginPath(); ctx.moveTo(a.x * w, a.y * h); ctx.lineTo(c.x * w, c.y * h); ctx.stroke();
          }
        }
      }
      for (k = 0; k < P.length; k++) {
        var q = P[k];
        var tw = 0.55 + 0.45 * Math.sin(time * 1.4 + q.ph);
        ctx.fillStyle = 'rgba(255,255,255,' + (0.5 * tw).toFixed(3) + ')';
        ctx.beginPath(); ctx.arc(q.x * w, q.y * h, q.r, 0, 6.283); ctx.fill();
      }
    }
    G.ticker.add(tick);
    bg = { cv: cv, tick: tick, resize: resize };
  }
  function syncBg() {
    if (maxOn() && cfg.theme !== 2) startBg(); else stopBg();
  }

  // Titres lettre par lettre
  function splitTitle(el) {
    if (el.__sfxSplit || el.children.length > 0) return null;
    var text = el.textContent;
    if (!text || text.length > 90) return null;
    el.__sfxSplit = true;
    el.setAttribute('aria-label', text);
    el.textContent = '';
    var chars = [];
    text.split(' ').forEach(function (word, wi, arr) {
      var w = D.createElement('span');
      w.style.display = 'inline-block';
      w.setAttribute('aria-hidden', 'true');
      for (var i = 0; i < word.length; i++) {
        var m = D.createElement('span'); m.className = 'sfx-mask';
        var c = D.createElement('span'); c.className = 'sfx-ch'; c.textContent = word[i];
        m.appendChild(c); w.appendChild(m); chars.push(c);
      }
      el.appendChild(w);
      if (wi < arr.length - 1) el.appendChild(D.createTextNode(' '));
    });
    return chars;
  }
  function animateTitle(el) {
    if (!maxOn() || !el.isConnected) return;
    var chars = splitTitle(el);
    if (!chars || !chars.length) return;
    G.fromTo(chars, { yPercent: 120, rotation: 8, autoAlpha: 0 },
      { yPercent: 0, rotation: 0, autoAlpha: 1, duration: 0.8, ease: 'power4.out', stagger: 0.022, clearProps: 'opacity,visibility,transform' });
  }

  // Parallaxe du fond d'une fiche au défilement
  var parTick = false;
  function onScroll() {
    if (!maxOn() || parTick) return;
    parTick = true;
    requestAnimationFrame(function () {
      parTick = false;
      var el = $('.backdropImage,.itemBackdrop,.backdropContainer');
      if (!el || hash().indexOf('#/details') !== 0) return;
      var se = D.scrollingElement || D.documentElement;
      G.set(el, { y: Math.min(220, (se.scrollTop || 0) * 0.3) });
    });
  }

  // ------------------------------------------------------------------ entrées en cascade
  var SEL_ENTER = '.card,.listItem,.sectionTitleContainer,.itemName,.detailImageContainer';
  var SEL_TITLE = 'h1.itemName,.itemName-primary,.detailPagePrimaryContainer h1,.sectionTitle';
  var pending = [];
  var flushQueued = false;

  function queueEnter(el) {
    if (el.__sfxDone) return;
    el.__sfxDone = true;
    pending.push(el);
    if (!flushQueued) { flushQueued = true; requestAnimationFrame(flush); }
  }
  function flush() {
    flushQueued = false;
    var items = pending; pending = [];
    if (!animOn() || !items.length) return;
    var vh = W.innerHeight || 800, vw = W.innerWidth || 400;
    if (maxOn()) {
      var titles = items.filter(function (el) { return el.matches && el.matches(SEL_TITLE); });
      titles.forEach(animateTitle);
    }
    var visible = items.filter(function (el) {
      if (el.__sfxSplit || (el.matches && el.matches(SEL_TITLE))) return false;
      if (!el.isConnected) return false;
      var r = el.getBoundingClientRect();
      return r.width > 0 && r.height > 0 && r.top < vh + 60 && r.bottom > -60 && r.left < vw && r.right > 0;
    }).slice(0, 30);
    if (!visible.length) return;
    if (maxOn()) {
      G.fromTo(visible,
        { autoAlpha: 0, y: 46, rotationX: -38, scale: 0.82, transformPerspective: 700, transformOrigin: '50% 100%' },
        { autoAlpha: 1, y: 0, rotationX: 0, scale: 1, duration: 0.8, stagger: { each: 0.035, from: 'random' }, ease: 'back.out(1.6)',
          overwrite: 'auto', clearProps: 'opacity,visibility,transform,transformOrigin' });
    } else {
      G.fromTo(visible,
        { autoAlpha: 0, y: 18, scale: 0.96 },
        { autoAlpha: 1, y: 0, scale: 1, duration: 0.45, stagger: 0.03, ease: 'power3.out', overwrite: 'auto',
          clearProps: 'opacity,visibility,transform' });
    }
  }
  function scan(root) {
    if (!root || root.nodeType !== 1) return;
    if (root.matches && root.matches(SEL_ENTER + ',' + SEL_TITLE)) queueEnter(root);
    var list = root.querySelectorAll ? root.querySelectorAll(SEL_ENTER + ',' + SEL_TITLE) : [];
    for (var i = 0; i < list.length; i++) queueEnter(list[i]);
  }
  function observeDom() {
    if (!W.MutationObserver || !D.body) return;
    var generic = !isJf();
    new MutationObserver(function (muts) {
      if (!animOn() || generic) return; // le Bridge Hub a ses propres éléments : transition de page seulement
      for (var i = 0; i < muts.length; i++) {
        var added = muts[i].addedNodes;
        for (var j = 0; j < added.length; j++) scan(added[j]);
      }
    }).observe(D.body, { childList: true, subtree: true });
  }

  // ------------------------------------------------------------------ transition de page
  var lastRoute = '';
  function pageEl() {
    var list = isJf()
      ? ['.mainAnimatedPage:not(.hide)', '.page:not(.hide)', '#reactRoot > div']
      : ['main', '#app', '#root', 'body > div'];
    for (var i = 0; i < list.length; i++) { var el = $(list[i]); if (el) return el; }
    return null;
  }
  function onRoute() {
    var key = hash();
    if (key === lastRoute) return;
    lastRoute = key;
    if (!animOn() || onVideo()) return;
    setTimeout(function () {
      var el = pageEl();
      if (!el) return;
      if (maxOn()) {
        G.fromTo(el, { autoAlpha: 0, y: 36, scale: 0.97, filter: 'blur(14px)' },
          { autoAlpha: 1, y: 0, scale: 1, filter: 'blur(0px)', duration: 0.6, ease: 'expo.out', overwrite: 'auto', clearProps: 'opacity,visibility,transform,filter' });
      } else {
        G.fromTo(el, { autoAlpha: 0, y: 14 }, { autoAlpha: 1, y: 0, duration: 0.34, ease: 'power2.out', overwrite: 'auto', clearProps: 'opacity,visibility,transform' });
      }
    }, 40);
  }

  // ------------------------------------------------------------------ retour tactile (ressort)
  var PRESSABLE = '.card,.listItem,.emby-button,button,[role="button"],.navMenuOption,.MuiButtonBase-root';
  var pressed = null;

  function pressTarget(t) {
    if (!t || !t.closest) return null;
    var el = t.closest(PRESSABLE);
    if (!el) return null;
    if (el.closest('video,.videoOsdBottom,.sfx-sheet,input,textarea,select')) return null;
    var r = el.getBoundingClientRect();
    if (r.width > (W.innerWidth || 400) * 0.95 && r.height > (W.innerHeight || 800) * 0.5) return null;
    return el;
  }
  function pressDown(e) {
    if (!cfg.press && !cfg.haptics) return;
    var el = pressTarget(e.target);
    if (!el) return;
    pressed = el;
    var tp = e.touches && e.touches[0];
    if (cfg.press && animOn()) {
      var rc = el.getBoundingClientRect();
      var wide = rc.width > (W.innerWidth || 400) * 0.7;
      var vars = { scale: wide ? 0.985 : 0.955, duration: 0.12, ease: 'power2.out', overwrite: 'auto' };
      if (cfg.max && tp && !wide && el.matches('.card')) {
        vars.rotationY = ((tp.clientX - (rc.left + rc.width / 2)) / (rc.width / 2)) * 16;
        vars.rotationX = -((tp.clientY - (rc.top + rc.height / 2)) / (rc.height / 2)) * 16;
        vars.transformPerspective = 600;
      }
      G.to(el, vars);
    }
    if (tp && cfg.max) ripple(tp.clientX, tp.clientY);
    haptic('tick');
  }
  function pressUp() {
    var el = pressed; pressed = null;
    if (!el || !G) return;
    if (cfg.press && animOn()) {
      G.to(el, { scale: 1, rotationX: 0, rotationY: 0, duration: 0.7, ease: 'elastic.out(1, 0.5)', overwrite: 'auto',
        onComplete: function () { el.style.transform = ''; el.style.scale = ''; } });
    }
  }

  // ------------------------------------------------------------------ geste : tirer vers le bas
  var pullEl = null, pull = null;
  function ensurePull() {
    if (pullEl && pullEl.parentNode) return pullEl;
    pullEl = D.createElement('div');
    pullEl.className = 'sfx-pull';
    pullEl.textContent = 'Raccourcis';
    D.body.appendChild(pullEl);
    return pullEl;
  }
  function scrolledAncestor(t) {
    var se = D.scrollingElement || D.documentElement;
    if (se && se.scrollTop > 2) return true;
    var el = t;
    while (el && el !== D.body && el.nodeType === 1) {
      if (el.scrollTop > 2) {
        var oy = W.getComputedStyle(el).overflowY;
        if (oy === 'auto' || oy === 'scroll') return true;
      }
      el = el.parentNode;
    }
    return false;
  }

  var sheetOpen = false;
  function shortcuts() {
    return isJf() ? [
      ['⌂', 'Accueil', function () { W.location.hash = '#/home'; }],
      ['⌕', 'Recherche', function () { W.location.hash = '#/search'; }],
      ['♥', 'Favoris', function () { W.location.hash = '#/home?tab=1'; }],
      ['⟳', 'Actualiser', function () { W.location.reload(); }],
      ['↑', 'Haut de page', function () { toTop(); }]
    ] : [
      ['←', 'Retour', function () { W.history.back(); }],
      ['⟳', 'Actualiser', function () { W.location.reload(); }],
      ['↑', 'Haut de page', function () { toTop(); }]
    ];
  }
  function toTop() {
    var se = D.scrollingElement || D.documentElement;
    if (G && animOn()) G.to(se, { scrollTop: 0, duration: 0.5, ease: 'power2.inOut' });
    else se.scrollTop = 0;
  }
  function openSheet() {
    if (sheetOpen || !D.body) return;
    sheetOpen = true;
    haptic('heavy');
    var bg = D.createElement('div'); bg.className = 'sfx-sheet-bg';
    var sh = D.createElement('div'); sh.className = 'sfx-sheet';
    var h = D.createElement('h3'); h.textContent = 'Raccourcis'; sh.appendChild(h);
    var grid = D.createElement('div'); grid.className = 'grid'; sh.appendChild(grid);
    shortcuts().forEach(function (s) {
      var b = D.createElement('button');
      var i = D.createElement('b'); i.textContent = s[0];
      var t = D.createElement('span'); t.textContent = s[1];
      b.appendChild(i); b.appendChild(t);
      b.addEventListener('click', function (ev) { ev.stopPropagation(); close(function () { s[2](); }); });
      grid.appendChild(b);
    });
    D.body.appendChild(bg); D.body.appendChild(sh);
    function close(after) {
      function done() { bg.remove(); sh.remove(); sheetOpen = false; if (after) after(); }
      if (animOn()) {
        G.to(sh, { yPercent: 100, duration: 0.26, ease: 'power2.in' });
        G.to(bg, { autoAlpha: 0, duration: 0.26, onComplete: done });
      } else done();
    }
    bg.addEventListener('click', function () { close(); });
    if (animOn()) {
      G.fromTo(bg, { autoAlpha: 0 }, { autoAlpha: 1, duration: 0.25 });
      G.fromTo(sh, { yPercent: 100, autoAlpha: 1 }, { yPercent: 0, autoAlpha: 1, duration: 0.42, ease: 'back.out(1.25)' });
      G.from(grid.children, { y: 18, autoAlpha: 0, scale: 0.9, duration: 0.35, stagger: 0.05, delay: 0.1, ease: 'power3.out' });
    } else {
      bg.style.visibility = 'visible'; sh.style.visibility = 'visible';
    }
  }

  // ------------------------------------------------------------------ gestes tactiles (un seul jeu d'écouteurs)
  var t0 = null;          // { x, y, mode, row, ready, target }
  var lastTap = { t: 0, x: 0, y: 0 };
  var swallowClick = 0;

  function rowOf(t) { return t && t.closest ? t.closest('.listItem[data-id]') : null; }

  function onTouchStart(e) {
    if (e.touches.length !== 1 || sheetOpen) { t0 = null; return; }
    var tt = e.touches[0];
    t0 = { x: tt.clientX, y: tt.clientY, mode: null, row: null, ready: false, target: e.target };
    pressDown(e);
  }

  function onTouchMove(e) {
    if (!t0 || e.touches.length !== 1) return;
    var tt = e.touches[0];
    var dx = tt.clientX - t0.x, dy = tt.clientY - t0.y;

    if (!t0.mode) {
      var adx = Math.abs(dx), ady = Math.abs(dy);
      if (adx < 10 && ady < 10) return;
      if (isJf() && onVideo()) { t0 = null; return; }
      var inField = t0.target && t0.target.closest && t0.target.closest('input,textarea,select,video,.sliderContainer,.MuiSlider-root');
      if (inField) { t0 = null; return; }
      if (cfg.swipe && isJf() && adx > ady * 1.4 && rowOf(t0.target)) {
        t0.mode = 'swipe'; t0.row = rowOf(t0.target);
      } else if (cfg.pull && dy > 0 && ady > adx * 1.5 && !scrolledAncestor(t0.target)) {
        t0.mode = 'pull';
      } else if (ady > 14 || adx > 14) {
        pressUp();
        t0 = null; return;
      } else return;
      pressUp();
    }

    if (t0.mode === 'swipe') {
      if (e.cancelable) e.preventDefault();
      var x = Math.max(-140, Math.min(140, dx)) * 0.7;
      if (G) G.set(t0.row, { x: x });
      var ok = Math.abs(dx) > 80;
      if (ok && !t0.ready) { t0.ready = true; haptic('tick'); }
      else if (!ok && t0.ready) t0.ready = false;
    } else if (t0.mode === 'pull') {
      if (dy <= 0) { hidePull(); return; }
      var p = Math.max(0, Math.min(1, dy / PULL));
      var el = ensurePull();
      el.textContent = p >= 1 ? 'Relâcher' : 'Raccourcis';
      var yy = Math.min(dy, PULL * 1.4) * 0.5;
      if (G) G.set(el, { y: yy - 40, autoAlpha: Math.min(1, p * 1.3), scale: 0.75 + p * 0.25 });
      else { el.style.visibility = 'visible'; el.style.opacity = p; }
      var ready = p >= 1;
      if (ready && !t0.ready) { t0.ready = true; el.classList.add('ready'); haptic('tick'); }
      else if (!ready && t0.ready) { t0.ready = false; el.classList.remove('ready'); }
    }
  }

  function hidePull() {
    if (!pullEl) return;
    pullEl.classList.remove('ready');
    if (G && animOn()) G.to(pullEl, { y: -40, autoAlpha: 0, duration: 0.22, ease: 'power2.in' });
    else { pullEl.style.visibility = 'hidden'; pullEl.style.opacity = 0; }
  }

  function onTouchEnd(e) {
    var s = t0; t0 = null;
    pressUp();
    if (!s) { maybeDoubleTap(e); return; }

    if (s.mode === 'swipe' && s.row) {
      var tt = e.changedTouches && e.changedTouches[0];
      var dx = tt ? tt.clientX - s.x : 0;
      if (G) G.to(s.row, { x: 0, duration: 0.5, ease: 'elastic.out(1, 0.7)', clearProps: 'transform' });
      swallowClick = Date.now() + 350;
      if (Math.abs(dx) > 80) {
        var id = s.row.getAttribute('data-id');
        if (id) { if (dx > 0) togglePlayed(id); else toggleFavorite(id, tt.clientX, tt.clientY); }
      }
      return;
    }
    if (s.mode === 'pull') {
      hidePull();
      if (s.ready) openSheet();
      return;
    }
    maybeDoubleTap(e);
  }

  function onTouchCancel() {
    var s = t0; t0 = null;
    pressUp();
    if (s && s.mode === 'swipe' && s.row && G) G.to(s.row, { x: 0, duration: 0.3, clearProps: 'transform' });
    if (s && s.mode === 'pull') hidePull();
  }

  // double-tap sur l'affiche d'une fiche = favori
  function currentItemId() {
    var m = /[?&]id=([0-9a-fA-F-]{32,36})/.exec(hash());
    return m ? m[1] : null;
  }
  function maybeDoubleTap(e) {
    if (!cfg.dbl || !isJf() || sheetOpen) return;
    var tt = e.changedTouches && e.changedTouches[0];
    if (!tt) return;
    var t = e.target;
    if (!t || !t.closest || !t.closest('.detailImageContainer,.itemBackdrop,.backdropContainer,.detailPagePrimaryContainer .cardImageContainer')) return;
    if (hash().indexOf('#/details') !== 0) return;
    var now = Date.now();
    var close = now - lastTap.t < 320 && Math.abs(tt.clientX - lastTap.x) < 40 && Math.abs(tt.clientY - lastTap.y) < 40;
    lastTap = { t: close ? 0 : now, x: tt.clientX, y: tt.clientY };
    if (!close) return;
    var id = currentItemId();
    if (id) toggleFavorite(id, tt.clientX, tt.clientY);
  }

  function onClickCapture(e) {
    if (Date.now() < swallowClick) { e.stopPropagation(); e.preventDefault(); }
  }

  // ------------------------------------------------------------------ démarrage
  function start() {
    if (started) return;
    started = true;
    ensureStyle();
    observeDom();
    D.addEventListener('touchstart', onTouchStart, { passive: true, capture: true });
    D.addEventListener('touchmove', onTouchMove, { passive: false, capture: true });
    D.addEventListener('touchend', onTouchEnd, { passive: true, capture: true });
    D.addEventListener('touchcancel', onTouchCancel, { passive: true, capture: true });
    D.addEventListener('click', onClickCapture, true);
    W.addEventListener('scroll', onScroll, { passive: true, capture: true });
    W.addEventListener('hashchange', onRoute);
    W.addEventListener('popstate', onRoute);
    lastRoute = hash();
    if (animOn() && D.body) scan(D.body);
    if (animOn() && !isJf()) {
      var el = pageEl();
      if (el) G.fromTo(el, { autoAlpha: 0, y: 12 }, { autoAlpha: 1, y: 0, duration: 0.4, ease: 'power2.out', clearProps: 'opacity,visibility,transform' });
    }
  }

  W.__sonnetFxInit = function (c) {
    try {
      if (typeof c === 'string') c = JSON.parse(c);
      for (var k in c) if (Object.prototype.hasOwnProperty.call(c, k)) cfg[k] = c[k];
    } catch (e) {}
    G = G || W.gsap;
    ensureStyle();
    applyTheme();
    if (D.body) { start(); syncBg(); }
    else D.addEventListener('DOMContentLoaded', function () { start(); syncBg(); });
  };
  W.__sonnetFx = { cfg: cfg, toast: toast, openSheet: openSheet, _toggle: toggle };
})();
