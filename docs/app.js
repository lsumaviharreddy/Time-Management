(() => {
'use strict';

/* ---------- small helpers ---------- */
const $ = (s, r = document) => r.querySelector(s);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const pad = (n) => String(n).padStart(2, '0');
const ymd = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const parseYmd = (s) => { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d); };
const addDays = (d, n) => { const x = new Date(d); x.setDate(x.getDate() + n); return x; };
const todayD = () => { const n = new Date(); return new Date(n.getFullYear(), n.getMonth(), n.getDate()); };
const wdIdx = (d) => (d.getDay() + 6) % 7; // Monday = 0
const mondayOf = (d) => addDays(d, -wdIdx(d));
const DAYS = ['MO', 'TU', 'WE', 'TH', 'FR', 'SA', 'SU'];
const DAY_LETTER = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
const DAY_SHORT = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
const fmtTime = (m) => { m = ((m % 1440) + 1440) % 1440; let h = Math.floor(m / 60); const ap = h >= 12 ? 'PM' : 'AM'; h = h % 12 || 12; return `${h}:${pad(m % 60)} ${ap}`; };
const fmtDur = (m) => (m >= 60 ? `${Math.floor(m / 60)}h${m % 60 ? ' ' + (m % 60) + 'm' : ''}` : `${m}m`);
const fmtDayLong = (d) => `${DAY_SHORT[wdIdx(d)]}, ${d.getDate()} ${MONTHS[d.getMonth()].slice(0, 3)} ${d.getFullYear()}`;
const toMin = (hhmm) => { const [h, m] = hhmm.split(':').map(Number); return h * 60 + m; };
const fromMin = (m) => `${pad(Math.floor(m / 60) % 24)}:${pad(m % 60)}`;
const TZ = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
const TASK_COLORS = ['#3F51B5', '#009688', '#F57C00', '#D81B60', '#43A047', '#8E24AA'];
const THEMES = [['sky', 'Sky blue', '#81D4FA'], ['orange', 'Orange', '#FFB74D'], ['red', 'Red', '#EF9A9A'], ['gray', 'Gray', '#B0BEC5'], ['black', 'Black', '#000000'], ['white', 'White', '#FFFFFF']];
const SCOPE = 'https://www.googleapis.com/auth/calendar.events https://www.googleapis.com/auth/calendar.calendarlist.readonly';

const lsGet = (k, def) => { try { const v = localStorage.getItem('tb_' + k); return v == null ? def : JSON.parse(v); } catch { return def; } };
const lsSet = (k, v) => { try { localStorage.setItem('tb_' + k, JSON.stringify(v)); } catch { /* storage blocked */ } };
const lsDel = (k) => { try { localStorage.removeItem('tb_' + k); } catch { /* ignore */ } };

/* ---------- repeating rules (same model as the Android app) ---------- */
// rule: 0 none, 1 daily, 2 weekly (mask bit0 = Monday), 3 monthly (same day number)
function matchesRule(s, d) {
  if (s.rule === 1) return true;
  if (s.rule === 2) return (s.mask & (1 << wdIdx(d))) !== 0;
  if (s.rule === 3) return d.getDate() === parseYmd(s.startDate).getDate();
  return false;
}
function occursOn(s, d) {
  const ds = ymd(d);
  if (ds < s.startDate) return false;
  if (s.endDate && ds > s.endDate) return false;
  if ((s.skipped || []).includes(ds)) return false;
  return matchesRule(s, d);
}
function firstOccurrence(startDate, rule, mask) {
  let d = parseYmd(startDate);
  for (let i = 0; i < 400; i++, d = addDays(d, 1)) {
    if (matchesRule({ rule, mask, startDate }, d)) return ymd(d);
  }
  return startDate;
}
function ruleSummary(s) {
  let t = 'Repeats';
  if (s.rule === 1) t = 'Daily';
  else if (s.rule === 2) t = s.mask === 127 ? 'Daily' : 'Weekly on ' + DAY_SHORT.filter((_, i) => s.mask & (1 << i)).join(', ');
  else if (s.rule === 3) t = `Monthly on day ${parseYmd(s.startDate).getDate()}`;
  if (s.complex) t = 'Custom repeat';
  if (s.endDate) t += ` until ${fmtDayLong(parseYmd(s.endDate))}`;
  return t;
}

/* ---------- local (demo) backend ---------- */
const Local = {
  db() { return lsGet('demo', { tasks: [], series: [], next: 1 }); },
  save(db) { lsSet('demo', db); },
  async range(from, to) {
    const db = Local.db(); const a = ymd(from), b = ymd(to); const out = [];
    db.tasks.filter((t) => t.date >= a && t.date <= b).forEach((t) => out.push({ ...t, seriesId: null }));
    for (const s of db.series) {
      for (let d = parseYmd(a); ymd(d) <= b; d = addDays(d, 1)) {
        if (occursOn(s, d)) {
          const ds = ymd(d);
          out.push({ id: `s${s.id}|${ds}`, title: s.title, date: ds, start: s.start, dur: s.dur, done: (s.doneDates || []).includes(ds), color: s.color, seriesId: s.id });
        }
      }
    }
    return out;
  },
  async saveTask(t) {
    const db = Local.db();
    if (t.id) { const i = db.tasks.findIndex((x) => x.id === t.id); if (i >= 0) db.tasks[i] = { ...t }; } else { db.tasks.push({ ...t, id: 't' + db.next++ }); }
    Local.save(db);
  },
  async setDone(t, done) {
    const db = Local.db();
    if (t.seriesId) {
      const s = db.series.find((x) => x.id === t.seriesId); if (!s) return;
      s.doneDates = (s.doneDates || []).filter((x) => x !== t.date); if (done) s.doneDates.push(t.date);
    } else { const x = db.tasks.find((y) => y.id === t.id); if (x) x.done = done; }
    Local.save(db);
  },
  async deleteTask(t) { const db = Local.db(); db.tasks = db.tasks.filter((x) => x.id !== t.id); Local.save(db); },
  async skip(t) { const db = Local.db(); const s = db.series.find((x) => x.id === t.seriesId); if (s) { s.skipped = s.skipped || []; s.skipped.push(t.date); } Local.save(db); },
  async series() { return Local.db().series.map((s) => ({ ...s })); },
  async getSeries(id) { const s = Local.db().series.find((x) => x.id === id); return s ? { ...s } : null; },
  async saveSeries(s) {
    const db = Local.db();
    if (s.id) { const i = db.series.findIndex((x) => x.id === s.id); if (i >= 0) db.series[i] = { ...db.series[i], ...s }; } else { db.series.push({ ...s, id: 'r' + db.next++, skipped: [], doneDates: [] }); }
    Local.save(db);
  },
  async deleteSeries(id) { const db = Local.db(); db.series = db.series.filter((x) => x.id !== id); Local.save(db); },
};

/* ---------- Google Calendar backend ---------- */
function validToken() { return S.token && S.token.exp > Date.now() ? S.token.access : null; }
const localDT = (dateStr, min) => { const d = parseYmd(dateStr); d.setMinutes(min); return `${ymd(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`; };

function evToTask(ev) {
  if (ev.status === 'cancelled' || !ev.start || !ev.start.dateTime || !ev.end || !ev.end.dateTime) return null; // all-day events are ignored
  const s = new Date(ev.start.dateTime), e = new Date(ev.end.dateTime);
  const start = s.getHours() * 60 + s.getMinutes();
  const dur = Math.min(Math.max(5, Math.round((e - s) / 60000)), 1440 - start);
  let title = ev.summary || '(no title)', done = false;
  if (title.startsWith('✓')) { done = true; title = title.replace(/^✓\s*/, ''); }
  const color = parseInt(((ev.extendedProperties || {}).private || {}).tb_color || '0', 10) || 0;
  return { id: ev.id, title, date: ymd(s), start, dur, done, color, seriesId: ev.recurringEventId || null };
}
function parseUntil(v) {
  if (!v) return null;
  const m = /^(\d{4})(\d{2})(\d{2})(?:T(\d{2})(\d{2})(\d{2})Z?)?$/.exec(v);
  if (!m) return null;
  if (m[4] === undefined) return `${m[1]}-${m[2]}-${m[3]}`;
  return ymd(new Date(Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +m[6])));
}
function evToSeries(ev) {
  if (!ev.start || !ev.start.dateTime || !ev.recurrence) return null;
  const rr = ev.recurrence.find((r) => r.startsWith('RRULE:'));
  if (!rr) return null;
  const p = {};
  rr.slice(6).split(';').forEach((x) => { const i = x.indexOf('='); if (i > 0) p[x.slice(0, i)] = x.slice(i + 1); });
  if (p.INTERVAL === '1') delete p.INTERVAL;
  const s = new Date(ev.start.dateTime), e = new Date(ev.end.dateTime);
  let complex = Object.keys(p).some((k) => !['FREQ', 'BYDAY', 'BYMONTHDAY', 'UNTIL', 'WKST'].includes(k));
  let rule = 1, mask = 0;
  if (p.FREQ === 'DAILY') { rule = 1; if (p.BYDAY || p.BYMONTHDAY) complex = true; }
  else if (p.FREQ === 'WEEKLY') {
    rule = 2;
    if (p.BYDAY) p.BYDAY.split(',').forEach((t) => { const i = DAYS.indexOf(t); if (i < 0) complex = true; else mask |= 1 << i; });
    else mask = 1 << wdIdx(s);
    if (p.BYMONTHDAY) complex = true;
  } else if (p.FREQ === 'MONTHLY') {
    rule = 3;
    if (p.BYDAY || (p.BYMONTHDAY && parseInt(p.BYMONTHDAY, 10) !== s.getDate())) complex = true;
  } else { complex = true; }
  const t = evToTask({ ...ev, recurringEventId: null });
  return { id: ev.id, title: t ? t.title : (ev.summary || ''), startDate: ymd(s), start: s.getHours() * 60 + s.getMinutes(), dur: Math.max(5, Math.round((e - s) / 60000)), color: t ? t.color : 0, rule, mask, endDate: parseUntil(p.UNTIL), complex, skipped: [] };
}
function rrule(s) {
  let r = 'RRULE:FREQ=' + (s.rule === 1 ? 'DAILY' : s.rule === 2 ? 'WEEKLY' : 'MONTHLY');
  if (s.rule === 2) r += ';BYDAY=' + DAYS.filter((_, i) => s.mask & (1 << i)).join(',');
  if (s.rule === 3) r += ';BYMONTHDAY=' + parseYmd(s.startDate).getDate();
  if (s.endDate) {
    const e = parseYmd(s.endDate); e.setHours(23, 59, 59, 0);
    r += ';UNTIL=' + e.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
  }
  return r;
}
function bodyFor(t, dateStr) {
  return {
    summary: (t.done ? '✓ ' : '') + t.title,
    start: { dateTime: localDT(dateStr, t.start), timeZone: TZ },
    end: { dateTime: localDT(dateStr, t.start + t.dur), timeZone: TZ },
    extendedProperties: { private: { tb_color: String(t.color || 0) } },
  };
}

