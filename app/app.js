'use strict';
/* ===================== التخزين ===================== */
const KEY = 'murafiq_pro_db_v1';
const store = {
  load() {
    try {
      if (window.native) return window.native.load();
      return localStorage.getItem(KEY);
    } catch (e) { return null; }
  },
  save(str) {
    if (window.native) return window.native.save(str);
    try { localStorage.setItem(KEY, str); } catch (e) {}
  }
};
const EMPTY = () => ({ version: 1, settings: { owner: 'زياد بالشاوش', phone: '', shop: '' }, trips: [], pilgrims: [], payments: [], agency: [], nextNo: 1, nextRcpt: 1 });
let DB;
try { DB = JSON.parse(store.load() || 'null'); } catch (e) { DB = null; }
if (!DB) DB = EMPTY();
DB.settings = DB.settings || { owner: '', phone: '', shop: '' };
DB.nextRcpt = DB.nextRcpt || 1;
let saveT;
function save() {
  document.getElementById('saveState').textContent = '… جاري الحفظ';
  clearTimeout(saveT);
  saveT = setTimeout(() => {
    store.save(JSON.stringify(DB));
    document.getElementById('saveState').textContent = '✔ محفوظ ' + new Date().toLocaleTimeString('ar-TN', { hour: '2-digit', minute: '2-digit' });
  }, 250);
}

/* ===================== أدوات ===================== */
const $ = s => document.querySelector(s);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const uid = p => p + Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
const num = v => { const n = parseFloat(String(v ?? '').replace(/[^\d.\-]/g, '')); return isNaN(n) ? 0 : n; };
const money = v => (Math.round(num(v)) || 0).toLocaleString('fr-TN').replace(/ | /g, ' ') + ' د';
const today = () => new Date().toISOString().slice(0, 10);
const dfmt = d => d ? d.split('-').reverse().join('/') : '—';
const TYPES = ['ثنائي', 'ثلاثي', 'رباعي', 'خماسي'];
const CAP = { 'ثنائي': 2, 'ثلاثي': 3, 'رباعي': 4, 'خماسي': 5 };
const ROLES = { pilgrim: 'معتمر', leader: 'رئيس المجموعة', guide: 'مرافق' };
const METHODS = ['نقداً', 'تحويل بنكي', 'D17', 'حوالة بريدية', 'شيك'];
const fields = el => new Proxy({}, { get: (_, k) => k === 'el' ? el : el.querySelector(`[name="${String(k)}"]`) });
function toast(m) { const t = $('#toast'); t.textContent = m; t.classList.remove('hidden'); clearTimeout(t._t); t._t = setTimeout(() => t.classList.add('hidden'), 2400); }

/* ===================== الحسابات ===================== */
let curTrip = localStorage.getItem('curTrip') || '';
const trip = () => DB.trips.find(t => t.id === curTrip) || DB.trips[0];
const tripPilgrims = (t = trip()) => t ? DB.pilgrims.filter(p => p.tripId === t.id) : [];
function paidOf(p) { return DB.payments.filter(x => x.pilgrimId === p.id).reduce((s, x) => s + num(x.amount), 0); }
function buyOf(p) {
  if (p.buy !== null && p.buy !== undefined && p.buy !== '') return num(p.buy);
  const t = DB.trips.find(t => t.id === p.tripId); return t ? num(t.buy?.[p.roomType]) : 0;
}
function calc(p) {
  const sell = num(p.sell), paid = paidOf(p), rem = sell - paid, buy = buyOf(p);
  let st, cls;
  if (sell === 0) { st = 'مجاني'; cls = 'st-free'; }
  else if (rem < 0) { st = 'دفع زيادة'; cls = 'st-over'; }
  else if (rem === 0) { st = 'خالص'; cls = 'st-paid'; }
  else if (paid === 0) { st = 'ما دفعش'; cls = 'st-none'; }
  else { st = 'باقي عليه'; cls = 'st-part'; }
  return { sell, paid, rem, buy, profit: sell - buy, st, cls };
}
function tripTotals(t = trip()) {
  const ps = tripPilgrims(t); let sell = 0, paid = 0, rem = 0, buy = 0, owing = 0, full = 0;
  ps.forEach(p => { const c = calc(p); sell += c.sell; paid += c.paid; rem += Math.max(c.rem, 0); buy += c.buy; if (c.rem > 0) owing++; if (c.st === 'خالص') full++; });
  const agPaid = t ? DB.agency.filter(a => a.tripId === t.id).reduce((s, a) => s + num(a.amount), 0) : 0;
  return { n: ps.length, sell, paid, rem, buy, agPaid, agRem: buy - agPaid, profit: sell - buy, cash: paid - agPaid, owing, full };
}
function passportWarn(p) {
  const t = DB.trips.find(t => t.id === p.tripId);
  if (!p.passport) return 'جواز السفر ناقص';
  if (p.passportExp && t?.ret) {
    const lim = new Date(t.ret); lim.setMonth(lim.getMonth() + 6);
    if (new Date(p.passportExp) < lim) return 'الجواز يوفى قبل 6 أشهر من الرجوع';
  }
  return '';
}

/* ===================== التنقّل ===================== */
let view = 'dash';
const TITLES = { dash: 'لوحة القيادة', pilgrims: 'المعتمرون', payments: 'دفعات المعتمرين', agency: 'دفعاتي للوكالة', rooms: 'الغرف', trips: 'الرحلات', settings: 'الإعدادات والنسخ' };
$('#nav').addEventListener('click', e => { const a = e.target.closest('a'); if (a) go(a.dataset.v); });
$('#tripSel').addEventListener('change', e => { curTrip = e.target.value; localStorage.setItem('curTrip', curTrip); render(); });
function go(v) { view = v; document.querySelectorAll('#nav a').forEach(a => a.classList.toggle('on', a.dataset.v === v)); render(); }
function fillTripSel() {
  const s = $('#tripSel');
  s.innerHTML = DB.trips.length ? DB.trips.map(t => `<option value="${t.id}">${esc(t.name)}</option>`).join('') : '<option>— ما فماش رحلة —</option>';
  if (trip()) { curTrip = trip().id; s.value = curTrip; }
}
function render() {
  fillTripSel();
  $('#title').textContent = TITLES[view];
  $('#topAct').innerHTML = '';
  if (!DB.trips.length && view !== 'settings') return renderWelcome();
  ({ dash: vDash, pilgrims: vPilgrims, payments: vPayments, agency: vAgency, rooms: vRooms, trips: vTrips, settings: vSettings })[view]();
}
function acts(html) { $('#topAct').innerHTML = html; }

