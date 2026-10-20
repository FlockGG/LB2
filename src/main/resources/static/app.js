/* Library Connect - front end (plain JavaScript).
   It talks to the Java back end through the REST API under /api. */
'use strict';
const $ = s => document.querySelector(s), app = $('#app');
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));

// ---------- state ----------
let token = localStorage.getItem('lc-token'), me = null, books = [], loans = [], pstats = null;
let gRole = null, gMode = null, staffExists = false, pending = null;
let view = 'home', sub = 'books', q = '', genre = 'All', onlyAvail = false, onlySaved = false, pEdit = null, newCover = null;
const role = () => me && me.role;
const ACTIVE = l => l.status === 'REQUESTED' || l.status === 'ISSUED';
const late = l => l.status === 'ISSUED' && l.dueAt < Date.now();
const fmt = t => new Date(t).toLocaleDateString(undefined, {day: 'numeric', month: 'short'});
const saved = () => ((me && me.saved) || '').split(',').filter(Boolean).map(Number);

// ---------- talking to the back end ----------
async function api(path, method = 'GET', body) {
  const r = await fetch('/api' + path, {
    method, headers: {'Content-Type': 'application/json', 'X-Token': token || ''},
    body: body ? JSON.stringify(body) : undefined
  });
  const text = await r.text();
  let data = null; try { data = text ? JSON.parse(text) : null; } catch (e) {}
  if (r.status === 401 && token) { localStorage.removeItem('lc-token'); token = null; me = null; render(); }
  if (!r.ok) throw new Error((data && data.message) || 'Something went wrong.');
  return data;
}
async function upload(file) {
  const fd = new FormData(); fd.append('file', file);
  const r = await fetch('/api/upload', {method: 'POST', headers: {'X-Token': token}, body: fd});
  if (!r.ok) throw new Error('Upload failed. Use a PNG, JPG, GIF or WebP image under 5 MB.');
  return (await r.json()).url;
}
async function load() {
  me = await api('/me');
  [books, loans] = await Promise.all([api('/books'), api(role() === 'lib' ? '/loans' : '/loans/mine')]);
}
async function loadStats() { pstats = await api('/me/stats'); }

// ---------- small UI helpers ----------
const closeModal = () => { $('#modal').innerHTML = ''; };
const say = m => { $('#modal').innerHTML = `<div class="ov"><div class="mod"><h2>${esc(m)}</h2><div class="row"><button class="b" data-a="close">OK</button></div></div></div>`; };
const stat = (n, l, hl) => `<div class="stat ${hl ? 'hl' : ''}"><b>${n}</b><small>${l}</small></div>`;
const cover = b => b.cover ? `<img class="cover" src="${esc(b.cover)}" alt="">` : `<div class="cover">${esc((b.title || '?')[0]).toUpperCase()}</div>`;
const avatar = (a, big) => {
  const n = (a && (a.role === 'lib' ? a.libraryName : a.name)) || '?';
  return a && a.photo ? `<img class="av ${big ? 'big' : ''}" src="${esc(a.photo)}" alt="">` : `<div class="av ${big ? 'big' : ''}">${esc(n[0]).toUpperCase()}</div>`;
};
const studentAv = l => l.studentPhoto ? `<img class="av" src="${esc(l.studentPhoto)}" alt="">` : `<div class="av">${esc((l.studentName || 'S')[0]).toUpperCase()}</div>`;

// ---------- actions (every button has data-a="name") ----------
const A = {};
document.addEventListener('click', async e => {
  if (e.target.classList.contains('ov') && !e.target.classList.contains('nc')) return closeModal();
  const el = e.target.closest('[data-a]');
  if (!el || !A[el.dataset.a]) return;
  try { await A[el.dataset.a](el.dataset, el); } catch (err) { say(err.message); }
});
async function refresh() { await load(); render(); }

A.close = () => closeModal();
A.tab = async d => { view = d.v; sub = 'books'; if (d.v === 'pro') { pEdit = null; await loadStats(); } render(); };
A.sub = d => { sub = d.v; render(); };
A.genre = d => { genre = d.v; render(); };
A.tog = d => { if (d.v === 'avail') onlyAvail = !onlyAvail; else onlySaved = !onlySaved; render(); };