const G = {
  base: 'https://www.googleapis.com/calendar/v3',
  async req(method, path, body, params) {
    const tok = validToken();
    if (!tok) { S.needAuth = true; render(); throw new Error('auth'); }
    const url = new URL(G.base + path);
    Object.entries(params || {}).forEach(([k, v]) => { if (v != null) url.searchParams.set(k, v); });
    const res = await fetch(url, { method, headers: { Authorization: 'Bearer ' + tok, ...(body ? { 'Content-Type': 'application/json' } : {}) }, body: body ? JSON.stringify(body) : undefined });
    if (res.status === 401) { S.token = null; lsDel('tok'); S.needAuth = true; render(); throw new Error('auth'); }
    if (res.status === 204) return null;
    if ((res.status === 404 || res.status === 410) && method === 'DELETE') return null;
    if (!res.ok) { const t = await res.text(); throw new Error(`Google Calendar error ${res.status}: ${t.slice(0, 160)}`); }
    return res.json();
  },
  ev() { return `/calendars/${encodeURIComponent(S.calId)}/events`; },
  async list(params) {
    let items = [], pageToken;
    do {
      const r = await G.req('GET', G.ev(), null, { ...params, maxResults: 2500, pageToken });
      items = items.concat(r.items || []); pageToken = r.nextPageToken;
    } while (pageToken);
    return items;
  },
  async calendars() { const r = await G.req('GET', '/users/me/calendarList', null, { minAccessRole: 'writer' }); return r.items || []; },
  async range(from, to) {
    const items = await G.list({ singleEvents: 'true', orderBy: 'startTime', timeMin: from.toISOString(), timeMax: addDays(to, 1).toISOString() });
    return items.map(evToTask).filter(Boolean);
  },
  async saveTask(t) {
    const body = bodyFor(t, t.date);
    if (t.id) await G.req('PATCH', `${G.ev()}/${encodeURIComponent(t.id)}`, body);
    else await G.req('POST', G.ev(), body);
  },
  async setDone(t, done) {
    await G.req('PATCH', `${G.ev()}/${encodeURIComponent(t.id)}`, { summary: (done ? '✓ ' : '') + t.title });
  },
  async deleteTask(t) { await G.req('DELETE', `${G.ev()}/${encodeURIComponent(t.id)}`); },
  async skip(t) { await G.req('DELETE', `${G.ev()}/${encodeURIComponent(t.id)}`); },
  async series() {
    const items = await G.list({ singleEvents: 'false', timeMin: todayD().toISOString() });
    return items.map(evToSeries).filter(Boolean);
  },
  async getSeries(id) { return evToSeries(await G.req('GET', `${G.ev()}/${encodeURIComponent(id)}`)); },
  async saveSeries(s) {
    const body = bodyFor({ ...s, done: false }, s.startDate);
    if (!s.complex) body.recurrence = [rrule(s)];
    if (s.id) await G.req('PATCH', `${G.ev()}/${encodeURIComponent(s.id)}`, body);
    else await G.req('POST', G.ev(), body);
  },
  async deleteSeries(id) { await G.req('DELETE', `${G.ev()}/${encodeURIComponent(id)}`); },
};
const api = () => (S.mode === 'google' ? G : Local);