/* ===================== الترحيب ===================== */
function renderWelcome() {
  $('#view').innerHTML = `<div class="welcome card">
    <div class="logo">🕋</div><h2>مرحبا بيك في مرافق برو</h2>
    <p>سجّل معتمرينك، دفعاتهم، دفعاتك للوكالة والغرف، وكل شي يتحسب وحدو.</p>
    <div class="acts">
      ${window.SEED_DECEMBER ? '<button class="btn pri" onclick="loadSeed()">📥 حمّل معتمرين رحلة ديسمبر (من ملف Excel)</button>' : ''}
      <button class="btn" onclick="tripForm()">✈️ رحلة جديدة فارغة</button>
      <button class="btn" onclick="importJSON()">📂 رجّع نسخة احتياطية</button>
    </div></div>`;
}
function loadSeed() {
  const s = JSON.parse(JSON.stringify(window.SEED_DECEMBER));
  DB.trips.push(...s.trips); DB.pilgrims.push(...s.pilgrims);
  s.payments.forEach(p => { p.rcpt = DB.nextRcpt++; }); DB.payments.push(...s.payments);
  DB.nextNo = Math.max(DB.nextNo, s.nextNo); curTrip = s.trips[0].id; save(); go('dash'); toast('تحمّلو 27 معتمر و 41 دفعة');
}

/* ===================== لوحة القيادة ===================== */
function vDash() {
  const t = trip(), T = tripTotals(t), ps = tripPilgrims(t);
  acts(`<button class="btn pri" onclick="pilgrimForm()">➕ معتمر جديد</button><button class="btn" onclick="paymentForm()">💵 دفعة جديدة</button>`);
  const days = t.dep ? Math.ceil((new Date(t.dep) - new Date(today())) / 864e5) : null;
  const pct = T.sell ? Math.round(T.paid / T.sell * 100) : 0;
  const owing = ps.map(p => ({ p, c: calc(p) })).filter(x => x.c.rem > 0).sort((a, b) => b.c.rem - a.c.rem);
  const noPass = ps.filter(p => passportWarn(p));
  const byType = TYPES.map(ty => [ty, ps.filter(p => p.roomType === ty).length]).filter(x => x[1]);
  $('#view').innerHTML = `
  <div class="card" style="margin-bottom:16px"><div class="trip-head">
    ${days !== null ? `<div class="countdown"><b>${days > 0 ? days : (days === 0 ? 'اليوم' : '✓')}</b>${days > 0 ? 'يوم على السفر' : (days === 0 ? 'يوم السفر' : 'الرحلة فاتت')}</div>` : ''}
    <div class="f"><small>الرحلة</small><b>${esc(t.name)}</b></div>
    <div class="f"><small>الذهاب</small><b>${dfmt(t.dep)} · ${esc(t.flightGo || '')}</b><small>${esc(t.timeGo || '')}</small></div>
    <div class="f"><small>العودة</small><b>${dfmt(t.ret)} · ${esc(t.flightBack || '')}</b><small>${esc(t.timeBack || '')}</small></div>
    <div class="f"><small>رئيس المجموعة</small><b>${esc(t.leader || '—')}</b></div>
    <div class="f"><small>المرافقون</small><b>${esc(t.guides || '—')}</b></div>
    <div class="f"><small>الوكالة</small><b>${esc(t.agency || '—')}</b></div>
  </div></div>
  <div class="cards">
    <div class="card kpi"><small>المعتمرون</small><b>${T.n}</b><span>${byType.map(x => x[1] + ' ' + x[0]).join(' · ')}</span></div>
    <div class="card kpi g"><small>المقبوض من المعتمرين</small><b>${money(T.paid)}</b><div class="bar"><div style="width:${Math.min(pct, 100)}%"></div></div><span>${pct}% من ${money(T.sell)}</span></div>
    <div class="card kpi r"><small>الباقي عند المعتمرين</small><b>${money(T.rem)}</b><span>${T.owing} معتمر باقي عليهم</span></div>
    <div class="card kpi a"><small>الباقي للوكالة</small><b>${money(T.agRem)}</b><span>دفعت ${money(T.agPaid)} من ${money(T.buy)}</span></div>
    <div class="card kpi b"><small>الربح المتوقع</small><b>${money(T.profit)}</b><span>${T.buy ? '' : '⚠️ عمّر أسعار الشراء في «الرحلات»'}</span></div>
    <div class="card kpi"><small>الفلوس اللي عندك توّا</small><b>${money(T.cash)}</b><span>المقبوض − المدفوع للوكالة</span></div>
  </div>
  <div class="grid2">
    <div class="card"><h3>💰 شكون باقي عليه (${owing.length})</h3>
      ${owing.length ? `<table><thead><tr><th>المعتمر</th><th>الجوال</th><th>دفع</th><th>الباقي</th><th></th></tr></thead><tbody>
      ${owing.map(({ p, c }) => `<tr><td class="name">${esc(p.name)}</td><td>${esc(p.phone)}</td><td class="num">${money(c.paid)}</td><td class="num warn">${money(c.rem)}</td>
      <td><button class="btn sm" onclick="paymentForm(null,'${p.id}')">💵 دفعة</button> ${p.phone ? `<button class="btn sm" onclick="waRemind('${p.id}')">واتساب</button>` : ''}</td></tr>`).join('')}
      </tbody></table>` : '<div class="empty">الكل خالص 🎉</div>'}
    </div>
    <div class="card"><h3>🛂 جوازات السفر (${noPass.length})</h3>
      ${noPass.length ? `<table><tbody>${noPass.map(p => `<tr><td class="name">${esc(p.name)}</td><td class="warn">${passportWarn(p)}</td><td><button class="btn sm" onclick="pilgrimForm('${p.id}')">✏️</button></td></tr>`).join('')}</tbody></table>` : '<div class="empty">الجوازات الكل مسجّلة ✔</div>'}
    </div>
  </div>`;
}