// --- login / account creation ---
A.role = d => { gRole = d.v; gMode = null; render(); };
A.gback = () => { if (gMode) gMode = null; else gRole = null; render(); };
A.glogin = () => { gMode = 'login'; render(); };
A.gcreate = async () => {
  if (gRole === 'lib') { staffExists = (await api('/auth/staff-key')).exists; gMode = 'create'; render(); }
  else await createAccount();
};
A.docreate = async () => createAccount($('#gkey').value);
async function createAccount(staffKey) {
  const r = await api('/auth/create', 'POST', {role: gRole, staffKey});
  pending = r;
  $('#modal').innerHTML = `<div class="ov nc"><div class="mod"><h2>Your secret code</h2><div class="code" id="ccode">${esc(r.code)}</div>
  <p class="m" style="margin:0;color:var(--mut)">Save this code somewhere safe. You need it to log in from another device. It cannot be recovered if you lose it.</p>
  <div class="row"><button class="b alt" data-a="copy">Copy code</button><button class="b" data-a="enter">I saved it, continue</button></div></div></div>`;
}
A.copy = async (d, el) => { try { await navigator.clipboard.writeText(pending.code); el.textContent = 'Copied \u2713'; } catch (e) {} };
A.enter = async () => { token = pending.token; localStorage.setItem('lc-token', token); gRole = gMode = null; closeModal(); view = 'home'; await load(); await loadStats(); pEdit = null; render(); };
A.dologin = async () => {
  const r = await api('/auth/login', 'POST', {role: gRole, code: $('#gcode').value});
  token = r.token; localStorage.setItem('lc-token', token); gRole = gMode = null; view = 'home'; await load(); render();
};
A.logout = async () => { try { await api('/auth/logout', 'POST'); } catch (e) {} localStorage.removeItem('lc-token'); token = me = null; gRole = gMode = null; view = 'home'; render(); };

// --- books ---
A.open = d => openDetail(+d.id);
A.addbook = () => openBookForm();
A.editbook = d => openBookForm(books.find(b => b.id === +d.id));
A.delbook = async d => { if (confirm('Remove this book?')) { await api('/books/' + d.id, 'DELETE'); closeModal(); await refresh(); } };
A.savebook = async d => {
  const v = id => $(id).value.trim();
  const body = {title: v('#bt'), author: v('#ba'), genre: v('#bg'), copies: +v('#bn') || 1, description: v('#bd'), cover: newCover || ''};
  if (!body.title || !body.author) return say('Title and author are required.');
  await api(d.id ? '/books/' + d.id : '/books', d.id ? 'PUT' : 'POST', body);
  closeModal(); newCover = null; await refresh();
};
A.save = async d => { me = await api('/saved/' + d.id, 'POST'); openDetail(+d.id); render(); };

// --- loans ---
A.take = d => openBorrow(+d.id);
A.confirm = async d => { const days = +$('#days').value || 14; closeModal(); await api('/loans', 'POST', {bookId: +d.id, days}); await refresh(); };
A.iss = async d => { closeModal(); await api(`/loans/${d.id}/issue`, 'POST'); await refresh(); };
A.rej = async d => { closeModal(); await api(`/loans/${d.id}/decline`, 'POST'); await refresh(); };
A.ret = async d => { await api(`/loans/${d.id}/return`, 'POST'); await refresh(); };
A.ext = async d => { await api(`/loans/${d.id}/extend`, 'POST'); await refresh(); };
A.sret = async d => { if (confirm('Have you returned this book?')) { await api(`/loans/${d.id}/return-request`, 'POST'); await refresh(); } };
A.stu = d => openStudent(d.id, +d.loan);
A.lib = () => openLibrary();