/* ---------- state ---------- */
const S = {
  mode: lsGet('mode', null), // 'google' | 'demo' | null
  clientId: lsGet('clientId', '') || ((window.TB_CONFIG || {}).clientId || ''),
  theme: lsGet('theme', 'white'),
  view: lsGet('view', 'day'),
  date: todayD(),
  tasks: [], error: null, lastSync: null,
  calId: lsGet('calId', null), calName: lsGet('calName', ''),
  token: lsGet('tok', null), needAuth: false,
  needScroll: true,
};
if (S.token && S.token.exp <= Date.now()) S.token = null;

/* ---------- toast & dialog ---------- */
let toastTimer;
function toast(msg) {
  const t = $('#toast'); t.textContent = msg; t.hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => { t.hidden = true; }, 4200);
}
function showDlg(html, mount) {
  const d = $('#dlg'); d.innerHTML = `<div class="dbox">${html}</div>`;
  if (!d.open) d.showModal();
  if (mount) mount(d);
  return d;
}
const closeDlg = () => { const d = $('#dlg'); if (d.open) d.close(); };
$('#dlg').addEventListener('click', (e) => { if (e.target === $('#dlg')) closeDlg(); });

/* ---------- auth ---------- */
let tokenClient = null;
function initAuth() {
  if (tokenClient) return true;
  if (!S.clientId || !(window.google && google.accounts && google.accounts.oauth2)) return false;
  tokenClient = google.accounts.oauth2.initTokenClient({
    client_id: S.clientId, scope: SCOPE, callback: onToken,
    error_callback: (e) => toast('Sign-in failed: ' + ((e && (e.type || e.message)) || 'unknown error')),
  });
  return true;
}
function signIn() {
  if (!initAuth()) { toast(S.clientId ? 'Google sign-in is still loading. Try again in a second.' : 'Add your Google client ID first (menu → Settings).'); return; }
  tokenClient.requestAccessToken({ prompt: '' });
}
async function onToken(resp) {
  if (!resp || resp.error) { toast('Sign-in failed: ' + ((resp && resp.error) || 'unknown')); return; }
  S.token = { access: resp.access_token, exp: Date.now() + (Number(resp.expires_in || 3600) - 60) * 1000 };
  lsSet('tok', S.token); S.needAuth = false; S.mode = 'google'; lsSet('mode', 'google');
  try { await ensureCalendar(); } catch (e) { if (e.message !== 'auth') toast(e.message); }
  closeDlg(); await load(); refreshToday();
}
function signOut() {
  try { if (S.token && window.google) google.accounts.oauth2.revoke(S.token.access, () => {}); } catch { /* ignore */ }
  S.token = null; lsDel('tok'); S.mode = null; lsDel('mode'); S.tasks = []; closeDlg(); render();
}
async function ensureCalendar() {
  const cals = await G.calendars();
  if (S.calId && cals.some((c) => c.id === S.calId)) return;
  const pick = cals.find((c) => (c.summary || '').trim().toLowerCase() === 'timeblock') || cals.find((c) => c.primary) || cals[0];
  if (!pick) throw new Error('No writable Google calendar found.');
  S.calId = pick.id; S.calName = pick.summary || pick.id; lsSet('calId', S.calId); lsSet('calName', S.calName);
  if ((pick.summary || '').toLowerCase() !== 'timeblock') toast(`Using calendar "${S.calName}". Change it under ⋮ → Google Calendar.`);
}

/* ---------- loading ---------- */
let loadSeq = 0;
function viewRange() {
  if (S.view === 'day') return [S.date, S.date];
  if (S.view === 'week') { const m = mondayOf(S.date); return [m, addDays(m, 6)]; }
  const g = mondayOf(new Date(S.date.getFullYear(), S.date.getMonth(), 1)); return [g, addDays(g, 41)];
}
async function load() {
  if (!S.mode) { render(); return; }
  if (S.mode === 'google' && (!validToken() || !S.calId)) { if (!validToken()) S.needAuth = true; render(); return; }
  const seq = ++loadSeq; const [a, b] = viewRange();
  try {
    const ts = await api().range(a, b);
    if (seq !== loadSeq) return;
    S.tasks = ts; S.error = null; S.lastSync = new Date();
  } catch (e) { if (e.message !== 'auth') S.error = e.message; }
  render();
}
function setDate(d, scroll = true) {
  const before = viewRange().map(ymd).join();
  S.date = d; if (scroll) S.needScroll = true;
  if (viewRange().map(ymd).join() !== before) load(); else render();
}