/* ===================== المعتمرون ===================== */
let pq = '', pf = 'all';
function vPilgrims() {
  acts(`<button class="btn pri" onclick="pilgrimForm()">➕ معتمر جديد <small>(Ctrl+N)</small></button>
    <button class="btn" onclick="printList()">🖨️ طباعة القائمة</button><button class="btn" onclick="exportExcel()">📗 Excel</button>`);
  const F = { all: 'الكل', 'باقي عليه': 'باقي عليه', 'ما دفعش': 'ما دفعش', 'خالص': 'خالص', pass: 'جواز ناقص' };
  $('#view').innerHTML = `<div class="toolbar">
    <div class="search"><input id="pq" placeholder="ابحث بالاسم، الجوال، الجواز، رقم الغرفة…" value="${esc(pq)}"></div>
    <div class="chips">${Object.entries(F).map(([k, v]) => `<span class="chip ${pf === k ? 'on' : ''}" data-f="${k}">${v}</span>`).join('')}</div>
  </div><div class="tbl-wrap" id="ptbl"></div>`;
  $('#pq').addEventListener('input', e => { pq = e.target.value; drawP(); });
  document.querySelectorAll('.chip').forEach(c => c.onclick = () => { pf = c.dataset.f; vPilgrims(); });
  drawP(); $('#pq').focus();
}
function drawP() {
  const q = pq.trim().toLowerCase();
  let rows = tripPilgrims().map(p => ({ p, c: calc(p) }));
  if (q) rows = rows.filter(({ p }) => [p.name, p.phone, p.passport, 'الغرفة ' + p.room, p.no].join(' ').toLowerCase().includes(q));
  if (pf === 'pass') rows = rows.filter(({ p }) => passportWarn(p));
  else if (pf !== 'all') rows = rows.filter(({ c }) => c.st === pf);
  rows.sort((a, b) => (num(a.p.room) - num(b.p.room)) || (a.p.no - b.p.no));
  const S = rows.reduce((s, { c }) => (s.sell += c.sell, s.paid += c.paid, s.rem += c.rem, s), { sell: 0, paid: 0, rem: 0 });
  $('#ptbl').innerHTML = rows.length ? `<table><thead><tr><th>#</th><th>الاسم</th><th>الجوال</th><th>جواز السفر</th><th>الغرفة</th><th>النوع</th><th>الصفة</th><th>ثمن البيع</th><th>دفع</th><th>الباقي</th><th>الحالة</th><th></th></tr></thead><tbody>
  ${rows.map(({ p, c }) => `<tr ondblclick="pilgrimForm('${p.id}')">
    <td>${p.no}</td><td class="name"><span class="tag ${p.role !== 'pilgrim' ? 'role-' + p.role : ''}" style="${p.role === 'pilgrim' ? 'padding:0' : ''}">${esc(p.name)}</span></td>
    <td>${esc(p.phone)}</td><td>${p.passport ? esc(p.passport) : '<span class="warn">ناقص</span>'}${passportWarn(p) && p.passport ? ' ⚠️' : ''}</td>
    <td>الغرفة ${esc(p.room)}</td><td><span class="tag rt-${p.roomType}">${p.roomType}</span></td><td>${ROLES[p.role]}</td>
    <td class="num">${money(c.sell)}</td><td class="num">${money(c.paid)}</td><td class="num ${c.rem > 0 ? 'warn' : ''}">${money(c.rem)}</td>
    <td><span class="tag ${c.cls}">${c.st}</span></td>
    <td><button class="btn sm" onclick="paymentForm(null,'${p.id}')">💵</button> <button class="btn sm" onclick="statement('${p.id}')">🧾</button> <button class="btn sm" onclick="pilgrimForm('${p.id}')">✏️</button></td></tr>`).join('')}
  </tbody><tfoot><tr><td colspan="7">المجموع (${rows.length})</td><td class="num">${money(S.sell)}</td><td class="num">${money(S.paid)}</td><td class="num">${money(S.rem)}</td><td colspan="2"></td></tr></tfoot></table>`
    : '<div class="empty">ما فما حتى معتمر. اضغط «➕ معتمر جديد».</div>';
}
function pilgrimForm(id) {
  const t = trip(); const p = id ? DB.pilgrims.find(x => x.id === id) : null;
  const used = tripPilgrims(t).map(x => num(x.room));
  const lastRoom = Math.max(0, ...used);
  // اقتراح غرفة: آخر غرفة فيها بلاصة وإلا غرفة جديدة
  let sugRoom = lastRoom + 1, sugType = 'رباعي';
  if (lastRoom) { const occ = tripPilgrims(t).filter(x => num(x.room) === lastRoom); const ty = occ[0]?.roomType; if (ty && occ.length < CAP[ty]) { sugRoom = lastRoom; sugType = ty; } }
  const v = p || { name: '', phone: '', passport: '', passportExp: '', room: sugRoom, roomType: sugType, role: 'pilgrim', sell: num(t.sell?.[sugType]) || '', buy: null, notes: '' };
  modal(`<div class="box-h"><h2>${p ? '✏️ تعديل: ' + esc(p.name) : '➕ تسجيل معتمر جديد'}</h2><button class="btn ghost" onclick="closeModal()">✕</button></div>
  <div class="box-b"><div class="form" id="pf">
    <div class="sec-t">المعلومات الشخصية</div>
    <label class="full">الاسم الكامل (كيف في جواز السفر)<input name="name" value="${esc(v.name)}" required></label>
    <label>رقم الجوال<input name="phone" value="${esc(v.phone)}" inputmode="tel" placeholder="مثال: 22123456"></label>
    <label>الصفة<select name="role">${Object.entries(ROLES).map(([k, n]) => `<option value="${k}" ${v.role === k ? 'selected' : ''}>${n}</option>`).join('')}</select></label>
    <label>رقم جواز السفر<input name="passport" value="${esc(v.passport)}" style="text-transform:uppercase" placeholder="مثال: Z1234567"></label>
    <label>تاريخ انتهاء الجواز <span class="hint">(اختياري)</span><input type="date" name="passportExp" value="${esc(v.passportExp || '')}"></label>
    <div class="sec-t">الغرفة</div>
    <label>رقم الغرفة <span class="hint">(نفس الرقم = نفس الغرفة)</span><input type="number" min="1" name="room" value="${esc(v.room)}"></label>
    <label>نوع الغرفة<div class="seg" id="rtSeg">${TYPES.map(ty => `<button type="button" data-t="${ty}" class="${v.roomType === ty ? 'on' : ''}">${ty}</button>`).join('')}</div></label>
    <div class="full hint" id="roomInfo"></div>
    <div class="sec-t">الثمن</div>
    <label>ثمن البيع للمعتمر (دينار)<input name="sell" value="${esc(v.sell)}" inputmode="decimal"></label>
    <label>ثمن الشراء من الوكالة <span class="hint">(فارغ = حسب الرحلة: <b id="buyDef"></b>)</span><input name="buy" value="${v.buy ?? ''}" inputmode="decimal"></label>
    ${p ? '' : `<label>قداش دفع توّا (دينار)<input name="first" inputmode="decimal" placeholder="0"></label>
    <label>طريقة الدفع<select name="method">${METHODS.map(m => `<option>${m}</option>`).join('')}</select></label>`}
    <label class="full">ملاحظات<textarea name="notes" rows="2">${esc(v.notes)}</textarea></label>
    ${p ? `<div class="full sumline" id="pSum"></div>` : ''}
  </div></div>
  <div class="box-f"><button class="btn pri" id="pSave">💾 حفظ</button>${p ? '' : '<button class="btn" id="pSaveNew">💾 حفظ وسجّل واحد آخر</button>'}
   ${p ? `<button class="btn" onclick="paymentForm(null,'${p.id}')">💵 دفعة</button><button class="btn danger" style="margin-inline-start:auto" onclick="delPilgrim('${p.id}')">🗑️ حذف</button>` : ''}</div>`);
  const f = fields($('#pf')); let rt = v.roomType;
  const upd = () => {
    $('#buyDef').textContent = money(t.buy?.[rt]);
    const r = num(f.room.value); const occ = tripPilgrims(t).filter(x => num(x.room) === r && x.id !== p?.id);
    const ty0 = occ[0]?.roomType;
    $('#roomInfo').innerHTML = occ.length ? `في الغرفة ${r}: ${occ.map(x => esc(x.name)).join('، ')} (${ty0})` + (ty0 && ty0 !== rt ? ' <span class="warn">⚠️ نوع الغرفة مختلف</span>' : '') + (occ.length + 1 > CAP[rt] ? ' <span class="warn">⚠️ الغرفة كاملة</span>' : '') : `الغرفة ${r || '?'} فارغة: هذا أول واحد فيها.`;
  };
  $('#rtSeg').onclick = e => { const b = e.target.closest('button'); if (!b) return; rt = b.dataset.t; $('#rtSeg').querySelectorAll('button').forEach(x => x.classList.toggle('on', x === b)); if (!p && t.sell?.[rt]) f.sell.value = t.sell[rt]; upd(); };
  f.room.oninput = () => { const r = num(f.room.value); const o = tripPilgrims(t).find(x => num(x.room) === r && x.id !== p?.id); if (o) { rt = o.roomType; $('#rtSeg').querySelectorAll('button').forEach(x => x.classList.toggle('on', x.dataset.t === rt)); } upd(); };
  upd();
  if (p) { const c = calc(p); $('#pSum').innerHTML = `<div><small>ثمن البيع</small><b>${money(c.sell)}</b></div><div><small>دفع</small><b>${money(c.paid)}</b></div><div><small>الباقي</small><b class="${c.rem > 0 ? 'warn' : ''}">${money(c.rem)}</b></div><div><small>الربح</small><b>${money(c.profit)}</b></div><div><small>الحالة</small><b><span class="tag ${c.cls}">${c.st}</span></b></div>`; }
  const doSave = again => {
    const name = f.name.value.trim(); if (!name) { f.name.focus(); return toast('اكتب الاسم'); }
    const o = p || { id: uid('p'), no: DB.nextNo++, tripId: t.id, created: today() };
    Object.assign(o, { name, phone: f.phone.value.trim(), role: f.role.value, passport: f.passport.value.trim().toUpperCase(), passportExp: f.passportExp.value, room: num(f.room.value) || 1, roomType: rt, sell: num(f.sell.value), buy: f.buy.value.trim() === '' ? null : num(f.buy.value), notes: f.notes.value.trim() });
    if (!p) { DB.pilgrims.push(o); const first = num(f.first.value); if (first) DB.payments.push({ id: uid('y'), rcpt: DB.nextRcpt++, pilgrimId: o.id, date: today(), amount: first, method: f.method.value, note: 'دفعة عند التسجيل' }); }
    save(); toast(p ? 'تبدّل ✔' : `تسجّل ${name} ✔`); closeModal(); render(); if (again) pilgrimForm();
  };
  $('#pSave').onclick = () => doSave(false); if (!p) $('#pSaveNew').onclick = () => doSave(true);
  f.name.focus();
}
function delPilgrim(id) {
  const p = DB.pilgrims.find(x => x.id === id); const n = DB.payments.filter(x => x.pilgrimId === id).length;
  confirmBox(`تحذف ${esc(p.name)}${n ? ` و ${n} دفعة متاعو` : ''}؟ ما ترجعش.`, () => { DB.pilgrims = DB.pilgrims.filter(x => x.id !== id); DB.payments = DB.payments.filter(x => x.pilgrimId !== id); save(); closeModal(); render(); toast('تحذف'); });
}
function waRemind(id) {
  const p = DB.pilgrims.find(x => x.id === id), c = calc(p), t = trip();
  let ph = String(p.phone).replace(/\D/g, ''); if (ph.length === 8) ph = '216' + ph;
  const msg = `السلام عليكم ${p.name}،\nنذكّرك بخصوص ${t.name} (الذهاب ${dfmt(t.dep)}):\nالثمن: ${money(c.sell)}\nخلّصت: ${money(c.paid)}\nالباقي: ${money(c.rem)}\nربي يتقبّل منّا ومنكم 🤲\n${DB.settings.owner || ''}`;
  window.open('https://wa.me/' + ph + '?text=' + encodeURIComponent(msg), '_blank');
}

