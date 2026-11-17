/* CHANGE – front-end behaviour.
   NOTE: accounts, bookings and flights are simulated in the browser (localStorage) for demo purposes.
   A real site needs a server for authentication, payments and live fares. */
(() => {
  'use strict';
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const el = (t, c, x) => { const e = document.createElement(t); if (c) e.className = c; if (x != null) e.textContent = x; return e; };
  const store = {
    get(k, d) { try { const v = localStorage.getItem(k); return v === null ? d : JSON.parse(v); } catch { return d; } },
    set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* storage unavailable */ } }
  };
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const money = n => 'R' + Math.round(n).toLocaleString('en-US');
  const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  const USERS = 'aurum-users', SESSION = 'aurum-session', BOOKINGS = 'aurum-bookings';

  /* ---------- Theme ---------- */
  const root = document.documentElement, themeBtn = $('#theme');
  function setTheme(t) {
    root.dataset.theme = t;
    themeBtn.textContent = t === 'dark' ? '☀' : '☾';
    themeBtn.setAttribute('aria-label', t === 'dark' ? 'Switch to light mode' : 'Switch to dark mode');
    store.set('aurum-theme', t);
  }
  setTheme(store.get('aurum-theme', 'dark'));
  themeBtn.addEventListener('click', () => setTheme(root.dataset.theme === 'dark' ? 'light' : 'dark'));

  /* ---------- Background: glows, flight routes, film grain ---------- */
  (function buildSky() {
    const sky = $('.sky');
    if (!sky) return;
    sky.replaceChildren();
    ['o1', 'o2', 'o3'].forEach(c => sky.append(el('i', 'orb ' + c)));
    const routes = el('div', 'routes');
    const nodes = [[120, 700], [980, 380], [300, 820], [1400, 250], [1480, 420], [60, 300], [840, 150]];
    routes.innerHTML = `<svg viewBox="0 0 1600 900" preserveAspectRatio="xMidYMid slice" role="presentation">
      <defs><linearGradient id="rg" x1="0" x2="1" y1="0" y2="0"><stop offset="0" stop-color="currentColor" stop-opacity="0"/><stop offset=".5" stop-color="currentColor"/><stop offset="1" stop-color="currentColor" stop-opacity="0"/></linearGradient></defs>
      <g class="globe" fill="none" stroke="currentColor" stroke-width="1">
        <circle cx="1230" cy="470" r="360"/><circle cx="1230" cy="470" r="470" stroke-dasharray="3 9"/>
        <ellipse cx="1230" cy="470" rx="360" ry="120"/><ellipse cx="1230" cy="470" rx="360" ry="240"/>
        <ellipse cx="1230" cy="470" rx="120" ry="360"/><ellipse cx="1230" cy="470" rx="240" ry="360"/>
        <path d="M870 470h720M1230 110v720"/>
      </g>
      <g class="arcs" fill="none" stroke="url(#rg)" stroke-width="1.6" stroke-dasharray="2 8" stroke-linecap="round">
        <path d="M120 700Q500 180 980 380"/><path d="M300 820Q900 300 1400 250"/><path d="M980 380Q1200 140 1480 420"/><path d="M60 300Q420 80 840 150"/>
      </g>
      <g>${nodes.map(([x, y], i) => `<circle class="halo" cx="${x}" cy="${y}" r="12" style="animation-delay:-${i * 0.7}s"/><circle class="node" cx="${x}" cy="${y}" r="3"/>`).join('')}</g>
    </svg>`;
    sky.append(routes, el('div', 'grain'));
  })();

  /* ---------- History view shell (built early so the router picks it up) ---------- */
  (function buildHistoryShell() {
    const prev = $('#v-bookings');
    if (!prev) return;
    const sec = el('section', 'view'); sec.id = 'v-history';
    const stats = el('div', 'stats'); stats.id = 'histStats';
    const chips = el('div', 'chips'); chips.id = 'histChips';
    [['all', 'All'], ['upcoming', 'Upcoming'], ['completed', 'Completed'], ['cancelled', 'Cancelled']].forEach(([k, t]) => {
      const b = el('button', 'chip', t); b.type = 'button'; b.dataset.filter = k; b.setAttribute('aria-pressed', k === 'all'); chips.append(b);
    });
    const list = el('div', 'panel'); list.id = 'histList';
    const head = el('div', 'hist-head'), clear = el('button', 'auth-btn', 'Clear searches');
    clear.type = 'button'; clear.id = 'histClear';
    head.append(el('h3', '', 'Recent searches'), clear);
    const searches = el('div', 'panel'); searches.id = 'histSearches';
    sec.append(el('h2', '', 'History'), el('p', '', 'Every trip you have booked and every route you have searched, in one place.'), stats, chips, list, head, searches);
    prev.after(sec);

    const li = el('li'), a = el('a', '', 'History');
    li.id = 'navHistory'; li.hidden = true; a.href = '#history'; a.dataset.view = 'history'; li.append(a);
    $('#nav a[data-view="bookings"]').closest('li').after(li);
    const ua = el('a', '', 'History'); ua.href = '#history'; ua.dataset.view = 'history';
    $('#userMenu a[data-view="bookings"]').after(ua);
  })();

  /* ---------- Menus & routing ---------- */
  const nav = $('#nav'), menuBtn = $('#menuBtn'), userBtn = $('#userBtn'), userMenu = $('#userMenu');
  menuBtn.setAttribute('aria-expanded', 'false');
  menuBtn.setAttribute('aria-controls', 'nav');
  function closeUserMenu() { userMenu.hidden = true; userBtn.setAttribute('aria-expanded', 'false'); }
  function closeMenus() { nav.classList.remove('open'); menuBtn.setAttribute('aria-expanded', 'false'); closeUserMenu(); }
  menuBtn.addEventListener('click', () => { const o = nav.classList.toggle('open'); menuBtn.setAttribute('aria-expanded', o); });
  userBtn.addEventListener('click', e => {
    e.stopPropagation();
    const opening = userMenu.hidden;
    userMenu.hidden = !opening;
    userBtn.setAttribute('aria-expanded', opening);
  });
  document.addEventListener('click', e => { if (!e.target.closest('.user')) closeUserMenu(); });
  document.addEventListener('keydown', e => { if (e.key === 'Escape') closeMenus(); });

  const views = $$('.view');
  function show(name) {
    if (!$('#v-' + name)) name = 'home';
    views.forEach(v => v.classList.toggle('show', v.id === 'v-' + name));
    $$('#nav a').forEach(a => {
      const on = a.dataset.view === name;
      a.classList.toggle('active', on);
      on ? a.setAttribute('aria-current', 'page') : a.removeAttribute('aria-current');
    });
    if (name === 'bookings') renderBookings();
    if (name === 'history') renderHistory();
    window.scrollTo({ top: 0 });
    closeMenus();
  }
  $$('[data-view]').forEach(a => a.addEventListener('click', () => {
    closeMenus();
    if (location.hash === '#' + a.dataset.view) show(a.dataset.view);
  }));
  window.addEventListener('hashchange', () => show(location.hash.slice(1) || 'home'));

  /* ---------- Accounts (demo only) ---------- */
  async function hash(pw, salt) {
    const data = new TextEncoder().encode(salt + ':' + pw);
    if (window.crypto && crypto.subtle) {
      const buf = await crypto.subtle.digest('SHA-256', data);
      return [...new Uint8Array(buf)].map(b => b.toString(16).padStart(2, '0')).join('');
    }
    let h = 5381; for (const b of data) h = ((h << 5) + h + b) >>> 0;
    return 'x' + h.toString(16);
  }
  const getSession = () => store.get(SESSION, null);
  const modal = $('#authModal'), authErr = $('#authErr');
  const forms = { login: $('#loginForm'), signup: $('#signupForm') };
  const tabs = { login: $('#tabLogin'), signup: $('#tabSignup') };
  let pending = null;

  function setMode(m) {
    for (const k in forms) { forms[k].hidden = k !== m; tabs[k].setAttribute('aria-selected', k === m); }
    authErr.textContent = '';
  }
  function openAuth(m = 'login') {
    setMode(m);
    if (!modal.open) modal.showModal();
    $('input', forms[m]).focus();
  }
  const closeAuth = () => { if (modal.open) modal.close(); };
  const fail = m => { authErr.textContent = m; };

  $('#openLogin').addEventListener('click', () => openAuth('login'));
  $('#openSignup').addEventListener('click', () => openAuth('signup'));
  $('#welcomeLogin').addEventListener('click', () => openAuth('login'));
  $('#welcomeSignup').addEventListener('click', () => openAuth('signup'));
  $('#toSignup').addEventListener('click', () => setMode('signup'));
  $('#toLogin').addEventListener('click', () => setMode('login'));
  tabs.login.addEventListener('click', () => setMode('login'));
  tabs.signup.addEventListener('click', () => setMode('signup'));
  $('#authClose').addEventListener('click', closeAuth);
  modal.addEventListener('click', e => { if (e.target === modal) closeAuth(); });
  modal.addEventListener('close', () => { pending = null; });

  function signIn(email, name) {
    store.set(SESSION, { email, name });
    const p = pending; pending = null;
    closeAuth(); applySession();
    if (p) p();
  }
  forms.signup.addEventListener('submit', async e => {
    e.preventDefault();
    const name = $('#suName').value.trim(), email = $('#suEmail').value.trim().toLowerCase();
    const p1 = $('#suPass').value, p2 = $('#suPass2').value;
    if (!name) return fail('Enter your full name.');
    if (!EMAIL.test(email)) return fail('Enter a valid email address.');
    if (p1.length < 8) return fail('Use a password of at least 8 characters.');
    if (p1 !== p2) return fail('The passwords do not match.');
    const users = store.get(USERS, {});
    if (users[email]) return fail('An account with this email already exists. Log in instead.');
    const salt = Array.from(crypto.getRandomValues(new Uint8Array(8)), b => b.toString(16).padStart(2, '0')).join('');
    users[email] = { name, salt, hash: await hash(p1, salt) };
    store.set(USERS, users);
    forms.signup.reset();
    signIn(email, name);
  });
  forms.login.addEventListener('submit', async e => {
    e.preventDefault();
    const email = $('#liEmail').value.trim().toLowerCase(), pw = $('#liPass').value;
    if (!EMAIL.test(email) || !pw) return fail('Enter your email and password.');
    const u = store.get(USERS, {})[email];
    if (!u || u.hash !== await hash(pw, u.salt)) return fail('Incorrect email or password.');
    forms.login.reset();
    signIn(email, u.name);
  });
  $('#logout').addEventListener('click', () => { store.set(SESSION, null); closeMenus(); applySession(); });

  const guestTitle = $('#welcomeTitle').textContent, guestMsg = $('#welcomeMsg').textContent;
  function applySession() {
    const s = getSession();
    $('#authGuest').hidden = !!s; $('#authUser').hidden = !s;
    $('#welcomeGuest').hidden = !!s; $('#welcomeUser').hidden = !s;
    $('#navHistory').hidden = !s;
    if (s) {
      const first = s.name.split(' ')[0];
      $('#avatar').textContent = [...s.name][0].toUpperCase();
      $('#uname').textContent = first;
      $('#uemail').textContent = s.email;
      $('#welcomeTitle').textContent = 'Welcome back, ' + first;
      $('#welcomeMsg').textContent = 'Search a route above to find your next flight, or review your trips under My Bookings.';
    } else {
      $('#welcomeTitle').textContent = guestTitle;
      $('#welcomeMsg').textContent = guestMsg;
    }
    if ($('#v-bookings').classList.contains('show')) renderBookings();
    if ($('#v-history').classList.contains('show')) renderHistory();
  }
  const requireLogin = fn => { if (getSession()) fn(); else { pending = fn; openAuth('login'); } };

  /* ---------- Airports & flight generator ---------- */
  const AIRPORTS = {
    JNB: ['Johannesburg', -26.14, 28.25], CPT: ['Cape Town', -33.97, 18.60], LHR: ['London', 51.47, -0.45],
    DXB: ['Dubai', 25.25, 55.36], JFK: ['New York', 40.64, -73.78], CDG: ['Paris', 49.01, 2.55],
    SIN: ['Singapore', 1.36, 103.99], SYD: ['Sydney', -33.94, 151.18], HND: ['Tokyo', 35.55, 139.78], NBO: ['Nairobi', -1.32, 36.93]
  };
  const label = c => `${AIRPORTS[c][0]} (${c})`;
  function resolve(text) {
    const t = text.trim(); if (!t) return null;
    const m = t.match(/\(([A-Za-z]{3})\)/) || t.match(/^([A-Za-z]{3})$/);
    if (m && AIRPORTS[m[1].toUpperCase()]) return m[1].toUpperCase();
    const low = t.toLowerCase();
    return Object.keys(AIRPORTS).find(c => { const n = AIRPORTS[c][0].toLowerCase(); return n.startsWith(low) || low.startsWith(n); }) || null;
  }
  function km(a, b) {
    const rad = d => d * Math.PI / 180, [, la1, lo1] = AIRPORTS[a], [, la2, lo2] = AIRPORTS[b];
    const x = Math.sin(rad(la2 - la1) / 2) ** 2 + Math.cos(rad(la1)) * Math.cos(rad(la2)) * Math.sin(rad(lo2 - lo1) / 2) ** 2;
    return 12742 * Math.asin(Math.sqrt(x));
  }
  const seedOf = s => { let h = 2166136261; for (const c of s) { h ^= c.charCodeAt(0); h = Math.imul(h, 16777619); } return h >>> 0; };
  function rng(a) {
    return () => { a |= 0; a = a + 0x6D2B79F5 | 0; let t = Math.imul(a ^ a >>> 15, 1 | a); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; };
  }
  const hhmm = m => { m = ((m % 1440) + 1440) % 1440; return String(Math.floor(m / 60)).padStart(2, '0') + ':' + String(m % 60).padStart(2, '0'); };
  const dur = m => `${Math.floor(m / 60)}h ${String(m % 60).padStart(2, '0')}m`;

  function makeFlights(from, to, date) {
    const d = km(from, to), r = rng(seedOf(from + to + date));
    const base = 300 + d * (d > 3000 ? 1.05 : .7);
    const air = d > 6000 ? ['Airbus A350', 'Boeing 787'] : d > 2500 ? ['Airbus A330', 'Boeing 787'] : ['Airbus A320', 'Boeing 737'];
    const slots = [6 * 60, 9 * 60 + 30, 12 * 60 + 45, 15 * 60 + 20, 18 * 60 + 10, 21 * 60 + 40];
    const list = slots.map((s, i) => {
      const stops = d > 7000 && i % 2 ? 1 : 0;
      const dep = s + Math.round(r() * 4) * 5;
      const mins = Math.round((d / (d > 3000 ? 850 : 700) * 60 + 45 + stops * 150) / 5) * 5;
      const price = Math.max(790, Math.round(base * (.85 + r() * .5) * (stops ? .88 : 1) / 100) * 100 - 10);
      return { dep, mins, stops, price, no: 'AU' + (100 + Math.floor(r() * 899)), plane: air[Math.floor(r() * air.length)], from, to, date };
    });
    const low = Math.min(...list.map(f => f.price));
    list.forEach(f => { f.low = f.price === low; });
    return list;
  }

  /* ---------- Search & results ---------- */
  const dateIn = $('#date');
  const isoDay = d => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
  dateIn.min = isoDay(new Date());
  const fmtDate = iso => new Date(iso + 'T12:00:00').toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
  const smooth = reduce ? 'auto' : 'smooth';

  $('#searchForm').addEventListener('submit', e => {
    e.preventDefault();
    const err = $('#err'), from = resolve($('#from').value), to = resolve($('#to').value);
    const date = dateIn.value, pax = parseInt($('#pax').value, 10);
    err.textContent = '';
    if (!from) return void (err.textContent = 'Choose a departure airport from the list.');
    if (!to) return void (err.textContent = 'Choose a destination airport from the list.');
    if (from === to) return void (err.textContent = 'Departure and destination must be different.');
    if (!date) return void (err.textContent = 'Select a departure date.');
    if (date < dateIn.min) return void (err.textContent = 'The departure date cannot be in the past.');
    $('#from').value = label(from); $('#to').value = label(to);
    renderResults({ from, to, date, pax }, makeFlights(from, to, date));
    logSearch({ from, to, date, pax });
    location.hash = '#flights';
    if (location.hash === '#flights') show('flights');
  });

  function renderResults(q, flights) {
    $('#sub').textContent = `${label(q.from)} → ${label(q.to)} · ${fmtDate(q.date)} · ${q.pax} passenger${q.pax > 1 ? 's' : ''}`;
    const box = $('#results'); box.replaceChildren();
    flights.forEach(f => {
      const end = f.dep + f.mins, next = Math.floor(end / 1440);
      const art = el('article', 'flight'), info = el('div', 'f-info'), main = el('div', 'f-main');
      const t1 = el('div', 'f-t'), t2 = el('div', 'f-t'), line = el('div', 'f-line');
      t1.append(el('strong', '', hhmm(f.dep)), el('span', '', f.from));
      t2.append(el('strong', '', hhmm(end) + (next ? ` +${next}` : '')), el('span', '', f.to));
      line.append(el('span', '', dur(f.mins)), el('i'), el('span', '', f.stops ? '1 stop' : 'Non-stop'));
      main.append(t1, line, t2);
      const meta = el('div', 'f-meta', `${f.no} · ${f.plane}`);
      if (f.low) meta.append(el('span', 'tag', 'Lowest fare'));
      info.append(main, meta);
      const buy = el('div', 'f-buy'), btn = el('button', 'btn', 'Book');
      btn.type = 'button';
      btn.addEventListener('click', () => requireLogin(() => book(f, q.pax)));
      buy.append(el('strong', '', money(f.price)), el('small', '', 'per person'), el('small', '', `${money(f.price * q.pax)} total`), btn);
      art.append(info, buy);
      box.append(art);
    });
  }

  /* ---------- Bookings & history ---------- */
  let justBooked = null, histFilter = 'all';
  const SEARCHES = 'aurum-searches';
  const myBookings = () => { const s = getSession(); return s ? store.get(BOOKINGS, []).filter(b => b.email === s.email) : []; };
  const goTo = name => { if (location.hash === '#' + name) show(name); else location.hash = '#' + name; };
  function statusOf(b) {
    if (b.status === 'cancelled') return 'cancelled';
    const n = new Date(), today = isoDay(n), mins = n.getHours() * 60 + n.getMinutes();
    return b.date < today || (b.date === today && b.dep < mins) ? 'completed' : 'upcoming';
  }

  function book(f, pax) {
    const s = getSession(), all = store.get(BOOKINGS, []);
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    const ref = 'AU' + Array.from({ length: 6 }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
    all.push({ ref, email: s.email, from: f.from, to: f.to, date: f.date, dep: f.dep, mins: f.mins, no: f.no, pax, total: f.price * pax, stops: f.stops, status: 'confirmed', bookedAt: Date.now() });
    store.set(BOOKINGS, all);
    justBooked = ref;
    goTo('bookings');
  }

  function bookingRow(b, opts = {}) {
    const st = statusOf(b), end = b.dep + b.mins, next = Math.floor(end / 1440);
    const art = el('article', 'flight booking' + (b.ref === justBooked ? ' new' : '') + (st === 'cancelled' ? ' cancelled' : ''));
    const info = el('div', 'f-info'), main = el('div', 'f-main'), t1 = el('div', 'f-t'), t2 = el('div', 'f-t'), line = el('div', 'f-line');
    t1.append(el('strong', '', hhmm(b.dep)), el('span', '', label(b.from)));
    t2.append(el('strong', '', hhmm(end) + (next ? ` +${next}` : '')), el('span', '', label(b.to)));
    line.append(el('span', '', dur(b.mins)), el('i'), el('span', '', b.stops ? '1 stop' : 'Non-stop'));
    main.append(t1, line, t2);
    const meta = el('div', 'f-meta');
    meta.append(el('span', 'ref', b.ref), document.createTextNode(`${b.no} · ${fmtDate(b.date)} · ${b.pax} passenger${b.pax > 1 ? 's' : ''}`), el('span', 'badge ' + st, st[0].toUpperCase() + st.slice(1)));
    info.append(main, meta);
    const buy = el('div', 'f-buy');
    buy.append(el('strong', '', money(b.total)), el('small', '', st === 'cancelled' ? 'cancelled' : 'total paid'));
    if (opts.cancel && st === 'upcoming') {
      const cancel = el('button', 'auth-btn danger', 'Cancel booking'); cancel.type = 'button';
      cancel.addEventListener('click', () => {
        if (!confirm(`Cancel booking ${b.ref}?`)) return;
        const all = store.get(BOOKINGS, []), x = all.find(r => r.ref === b.ref);
        if (x) { x.status = 'cancelled'; x.cancelledAt = Date.now(); store.set(BOOKINGS, all); }
        renderBookings(); renderHistory();
      });
      buy.append(cancel);
    }
    art.append(info, buy);
    return art;
  }

  function loginPrompt(box, text) {
    const row = el('div', 'empty', text), b = el('button', 'btn', 'Log in'); b.type = 'button';
    b.addEventListener('click', () => openAuth('login'));
    row.append(b); box.append(row);
  }

  function renderBookings() {
    const box = $('#bookings'), s = getSession();
    box.replaceChildren();
    if (!s) return loginPrompt(box, 'Log in to see your bookings.');
    const upcoming = myBookings().filter(b => statusOf(b) === 'upcoming').reverse().sort((a, b) => (b.bookedAt || 0) - (a.bookedAt || 0));
    if (!upcoming.length) {
      const row = el('div', 'empty', 'You have no upcoming bookings.');
      const a = el('a', 'btn', 'Find a flight'); a.href = '#home'; a.addEventListener('click', () => { if (location.hash === '#home') show('home'); });
      row.append(a);
      if (myBookings().length) { const h = el('a', 'auth-btn', 'View history'); h.href = '#history'; row.append(h); }
      box.append(row); return;
    }
    if (justBooked) box.append(el('p', 'flash', `Booking confirmed. Your reference is ${justBooked}.`));
    upcoming.forEach(b => box.append(bookingRow(b, { cancel: true })));
    justBooked = null;
  }

  /* ---- search history ---- */
  const mySearches = () => { const s = getSession(); return s ? (store.get(SEARCHES, {})[s.email] || []) : []; };
  function logSearch(q) {
    const s = getSession(); if (!s) return;
    const all = store.get(SEARCHES, {});
    const list = (all[s.email] || []).filter(x => !(x.from === q.from && x.to === q.to && x.date === q.date && x.pax === q.pax));
    list.unshift({ ...q, at: Date.now() });
    all[s.email] = list.slice(0, 10);
    store.set(SEARCHES, all);
  }
  function searchAgain(x) {
    $('#from').value = label(x.from); $('#to').value = label(x.to);
    $('#pax').value = `${x.pax} Passenger${x.pax > 1 ? 's' : ''}`;
    if (x.date < dateIn.min) {
      dateIn.value = '';
      $('#err').textContent = 'That date has passed. Choose a new departure date.';
      return goTo('home');
    }
    dateIn.value = x.date; $('#err').textContent = '';
    const q = { from: x.from, to: x.to, date: x.date, pax: x.pax };
    renderResults(q, makeFlights(q.from, q.to, q.date));
    logSearch(q);
    goTo('flights');
  }

  /* ---- history view ---- */
  function renderHistory() {
    const s = getSession();
    const stats = $('#histStats'), list = $('#histList'), box = $('#histSearches'), chips = $('#histChips'), head = $('.hist-head');
    if (!stats) return;
    [stats, list, box].forEach(n => n.replaceChildren());
    [stats, chips, head, box].forEach(n => { n.hidden = !s; });
    if (!s) return loginPrompt(list, 'Log in to see your booking and search history.');

    const all = myBookings().slice().reverse().sort((a, b) => (b.bookedAt || 0) - (a.bookedAt || 0));
    const kept = all.filter(b => statusOf(b) !== 'cancelled');
    const figures = [[String(all.length), 'Trips booked'], [money(kept.reduce((n, b) => n + b.total, 0)), 'Total spent'],
      [String(new Set(kept.map(b => b.to)).size), 'Destinations'], [String(all.length - kept.length), 'Cancelled']];
    figures.forEach(([n, l]) => { const d = el('div', 'panel stat'); d.append(el('strong', '', n), el('span', '', l)); stats.append(d); });

    $$('.chip', chips).forEach(c => c.setAttribute('aria-pressed', c.dataset.filter === histFilter));
    const shown = all.filter(b => histFilter === 'all' || statusOf(b) === histFilter);
    if (!shown.length) list.append(el('p', 'empty', all.length ? 'No trips match this filter.' : 'Your trips will appear here once you book a flight.'));
    shown.forEach(b => list.append(bookingRow(b, { cancel: true })));

    const searches = mySearches();
    $('#histClear').hidden = !searches.length;
    if (!searches.length) box.append(el('p', 'empty', 'No searches yet. Your latest routes will be saved here.'));
    searches.forEach(x => {
      const row = el('div', 'search-row'), txt = el('div'), btn = el('button', 'auth-btn', 'Search again'); btn.type = 'button';
      txt.append(el('strong', '', `${label(x.from)} → ${label(x.to)}`),
        el('small', '', `${fmtDate(x.date)} · ${x.pax} passenger${x.pax > 1 ? 's' : ''} · searched ${new Date(x.at).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })}`));
      btn.addEventListener('click', () => searchAgain(x));
      row.append(txt, btn); box.append(row);
    });
  }
  $('#histChips').addEventListener('click', e => {
    const c = e.target.closest('.chip'); if (!c) return;
    histFilter = c.dataset.filter; renderHistory();
  });
  $('#histClear').addEventListener('click', () => {
    const s = getSession(); if (!s) return;
    const all = store.get(SEARCHES, {}); all[s.email] = []; store.set(SEARCHES, all); renderHistory();
  });

  /* ---------- Offer & destination cards fill the search ---------- */
  $$('#offers .card, #destinations .card').forEach(c => {
    c.classList.add('link'); c.tabIndex = 0; c.setAttribute('role', 'button');
    const go = () => {
      const code = resolve($('h3', c).textContent.replace(/\s+from\s+.*/i, ''));
      if (!code) return;
      $('#to').value = label(code);
      if (!resolve($('#from').value)) $('#from').value = label(code === 'JNB' ? 'CPT' : 'JNB');
      $('#searchForm').scrollIntoView({ behavior: smooth, block: 'center' });
      dateIn.focus({ preventScroll: true });
    };
    c.addEventListener('click', go);
    c.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); go(); } });
  });

  /* ---------- Contact form ---------- */
  $('#contactForm').addEventListener('submit', e => {
    e.preventDefault();
    $('#cok').textContent = `Thanks, ${$('#cn').value.trim()}. We'll reply to ${$('#ce').value.trim()} within 24 hours.`;
    e.target.reset();
  });

  /* ---------- About page: extra sections (edit the text below) ---------- */
  (function buildAbout() {
    const host = $('#v-about');
    if (!host) return;
    const section = title => { const s = el('section', 'about-sec'); if (title) s.append(el('h3', '', title)); host.append(s); return s; };
    const card = (title, text, tag = 'div') => { const c = el(tag, 'card'); c.append(el('h3', '', title), el('p', '', text)); return c; };

    const stats = el('div', 'stats');
    [['Many', 'Places to fly'], ['6', 'Continents'], ['24/7', 'Human support'], ['2M+', 'Travellers served']].forEach(([n, l]) => {
      const d = el('div', 'panel stat'); d.append(el('strong', '', n), el('span', '', l)); stats.append(d);
    });
    host.append(stats);

    const story = section('Our story'), wrap = el('div', 'story'), text = el('div', 'panel'), tlBox = el('div', 'panel'), tl = el('ul', 'timeline');
    ['CHANGE began in Johannesburg with a simple frustration: booking a flight should not take ten tabs and a calculator. We set out to build one place where every fare is shown in full, with taxes and fees included, before you commit.',
     'Today we work with leading airlines on every continent, so you can compare real options and choose the seat that suits your plans and budget. Our team stays on call day and night, because travel plans do not keep office hours.']
      .forEach(t => text.append(el('p', '', t)));
    [['2012', 'Founded in Johannesburg with five routes.'], ['2016', 'Passed 100 destinations and introduced flexible fares.'],
     ['2020', 'Opened round-the-clock support.'], ['2023', 'Made comparing fares simple.'], ['2026', 'Now flying to many places around the world.']]
      .forEach(([y, t]) => { const li = el('li'); li.append(el('b', '', y), el('span', '', t)); tl.append(li); });
    tlBox.append(tl); wrap.append(text, tlBox); story.append(wrap);

    const values = section('What we stand for'), vg = el('div', 'grid');
    vg.style.marginTop = '0';
    [['Transparent pricing', 'The fare you see is the fare you pay, with taxes and airport fees included.'],
     ['Secure by design', 'Trusted payment partners help keep every booking safe.'],
     ['People, not scripts', 'Real agents are available 24 hours a day to answer questions or rebook you.'],
     ['Fair prices', 'Compare flights side by side and pick what suits you.']]
      .forEach(([t, p]) => vg.append(card(t, p)));
    values.append(vg);

    const how = section('How booking works'), steps = el('ol', 'steps');
    [['Search', 'Choose your route, date and number of passengers.'], ['Compare', 'See every flight with times, stops and the total price.'],
     ['Book', 'Log in, pick your flight and confirm in a few clicks.'], ['Fly', 'Review or cancel your trip any time under My Bookings.']]
      .forEach(([t, p]) => steps.append(card(t, p, 'li')));
    how.append(steps);

    const faq = section('Questions travellers ask');
    [['Are taxes and fees included in the price?', 'Yes. Every fare shown on CHANGE includes taxes and airport fees, so there are no surprises at checkout.'],
     ['How do I compare flights?', 'Search your route and date, then compare times, stops and prices on the Flights page.'],
     ['Can I cancel a booking?', 'Yes. Open My Bookings and choose Cancel booking next to your trip. Refund rules depend on the fare you booked.'],
     ['Is my payment information safe?', 'Payments are handled by trusted payment partners to help keep your details safe.'],
     ['When can I reach support?', 'Every day of the year, 24 hours a day, through the Contact page.']]
      .forEach(([q, a]) => { const d = el('details', 'faq'); d.append(el('summary', '', q), el('p', '', a)); faq.append(d); });

    const cta = section(''), box = el('div', 'panel about-cta'), acts = el('div', 'welcome-actions');
    const a1 = el('a', 'btn', 'Search flights'), a2 = el('a', 'auth-btn', 'Talk to us');
    a1.href = '#home'; a2.href = '#contact';
    acts.append(a1, a2);
    box.append(el('h3', '', 'Ready to fly?'), el('p', '', 'Find your next flight in under a minute, or ask our team anything first.'), acts);
    cta.append(box);
  })();

  /* ---------- Home layout: welcome panel sits above the hero ---------- */
  const heroEl = $('.hero'), welcomeEl = $('#welcome');
  if (heroEl && welcomeEl) heroEl.before(welcomeEl);

  /* ---------- Init ---------- */
  applySession();
  show(location.hash.slice(1) || 'home');
})();