/* ---------- timeline rendering ---------- */
const HP = 52;
function placements(list) {
  const sorted = [...list].sort((a, b) => a.start - b.start || b.dur - a.dur);
  const out = []; let cluster = [], clusterEnd = -1;
  const flush = () => {
    if (!cluster.length) return;
    const ends = [];
    const items = cluster.map((t) => {
      let i = ends.findIndex((e) => e <= t.start);
      if (i < 0) { i = ends.length; ends.push(0); }
      ends[i] = t.start + t.dur; return { t, lane: i };
    });
    items.forEach((it) => out.push({ ...it, lanes: ends.length }));
    cluster = [];
  };
  sorted.forEach((t) => {
    if (cluster.length && t.start >= clusterEnd) { flush(); clusterEnd = -1; }
    cluster.push(t); clusterEnd = Math.max(clusterEnd, t.start + t.dur);
  });
  flush(); return out;
}
function blockHtml(p, compact) {
  const { t, lane, lanes } = p;
  const h = Math.max(t.dur / 60 * HP - 2, 16);
  const time = `${fmtTime(t.start)} – ${fmtTime(t.start + t.dur)}`;
  return `<div class="blk ${t.done ? 'done' : ''} ${t.seriesId ? 'rep' : ''}" data-a="task" data-id="${esc(t.id)}" title="${esc(t.title)} · ${time}"
    style="top:${t.start / 60 * HP}px;height:${h}px;left:${lane / lanes * 100}%;width:calc(${100 / lanes}% - 3px);background:${TASK_COLORS[t.color] || TASK_COLORS[0]}">
    <div class="t">${esc(t.title)}</div>${h >= 34 && !compact ? `<div class="m">${time}</div>` : ''}</div>`;
}
function gridHtml(dates) {
  const compact = dates.length > 1;
  const now = new Date(), nowMin = now.getHours() * 60 + now.getMinutes(), today = ymd(now);
  let heads = '';
  if (compact) {
    heads = `<div class="heads">${dates.map((d) => `<div class="h ${ymd(d) === today ? 'today' : ''}" data-a="openday" data-date="${ymd(d)}">${DAY_SHORT[wdIdx(d)]}<b>${d.getDate()}</b></div>`).join('')}</div>`;
  }
  let gut = '', lines = '';
  for (let h = 0; h < 24; h++) {
    if (h) gut += `<span style="top:${h * HP}px">${fmtTime(h * 60).replace(':00', '')}</span>`;
    lines += `<div class="hl" style="top:${h * HP}px"></div><div class="hl half" style="top:${h * HP + HP / 2}px"></div>`;
  }
  const cols = dates.map((d) => {
    const ds = ymd(d);
    const ps = placements(S.tasks.filter((t) => t.date === ds));
    return `<div class="col ${ds === today ? 'today' : ''}" data-a="col" data-date="${ds}">${lines}${ps.map((p) => blockHtml(p, compact)).join('')}${ds === today ? `<div class="nowline" style="top:${nowMin / 60 * HP}px"></div>` : ''}</div>`;
  }).join('');
  return `${heads}<div class="scroll"><div class="grid ${compact ? 'compact' : ''}" style="height:${24 * HP}px"><div class="gutter">${gut}</div><div class="cols">${cols}</div></div></div>`;
}
function monthHtml() {
  const first = new Date(S.date.getFullYear(), S.date.getMonth(), 1);
  const g0 = mondayOf(first), today = ymd(todayD()), sel = ymd(S.date);
  let cells = '';
  for (let i = 0; i < 42; i++) {
    const d = addDays(g0, i), ds = ymd(d);
    const ts = S.tasks.filter((t) => t.date === ds).sort((a, b) => a.start - b.start);
    const dots = ts.slice(0, 5).map((t) => `<span class="dot" style="background:${TASK_COLORS[t.color] || TASK_COLORS[0]};${t.done ? 'opacity:.4' : ''}"></span>`).join('') + (ts.length > 5 ? `<span class="more">+${ts.length - 5}</span>` : '');
    cells += `<div class="cell ${d.getMonth() !== first.getMonth() ? 'dim' : ''} ${ds === sel ? 'sel' : ''} ${ds === today ? 'today' : ''}" data-a="pickday" data-date="${ds}"><div class="n">${d.getDate()}</div><div class="dots">${dots}</div></div>`;
  }
  const list = S.tasks.filter((t) => t.date === sel).sort((a, b) => a.start - b.start);
  const items = list.length ? list.map(itemHtml).join('') : '<div class="empty">Nothing planned.</div>';
  return `<div class="month"><div class="mhead">${DAY_SHORT.map((d) => `<div>${d}</div>`).join('')}</div><div class="mgrid">${cells}</div>
    <div class="dayhead"><h3>${fmtDayLong(S.date)}</h3><button class="btn" data-a="openday" data-date="${sel}">Open day</button><button class="btn primary" data-a="add">+ Add task</button></div>${items}</div>`;
}
function itemHtml(t) {
  return `<div class="item ${t.done ? 'done' : ''}" data-a="task" data-id="${esc(t.id)}"><div class="bar" style="background:${TASK_COLORS[t.color] || TASK_COLORS[0]}"></div>
    <div><div class="tt">${esc(t.title)}${t.seriesId ? ' ↻' : ''}</div><div class="tm">${fmtTime(t.start)} – ${fmtTime(t.start + t.dur)} · ${fmtDur(t.dur)}${t.done ? ' · done' : ''}</div></div></div>`;
}