/* ===================== الدفعات ===================== */
let yq = '';
function vPayments() {
  acts(`<button class="btn pri" onclick="paymentForm()">💵 دفعة جديدة</button><button class="btn" onclick="exportExcel()">📗 Excel</button>`);
  $('#view').innerHTML = `<div class="toolbar"><div class="search"><input id="yq" placeholder="ابحث باسم المعتمر…" value="${esc(yq)}"></div></div><div class="tbl-wrap" id="ytbl"></div>`;
  $('#yq').oninput = e => { yq = e.target.value; drawY(); }; drawY();
}
function drawY() {
  const ids = new Set(tripPilgrims().map(p => p.id)); const q = yq.trim();
  let rows = DB.payments.filter(y => ids.has(y.pilgrimId)).map(y => ({ y, p: DB.pilgrims.find(p => p.id === y.pilgrimId) }));
  if (q) rows = rows.filter(r => r.p.name.includes(q));
  rows.sort((a, b) => (b.y.date || '').localeCompare(a.y.date || '') || (b.y.rcpt - a.y.rcpt));
  const tot = rows.reduce((s, r) => s + num(r.y.amount), 0);
  $('#ytbl').innerHTML = rows.length ? `<table><thead><tr><th>وصل</th><th>التاريخ</th><th>المعتمر</th><th>المبلغ</th><th>الطريقة</th><th>ملاحظة</th><th></th></tr></thead><tbody>
  ${rows.map(({ y, p }) => `<tr><td>${y.rcpt || ''}</td><td>${dfmt(y.date)}</td><td class="name">${esc(p.name)}</td><td class="num">${money(y.amount)}</td><td>${esc(y.method)}</td><td>${esc(y.note)}</td>
  <td><button class="btn sm" onclick="receipt('${y.id}')">🖨️ وصل</button> <button class="btn sm" onclick="paymentForm('${y.id}')">✏️</button></td></tr>`).join('')}
  </tbody><tfoot><tr><td colspan="3">المجموع (${rows.length} دفعة)</td><td class="num">${money(tot)}</td><td colspan="3"></td></tr></tfoot></table>` : '<div class="empty">ما فما حتى دفعة.</div>';
}
function paymentForm(id, pid) {
  const y = id ? DB.payments.find(x => x.id === id) : null;
  const ps = tripPilgrims().slice().sort((a, b) => a.name.localeCompare(b.name, 'ar'));
  const sel = y ? y.pilgrimId : (pid || '');
  modal(`<div class="box-h"><h2>${y ? '✏️ تعديل دفعة' : '💵 دفعة جديدة'}</h2><button class="btn ghost" onclick="closeModal()">✕</button></div>
  <div class="box-b"><div class="form" id="yf">
    <label class="full">المعتمر<select name="pid"><option value="">— اختار —</option>${ps.map(p => { const c = calc(p); return `<option value="${p.id}" ${p.id === sel ? 'selected' : ''}>${esc(p.name)} — الباقي ${money(c.rem)}</option>`; }).join('')}</select></label>
    <label>المبلغ (دينار)<input name="amount" inputmode="decimal" value="${y ? y.amount : ''}"></label>
    <label>التاريخ<input type="date" name="date" value="${y ? y.date : today()}"></label>
    <label>طريقة الدفع<select name="method">${METHODS.map(m => `<option ${y?.method === m ? 'selected' : ''}>${m}</option>`).join('')}</select></label>
    <label>ملاحظة<input name="note" value="${esc(y?.note || '')}"></label>
    <div class="full sumline" id="ySum"></div>
  </div></div>
  <div class="box-f"><button class="btn pri" id="ySave">💾 حفظ</button><button class="btn" id="ySaveP">💾 حفظ وطباعة وصل</button>
  ${y ? `<button class="btn danger" style="margin-inline-start:auto" onclick="delPayment('${y.id}')">🗑️ حذف</button>` : ''}</div>`);
  const f = fields($('#yf'));
  const upd = () => { const p = DB.pilgrims.find(x => x.id === f.pid.value); if (!p) return $('#ySum').innerHTML = ''; const c = calc(p); const a = num(f.amount.value) - (y ? num(y.amount) : 0);
    $('#ySum').innerHTML = `<div><small>ثمن البيع</small><b>${money(c.sell)}</b></div><div><small>دفع قبل</small><b>${money(c.paid - (y ? num(y.amount) : 0))}</b></div><div><small>الباقي بعد الدفعة</small><b class="${c.rem - a > 0 ? 'warn' : ''}">${money(c.rem - a)}</b></div>`; };
  f.pid.onchange = upd; f.amount.oninput = upd; upd();
  const doSave = pr => {
    if (!f.pid.value) return toast('اختار المعتمر'); const amount = num(f.amount.value); if (!amount) { f.amount.focus(); return toast('اكتب المبلغ'); }
    const o = y || { id: uid('y'), rcpt: DB.nextRcpt++ };
    Object.assign(o, { pilgrimId: f.pid.value, amount, date: f.date.value, method: f.method.value, note: f.note.value.trim() });
    if (!y) DB.payments.push(o); save(); closeModal(); render(); toast('الدفعة تسجّلت ✔'); if (pr) receipt(o.id);
  };
  $('#ySave').onclick = () => doSave(false); $('#ySaveP').onclick = () => doSave(true);
  (sel ? f.amount : f.pid).focus();
}
function delPayment(id) { confirmBox('تحذف الدفعة هاذي؟', () => { DB.payments = DB.payments.filter(x => x.id !== id); save(); closeModal(); render(); toast('تحذفت'); }); }