// --- profile ---
A.pedit = d => { pEdit = d.v === '1'; render(); };
A.psave = async () => {
  const v = id => $(id) ? $(id).value.trim() : undefined;
  const body = role() === 'lib' ? {libraryName: v('#pk'), address: v('#pd'), hours: v('#pg'), about: v('#pa')}
                                : {name: v('#pn'), school: v('#pk'), genres: v('#pg'), about: v('#pa')};
  me = await api('/profile', 'PUT', body); pEdit = false; render();
};
document.addEventListener('change', async e => {
  const f = e.target.files && e.target.files[0]; if (!f) return;
  try {
    if (e.target.id === 'cv') { newCover = await upload(f); $('#fl').innerHTML = coverLabel(); }
    if (e.target.id === 'ph') { const url = await upload(f); me = await api('/profile', 'PUT', {photo: url}); render(); }
  } catch (err) { say(err.message); }
});
document.addEventListener('input', e => {
  if (e.target.id === 'q') { q = e.target.value; const p = e.target.selectionStart; app.innerHTML = role() === 'lib' ? libView() : stuView(); const i = $('#q'); i.focus(); i.setSelectionRange(p, p); }
});

// ---------- modals ----------
const coverLabel = () => `${newCover ? `<img src="${esc(newCover)}">Cover added` : '\uD83D\uDCF7 Add cover image'}<input type="file" accept="image/*" id="cv">`;
function openBookForm(b) {
  b = b || {}; newCover = b.cover || null;
  $('#modal').innerHTML = `<div class="ov"><div class="mod"><h2>${b.id ? 'Edit book' : 'Add book'}</h2>
  <div><label>Title</label><input id="bt" value="${esc(b.title)}"></div><div><label>Author</label><input id="ba" value="${esc(b.author)}"></div>
  <div><label>Genre</label><input id="bg" value="${esc(b.genre)}"></div><div><label>Number of books</label><input id="bn" type="number" min="1" value="${b.copies || 1}"></div>
  <div><label>Description</label><textarea id="bd" rows="4">${esc(b.description)}</textarea></div>
  <label class="file" id="fl">${coverLabel()}</label>
  <div class="row"><button class="b ghost" style="color:var(--mut)" data-a="close">Cancel</button><button class="b" data-a="savebook" data-id="${b.id || ''}">${b.id ? 'Save' : 'Add book'}</button></div></div></div>`;
}
function openDetail(id) {
  const b = books.find(x => x.id === id); if (!b) return;
  const mine = loans.find(l => l.bookId === id && ACTIVE(l) && role() === 'stu');
  const sv = saved().includes(id), left = b.available;
  const act = role() === 'lib'
    ? `<button class="b" data-a="editbook" data-id="${id}">Edit</button>${b.available === b.copies ? `<button class="b ghost" data-a="delbook" data-id="${id}">Remove</button>` : ''}`
    : mine ? `<span class="pill warn">${mine.status === 'ISSUED' ? 'With you' : 'Requested'}</span>`
    : left > 0 ? `<button class="b" data-a="take" data-id="${id}">Borrow</button>` : '<span class="pill">Unavailable</span>';
  $('#modal').innerHTML = `<div class="ov"><div class="mod"><div class="row" style="justify-content:flex-start;align-items:flex-start;gap:14px;flex-wrap:nowrap">${cover(b)}<div><h3>${esc(b.title)}</h3>
  <div class="m" style="color:var(--mut);margin-bottom:8px">by ${esc(b.author)}</div><div class="row" style="justify-content:flex-start">${b.genre ? `<span class="pill">${esc(b.genre)}</span>` : ''}
  <span class="pill ${left > 0 ? 'ok' : 'warn'}">${left > 0 ? left + ' of ' + b.copies + ' available' : 'All out'}</span></div>
  <div class="m" style="color:var(--mut);margin-top:8px">Borrowed ${b.timesBorrowed} time${b.timesBorrowed === 1 ? '' : 's'}</div></div></div>
  <div><label>About this book</label><p class="dsc">${esc(b.description) || 'No description yet.'}</p></div>
  ${role() === 'stu' ? '<div><button class="chip" data-a="lib">\uD83C\uDFDB About the library &amp; librarian</button></div>' : ''}
  <div class="row" style="justify-content:space-between"><div class="row">${role() === 'stu' ? `<button class="b ghost" style="color:var(--ink)" data-a="save" data-id="${id}">${sv ? '\u2665 Saved' : '\u2661 Save'}</button>` : ''}
  <button class="b ghost" style="color:var(--mut)" data-a="close">Close</button></div><div class="row">${act}</div></div></div></div>`;
}
function openBorrow(id) {
  const b = books.find(x => x.id === id); if (!b) return;
  $('#modal').innerHTML = `<div class="ov"><div class="mod"><h2>Borrow \u201C${esc(b.title)}\u201D</h2><div><label>How long do you need it?</label>
  <select id="days"><option value="7">1 week</option><option value="14" selected>2 weeks</option><option value="21">3 weeks</option><option value="30">1 month</option></select></div>
  <div class="row"><button class="b ghost" style="color:var(--mut)" data-a="close">Cancel</button><button class="b" data-a="confirm" data-id="${b.id}">Request book</button></div></div></div>`;
}
const recordHtml = (s, title) => {
  const avg = s.returned ? (s.avgDays >= 0 ? s.avgDays.toFixed(1) + 'd early' : Math.abs(s.avgDays).toFixed(1) + 'd late') : '\u2013';
  return `<div class="hero" style="margin:0"><h2>${title} <span class="pill ${s.grade === 'Needs attention' ? 'late' : s.grade === 'New borrower' ? '' : 'ok'}">${s.grade}</span></h2><div class="stats">
  ${stat(s.total, 'Borrowed')}${stat(s.onTimeRate == null ? '\u2013' : s.onTimeRate + '%', 'On-time', true)}${stat(avg, 'Avg return')}${stat(s.lateReturns, 'Late returns')}${stat(s.overdueNow, 'Overdue now')}</div></div>`;
};
const libRecordHtml = s => `<div class="hero" style="margin:0"><h2>Library record</h2><div class="stats">${stat(s.issued, 'Books issued')}${stat(s.requests, 'Open requests')}
  ${stat(s.completion == null ? '\u2013' : s.completion + '%', 'Completion', true)}${stat(s.approval == null ? '\u2013' : s.approval + '%', 'Approval')}${stat(s.titles, 'Titles')}</div></div>`;