/* ---------- main render ---------- */
function titleText() {
  if (S.view === 'day') return fmtDayLong(S.date);
  if (S.view === 'week') {
    const m = mondayOf(S.date), e = addDays(m, 6);
    return m.getMonth() === e.getMonth() ? `${m.getDate()} – ${e.getDate()} ${MONTHS[e.getMonth()].slice(0, 3)} ${e.getFullYear()}` : `${m.getDate()} ${MONTHS[m.getMonth()].slice(0, 3)} – ${e.getDate()} ${MONTHS[e.getMonth()].slice(0, 3)} ${e.getFullYear()}`;
  }
  return `${MONTHS[S.date.getMonth()]} ${S.date.getFullYear()}`;
}
function welcomeHtml() {
  return `<div class="welcome"><h1>TimeBlock</h1>
    <div class="note">Plan your day on your laptop. Everything syncs with Google Calendar, so your phone app shows the same tasks.</div>
    <div class="card">
      ${S.clientId ? `<button class="btn primary" data-a="signin">Sign in with Google</button>`
        : `<label class="note">Google client ID (one-time setup, see the README)<input id="cid" placeholder="1234567890-abc.apps.googleusercontent.com" autocomplete="off"></label><button class="btn primary" data-a="saveclient">Save and continue</button>`}
    </div>
    <div class="card"><div class="note">Just looking around? Demo mode keeps tasks only in this browser.</div><button class="btn" data-a="demo">Try demo mode</button></div></div>`;
}
let rendering = false;
function render() {
  if (rendering) return; rendering = true;
  try {
    document.documentElement.dataset.theme = S.theme;
    const meta = document.querySelector('meta[name=theme-color]');
    if (meta) meta.content = getComputedStyle(document.documentElement).getPropertyValue('--bg').trim() || '#ffffff';
    const app = $('#app');
    if (!S.mode) { app.innerHTML = welcomeHtml(); return; }
    const prevScroll = $('.scroll') ? $('.scroll').scrollTop : null;
    let banner = '';
    if (S.mode === 'demo') banner = `<div class="banner"><span>Demo mode: tasks are stored only in this browser.</span><span class="sp"></span><button class="btn" data-a="gcal">Use Google Calendar</button></div>`;
    if (S.mode === 'google' && S.needAuth) banner = `<div class="banner"><span>Session expired. Sign in again to keep syncing.</span><span class="sp"></span><button class="btn primary" data-a="signin">Reconnect</button></div>`;
    if (S.error) banner += `<div class="banner" style="background:rgba(198,40,40,.15)"><span>${esc(S.error)}</span><span class="sp"></span><button class="btn" data-a="refresh">Retry</button></div>`;
    const sync = S.mode === 'google' ? (S.lastSync ? `Synced ${fmtTime(S.lastSync.getHours() * 60 + S.lastSync.getMinutes())}${S.calName ? ' · ' + esc(S.calName) : ''}` : 'Not synced') : 'Demo';
    const focusChip = F.end ? `<span class="focuschip" id="focuschip" data-a="focus">${fmtClock(F.end - Date.now())}</span>` : '';
    let body;
    if (S.mode === 'google' && !validToken() && !S.tasks.length) body = `<div class="welcome"><div class="card"><div class="note">Sign in to load your calendar.</div><button class="btn primary" data-a="signin">Sign in with Google</button></div></div>`;
    else if (S.view === 'month') body = monthHtml();
    else body = gridHtml(S.view === 'day' ? [S.date] : Array.from({ length: 7 }, (_, i) => addDays(mondayOf(S.date), i)));
    app.innerHTML = `<div class="shell"><header>
      <div class="row1"><h1>TimeBlock</h1><span class="sp"></span>${focusChip}<span class="sync ${S.error ? 'err' : ''}">${sync}</span><button class="ibtn" data-a="refresh" title="Sync now">⟳</button><button class="ibtn" data-a="menu" title="Menu">⋮</button></div>
      <div class="row2"><button class="ibtn" data-a="prev">‹</button><button class="btn" data-a="today">Today</button><button class="ibtn" data-a="next">›</button><span class="ptitle">${titleText()}</span>
        <div class="chips">${['day', 'week', 'month'].map((v) => `<button class="chip ${S.view === v ? 'on' : ''}" data-a="view" data-v="${v}">${v[0].toUpperCase() + v.slice(1)}</button>`).join('')}</div>
        <button class="btn primary" data-a="add">+ Add</button></div></header>${banner}<main>${body}</main></div>`;
    const sc = $('.scroll');
    if (sc) {
      if (S.needScroll || prevScroll == null) {
        const isToday = ymd(S.date) === ymd(todayD()) || S.view === 'week';
        const target = isToday ? Math.max(0, (new Date().getHours() - 1) * HP) : 7 * HP;
        sc.scrollTop = target; S.needScroll = false;
      } else sc.scrollTop = prevScroll;
    }
  } finally { rendering = false; }
}

/* ---------- actions ---------- */
const findTask = (id) => S.tasks.find((t) => t.id === id);
function defaultStart(d) {
  if (ymd(d) !== ymd(todayD())) return 9 * 60;
  const n = new Date(); return Math.min(22 * 60, Math.ceil((n.getHours() * 60 + n.getMinutes() + 1) / 15) * 15);
}
const actions = {
  prev() { S.view === 'month' ? setDate(new Date(S.date.getFullYear(), S.date.getMonth() - 1, 1)) : setDate(addDays(S.date, S.view === 'week' ? -7 : -1)); },
  next() { S.view === 'month' ? setDate(new Date(S.date.getFullYear(), S.date.getMonth() + 1, 1)) : setDate(addDays(S.date, S.view === 'week' ? 7 : 1)); },
  today() { setDate(todayD()); },
  view(el) { S.view = el.dataset.v; lsSet('view', S.view); S.needScroll = true; load(); },
  add() { taskForm(null, { date: ymd(S.date), start: defaultStart(S.date) }); },
  task(el) { const t = findTask(el.dataset.id); if (t) openTask(t); },
  col(el, e) {
    const r = el.getBoundingClientRect();
    const min = Math.max(0, Math.min(23 * 60 + 45, Math.floor(((e.clientY - r.top) / HP * 60) / 15) * 15));
    taskForm(null, { date: el.dataset.date, start: min });
  },
  pickday(el) { setDate(parseYmd(el.dataset.date), false); },
  openday(el) { S.view = 'day'; lsSet('view', 'day'); S.date = parseYmd(el.dataset.date); S.needScroll = true; load(); },
  refresh() { load(); },
  menu() { menuDialog(); },
  signin() { signIn(); },
  gcal() { calendarDialog(); },
  focus() { focusDialog(); },
  demo() { S.mode = 'demo'; lsSet('mode', 'demo'); load(); },
  saveclient() { const v = ($('#cid') || {}).value || ''; if (!v.trim()) { toast('Paste your client ID first.'); return; } saveClientId(v); },
};
function saveClientId(v) {
  S.clientId = v.trim(); lsSet('clientId', S.clientId); tokenClient = null; toast('Client ID saved.'); render();
}
$('#app').addEventListener('click', (e) => {
  const el = e.target.closest('[data-a]');
  if (el && actions[el.dataset.a]) actions[el.dataset.a](el, e);
});
document.addEventListener('keydown', (e) => {
  if ($('#dlg').open || /INPUT|SELECT|TEXTAREA/.test((e.target || {}).tagName || '')) return;
  if (e.key === 'ArrowLeft') actions.prev(); else if (e.key === 'ArrowRight') actions.next();
  else if (e.key === 't') actions.today(); else if (e.key === 'n') actions.add();
});