/* ===================== الوكالة ===================== */
function vAgency() {
  const t = trip(), T = tripTotals(t);
  acts(`<button class="btn pri" onclick="agencyForm()">🏢 دفعة للوكالة</button>`);
  const rows = DB.agency.filter(a => a.tripId === t.id).sort((a, b) => (b.date || '').localeCompare(a.date || ''));
  const ps = tripPilgrims(t);
  const byType = TYPES.map(ty => { const l = ps.filter(p => p.roomType === ty); return [ty, l.length, l.reduce((s, p) => s + buyOf(p), 0)]; }).filter(x => x[1]);
  $('#view').innerHTML = `<div class="cards">
    <div class="card kpi"><small>مجموع الشراء من الوكالة</small><b>${money(T.buy)}</b><span>${T.buy ? '' : '⚠️ عمّر أسعار الشراء في «الرحلات»'}</span></div>
    <div class="card kpi g"><small>دفعت للوكالة</small><b>${money(T.agPaid)}</b></div>
    <div class="card kpi r"><small>الباقي للوكالة</small><b>${money(T.agRem)}</b></div></div>
  <div class="grid2"><div class="tbl-wrap">${rows.length ? `<table><thead><tr><th>التاريخ</th><th>المبلغ</th><th>الطريقة</th><th>رقم الوصل</th><th>ملاحظة</th><th></th></tr></thead><tbody>
    ${rows.map(a => `<tr><td>${dfmt(a.date)}</td><td class="num">${money(a.amount)}</td><td>${esc(a.method)}</td><td>${esc(a.ref)}</td><td>${esc(a.note)}</td><td><button class="btn sm" onclick="agencyForm('${a.id}')">✏️</button></td></tr>`).join('')}
    </tbody></table>` : '<div class="empty">ما سجّلت حتى دفعة للوكالة.</div>'}</div>
    <div class="card"><h3>تفصيل الشراء</h3><table><thead><tr><th>النوع</th><th>العدد</th><th>المبلغ</th></tr></thead><tbody>${byType.map(x => `<tr><td>${x[0]}</td><td>${x[1]}</td><td class="num">${money(x[2])}</td></tr>`).join('')}</tbody></table></div></div>`;
}
function agencyForm(id) {
  const a = id ? DB.agency.find(x => x.id === id) : null;
  modal(`<div class="box-h"><h2>🏢 ${a ? 'تعديل دفعة للوكالة' : 'دفعة للوكالة'}</h2><button class="btn ghost" onclick="closeModal()">✕</button></div>
  <div class="box-b"><div class="form" id="af">
    <label>المبلغ (دينار)<input name="amount" inputmode="decimal" value="${a ? a.amount : ''}"></label>
    <label>التاريخ<input type="date" name="date" value="${a ? a.date : today()}"></label>
    <label>طريقة الدفع<select name="method">${METHODS.map(m => `<option ${a?.method === m ? 'selected' : ''}>${m}</option>`).join('')}</select></label>
    <label>رقم الوصل<input name="ref" value="${esc(a?.ref || '')}"></label>
    <label class="full">ملاحظة<input name="note" value="${esc(a?.note || '')}"></label>
  </div></div><div class="box-f"><button class="btn pri" id="aSave">💾 حفظ</button>${a ? `<button class="btn danger" style="margin-inline-start:auto" id="aDel">🗑️ حذف</button>` : ''}</div>`);
  const f = fields($('#af')); f.amount.focus();
  $('#aSave').onclick = () => { const amount = num(f.amount.value); if (!amount) return toast('اكتب المبلغ');
    const o = a || { id: uid('a'), tripId: trip().id }; Object.assign(o, { amount, date: f.date.value, method: f.method.value, ref: f.ref.value.trim(), note: f.note.value.trim() });
    if (!a) DB.agency.push(o); save(); closeModal(); render(); toast('تسجّلت ✔'); };
  if (a) $('#aDel').onclick = () => confirmBox('تحذف الدفعة هاذي؟', () => { DB.agency = DB.agency.filter(x => x.id !== id); save(); closeModal(); render(); });
}