async function openStudent(id, loanId) {
  const {account: a, stats: s} = await api('/students/' + id);
  const l = loans.find(x => x.id === loanId), pend = l && l.status === 'REQUESTED';
  $('#modal').innerHTML = `<div class="ov"><div class="mod"><div class="row" style="flex-wrap:nowrap">${avatar(a, true)}<div><h3>${esc(a.name || 'Student')}</h3><div class="m" style="color:var(--mut)">${esc(a.school || 'Student')}</div></div></div>
  ${a.about ? `<p class="dsc">${esc(a.about)}</p>` : ''}${recordHtml(s, 'Reading record')}
  <div class="m" style="color:var(--mut)">${s.returned ? `${s.returned - s.lateReturns} of ${s.returned} returned on time.` : 'No returns yet.'}${s.overdueNow ? ` Has ${s.overdueNow} overdue now.` : ''}</div>
  ${pend ? `<div class="m"><b>Requested:</b> ${esc(l.bookTitle)} for ${l.days} days</div>` : ''}
  <div class="row" style="justify-content:flex-end"><button class="b ghost" style="color:var(--mut)" data-a="close">Close</button>${pend ? `<button class="b ghost" data-a="rej" data-id="${l.id}">Decline</button><button class="b" data-a="iss" data-id="${l.id}">Issue book</button>` : ''}</div></div></div>`;
}
async function openLibrary() {
  const {account: a, stats: s} = await api('/library');
  if (!a) return say('The librarian has not set up a profile yet.');
  const f = (l, v) => v ? `<div><label>${l}</label><p class="dsc">${esc(v)}</p></div>` : '';
  $('#modal').innerHTML = `<div class="ov"><div class="mod"><div class="row" style="flex-wrap:nowrap">${avatar(a, true)}<div><h3>${esc(a.libraryName || 'Library')}</h3><div class="m" style="color:var(--mut)">Librarian</div></div></div>
  ${f('Address', a.address)}${f('Desk hours', a.hours)}${f('About', a.about)}${libRecordHtml(s)}<div class="row"><button class="b" data-a="close">Close</button></div></div></div>`;
}