/* ---------- task / series form ---------- */
async function openTask(t) {
  if (t.seriesId) {
    try {
      const s = await api().getSeries(t.seriesId);
      if (s) return taskForm({ kind: 'series', ...s, date: s.startDate, occ: t, done: t.done }, null);
    } catch (e) { if (e.message !== 'auth') toast(e.message); return; }
  }
  taskForm({ kind: 'task', ...t }, null);
}
function taskForm(m, preset) {
  const isNew = !m;
  m = m || { kind: 'new', title: '', date: preset.date, start: preset.start, dur: 30, color: 0, rule: 0, mask: 1 << wdIdx(parseYmd(preset.date)), endDate: null, done: false };
  const showRepeat = m.kind === 'new' || (m.kind === 'series' && !m.complex);
  const heading = isNew ? 'New task' : m.kind === 'series' ? 'Repeating task' : 'Edit task';
  const html = `<h2>${heading}</h2>
    <label>Title<input id="f-title" value="${esc(m.title)}" placeholder="What are you doing?" autocomplete="off"></label>
    <div class="grid2"><label>${m.kind === 'series' ? 'Starts on' : 'Date'}<input type="date" id="f-date" value="${esc(m.date)}"></label><label>Start<input type="time" id="f-start" value="${fromMin(m.start)}"></label></div>
    <div class="grid2"><label>Duration (minutes)<input type="number" id="f-dur" min="5" step="5" value="${m.dur}"></label>
      <label>Quick pick<select id="f-pick"><option value="">…</option>${[15, 30, 45, 60, 90, 120].map((x) => `<option value="${x}">${fmtDur(x)}</option>`).join('')}</select></label></div>
    <div class="swatches" id="f-colors">${TASK_COLORS.map((c, i) => `<button type="button" class="sw ${i === m.color ? 'on' : ''}" data-c="${i}" style="background:${c}" aria-label="Colour ${i + 1}"></button>`).join('')}</div>
    ${showRepeat ? `<label>Repeat<select id="f-rule">${['Does not repeat', 'Every day', 'Every week', 'Every month'].map((x, i) => `<option value="${i}" ${m.rule === i ? 'selected' : ''}>${x}</option>`).join('')}</select></label>
      <div id="f-rep"><div class="wd" id="f-wd">${DAY_LETTER.map((l, i) => `<button type="button" data-d="${i}" class="${m.mask & (1 << i) ? 'on' : ''}" title="${DAY_SHORT[i]}">${l}</button>`).join('')}</div>
      <label style="margin-top:10px">Ends on (optional)<input type="date" id="f-end" value="${esc(m.endDate || '')}"></label></div>` : ''}
    ${m.kind === 'series' && m.complex ? `<div class="note">This task uses a custom repeat pattern (${esc(ruleSummary(m))}). Change the pattern in Google Calendar; title, time and colour can be edited here.</div>` : ''}
    ${m.kind === 'series' ? `<div class="note">${esc(ruleSummary(m))}. Changes apply to every occurrence.</div>` : ''}
    ${m.kind === 'task' || (m.kind === 'series' && m.occ) ? `<label style="flex-direction:row;align-items:center;gap:8px;font-size:14px;color:var(--text)"><input type="checkbox" id="f-done" ${m.done ? 'checked' : ''}> Completed${m.kind === 'series' ? ' (' + esc(fmtDayLong(parseYmd(m.occ.date))) + ')' : ''}</label>` : ''}
    <div id="f-clash"></div>
    <div class="actions">
      ${m.kind === 'task' ? '<button class="btn danger" id="b-del">Delete</button>' : ''}
      ${m.kind === 'series' && m.occ ? `<button class="btn" id="b-skip">Skip ${esc(DAY_SHORT[wdIdx(parseYmd(m.occ.date))])} ${parseYmd(m.occ.date).getDate()} only</button>` : ''}
      ${m.kind === 'series' ? '<button class="btn danger" id="b-delall">Delete whole series</button>' : ''}
      <span class="sp"></span><button class="btn" id="b-cancel">Cancel</button><button class="btn primary" id="b-save">Save</button></div>`;
  showDlg(html, (d) => {
    const q = (s) => d.querySelector(s);
    let color = m.color, mask = m.mask;
    const sync = () => { if (q('#f-rep')) q('#f-rep').style.display = Number(q('#f-rule').value) ? '' : 'none'; if (q('#f-wd')) q('#f-wd').style.display = Number(q('#f-rule').value) === 2 ? '' : 'none'; };
    const clash = () => {
      const date = q('#f-date').value, st = toMin(q('#f-start').value || '00:00'), du = Number(q('#f-dur').value) || 0;
      const own = m.occ ? m.occ.id : m.id;
      const hit = S.tasks.filter((t) => t.date === date && t.id !== own && t.start < st + du && st < t.start + t.dur);
      q('#f-clash').innerHTML = hit.length ? `<div class="warn">Overlaps with ${hit.map((t) => esc(t.title)).join(', ')}. <button type="button" class="btn" id="b-free" style="padding:3px 8px;margin-left:6px">Find next free slot</button></div>` : '';
      const fb = q('#b-free'); if (fb) fb.onclick = () => { q('#f-start').value = fromMin(findFreeSlot(S.tasks.filter((t) => t.date === date && t.id !== own), du, st)); clash(); };
    };
    if (q('#f-rule')) { q('#f-rule').onchange = sync; sync(); }
    q('#f-colors').onclick = (e) => { const b = e.target.closest('[data-c]'); if (!b) return; color = Number(b.dataset.c); d.querySelectorAll('#f-colors .sw').forEach((x) => x.classList.toggle('on', x === b)); };
    if (q('#f-wd')) q('#f-wd').onclick = (e) => { const b = e.target.closest('[data-d]'); if (!b) return; mask ^= 1 << Number(b.dataset.d); b.classList.toggle('on'); };
    q('#f-pick').onchange = (e) => { if (e.target.value) { q('#f-dur').value = e.target.value; clash(); } };
    ['#f-date', '#f-start', '#f-dur'].forEach((s) => { q(s).oninput = clash; });
    clash(); setTimeout(() => q('#f-title').focus(), 30);
    q('#b-cancel').onclick = closeDlg;
    const run = async (fn) => { try { await fn(); closeDlg(); await load(); refreshToday(); } catch (e) { if (e.message !== 'auth') toast(e.message); } };
    let armed = null;
    const confirmTwice = (btn, label, fn) => { btn.onclick = () => { if (armed === btn) { run(fn); } else { armed = btn; btn.dataset.old = btn.textContent; btn.textContent = label; } }; };
    if (q('#b-del')) confirmTwice(q('#b-del'), 'Tap again to delete', () => api().deleteTask(m));
    if (q('#b-delall')) confirmTwice(q('#b-delall'), 'Tap again to delete all', () => api().deleteSeries(m.id));
    if (q('#b-skip')) q('#b-skip').onclick = () => run(() => api().skip(m.occ));
    q('#b-save').onclick = () => {
      const title = q('#f-title').value.trim(), date = q('#f-date').value, startV = q('#f-start').value;
      const dur = Math.round(Number(q('#f-dur').value));
      if (!title) { toast('Give the task a title.'); q('#f-title').focus(); return; }
      if (!date || !startV) { toast('Pick a date and a start time.'); return; }
      if (!(dur >= 5)) { toast('Duration must be at least 5 minutes.'); return; }
      const start = toMin(startV), done = q('#f-done') ? q('#f-done').checked : false;
      const rule = q('#f-rule') ? Number(q('#f-rule').value) : (m.kind === 'series' ? m.rule : 0);
      let eff = mask; if (rule === 2 && !eff) eff = 1 << wdIdx(parseYmd(date));
      const endDate = q('#f-end') && q('#f-end').value ? q('#f-end').value : null;
      if (endDate && endDate < date) { toast('The end date is before the start date.'); return; }
      const base = { title, start, dur: Math.min(dur, 1440 - start), color };
      run(async () => {
        if (m.kind === 'task') await api().saveTask({ ...base, id: m.id, date, done });
        else if (m.kind === 'series') {
          const sd = m.complex ? date : firstOccurrence(date, rule, eff);
          await api().saveSeries({ ...base, id: m.id, startDate: sd, rule, mask: eff, endDate, complex: m.complex });
          if (m.occ && done !== m.occ.done) await api().setDone(m.occ, done);
        } else if (rule === 0) await api().saveTask({ ...base, date, done: false });
        else await api().saveSeries({ ...base, startDate: firstOccurrence(date, rule, eff), rule, mask: eff, endDate });
      });
    };
    q('#f-title').onkeydown = (e) => { if (e.key === 'Enter') q('#b-save').click(); };
  });
}
function findFreeSlot(existing, dur, from) {
  const DAY_START = 8 * 60, DAY_END = 22 * 60;
  let t = Math.max(from, DAY_START);
  const sorted = [...existing].sort((a, b) => a.start - b.start);
  for (let guard = 0; guard < 200; guard++) {
    const hit = sorted.find((x) => x.start < t + dur && t < x.start + x.dur);
    if (!hit) return t + dur <= DAY_END ? t : from;
    t = hit.start + hit.dur;
  }
  return from;
}