/* ===================== الغرف ===================== */
function vRooms() {
  acts(`<button class="btn" onclick="printRooms()">🖨️ طباعة قائمة الغرف</button><button class="btn pri" onclick="pilgrimForm()">➕ معتمر جديد</button>`);
  const ps = tripPilgrims(); const rooms = {};
  ps.forEach(p => { (rooms[p.room] = rooms[p.room] || []).push(p); });
  const keys = Object.keys(rooms).sort((a, b) => num(a) - num(b));
  const sum = TYPES.map(ty => [ty, keys.filter(k => rooms[k][0].roomType === ty).length]).filter(x => x[1]);
  $('#view').innerHTML = `<div class="cards">${sum.map(x => `<div class="card kpi"><small>غرف ${x[0]}</small><b>${x[1]}</b></div>`).join('')}<div class="card kpi"><small>مجموع الغرف</small><b>${keys.length}</b></div></div>
  <div class="rooms">${keys.map(k => { const l = rooms[k], ty = l[0].roomType, cap = CAP[ty], st = l.length === cap ? 'full' : l.length > cap ? 'over' : '';
    const mix = l.some(p => p.roomType !== ty);
    return `<div class="room ${st}"><div class="room-h"><b>الغرفة ${esc(k)}</b><span class="tag rt-${ty}">${ty}</span></div>
    <div class="beds">${Array.from({ length: Math.max(cap, l.length) }, (_, i) => `<div class="bed ${i < l.length ? 'f' : ''}"></div>`).join('')}</div>
    <div class="hint" style="font-size:12px;color:var(--muted);margin-bottom:6px">${l.length}/${cap} ${st === 'full' ? '· كاملة ✔' : st === 'over' ? '<span class="warn">· أكثر من السعة!</span>' : '· فيها ' + (cap - l.length) + ' بلاصة'} ${mix ? '<span class="warn">· أنواع مختلفة!</span>' : ''}</div>
    <ul>${l.map(p => `<li><span>${esc(p.name)} ${p.role !== 'pilgrim' ? `<span class="tag role-${p.role}">${ROLES[p.role]}</span>` : ''}</span><a href="#" onclick="pilgrimForm('${p.id}');return false">✏️</a></li>`).join('')}</ul></div>`; }).join('')}</div>`;
}

/* ===================== الرحلات ===================== */
function vTrips() {
  acts(`<button class="btn pri" onclick="tripForm()">✈️ رحلة جديدة</button>`);
  $('#view').innerHTML = `<div class="tbl-wrap"><table><thead><tr><th>الرحلة</th><th>الذهاب</th><th>العودة</th><th>الطيران</th><th>رئيس المجموعة</th><th>المعتمرون</th><th>المقبوض</th><th>الربح</th><th></th></tr></thead><tbody>
  ${DB.trips.map(t => { const T = tripTotals(t); return `<tr><td class="name">${esc(t.name)}</td><td>${dfmt(t.dep)}</td><td>${dfmt(t.ret)}</td><td>${esc(t.flightGo)} / ${esc(t.flightBack)}</td><td>${esc(t.leader)}</td><td>${T.n}</td><td class="num">${money(T.paid)}</td><td class="num">${money(T.profit)}</td>
  <td><button class="btn sm" onclick="tripForm('${t.id}')">✏️ تعديل</button></td></tr>`; }).join('')}</tbody></table></div>`;
}
function tripForm(id) {
  const t = id ? DB.trips.find(x => x.id === id) : null;
  const v = t || { name: '', agency: '', dep: '', ret: '', flightGo: '', timeGo: '', flightBack: '', timeBack: '', leader: DB.settings.owner || '', guides: '', hotelMakkah: '', hotelMadinah: '', sell: {}, buy: {} };
  modal(`<div class="box-h"><h2>✈️ ${t ? 'تعديل الرحلة' : 'رحلة جديدة'}</h2><button class="btn ghost" onclick="closeModal()">✕</button></div>
  <div class="box-b"><div class="form" id="tf">
    <label class="full">اسم الرحلة<input name="name" value="${esc(v.name)}" placeholder="مثال: عمرة رمضان 2027"></label>
    <label>الوكالة<input name="agency" value="${esc(v.agency)}"></label>
    <label>رئيس المجموعة<input name="leader" value="${esc(v.leader)}"></label>
    <label class="full">المرافقون<input name="guides" value="${esc(v.guides)}" placeholder="أسماء مفرّقة بفاصلة"></label>
    <div class="sec-t">الطيران</div>
    <label>تاريخ الذهاب<input type="date" name="dep" value="${esc(v.dep)}"></label>
    <label>تاريخ العودة<input type="date" name="ret" value="${esc(v.ret)}"></label>
    <label>رقم رحلة الذهاب<input name="flightGo" value="${esc(v.flightGo)}" placeholder="SV366"></label>
    <label>توقيت الذهاب<input name="timeGo" value="${esc(v.timeGo)}" placeholder="11:40 تونس ← 18:05 السعودية"></label>
    <label>رقم رحلة العودة<input name="flightBack" value="${esc(v.flightBack)}" placeholder="SV365"></label>
    <label>توقيت العودة<input name="timeBack" value="${esc(v.timeBack)}"></label>
    <label>فندق مكة<input name="hotelMakkah" value="${esc(v.hotelMakkah)}"></label>
    <label>فندق المدينة<input name="hotelMadinah" value="${esc(v.hotelMadinah)}"></label>
    <div class="sec-t">الأسعار حسب نوع الغرفة (دينار)</div>
    <table class="full"><thead><tr><th></th>${TYPES.map(x => `<th>${x}</th>`).join('')}</tr></thead><tbody>
      <tr><td><b>ثمن البيع العادي</b></td>${TYPES.map(x => `<td><input data-s="${x}" value="${v.sell?.[x] || ''}" inputmode="decimal" style="width:100%"></td>`).join('')}</tr>
      <tr><td><b>ثمن الشراء من الوكالة</b></td>${TYPES.map(x => `<td><input data-b="${x}" value="${v.buy?.[x] || ''}" inputmode="decimal" style="width:100%"></td>`).join('')}</tr>
    </tbody></table>
    <div class="full hint">ثمن البيع العادي يتعمّر وحدو كي تسجّل معتمر، وتنجم تبدّلو لكل واحد. ثمن الشراء يتحسب لكل معتمر حسب نوع غرفتو.</div>
  </div></div><div class="box-f"><button class="btn pri" id="tSave">💾 حفظ</button>${t && DB.trips.length > 1 ? `<button class="btn danger" style="margin-inline-start:auto" id="tDel">🗑️ حذف الرحلة</button>` : ''}</div>`);
  const f = fields($('#tf')); f.name.focus();
  $('#tSave').onclick = () => { if (!f.name.value.trim()) return toast('اكتب اسم الرحلة');
    const o = t || { id: uid('t') }; ['name', 'agency', 'leader', 'guides', 'dep', 'ret', 'flightGo', 'timeGo', 'flightBack', 'timeBack', 'hotelMakkah', 'hotelMadinah'].forEach(k => o[k] = f[k].value.trim());
    o.sell = {}; o.buy = {}; f.el.querySelectorAll('[data-s]').forEach(i => o.sell[i.dataset.s] = num(i.value)); f.el.querySelectorAll('[data-b]').forEach(i => o.buy[i.dataset.b] = num(i.value));
    if (!t) { DB.trips.push(o); curTrip = o.id; localStorage.setItem('curTrip', curTrip); } save(); closeModal(); if (view === 'dash' || !t) go(t ? view : 'dash'); else render(); toast('الرحلة تسجّلت ✔'); };
  if (t && $('#tDel')) $('#tDel').onclick = () => confirmBox(`تحذف «${esc(t.name)}» بكل معتمرينها ودفعاتها؟`, () => {
    const ids = new Set(DB.pilgrims.filter(p => p.tripId === t.id).map(p => p.id));
    DB.payments = DB.payments.filter(y => !ids.has(y.pilgrimId)); DB.pilgrims = DB.pilgrims.filter(p => p.tripId !== t.id); DB.agency = DB.agency.filter(a => a.tripId !== t.id); DB.trips = DB.trips.filter(x => x.id !== t.id);
    curTrip = ''; save(); closeModal(); go('dash'); });
}