// ---------- pages ----------
function gateView() {
  const err = '<p id="rerr" style="color:var(--bad);font-weight:600"></p>', lib = gRole === 'lib';
  if (!gRole) return `<div class="gate"><h2>Welcome \uD83D\uDC4B</h2><p class="m" style="color:var(--mut)">Choose how you want to log in.</p><div class="roles">
    <button class="rc" data-a="role" data-v="lib"><b>\uD83D\uDCDA Librarian</b><small>Manage books, issue and return</small></button>
    <button class="rc" data-a="role" data-v="stu"><b>\uD83C\uDF93 Student</b><small>Browse and borrow books</small></button></div></div>`;
  if (!gMode) return `<div class="gate"><h2>${lib ? 'Librarian' : 'Student'}</h2><p class="m" style="color:var(--mut)">Create a new account or log in with your secret code.</p><div class="roles">
    <button class="rc" data-a="gcreate"><b>\u2728 Create account</b><small>Get a new secret code</small></button>
    <button class="rc" data-a="glogin"><b>\uD83D\uDD11 Log in</b><small>Use your secret code</small></button></div><p><button class="chip" data-a="gback">\u2190 Back</button></p></div>`;
  if (gMode === 'create') return `<div class="gate"><h2>New librarian account</h2><p class="m" style="color:var(--mut)">${staffExists ? 'Enter the staff key to confirm you are a librarian.' : 'Set a staff key (at least 6 characters). Only people who know it can create librarian accounts.'}</p>
    <div style="max-width:360px;margin:22px auto 0;display:grid;gap:12px"><input id="gkey" type="password" placeholder="Staff key" autocomplete="off"><button class="b" data-a="docreate">Create account</button><button class="chip" data-a="gback" style="justify-self:center">\u2190 Back</button></div></div>`;
  return `<div class="gate"><h2>Log in</h2><p class="m" style="color:var(--mut)">Enter the secret code you got when you created your ${lib ? 'librarian' : 'student'} account.</p>
    <div style="max-width:360px;margin:22px auto 0;display:grid;gap:12px"><input id="gcode" placeholder="XXXX-XXXX-XXXX-XXXX" autocomplete="off" style="text-align:center;letter-spacing:.08em;font-weight:600"><button class="b" data-a="dologin">Log in</button><button class="chip" data-a="gback" style="justify-self:center">\u2190 Back</button></div></div>`;
}
const chips = list => `<div class="sub">${list.map(([k, n, c]) => `<button class="chip ${sub === k ? 'on' : ''}" data-a="sub" data-v="${k}">${n}${c != null ? ' \u00B7 ' + c : ''}</button>`).join('')}</div>`;
function bookCard(b) {
  const mine = role() === 'stu' && loans.find(l => l.bookId === b.id && ACTIVE(l));
  let foot;
  if (role() === 'lib') foot = `<span class="pill ${b.available > 0 ? 'ok' : 'warn'}">${b.available > 0 ? b.available + ' of ' + b.copies + ' in' : 'All out'}</span>${b.available === b.copies ? `<button class="b ghost" data-a="delbook" data-id="${b.id}">\u2715</button>` : ''}`;
  else foot = mine ? `<span class="pill warn">${mine.status === 'ISSUED' ? 'With you' : 'Requested'}</span>` : b.available > 0 ? `<button class="b" data-a="take" data-id="${b.id}">Borrow</button>` : '<span class="pill">Unavailable</span>';
  return `<div class="card bk" data-a="open" data-id="${b.id}">${cover(b)}<div class="g"><div class="t">${esc(b.title)}</div><div class="m">${esc(b.author)}${b.genre ? ' \u00B7 ' + esc(b.genre) : ''}</div></div><div class="ft">${foot}</div></div>`;
}
function libView() {
  const req = loans.filter(l => l.status === 'REQUESTED'), out = loans.filter(l => l.status === 'ISSUED'), hist = loans.filter(l => l.status === 'RETURNED');
  let h = `<div class="hero"><h2>Today at the desk</h2><div class="stats">${stat(books.length, 'Titles')}${stat(books.filter(b => b.available > 0).length, 'Available')}${stat(req.length, 'Requests', req.length > 0)}${stat(out.length, 'Issued')}${stat(out.filter(late).length, 'Overdue', out.some(late))}</div></div>`;
  h += chips([['books', 'Books', books.length], ['req', 'Requests', req.length], ['out', 'Issued', out.length], ['hist', 'History', hist.length]]);
  if (sub === 'books') {
    h += `<button class="addbox" data-a="addbook">\uFF0B Add book</button><input id="q" placeholder="Search books" value="${esc(q)}" style="margin-bottom:14px">`;
    const f = books.filter(b => (b.title + ' ' + b.author).toLowerCase().includes(q.toLowerCase()));
    return h + (f.length ? `<div class="grid">${f.map(bookCard).join('')}</div>` : '<div class="empty">No books yet. Add the first one.</div>');
  }
  const list = sub === 'req' ? req : sub === 'out' ? out : hist;
  return h + (list.length ? list.map(l => `<div class="card" data-a="stu" data-id="${l.studentId}" data-loan="${l.id}">${studentAv(l)}<div class="g"><div class="t">${esc(l.bookTitle)}</div>
    <div class="m">${esc(l.studentName)}${l.studentSchool ? ' \u00B7 ' + esc(l.studentSchool) : ''} \u00B7 ${l.status === 'ISSUED' ? 'due ' + fmt(l.dueAt) : l.status === 'RETURNED' ? 'returned ' + fmt(l.returnedAt) : 'asked ' + fmt(l.requestedAt) + ' \u00B7 ' + l.days + ' days'}</div></div>
    ${late(l) ? '<span class="pill late">Overdue</span>' : ''}
    ${l.status === 'REQUESTED' ? `<button class="b ghost" data-a="rej" data-id="${l.id}">Decline</button><button class="b" data-a="iss" data-id="${l.id}">Issue</button>` : ''}
    ${l.status === 'ISSUED' ? `${l.returnRequested ? '<span class="pill warn">Return requested</span>' : ''}<button class="b ghost" style="color:var(--ink)" data-a="ext" data-id="${l.id}">+7 days</button><button class="b alt" data-a="ret" data-id="${l.id}">${l.returnRequested ? 'Confirm return' : 'Return'}</button>` : ''}</div>`).join('') : '<div class="empty">Nothing here.</div>');
}
function stuView() {
  const mine = loans;
  let h = `<div class="hero"><h2>Hi ${esc((me.name || 'there').split(' ')[0])} \uD83D\uDC4B</h2><div class="stats">${stat(mine.filter(l => l.status === 'ISSUED').length, 'With you', true)}${stat(mine.filter(l => l.status === 'REQUESTED').length, 'Requested')}${stat(mine.filter(late).length, 'Overdue')}${stat(mine.filter(l => l.status === 'RETURNED').length, 'Read')}</div></div>`;
  h += `<div class="sub"><button class="chip ${sub !== 'mine' ? 'on' : ''}" data-a="sub" data-v="books">Browse</button><button class="chip ${sub === 'mine' ? 'on' : ''}" data-a="sub" data-v="mine">My books</button><button class="chip" data-a="lib">\uD83C\uDFDB Library info</button></div>`;
  if (sub !== 'mine') {
    const gs = ['All', ...new Set(books.map(b => b.genre).filter(Boolean))], sv = saved();
    h += `<input id="q" placeholder="Search title or author" value="${esc(q)}" style="margin-bottom:10px"><div class="sub"><button class="chip ${onlyAvail ? 'on' : ''}" data-a="tog" data-v="avail">Available now</button><button class="chip ${onlySaved ? 'on' : ''}" data-a="tog" data-v="saved">\u2665 Saved</button></div>`;
    if (gs.length > 1) h += `<div class="sub">${gs.map(g => `<button class="chip ${genre === g ? 'on' : ''}" data-a="genre" data-v="${esc(g)}">${esc(g)}</button>`).join('')}</div>`;
    const f = books.filter(b => (genre === 'All' || b.genre === genre) && (b.title + ' ' + b.author).toLowerCase().includes(q.toLowerCase()) && (!onlyAvail || b.available > 0) && (!onlySaved || sv.includes(b.id)));
    return h + (f.length ? `<div class="grid">${f.map(bookCard).join('')}</div>` : '<div class="empty">No books found.</div>');
  }
  const l2 = mine.filter(l => l.status !== 'DECLINED');
  return h + (l2.length ? l2.map(l => { const bk = books.find(b => b.id === l.bookId) || {title: l.bookTitle};
    return `<div class="card">${cover(bk)}<div class="g"><div class="t">${esc(l.bookTitle)}</div><div class="m">${l.status === 'ISSUED' ? 'Due ' + fmt(l.dueAt) : l.status === 'RETURNED' ? 'Returned ' + fmt(l.returnedAt) : 'Waiting for librarian \u00B7 ' + l.days + ' days'}</div></div>
    ${late(l) ? '<span class="pill late">Overdue</span>' : ''}
    ${l.status === 'REQUESTED' ? `<button class="b ghost" data-a="rej" data-id="${l.id}">Cancel</button>` : l.status === 'ISSUED' ? (l.returnRequested ? '<span class="pill warn">Return requested</span>' : `<button class="b alt" data-a="sret" data-id="${l.id}">Return book</button>`) : '<span class="pill">Returned</span>'}</div>`; }).join('') : '<div class="empty">You have no books yet.</div>');
}
function profileView() {
  const lib = role() === 'lib', p = me, filled = lib ? (p.libraryName || p.address || p.hours || p.about) : (p.name || p.school || p.genres || p.about);
  const stats = pstats ? (lib ? libRecordHtml(pstats) : recordHtml(pstats, 'Reading record')) : '';
  const editing = pEdit === null ? !filled : pEdit;
  if (!editing) {
    const f = (l, v) => v ? `<div><label>${l}</label><p class="dsc">${esc(v)}</p></div>` : '';
    return `<div class="pf"><div class="row" style="flex-wrap:nowrap;gap:16px">${avatar(p, true)}<div><h3 style="margin:0;font-size:22px;font-weight:700;letter-spacing:-.02em">${esc(lib ? (p.libraryName || 'Your library') : (p.name || 'Student'))}</h3>
      <span class="pill ${lib ? 'warn' : 'ok'}" style="display:inline-block;margin-top:8px">${lib ? 'Librarian' : 'Student'}</span></div></div>${stats}
      ${lib ? f('Library address', p.address) + f('Desk hours', p.hours) : f('Class / Roll no.', p.school) + f('Favourite genres', p.genres)}${f('About', p.about)}
      <div class="row"><button class="b" data-a="pedit" data-v="1">Edit profile</button><button class="b ghost" data-a="logout">Log out</button></div></div>`;
  }
  return `<div class="pf">${stats}<div class="row" style="flex-wrap:nowrap;gap:16px">${avatar(p, true)}<div><label class="file">\uD83D\uDCF7 Change photo<input type="file" accept="image/*" id="ph"></label></div></div>
    ${lib ? '' : `<div><label>Name</label><input id="pn" value="${esc(p.name)}"></div>`}
    <div><label>${lib ? 'Library name' : 'Class / Roll no.'}</label><input id="pk" value="${esc(lib ? p.libraryName : p.school)}"></div>
    ${lib ? `<div><label>Library address</label><input id="pd" value="${esc(p.address)}"></div>` : ''}
    <div><label>${lib ? 'Desk hours' : 'Favourite genres'}</label><input id="pg" value="${esc(lib ? p.hours : p.genres)}"></div>
    <div><label>About</label><textarea id="pa" rows="3">${esc(p.about)}</textarea></div>
    <div class="row"><button class="b" data-a="psave">Save profile</button>${filled ? '<button class="b alt" data-a="pedit" data-v="0">Cancel</button>' : ''}<button class="b ghost" data-a="logout">Log out</button></div></div>`;
}
function render() {
  const t = me ? [['home', 'Library'], ['pro', 'Profile']] : [];
  $('#tabs').innerHTML = t.map(([k, n]) => `<button class="${view === k ? 'on' : ''}" data-a="tab" data-v="${k}">${n}</button>`).join('');
  if (document.activeElement && document.activeElement.matches('input,textarea') && document.activeElement.id !== 'q' && $('#app').contains(document.activeElement)) return;
  app.innerHTML = !me ? gateView() : view === 'pro' ? profileView() : role() === 'lib' ? libView() : stuView();
}

// ---------- start ----------
(async () => {
  if (token) { try { await load(); } catch (e) { token = me = null; } }
  render();
  setInterval(async () => {          // keep the data fresh (new requests appear by themselves)
    if (!me || $('#modal').innerHTML || (document.activeElement && document.activeElement.matches('input,textarea,select'))) return;
    try { await load(); render(); } catch (e) {}
  }, 15000);
})();