/* ---------- menu & dialogs ---------- */
function menuDialog() {
  showDlg(`<h2>Menu</h2><div class="list">
    <button class="row" data-m="series">Repeating tasks<small>See and edit every repeating task</small></button>
    <button class="row" data-m="theme">Background colour<small>${THEMES.find((t) => t[0] === S.theme)[1]}</small></button>
    <button class="row" data-m="gcal">Google Calendar<small>${S.mode === 'google' ? 'Signed in' + (S.calName ? ' · ' + esc(S.calName) : '') : 'Demo mode (not syncing)'}</small></button>
    <button class="row" data-m="focus">Focus timer<small>Simple countdown with a notification</small></button>
    <button class="row" data-m="remind">Reminders in this tab<small>${Rem.on ? 'On: notifies 5 minutes before a task' : 'Off'}</small></button>
    <button class="row" data-m="settings">Settings<small>Google client ID</small></button></div>
    <div class="actions"><button class="btn" id="b-close">Close</button></div>`, (d) => {
    d.querySelector('#b-close').onclick = closeDlg;
    d.querySelector('.list').onclick = (e) => {
      const b = e.target.closest('[data-m]'); if (!b) return;
      ({ series: seriesListDialog, theme: themeDialog, gcal: calendarDialog, focus: focusDialog, remind: toggleReminders, settings: settingsDialog })[b.dataset.m]();
    };
  });
}
function themeDialog() {
  showDlg(`<h2>Background colour</h2><div class="themes">${THEMES.map(([id, name, sw]) => `<button data-t="${id}" class="${S.theme === id ? 'on' : ''}" style="background:${sw};color:${['black', 'red', 'gray'].includes(id) ? (id === 'black' ? '#fff' : '#222') : '#222'}">${name}</button>`).join('')}</div>
    <div class="actions"><button class="btn" id="b-close">Done</button></div>`, (d) => {
    d.querySelector('#b-close').onclick = closeDlg;
    d.querySelector('.themes').onclick = (e) => {
      const b = e.target.closest('[data-t]'); if (!b) return;
      S.theme = b.dataset.t; lsSet('theme', S.theme); render();
      d.querySelectorAll('.themes button').forEach((x) => x.classList.toggle('on', x === b));
    };
  });
}
async function seriesListDialog() {
  let list = [];
  try { list = await api().series(); } catch (e) { if (e.message !== 'auth') toast(e.message); return; }
  list.sort((a, b) => a.start - b.start);
  showDlg(`<h2>Repeating tasks</h2><div class="list">${list.length ? list.map((s, i) => `<button class="row" data-i="${i}">${esc(s.title)}<small>${esc(ruleSummary(s))} · ${fmtTime(s.start)} · ${fmtDur(s.dur)}</small></button>`).join('') : '<div class="empty">No repeating tasks yet. Add a task and choose how it repeats.</div>'}</div>
    <div class="actions"><button class="btn" id="b-close">Close</button><button class="btn primary" id="b-new">+ New</button></div>`, (d) => {
    d.querySelector('#b-close').onclick = closeDlg;
    d.querySelector('#b-new').onclick = () => { closeDlg(); actions.add(); };
    d.querySelector('.list').onclick = (e) => { const b = e.target.closest('[data-i]'); if (!b) return; const s = list[Number(b.dataset.i)]; taskForm({ kind: 'series', ...s, date: s.startDate, occ: null, done: false }, null); };
  });
}
async function calendarDialog() {
  if (S.mode !== 'google') {
    showDlg(`<h2>Google Calendar</h2><div class="note">${S.clientId ? 'Sign in to sync with your Google Calendar.' : 'Add your Google client ID in Settings first.'}</div>
      <div class="actions"><button class="btn" id="b-close">Close</button>${S.clientId ? '<button class="btn primary" id="b-in">Sign in with Google</button>' : '<button class="btn primary" id="b-set">Open settings</button>'}</div>`, (d) => {
      d.querySelector('#b-close').onclick = closeDlg;
      if (d.querySelector('#b-in')) d.querySelector('#b-in').onclick = signIn;
      if (d.querySelector('#b-set')) d.querySelector('#b-set').onclick = settingsDialog;
    });
    return;
  }
  let cals = [];
  try { cals = validToken() ? await G.calendars() : []; } catch (e) { if (e.message !== 'auth') toast(e.message); }
  showDlg(`<h2>Google Calendar</h2><label>Calendar to use<select id="c-sel">${cals.map((c) => `<option value="${esc(c.id)}" ${c.id === S.calId ? 'selected' : ''}>${esc(c.summary || c.id)}</option>`).join('') || `<option>${esc(S.calName || 'None')}</option>`}</select></label>
    <div class="note">Use the same calendar you chose in the Android app. A calendar named TimeBlock keeps your tasks separate from everything else.</div>
    <div class="actions"><button class="btn danger" id="b-out">Sign out</button><span class="sp"></span><button class="btn" id="b-close">Close</button></div>`, (d) => {
    d.querySelector('#b-close').onclick = closeDlg;
    d.querySelector('#b-out').onclick = signOut;
    d.querySelector('#c-sel').onchange = (e) => {
      const c = cals.find((x) => x.id === e.target.value); if (!c) return;
      S.calId = c.id; S.calName = c.summary || c.id; lsSet('calId', S.calId); lsSet('calName', S.calName); load(); refreshToday();
    };
  });
}
function settingsDialog() {
  showDlg(`<h2>Settings</h2><label>Google client ID<input id="s-cid" value="${esc(S.clientId)}" placeholder="1234567890-abc.apps.googleusercontent.com" autocomplete="off"></label>
    <div class="note">Create it once in Google Cloud (steps in the README). It only identifies this web app to Google and is stored in this browser.</div>
    <div class="actions"><button class="btn" id="b-close">Cancel</button><button class="btn primary" id="b-save">Save</button></div>`, (d) => {
    d.querySelector('#b-close').onclick = closeDlg;
    d.querySelector('#b-save').onclick = () => { saveClientId(d.querySelector('#s-cid').value); closeDlg(); };
  });
}