/* ===================== الإعدادات والنسخ ===================== */
function vSettings() {
  const s = DB.settings;
  $('#view').innerHTML = `<div class="grid2"><div class="card"><h3>👤 معلوماتك (تطلع في الوصولات)</h3><div class="form" id="sf">
    <label>اسمك<input name="owner" value="${esc(s.owner)}"></label><label>رقم الجوال<input name="phone" value="${esc(s.phone)}"></label>
    <label class="full">اسم المحل / النشاط<input name="shop" value="${esc(s.shop)}" placeholder="مثال: عمرة المرافق المحترف"></label>
    <div class="full"><button class="btn pri" id="sSave">💾 حفظ</button></div></div></div>
  <div class="card"><h3>💾 النسخ الاحتياطي</h3>
    <p class="hint" style="color:var(--muted)">البيانات تتحفظ وحدها في الكمبيوتر${window.native ? ' (وكل نهار تتعمل نسخة احتياطية أوتوماتيك)' : ''}. خرّج نسخة على فلاشة ولا Google Drive من وقت لآخر.</p>
    <div style="display:flex;gap:8px;flex-wrap:wrap">
      <button class="btn pri" onclick="exportJSON()">⬇️ خرّج نسخة احتياطية</button>
      <button class="btn" onclick="importJSON()">📂 رجّع نسخة</button>
      <button class="btn" onclick="exportExcel()">📗 خرّج الكل Excel</button>
      ${window.native ? '<button class="btn" onclick="native.openBackups()">📁 حل ملف النسخ الأوتوماتيكية</button>' : ''}
    </div>
    ${window.SEED_DECEMBER && !DB.trips.some(t => t.id === 't1') ? '<p><button class="btn" onclick="loadSeed()">📥 حمّل معتمرين رحلة ديسمبر</button></p>' : ''}
    <p class="hint" style="color:var(--muted);margin-top:14px">المعتمرون: ${DB.pilgrims.length} · الدفعات: ${DB.payments.length} · الرحلات: ${DB.trips.length}${window.native ? ' · النسخة ' + native.version : ''}</p></div></div>`;
  $('#sSave').onclick = () => { const f = fields($('#sf')); DB.settings = { owner: f.owner.value.trim(), phone: f.phone.value.trim(), shop: f.shop.value.trim() }; save(); toast('تحفظ ✔'); };
}
function download(name, data, type) {
  const b = new Blob([data], { type }); const a = document.createElement('a'); a.href = URL.createObjectURL(b); a.download = name; document.body.appendChild(a); a.click(); setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 500);
}
function exportJSON() { download(`مرافق-برو-نسخة-${today()}.json`, JSON.stringify(DB, null, 1), 'application/json'); toast('النسخة خرجت ✔'); }
function importJSON() {
  const i = document.createElement('input'); i.type = 'file'; i.accept = '.json';
  i.onchange = () => { const r = new FileReader(); r.onload = () => { try { const d = JSON.parse(r.result); if (!d.pilgrims || !d.trips) throw 0;
    confirmBox(`ترجّع النسخة هاذي (${d.pilgrims.length} معتمر، ${d.trips.length} رحلة)؟ البيانات الحالية تتبدّل بيها.`, () => { DB = d; curTrip = ''; save(); closeModal(); go('dash'); toast('النسخة رجعت ✔'); });
  } catch (e) { toast('الملف هذا موش نسخة صحيحة'); } }; r.readAsText(i.files[0]); };
  i.click();
}
function exportExcel() {
  const t = trip(); const wb = XLSX.utils.book_new();
  const P = tripPilgrims(t).sort((a, b) => num(a.room) - num(b.room)).map(p => { const c = calc(p); return { '#': p.no, 'الاسم': p.name, 'الجوال': p.phone, 'جواز السفر': p.passport, 'انتهاء الجواز': p.passportExp || '', 'الغرفة': p.room, 'نوع الغرفة': p.roomType, 'الصفة': ROLES[p.role], 'ثمن البيع': c.sell, 'ثمن الشراء': c.buy, 'المدفوع': c.paid, 'الباقي': c.rem, 'الحالة': c.st, 'ملاحظات': p.notes }; });
  const ids = new Set(tripPilgrims(t).map(p => p.id));
  const Y = DB.payments.filter(y => ids.has(y.pilgrimId)).map(y => ({ 'وصل': y.rcpt, 'التاريخ': dfmt(y.date), 'المعتمر': DB.pilgrims.find(p => p.id === y.pilgrimId).name, 'المبلغ': num(y.amount), 'الطريقة': y.method, 'ملاحظة': y.note }));
  const A = DB.agency.filter(a => a.tripId === t.id).map(a => ({ 'التاريخ': dfmt(a.date), 'المبلغ': num(a.amount), 'الطريقة': a.method, 'رقم الوصل': a.ref, 'ملاحظة': a.note }));
  const T = tripTotals(t);
  const S = [['الرحلة', t.name], ['الوكالة', t.agency], ['الذهاب', dfmt(t.dep) + ' ' + t.flightGo + ' ' + t.timeGo], ['العودة', dfmt(t.ret) + ' ' + t.flightBack + ' ' + t.timeBack], ['رئيس المجموعة', t.leader], ['المرافقون', t.guides], [], ['عدد المعتمرين', T.n], ['مجموع البيع', T.sell], ['المقبوض', T.paid], ['الباقي عند المعتمرين', T.rem], ['مجموع الشراء', T.buy], ['دفعت للوكالة', T.agPaid], ['الباقي للوكالة', T.agRem], ['الربح', T.profit]];
  const add = (ws, n) => { ws['!views'] = [{ RTL: true }]; XLSX.utils.book_append_sheet(wb, ws, n); };
  add(XLSX.utils.aoa_to_sheet(S), 'الملخص'); add(XLSX.utils.json_to_sheet(P), 'المعتمرون'); add(XLSX.utils.json_to_sheet(Y.length ? Y : [{ 'وصل': '' }]), 'دفعات المعتمرين'); add(XLSX.utils.json_to_sheet(A.length ? A : [{ 'التاريخ': '' }]), 'دفعات الوكالة');
  wb.Workbook = { Views: [{ RTL: true }] };
  download(`${t.name}.xlsx`, XLSX.write(wb, { type: 'array', bookType: 'xlsx' }), 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'); toast('ملف Excel خرج ✔');
}

/* ===================== الطباعة ===================== */
function doPrint(html) { $('#print').innerHTML = html; setTimeout(() => window.print(), 80); }
function head(title) { const s = DB.settings, t = trip(); return `<div class="ph"><div><h2>${esc(title)}</h2><div>${esc(t.name)} · ${dfmt(t.dep)} ← ${dfmt(t.ret)}</div></div><div style="text-align:left">${esc(s.shop || '')}<br>${esc(s.owner || '')} ${esc(s.phone || '')}</div></div>`; }
function receipt(id) {
  const y = DB.payments.find(x => x.id === id), p = DB.pilgrims.find(x => x.id === y.pilgrimId), c = calc(p), t = DB.trips.find(x => x.id === p.tripId), s = DB.settings;
  const one = copy => `<div class="rcpt"><div style="display:flex;justify-content:space-between"><div><h2>وصل خلاص</h2><div>${esc(s.shop || 'رحلات العمرة')} · ${esc(s.owner || '')} ${esc(s.phone || '')}</div></div><div style="text-align:left"><b>رقم ${y.rcpt || ''}</b><br>${dfmt(y.date)}<br><small>${copy}</small></div></div>
  <div class="row"><span>المعتمر</span><b>${esc(p.name)}</b></div><div class="row"><span>الرحلة</span><b>${esc(t.name)} (${dfmt(t.dep)})</b></div>
  <div class="row"><span>المبلغ المقبوض</span><span class="big">${money(y.amount)}</span></div><div class="row"><span>طريقة الدفع</span><b>${esc(y.method)}</b></div>
  <div class="row"><span>ثمن الرحلة</span><b>${money(c.sell)}</b></div><div class="row"><span>مجموع المدفوع</span><b>${money(c.paid)}</b></div><div class="row"><span>الباقي</span><b>${money(c.rem)}</b></div>
  <div class="sig"><span>إمضاء المعتمر</span><span>إمضاء المرافق</span></div></div>`;
  doPrint(one('نسخة المعتمر') + '<div style="border-top:1px dashed #999;margin:10px 0 18px"></div>' + one('نسخة المرافق'));
}
function statement(id) {
  const p = DB.pilgrims.find(x => x.id === id), c = calc(p); const ys = DB.payments.filter(y => y.pilgrimId === id);
  doPrint(head('كشف حساب: ' + p.name) + `<p>الجوال: ${esc(p.phone)} · الجواز: ${esc(p.passport)} · الغرفة ${p.room} (${p.roomType})</p>
  <table><thead><tr><th>وصل</th><th>التاريخ</th><th>المبلغ</th><th>الطريقة</th><th>ملاحظة</th></tr></thead><tbody>${ys.map(y => `<tr><td>${y.rcpt || ''}</td><td>${dfmt(y.date)}</td><td>${money(y.amount)}</td><td>${esc(y.method)}</td><td>${esc(y.note)}</td></tr>`).join('')}</tbody></table>
  <h3>ثمن الرحلة: ${money(c.sell)} · المدفوع: ${money(c.paid)} · الباقي: ${money(c.rem)}</h3>`);
}
function printList() {
  const ps = tripPilgrims().sort((a, b) => num(a.room) - num(b.room));
  doPrint(head('قائمة المعتمرين') + `<table><thead><tr><th>#</th><th>الاسم</th><th>جواز السفر</th><th>الجوال</th><th>الغرفة</th><th>النوع</th><th>الثمن</th><th>دفع</th><th>الباقي</th></tr></thead><tbody>
  ${ps.map((p, i) => { const c = calc(p); return `<tr><td>${i + 1}</td><td>${esc(p.name)}</td><td>${esc(p.passport)}</td><td>${esc(p.phone)}</td><td>${p.room}</td><td>${p.roomType}</td><td>${money(c.sell)}</td><td>${money(c.paid)}</td><td>${money(c.rem)}</td></tr>`; }).join('')}</tbody></table>`);
}
function printRooms() {
  const ps = tripPilgrims(); const rooms = {}; ps.forEach(p => (rooms[p.room] = rooms[p.room] || []).push(p));
  doPrint(head('توزيع الغرف (Rooming list)') + `<table><thead><tr><th>الغرفة</th><th>النوع</th><th>الأسماء</th><th>جوازات السفر</th></tr></thead><tbody>
  ${Object.keys(rooms).sort((a, b) => a - b).map(k => `<tr><td>${k}</td><td>${rooms[k][0].roomType}</td><td>${rooms[k].map(p => esc(p.name)).join('<br>')}</td><td>${rooms[k].map(p => esc(p.passport || '—')).join('<br>')}</td></tr>`).join('')}</tbody></table>`);
}

/* ===================== نوافذ ===================== */
function modal(html) { $('#modalBox').innerHTML = html; $('#modal').classList.remove('hidden'); }
function closeModal() { $('#modal').classList.add('hidden'); $('#modalBox').innerHTML = ''; }
function confirmBox(msg, yes) {
  modal(`<div class="box-h"><h2>⚠️ تأكيد</h2></div><div class="box-b">${msg}</div><div class="box-f"><button class="btn danger" id="cY">إيه، احذف</button><button class="btn" onclick="closeModal()">لا</button></div>`);
  $('#cY').onclick = yes;
}
$('#modal').addEventListener('mousedown', e => { if (e.target.id === 'modal') closeModal(); });
document.addEventListener('keydown', e => {
  if (e.key === 'Escape') closeModal();
  if (e.ctrlKey && (e.key === 'n' || e.key === 'N' || e.key === 'ى')) { e.preventDefault(); if (DB.trips.length) pilgrimForm(); }
  if (e.key === 'Enter' && !e.shiftKey && e.target.tagName === 'INPUT' && !$('#modal').classList.contains('hidden')) { const b = $('#modalBox .box-f .btn.pri'); if (b) { e.preventDefault(); b.click(); } }
});
render();