/* ---------- focus timer & reminders ---------- */
const F = { end: null, timer: null, mins: lsGet('focusMins', 25) };
const fmtClock = (ms) => { const s = Math.max(0, Math.round(ms / 1000)); return `${pad(Math.floor(s / 60))}:${pad(s % 60)}`; };
function notify(title, body) {
  toast(`${title}: ${body}`);
  try { if ('Notification' in window && Notification.permission === 'granted') new Notification(title, { body, icon: 'icon-192.png' }); } catch { /* ignore */ }
}
function focusDialog() {
  const running = !!F.end;
  showDlg(`<h2>Focus timer</h2><div class="bigtime" id="f-clock">${running ? fmtClock(F.end - Date.now()) : fmtClock(F.mins * 60000)}</div>
    ${running ? '' : `<label>Minutes<input type="number" id="f-min" min="1" max="180" value="${F.mins}"></label>`}
    <div class="actions"><button class="btn" id="b-close">Close</button>${running ? '<button class="btn danger" id="b-stop">Stop</button>' : '<button class="btn primary" id="b-start">Start</button>'}</div>`, (d) => {
    d.querySelector('#b-close').onclick = closeDlg;
    const tick = setInterval(() => { const c = d.querySelector('#f-clock'); if (!c || !d.open) { clearInterval(tick); return; } if (F.end) c.textContent = fmtClock(F.end - Date.now()); }, 500);
    if (d.querySelector('#b-start')) d.querySelector('#b-start').onclick = () => {
      F.mins = Math.min(180, Math.max(1, Number(d.querySelector('#f-min').value) || 25)); lsSet('focusMins', F.mins);
      F.end = Date.now() + F.mins * 60000;
      if ('Notification' in window && Notification.permission === 'default') Notification.requestPermission();
      clearInterval(F.timer);
      F.timer = setInterval(() => {
        if (!F.end) return;
        if (Date.now() >= F.end) { F.end = null; clearInterval(F.timer); notify('Focus session finished', 'Time for a break.'); render(); return; }
        const c = $('#focuschip'); if (c) c.textContent = fmtClock(F.end - Date.now());
      }, 1000);
      closeDlg(); render();
    };
    if (d.querySelector('#b-stop')) d.querySelector('#b-stop').onclick = () => { F.end = null; clearInterval(F.timer); closeDlg(); render(); };
  });
}
const Rem = { on: lsGet('remind', false), fired: new Set(), list: [] };
function toggleReminders() {
  Rem.on = !Rem.on; lsSet('remind', Rem.on);
  if (Rem.on && 'Notification' in window && Notification.permission === 'default') Notification.requestPermission();
  toast(Rem.on ? 'Reminders on. Keep this tab open to get them.' : 'Reminders off.');
  refreshToday(); closeDlg();
}
async function refreshToday() {
  if (!Rem.on || !S.mode || (S.mode === 'google' && (!validToken() || !S.calId))) return;
  try { Rem.list = await api().range(todayD(), todayD()); } catch { /* ignore */ }
}
function checkReminders() {
  if (!Rem.on) return;
  const n = new Date(), nm = n.getHours() * 60 + n.getMinutes() + n.getSeconds() / 60;
  Rem.list.forEach((t) => {
    const diff = t.start - nm, key = `${t.id}@${t.date}@${t.start}`;
    if (!t.done && diff > 0 && diff <= 5 && !Rem.fired.has(key)) {
      Rem.fired.add(key); notify(t.title, `starts at ${fmtTime(t.start)} (in ${Math.max(1, Math.round(diff))} min)`);
    }
  });
}

/* ---------- boot ---------- */
async function boot() {
  render();
  if (S.mode === 'google') {
    if (validToken()) { try { await ensureCalendar(); } catch (e) { if (e.message !== 'auth') S.error = e.message; } } else S.needAuth = true;
  }
  await load(); refreshToday();
  setInterval(() => { if (!document.hidden && !$('#dlg').open) load(); }, 60000);
  setInterval(() => { refreshToday(); }, 120000);
  setInterval(checkReminders, 20000);
  document.addEventListener('visibilitychange', () => { if (!document.hidden && !$('#dlg').open) { load(); refreshToday(); } });
  if ('serviceWorker' in navigator && location.protocol.startsWith('http')) navigator.serviceWorker.register('sw.js').catch(() => {});
}
window.__tb = { S, ymd }; // handy for debugging in the console
boot();
})();
