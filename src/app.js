import {HebrewCalendar, Location, Zmanim, HDate, flags, Locale} from '@hebcal/core';
import '@hebcal/learning';

/* ========================================================================
   יומן עברי – לוח שנה עברי בעיצוב יומן גוגל
   ======================================================================== */

const LOC = 'he-x-NoNikud';
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];

/* ---------- ערים ---------- */
const CITIES = [
  // ישראל (mins = דקות הדלקת נרות לפני השקיעה)
  {id: 'jerusalem', name: 'ירושלים', lat: 31.7683, lon: 35.2137, tz: 'Asia/Jerusalem', il: true, mins: 40, grp: 'ישראל'},
  {id: 'telaviv', name: 'תל אביב-יפו', lat: 32.0853, lon: 34.7818, tz: 'Asia/Jerusalem', il: true, mins: 20, grp: 'ישראל'},
  {id: 'haifa', name: 'חיפה', lat: 32.794, lon: 34.9896, tz: 'Asia/Jerusalem', il: true, mins: 30, grp: 'ישראל'},
  {id: 'beersheva', name: 'באר שבע', lat: 31.2518, lon: 34.7913, tz: 'Asia/Jerusalem', il: true, mins: 20, grp: 'ישראל'},
  {id: 'bneibrak', name: 'בני ברק', lat: 32.0809, lon: 34.8338, tz: 'Asia/Jerusalem', il: true, mins: 20, grp: 'ישראל'},
  {id: 'bethshemesh', name: 'בית שמש', lat: 31.7469, lon: 34.9881, tz: 'Asia/Jerusalem', il: true, mins: 30, grp: 'ישראל'},
  {id: 'ashdod', name: 'אשדוד', lat: 31.8044, lon: 34.6553, tz: 'Asia/Jerusalem', il: true, mins: 20, grp: 'ישראל'},
  {id: 'netanya', name: 'נתניה', lat: 32.3215, lon: 34.8532, tz: 'Asia/Jerusalem', il: true, mins: 20, grp: 'ישראל'},
  {id: 'petahtikva', name: 'פתח תקווה', lat: 32.0841, lon: 34.8878, tz: 'Asia/Jerusalem', il: true, mins: 20, grp: 'ישראל'},
  {id: 'rishon', name: 'ראשון לציון', lat: 31.973, lon: 34.7925, tz: 'Asia/Jerusalem', il: true, mins: 20, grp: 'ישראל'},
  {id: 'safed', name: 'צפת', lat: 32.9646, lon: 35.496, tz: 'Asia/Jerusalem', il: true, mins: 30, grp: 'ישראל'},
  {id: 'tiberias', name: 'טבריה', lat: 32.7922, lon: 35.5312, tz: 'Asia/Jerusalem', il: true, mins: 30, grp: 'ישראל'},
  {id: 'eilat', name: 'אילת', lat: 29.5577, lon: 34.9519, tz: 'Asia/Jerusalem', il: true, mins: 20, grp: 'ישראל'},
  // חוץ לארץ
  {id: 'newyork', name: 'ניו יורק', lat: 40.7128, lon: -74.006, tz: 'America/New_York', il: false, mins: 18, grp: 'חוץ לארץ'},
  {id: 'losangeles', name: 'לוס אנג׳לס', lat: 34.0522, lon: -118.2437, tz: 'America/Los_Angeles', il: false, mins: 18, grp: 'חוץ לארץ'},
  {id: 'miami', name: 'מיאמי', lat: 25.7617, lon: -80.1918, tz: 'America/New_York', il: false, mins: 18, grp: 'חוץ לארץ'},
  {id: 'toronto', name: 'טורונטו', lat: 43.6532, lon: -79.3832, tz: 'America/Toronto', il: false, mins: 18, grp: 'חוץ לארץ'},
  {id: 'montreal', name: 'מונטריאול', lat: 45.5017, lon: -73.5673, tz: 'America/Toronto', il: false, mins: 18, grp: 'חוץ לארץ'},
  {id: 'london', name: 'לונדון', lat: 51.5074, lon: -0.1278, tz: 'Europe/London', il: false, mins: 18, grp: 'חוץ לארץ'},
  {id: 'paris', name: 'פריז', lat: 48.8566, lon: 2.3522, tz: 'Europe/Paris', il: false, mins: 18, grp: 'חוץ לארץ'},
  {id: 'antwerp', name: 'אנטוורפן', lat: 51.2194, lon: 4.4025, tz: 'Europe/Brussels', il: false, mins: 18, grp: 'חוץ לארץ'},
  {id: 'buenosaires', name: 'בואנוס איירס', lat: -34.6037, lon: -58.3816, tz: 'America/Argentina/Buenos_Aires', il: false, mins: 18, grp: 'חוץ לארץ'},
  {id: 'melbourne', name: 'מלבורן', lat: -37.8136, lon: 144.9631, tz: 'Australia/Melbourne', il: false, mins: 18, grp: 'חוץ לארץ'},
];

/* ---------- קטגוריות ---------- */
const CATS = {
  shabbat: {name: 'כניסת ויציאת שבת', color: '#8e24aa'},
  parsha: {name: 'פרשת השבוע ושבתות מיוחדות', color: '#3949ab'},
  holiday: {name: 'חגים ומועדים', color: '#0b8043'},
  rc: {name: 'ראש חודש', color: '#0277bd'},
  fast: {name: 'צומות', color: '#c62828'},
  modern: {name: 'ימים מיוחדים (יום העצמאות ועוד)', color: '#d84315'},
  omer: {name: 'ספירת העומר', color: '#616161'},
  molad: {name: 'מולד הלבנה', color: '#6d4c41'},
  daf: {name: 'דף יומי', color: '#00796b'},
  zmanim: {name: 'זמני היום (שבוע/יום)', color: '#e65100'},
};
const CAT_ORDER = ['holiday', 'parsha', 'rc', 'fast', 'modern', 'shabbat', 'omer', 'molad', 'daf'];
const DEFAULT_FILTERS = {shabbat: true, parsha: true, holiday: true, rc: true, fast: true, modern: true, omer: true, molad: false, daf: false, zmanim: true};

/* ---------- זמני היום ---------- */
const ZMANIM = [
  {id: 'alot', label: 'עלות השחר', fn: 'alotHaShachar', desc: 'תחילת היום ההלכתי – מכאן אפשר להתפלל שחרית בדיעבד, ללבוש טלית וכד׳ לפי הפוסקים.'},
  {id: 'misheyakir', label: 'משיכיר', fn: 'misheyakir', desc: 'הזמן המוקדם להנחת טלית ותפילין ולקריאת שמע של שחרית – כשיש די אור להכיר חבר.'},
  {id: 'sunrise', label: 'הנץ החמה', short: 'הנץ החמה', fn: 'sunrise', desc: 'זריחת השמש.', key: true},
  {id: 'shmaMGA', label: 'סוף זמן ק״ש (מג״א)', fn: 'sofZmanShmaMGA', desc: 'סוף זמן קריאת שמע לפי שיטת המגן אברהם.'},
  {id: 'shma', label: 'סוף זמן ק״ש (גר״א)', short: 'סוף ק״ש', fn: 'sofZmanShma', desc: 'סוף זמן קריאת שמע לפי הגר״א – שלוש שעות זמניות מהנץ החמה.', key: true},
  {id: 'tfillaMGA', label: 'סוף זמן תפילה (מג״א)', fn: 'sofZmanTfillaMGA', desc: 'סוף זמן תפילת שחרית לפי שיטת המגן אברהם.'},
  {id: 'tfilla', label: 'סוף זמן תפילה (גר״א)', short: 'סוף תפילה', fn: 'sofZmanTfilla', desc: 'סוף זמן תפילת שחרית לפי הגר״א – ארבע שעות זמניות מהנץ החמה.', key: true},
  {id: 'chatzot', label: 'חצות היום', short: 'חצות', fn: 'chatzot', desc: 'אמצע היום, בין הנץ לשקיעה.', key: true},
  {id: 'gedola', label: 'מנחה גדולה', short: 'מנחה ג׳', fn: 'minchaGedola', desc: 'תחילת הזמן להתפלל מנחה – חצי שעה זמנית אחרי חצות.', key: true},
  {id: 'ketana', label: 'מנחה קטנה', fn: 'minchaKetana', desc: 'תשע שעות וחצי זמניות מהנץ – זמן המנחה המובחר.'},
  {id: 'plag', label: 'פלג המנחה', fn: 'plagHaMincha', desc: 'שעה ורבע זמנית לפני סוף היום. מכאן אפשר להדליק נרות שבת ולקבל שבת לפי חלק מהמנהגים.'},
  {id: 'sunset', label: 'שקיעה', fn: 'sunset', desc: 'שקיעת השמש.', key: true},
  {id: 'tzeit', label: 'צאת הכוכבים', fn: 'tzeit', desc: 'תחילת הלילה – מוצאי שבת וחג, זמן קריאת שמע של ערבית.', key: true},
  {id: 'midnight', label: 'חצות הלילה', fn: 'chatzotNight', desc: 'אמצע הלילה – סוף זמן אכילת אפיקומן וכד׳.'},
];

/* ---------- עזרי תאריך ---------- */
const WD_FULL = ['ראשון', 'שני', 'שלישי', 'רביעי', 'חמישי', 'שישי', 'שבת'];
const WD_LETTER = ['א׳', 'ב׳', 'ג׳', 'ד׳', 'ה׳', 'ו׳', 'ש׳'];
const noon = d => new Date(d.getFullYear(), d.getMonth(), d.getDate(), 12);
const addDays = (d, n) => { const x = noon(d); x.setDate(x.getDate() + n); return x; };
const addMonths = (d, n) => new Date(d.getFullYear(), d.getMonth() + n, Math.min(d.getDate(), 28), 12);
const keyOf = d => d.getFullYear() + '-' + (d.getMonth() + 1) + '-' + d.getDate();
const sameDay = (a, b) => keyOf(a) === keyOf(b);
const startOfWeek = d => addDays(d, -d.getDay());
const gfmt = (d, o) => new Intl.DateTimeFormat('he-IL', o).format(d);
const esc = s => String(s).replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));

const hdOf = d => new HDate(noon(d));
const hMonth = hd => Locale.gettext(hd.getMonthName(), LOC);
const hParts = hd => hd.renderGematriya(true).split(' ');
const hDay = hd => hParts(hd)[0];
const hYear = hd => hParts(hd).pop();
const hFull = hd => `${hDay(hd)} ב${hMonth(hd)} ${hYear(hd)}`;

/* ---------- מצב ושמירה ---------- */
const STORE_KEY = 'jcal.v1';
const state = {
  view: 'month',
  cursor: noon(new Date()),
  mini: noon(new Date()),
  cityId: 'jerusalem',
  custom: null,           // {name, lat, lon, tz, il}
  candleMins: null,       // null = ברירת מחדל של העיר
  havdalah: '8.5',        // מעלות או דקות
  theme: 'auto',
  filters: {...DEFAULT_FILTERS},
};
try {
  const saved = JSON.parse(localStorage.getItem(STORE_KEY) || 'null');
  if (saved) {
    for (const k of ['view', 'cityId', 'custom', 'candleMins', 'havdalah', 'theme']) if (saved[k] !== undefined) state[k] = saved[k];
    state.filters = {...DEFAULT_FILTERS, ...(saved.filters || {})};
  }
} catch (e) { /* אחסון לא זמין */ }
const save = () => {
  try {
    const {view, cityId, custom, candleMins, havdalah, theme, filters} = state;
    localStorage.setItem(STORE_KEY, JSON.stringify({view, cityId, custom, candleMins, havdalah, theme, filters}));
  } catch (e) { /* ignore */ }
};

/* ---------- מיקום ---------- */
function curCity() {
  if (state.cityId === 'custom' && state.custom) return {...state.custom, id: 'custom', mins: 18, grp: 'מיקום'};
  return CITIES.find(c => c.id === state.cityId) || CITIES[0];
}
const candleMins = () => state.candleMins ?? curCity().mins;
let _loc = null, _locKey = '';
function getLoc() {
  const c = curCity();
  const k = c.id + c.lat + c.lon;
  if (k !== _locKey) {
    _loc = new Location(c.lat, c.lon, c.il, c.tz, c.name, c.il ? 'IL' : undefined);
    _locKey = k;
  }
  return _loc;
}

/* ---------- זמנים ---------- */
const tzFmt = {};
function fmtTime(d) {
  const tz = curCity().tz;
  tzFmt[tz] ||= new Intl.DateTimeFormat('he-IL', {hour: '2-digit', minute: '2-digit', hour12: false, timeZone: tz});
  return tzFmt[tz].format(d);
}
function minutesOfDay(d) {
  const [h, m] = fmtTime(d).split(':').map(Number);
  return (h % 24) * 60 + m;
}
const valid = d => d instanceof Date && !isNaN(d);

function tzeitOf(z) {
  if (state.havdalah === '8.5') return z.tzeit(8.5);
  return z.sunsetOffset(+state.havdalah, true);
}
function zmanimOf(date, onlyKey = false) {
  const loc = getLoc();
  const z = new Zmanim(loc, noon(date), false);
  const out = [];
  for (const m of ZMANIM) {
    if (onlyKey && !m.key) continue;
    let t;
    try {
      if (m.id === 'tzeit') t = tzeitOf(z);
      else if (m.id === 'midnight') t = new Zmanim(loc, addDays(date, 1), false).chatzotNight();
      else t = z[m.fn]();
    } catch (e) { t = null; }
    if (valid(t)) out.push({...m, time: t});
  }
  return out;
}

/* ---------- אירועים ---------- */
const evCache = new Map();
function fetchEvents(start, end) {
  const c = curCity();
  const ck = [c.id, c.lat, c.lon, candleMins(), state.havdalah, keyOf(start), keyOf(end)].join('|');
  if (evCache.has(ck)) return evCache.get(ck);
  const opts = {
    start: noon(start), end: noon(end), location: getLoc(), il: c.il,
    candlelighting: true, candleLightingMins: candleMins(),
    sedrot: true, omer: true, molad: true, shabbatMevarchim: true,
    dailyLearning: {dafYomi: true},
  };
  if (state.havdalah === '8.5') opts.havdalahDeg = 8.5; else opts.havdalahMins = +state.havdalah;
  let raw = [];
  try { raw = HebrewCalendar.calendar(opts); } catch (e) { console.error(e); }
  const byDay = new Map();
  for (const e of raw) {
    const n = normalize(e);
    if (!n) continue;
    if (!byDay.has(n.key)) byDay.set(n.key, []);
    byDay.get(n.key).push(n);
  }
  if (evCache.size > 40) evCache.clear();
  evCache.set(ck, byDay);
  return byDay;
}

function classify(e) {
  const f = e.getFlags(), d = e.getDesc();
  if (d === 'Candle lighting' || d === 'Havdalah') return 'shabbat';
  if (f & flags.PARSHA_HASHAVUA) return 'parsha';
  if (f & flags.SHABBAT_MEVARCHIM) return 'parsha';
  if (f & flags.SPECIAL_SHABBAT) return 'parsha';
  if (f & flags.ROSH_CHODESH) return 'rc';
  if (f & flags.MOLAD) return 'molad';
  if (f & flags.DAF_YOMI) return 'daf';
  if (f & flags.OMER_COUNT) return 'omer';
  if (f & (flags.MAJOR_FAST | flags.MINOR_FAST)) return 'fast';
  if (f & flags.MODERN_HOLIDAY) return 'modern';
  return 'holiday';
}

function normalize(e) {
  const cat = classify(e);
  const g = e.getDate().greg();
  const f = e.getFlags();
  const time = e.eventTime && valid(e.eventTime) ? e.eventTime : null;
  const full = e.render(LOC);
  let label = full;
  if (cat === 'shabbat') {
    const fri = g.getDay() === 5, sat = g.getDay() === 6;
    label = e.getDesc() === 'Candle lighting' ? (fri ? 'כניסת שבת' : 'הדלקת נרות') : (sat ? 'צאת שבת' : 'צאת החג');
  } else if (cat === 'molad') {
    label = 'מולד ' + Locale.gettext(e.getDesc().split(' ')[1], LOC);
  } else if (time) {
    label = e.renderBrief(LOC);
  } else if (cat === 'daf') {
    label = full.replace('דף יומי: ', 'דף ');
  }
  return {
    cat, label, full, time, date: g, key: keyOf(g),
    url: typeof e.url === 'function' ? e.url() : null,
    memo: e.memo && !/^Parashat|^Parsha/.test(e.memo) ? e.memo : '',
    chag: !!(f & flags.CHAG), desc: e.getDesc(),
  };
}

function dayItems(byDay, date) {
  const list = (byDay.get(keyOf(date)) || []).filter(e => state.filters[e.cat]);
  const allday = list.filter(e => !e.time).sort((a, b) => CAT_ORDER.indexOf(a.cat) - CAT_ORDER.indexOf(b.cat));
  const timed = list.filter(e => e.time).sort((a, b) => a.time - b.time);
  return {allday, timed};
}

/* ---------- אייקונים ---------- */
const I = {
  menu: '<svg viewBox="0 0 24 24"><path d="M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z"/></svg>',
  prev: '<svg viewBox="0 0 24 24"><path d="M8.59 16.59 13.17 12 8.59 7.41 10 6l6 6-6 6z" transform="matrix(-1 0 0 1 24 0)"/></svg>',
  next: '<svg viewBox="0 0 24 24"><path d="M15.41 16.59 10.83 12l4.58-4.59L14 6l-6 6 6 6z" transform="matrix(-1 0 0 1 24 0)"/></svg>',
  gear: '<svg viewBox="0 0 24 24"><path d="M19.14 12.94c.04-.3.06-.61.06-.94s-.02-.64-.07-.94l2.03-1.58a.5.5 0 0 0 .12-.64l-1.92-3.32a.5.5 0 0 0-.61-.22l-2.39.96a7 7 0 0 0-1.62-.94l-.36-2.54a.5.5 0 0 0-.48-.41h-3.84a.5.5 0 0 0-.48.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96a.5.5 0 0 0-.61.22L2.74 8.84a.5.5 0 0 0 .12.64l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.5.5 0 0 0-.12.64l1.92 3.32c.12.22.37.29.61.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.48-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.49 0 .61-.22l1.92-3.32a.5.5 0 0 0-.12-.64l-2.01-1.58zM12 15.6A3.6 3.6 0 1 1 12 8.4a3.6 3.6 0 0 1 0 7.2z"/></svg>',
  pin: '<svg viewBox="0 0 24 24"><path d="M12 2a7 7 0 0 0-7 7c0 5.25 7 13 7 13s7-7.75 7-13a7 7 0 0 0-7-7zm0 9.5A2.5 2.5 0 1 1 12 6.5a2.5 2.5 0 0 1 0 5z"/></svg>',
  down: '<svg viewBox="0 0 24 24"><path d="M7 10l5 5 5-5z"/></svg>',
  check: '<svg viewBox="0 0 24 24"><path d="M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z"/></svg>',
  close: '<svg viewBox="0 0 24 24"><path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>',
  ext: '<svg viewBox="0 0 24 24"><path d="M19 19H5V5h7V3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14c1.1 0 2-.9 2-2v-7h-2v7zM14 3v2h3.59l-9.83 9.83 1.41 1.41L19 6.41V10h2V3h-7z"/></svg>',
  candle: '<svg viewBox="0 0 24 24"><path d="M12 2c1.6 2.2 2.4 3.6 2.4 4.9A2.4 2.4 0 0 1 12 9.3a2.4 2.4 0 0 1-2.4-2.4C9.6 5.6 10.4 4.2 12 2zM10.5 11h3v10h-3z"/></svg>',
  download: '<svg viewBox="0 0 24 24"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/></svg>',
};

/* ---------- שלד האפליקציה ---------- */
const app = $('#app');
app.innerHTML = `
<header class="topbar">
  <button class="icon-btn" id="btn-menu" aria-label="פתיחה וסגירה של תפריט צד">${I.menu}</button>
  <a class="brand" href="./" aria-label="יומן עברי">
    <svg class="logo" viewBox="0 0 40 40" aria-hidden="true"><rect x="3" y="5" width="34" height="32" rx="5" fill="#fff" stroke="#1a73e8" stroke-width="3"/><rect x="3" y="5" width="34" height="9" rx="4" fill="#1a73e8"/><path d="M20 17.5l5 8.6h-10zM20 31l-5-8.6h10z" fill="none" stroke="#1a73e8" stroke-width="1.8" stroke-linejoin="round"/></svg>
    <span class="brand-text">יומן עברי</span>
  </a>
  <button class="btn outline" id="btn-today">היום</button>
  <div class="nav-arrows">
    <button class="icon-btn" id="btn-prev" aria-label="הקודם">${I.next}</button>
    <button class="icon-btn" id="btn-next" aria-label="הבא">${I.prev}</button>
  </div>
  <div class="range-title" aria-live="polite"><h1 id="title"></h1><div id="subtitle"></div></div>
  <div class="spacer"></div>
  <button class="btn outline city-btn" id="btn-city" aria-label="בחירת מיקום">${I.pin}<span id="city-label"></span></button>
  <div class="view-wrap">
    <button class="btn outline" id="btn-view" aria-haspopup="menu" aria-expanded="false"><span id="view-label"></span>${I.down}</button>
    <div class="menu" id="view-menu" role="menu" hidden>
      <button role="menuitem" data-view="month">חודש<kbd>M</kbd></button>
      <button role="menuitem" data-view="week">שבוע<kbd>W</kbd></button>
      <button role="menuitem" data-view="day">יום<kbd>D</kbd></button>
      <button role="menuitem" data-view="agenda">לוח זמנים<kbd>A</kbd></button>
    </div>
  </div>
  <button class="icon-btn" id="btn-settings" aria-label="הגדרות">${I.gear}</button>
</header>
<div class="layout">
  <aside class="sidebar" id="sidebar" aria-label="תפריט צד">
    <section class="shabbat-card" id="shabbat-card"></section>
    <section class="mini" id="mini" aria-label="לוח חודשי קטן"></section>
    <section class="cals" id="cals"><h2>יומנים</h2><div id="cal-list"></div></section>
    <section class="side-actions"><button class="btn outline wide" id="btn-ics">${I.download}<span>ייצוא לגוגל קלנדר (ICS)</span></button></section>
  </aside>
  <main class="main" id="main" tabindex="-1"></main>
</div>
<div class="scrim" id="scrim" hidden></div>
<div class="popover" id="popover" hidden role="dialog" aria-modal="false"></div>
<dialog id="settings" aria-labelledby="settings-title">
  <form method="dialog" class="dlg">
    <div class="dlg-head"><h2 id="settings-title">הגדרות</h2><button class="icon-btn" value="close" aria-label="סגירה">${I.close}</button></div>
    <label class="field"><span>מיקום</span>
      <select id="set-city"></select>
    </label>
    <button type="button" class="btn outline" id="btn-geo">${I.pin}<span>השתמש במיקום שלי</span></button>
    <p class="hint" id="geo-msg" hidden></p>
    <label class="field"><span>הדלקת נרות – דקות לפני השקיעה</span>
      <input type="number" id="set-mins" min="0" max="90" inputmode="numeric">
    </label>
    <label class="field"><span>צאת הכוכבים / הבדלה</span>
      <select id="set-havdalah">
        <option value="8.5">8.5° מתחת לאופק (ברירת מחדל)</option>
        <option value="42">42 דקות אחרי השקיעה</option>
        <option value="50">50 דקות אחרי השקיעה</option>
        <option value="72">72 דקות אחרי השקיעה (רבנו תם)</option>
      </select>
    </label>
    <label class="field"><span>ערכת נושא</span>
      <select id="set-theme"><option value="auto">לפי המכשיר</option><option value="light">בהיר</option><option value="dark">כהה</option></select>
    </label>
    <p class="hint">הזמנים מחושבים לפי קואורדינטות המיקום שנבחר. לקביעה הלכתית מחייבת יש להיוועץ ברב.</p>
  </form>
</dialog>`;

/* ---------- ניווט ---------- */
function shift(dir) {
  const v = state.view;
  if (v === 'month') state.cursor = addMonths(state.cursor, dir);
  else if (v === 'week') state.cursor = addDays(state.cursor, 7 * dir);
  else if (v === 'day') state.cursor = addDays(state.cursor, dir);
  else state.cursor = addDays(state.cursor, 14 * dir);
  state.mini = state.cursor;
  render();
}
function setView(v) { state.view = v; save(); render(); }
function goDay(date) { state.cursor = noon(date); state.mini = state.cursor; state.view = 'day'; save(); render(); }

/* ---------- כותרת ---------- */
function monthRangeLabel(first, last) {
  const a = hdOf(first), b = hdOf(last);
  const ma = hMonth(a), mb = hMonth(b);
  const ya = hYear(a), yb = hYear(b);
  if (ma === mb && ya === yb) return `${ma} ${ya}`;
  return ya === yb ? `${ma} – ${mb} ${yb}` : `${ma} ${ya} – ${mb} ${yb}`;
}
function updateTitle() {
  const c = state.cursor, v = state.view;
  let t, s;
  if (v === 'month') {
    const first = new Date(c.getFullYear(), c.getMonth(), 1, 12);
    const last = new Date(c.getFullYear(), c.getMonth() + 1, 0, 12);
    t = gfmt(first, {month: 'long', year: 'numeric'});
    s = monthRangeLabel(first, last);
  } else if (v === 'week') {
    const a = startOfWeek(c), b = addDays(a, 6);
    t = a.getMonth() === b.getMonth()
      ? `${a.getDate()}–${b.getDate()} ${gfmt(a, {month: 'long', year: 'numeric'})}`
      : `${gfmt(a, {day: 'numeric', month: 'short'})} – ${gfmt(b, {day: 'numeric', month: 'short', year: 'numeric'})}`;
    s = monthRangeLabel(a, b);
  } else if (v === 'day') {
    t = gfmt(c, {day: 'numeric', month: 'long', year: 'numeric'});
    s = `יום ${WD_FULL[c.getDay()]} · ${hFull(hdOf(c))}`;
  } else {
    const b = addDays(c, 29);
    t = `${gfmt(c, {day: 'numeric', month: 'short'})} – ${gfmt(b, {day: 'numeric', month: 'short', year: 'numeric'})}`;
    s = monthRangeLabel(c, b);
  }
  $('#title').textContent = t;
  $('#subtitle').textContent = s;
  document.title = `${t} · יומן עברי`;
  $('#view-label').textContent = {month: 'חודש', week: 'שבוע', day: 'יום', agenda: 'לוח זמנים'}[v];
  $('#city-label').textContent = curCity().name;
}

/* ---------- תצוגת חודש ---------- */
function pillHTML(e, extra = '') {
  const c = CATS[e.cat].color;
  if (e.time) {
    return `<button class="pill timed ${extra}" data-ev data-cat="${e.cat}" style="--c:${c}" title="${esc(e.full)}"><i></i><b>${fmtTime(e.time)}</b><span>${esc(e.label)}</span></button>`;
  }
  return `<button class="pill ${extra}${e.chag ? ' chag' : ''}" data-ev data-cat="${e.cat}" style="--c:${c}" title="${esc(e.full)}"><span>${esc(e.label)}</span></button>`;
}
let evRegistry = [];
const reg = e => { evRegistry.push(e); return evRegistry.length - 1; };
function pill(e, extra) { return pillHTML(e, extra).replace('data-ev', `data-ev="${reg(e)}"`); }

function renderMonth(main) {
  const c = state.cursor;
  const first = new Date(c.getFullYear(), c.getMonth(), 1, 12);
  const dim = new Date(c.getFullYear(), c.getMonth() + 1, 0).getDate();
  const weeks = Math.ceil((first.getDay() + dim) / 7);
  const start = startOfWeek(first);
  const end = addDays(start, weeks * 7 - 1);
  const byDay = fetchEvents(start, end);
  const today = noon(new Date());
  let html = `<div class="month" style="--rows:${weeks}"><div class="dow">` +
    WD_FULL.map((n, i) => `<div class="${i === 6 ? 'sat' : ''}"><span class="long">יום ${n}</span><span class="short">${WD_LETTER[i]}</span></div>`).join('') + '</div>';
  html += '<div class="mgrid">';
  for (let i = 0; i < weeks * 7; i++) {
    const d = addDays(start, i);
    const hd = hdOf(d);
    const {allday, timed} = dayItems(byDay, d);
    const items = [...allday, ...timed];
    const isChag = (byDay.get(keyOf(d)) || []).some(e => e.chag);
    const cls = ['cell', d.getMonth() !== c.getMonth() ? 'other' : '', sameDay(d, today) ? 'today' : '', d.getDay() === 6 ? 'sat' : '', isChag ? 'yt' : ''].join(' ');
    const gLabel = d.getDate() === 1 ? gfmt(d, {day: 'numeric', month: 'short'}) : d.getDate();
    const hLabel = hd.getDate() === 1 ? `${hDay(hd)} ${hMonth(hd)}` : hDay(hd);
    html += `<div class="${cls}" data-day="${keyOf(d)}" data-ts="${d.getTime()}">
      <div class="chead"><button class="gd" aria-label="${esc(gfmt(d, {weekday: 'long', day: 'numeric', month: 'long'}))}" data-goday>${gLabel}</button><span class="hd${hd.getDate() === 1 ? ' first' : ''}">${hLabel}</span></div>
      <div class="cevs">${items.map(e => pill(e)).join('')}</div>
      <div class="dots" aria-hidden="true">${items.slice(0, 6).map(e => `<i style="--c:${CATS[e.cat].color}"></i>`).join('')}</div>
    </div>`;
  }
  html += '</div></div>';
  main.innerHTML = html;
  fitCells();
}
function fitCells() {
  if (state.view !== 'month') return;
  for (const cell of $$('.mgrid .cell')) {
    const box = $('.cevs', cell);
    const pills = [...box.children];
    pills.forEach(p => p.hidden = false);
    const old = $('.more', box); if (old) old.remove();
    if (!pills.length || getComputedStyle(box).display === 'none') continue;
    const avail = box.clientHeight;
    const ph = 22;
    const fit = Math.floor(avail / ph);
    if (pills.length > fit) {
      const keep = Math.max(0, fit - 1);
      pills.slice(keep).forEach(p => p.hidden = true);
      const more = document.createElement('button');
      more.className = 'more';
      more.dataset.goday = '';
      more.textContent = `עוד ${pills.length - keep}`;
      box.appendChild(more);
    }
  }
}

/* ---------- רשת זמן (שבוע / יום) ---------- */
const PX_H = 52;
function layoutCluster(items) {
  // items: {start,end}; מחזיר col ו-cols לכל פריט
  items.sort((a, b) => a.start - b.start);
  let cluster = [], clusterEnd = -1;
  const flush = () => {
    const cols = [];
    for (const it of cluster) {
      let i = cols.findIndex(end => end <= it.start);
      if (i < 0) { cols.push(it.end); i = cols.length - 1; } else cols[i] = it.end;
      it.col = i;
    }
    cluster.forEach(it => it.cols = cols.length);
    cluster = [];
  };
  for (const it of items) {
    if (cluster.length && it.start >= clusterEnd) { flush(); clusterEnd = -1; }
    cluster.push(it);
    clusterEnd = Math.max(clusterEnd, it.end);
  }
  if (cluster.length) flush();
  return items;
}
function renderTimeGrid(main, days, full) {
  const byDay = fetchEvents(days[0], days[days.length - 1]);
  const today = noon(new Date());
  const showZ = state.filters.zmanim;
  let head = '<div class="tg-corner"></div>';
  let allday = '<div class="tg-allday-label">כל היום</div>';
  let cols = '';
  days.forEach(d => {
    const hd = hdOf(d);
    const {allday: ad, timed} = dayItems(byDay, d);
    const isToday = sameDay(d, today);
    head += `<div class="tg-dayhead ${isToday ? 'today' : ''} ${d.getDay() === 6 ? 'sat' : ''}">
      <div class="wd">יום ${WD_LETTER[d.getDay()]}</div>
      <button class="gd" data-goday data-ts="${d.getTime()}" aria-label="${esc(gfmt(d, {weekday: 'long', day: 'numeric', month: 'long'}))}">${d.getDate()}</button>
      <div class="hdl">${hDay(hd)} ${hMonth(hd)}</div></div>`;
    allday += `<div class="tg-ad ${d.getDay() === 6 ? 'sat' : ''}">${ad.map(e => pill(e)).join('')}</div>`;
    let ev = timed.map(e => ({e, start: minutesOfDay(e.time), kind: 'ev'}));
    if (showZ) {
      const taken = ev.map(x => x.start);
      zmanimOf(d, !full).forEach(z => {
        const st = minutesOfDay(z.time);
        if (z.id === 'tzeit' && taken.some(t => Math.abs(t - st) <= 1)) return; // כבר מוצג כצאת שבת/חג
        ev.push({z, start: st, kind: 'z'});
      });
    }
    ev.forEach(x => { x.end = x.start + 25; });
    layoutCluster(ev);
    const items = ev.map(x => {
      const top = x.start * PX_H / 60, w = 100 / x.cols, left = x.col * w;
      const pos = `top:${top}px;height:22px;inset-inline-start:${left}%;width:calc(${w}% - 2px)`;
      if (x.kind === 'ev') {
        const idx = reg(x.e);
        return `<button class="tev" data-ev="${idx}" style="${pos};--c:${CATS[x.e.cat].color}" title="${esc(x.e.full)}"><b>${esc(x.e.label)}</b><span>${fmtTime(x.e.time)}</span></button>`;
      }
      const idx = reg({cat: 'zmanim', label: x.z.label, full: `${x.z.label}: ${fmtTime(x.z.time)}`, time: x.z.time, date: d, memo: x.z.desc, zm: true});
      return `<button class="tev z" data-ev="${idx}" style="${pos};--c:${CATS.zmanim.color}" title="${esc(x.z.label)}"><b>${esc(full ? x.z.label : (x.z.short || x.z.label))}</b><span>${fmtTime(x.z.time)}</span></button>`;
    }).join('');
    const nowLine = isToday ? `<div class="nowline" style="top:${minutesOfDay(new Date()) * PX_H / 60}px"></div>` : '';
    cols += `<div class="tg-col ${isToday ? 'today' : ''} ${d.getDay() === 6 ? 'sat' : ''}" data-ts="${d.getTime()}">${items}${nowLine}</div>`;
  });
  let hours = '';
  for (let h = 0; h < 24; h++) hours += `<div class="hr" style="top:${h * PX_H}px"><span>${h === 0 ? '' : String(h).padStart(2, '0') + ':00'}</span></div>`;
  const n = days.length;
  main.innerHTML = `<div class="tgrid ${full ? 'full' : ''}" style="--n:${n}">
    <div class="tg-head" style="grid-template-columns:var(--gutter) repeat(${n},1fr)">${head}</div>
    <div class="tg-adrow" style="grid-template-columns:var(--gutter) repeat(${n},1fr)">${allday}</div>
    <div class="tg-scroll" id="tg-scroll"><div class="tg-body" style="height:${24 * PX_H}px;grid-template-columns:var(--gutter) repeat(${n},1fr)">
      <div class="tg-hours">${hours}</div>${cols}</div></div></div>`;
  const sc = $('#tg-scroll');
  const todayIn = days.some(d => sameDay(d, today));
  sc.scrollTop = Math.max(0, (todayIn ? minutesOfDay(new Date()) - 120 : 5 * 60) * PX_H / 60);
}

/* ---------- תצוגת יום ---------- */
function renderDay(main) {
  const d = state.cursor;
  const wrap = document.createElement('div');
  wrap.className = 'dayview';
  wrap.innerHTML = '<div class="day-grid"></div><aside class="day-panel" aria-label="פרטי היום"></aside>';
  main.innerHTML = '';
  main.appendChild(wrap);
  renderTimeGrid($('.day-grid', wrap), [d], true);
  const byDay = fetchEvents(d, d);
  const all = byDay.get(keyOf(d)) || [];
  const hd = hdOf(d);
  const zm = zmanimOf(d);
  const shown = all.filter(e => e.cat !== 'daf' || state.filters.daf);
  $('.day-panel', wrap).innerHTML = `
    <div class="panel-date"><div class="big-h">${hFull(hd)}</div><div class="sub">יום ${WD_FULL[d.getDay()]}, ${gfmt(d, {day: 'numeric', month: 'long', year: 'numeric'})}</div></div>
    ${shown.length ? `<h3>אירועים</h3><ul class="ev-list">${shown.map(e => `<li><i style="--c:${CATS[e.cat].color}"></i><span>${esc(e.full)}</span></li>`).join('')}</ul>` : ''}
    <h3>זמני היום · ${esc(curCity().name)}</h3>
    <table class="ztable"><tbody>${zm.map(z => `<tr title="${esc(z.desc)}"><th scope="row">${esc(z.label)}</th><td>${fmtTime(z.time)}</td></tr>`).join('')}</tbody></table>`;
}

/* ---------- לוח זמנים ---------- */
function renderAgenda(main) {
  const start = state.cursor, end = addDays(start, 29);
  const byDay = fetchEvents(start, end);
  const today = noon(new Date());
  let html = '<div class="agenda">', any = false;
  for (let i = 0; i < 30; i++) {
    const d = addDays(start, i);
    const {allday, timed} = dayItems(byDay, d);
    const items = [...allday, ...timed];
    if (!items.length) continue;
    any = true;
    const hd = hdOf(d);
    html += `<section class="ag-day ${sameDay(d, today) ? 'today' : ''}"><button class="ag-date" data-goday data-ts="${d.getTime()}">
        <span class="n">${d.getDate()}</span><span class="m">${gfmt(d, {month: 'short'})}, יום ${WD_LETTER[d.getDay()]}</span></button>
      <div class="ag-h">${hDay(hd)} ${hMonth(hd)}</div>
      <div class="ag-evs">${items.map(e => `<button class="ag-ev" data-ev="${reg(e)}"><i style="--c:${CATS[e.cat].color}"></i><span class="t">${e.time ? fmtTime(e.time) : 'כל היום'}</span><span class="l">${esc(e.label)}</span></button>`).join('')}</div></section>`;
  }
  if (!any) html += '<p class="empty">אין אירועים בטווח הזה עם המסננים הנוכחיים.</p>';
  main.innerHTML = html + '</div>';
}

/* ---------- תפריט צד ---------- */
function renderShabbatCard() {
  const today = noon(new Date());
  const byDay = fetchEvents(today, addDays(today, 14));
  const list = [];
  for (let i = 0; i < 15; i++) (byDay.get(keyOf(addDays(today, i))) || []).forEach(e => list.push(e));
  const now = new Date();
  const candle = list.find(e => e.desc === 'Candle lighting' && e.time > now) || list.find(e => e.desc === 'Candle lighting');
  const havd = candle && list.find(e => e.desc === 'Havdalah' && e.time > candle.time);
  const parsha = list.find(e => e.cat === 'parsha' && /^פרשת|^פרשיות/.test(e.full) && e.date >= noon(candle ? candle.date : today));
  const hd = hdOf(today);
  const row = (e, label) => e ? `<div class="srow"><div><span>${label}</span><small>${gfmt(e.date, {weekday: 'long', day: 'numeric', month: 'long'})}</small></div><b>${fmtTime(e.time)}</b></div>` : '';
  $('#shabbat-card').innerHTML = `
    <div class="today-h">${hFull(hd)}</div>
    <div class="today-g">${gfmt(today, {weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'})}</div>
    ${parsha ? `<div class="parsha-chip">${I.candle}<span>${esc(parsha.full)}</span></div>` : ''}
    ${row(candle, candle && candle.date.getDay() === 5 ? 'כניסת שבת' : 'הדלקת נרות')}
    ${row(havd, havd && havd.date.getDay() === 6 ? 'צאת שבת' : 'צאת החג')}
    <div class="srow note">${esc(curCity().name)} · ${candleMins()} דק׳ לפני השקיעה</div>`;
}
function renderMini() {
  const m = state.mini;
  const first = new Date(m.getFullYear(), m.getMonth(), 1, 12);
  const dim = new Date(m.getFullYear(), m.getMonth() + 1, 0).getDate();
  const weeks = Math.ceil((first.getDay() + dim) / 7);
  const start = startOfWeek(first);
  const today = noon(new Date());
  const wk = startOfWeek(state.cursor);
  let h = `<div class="mini-head"><span>${gfmt(first, {month: 'long', year: 'numeric'})}</span>
    <button class="icon-btn sm" id="mini-prev" aria-label="חודש קודם">${I.next}</button><button class="icon-btn sm" id="mini-next" aria-label="חודש הבא">${I.prev}</button></div>
    <div class="mini-grid">${WD_LETTER.map(l => `<span class="mdow">${l}</span>`).join('')}`;
  for (let i = 0; i < weeks * 7; i++) {
    const d = addDays(start, i);
    const inWeek = state.view === 'week' && sameDay(startOfWeek(d), wk);
    const sel = (state.view === 'day' || state.view === 'agenda') && sameDay(d, state.cursor);
    h += `<button class="md ${d.getMonth() !== m.getMonth() ? 'o' : ''} ${sameDay(d, today) ? 'today' : ''} ${sel ? 'sel' : ''} ${inWeek ? 'inw' : ''}" data-ts="${d.getTime()}" title="${esc(hFull(hdOf(d)))}">${d.getDate()}</button>`;
  }
  $('#mini').innerHTML = h + '</div>';
}
function renderCals() {
  $('#cal-list').innerHTML = Object.entries(CATS).map(([k, c]) =>
    `<label class="calrow"><input type="checkbox" data-filter="${k}" ${state.filters[k] ? 'checked' : ''}>
     <span class="box" style="--c:${c.color}">${I.check}</span><span class="nm">${c.name}</span></label>`).join('');
}

/* ---------- רינדור ראשי ---------- */
function render() {
  evRegistry = [];
  hidePopover();
  const main = $('#main');
  updateTitle();
  const v = state.view;
  if (v === 'month') renderMonth(main);
  else if (v === 'week') { const s = startOfWeek(state.cursor); renderTimeGrid(main, [0, 1, 2, 3, 4, 5, 6].map(i => addDays(s, i)), false); }
  else if (v === 'day') renderDay(main);
  else renderAgenda(main);
  renderMini();
  renderCals();
  renderShabbatCard();
}

/* ---------- חלון פרטי אירוע ---------- */
const pop = $('#popover');
function hidePopover() { pop.hidden = true; }
function showPopover(e, anchor) {
  const hd = hdOf(e.date);
  const dateLine = `יום ${WD_FULL[e.date.getDay()]}, ${gfmt(e.date, {day: 'numeric', month: 'long', year: 'numeric'})} · ${hFull(hd)}`;
  pop.innerHTML = `<div class="pop-head" style="--c:${CATS[e.cat].color}"><i></i><div><h3>${esc(e.zm || e.cat === 'shabbat' ? e.label : e.full.replace(/:\s*\d{1,2}:\d{2}.*$/, ''))}</h3>
    <p>${dateLine}${e.time ? ` · <b>${fmtTime(e.time)}</b>` : ''}</p></div><button class="icon-btn sm" id="pop-close" aria-label="סגירה">${I.close}</button></div>
    ${e.memo ? `<p class="pop-memo">${esc(e.memo)}</p>` : ''}
    <p class="pop-cat">${esc(CATS[e.cat].name)} · ${esc(curCity().name)}</p>
    ${e.url ? `<a class="pop-link" href="${esc(e.url)}" target="_blank" rel="noopener noreferrer">${I.ext}<span>מידע נוסף ב-Hebcal</span></a>` : ''}`;
  pop.hidden = false;
  const r = anchor.getBoundingClientRect();
  const pw = pop.offsetWidth, ph = pop.offsetHeight;
  let left = r.left + r.width / 2 - pw / 2;
  let top = r.bottom + 6;
  left = Math.max(8, Math.min(left, innerWidth - pw - 8));
  if (top + ph > innerHeight - 8) top = Math.max(8, r.top - ph - 6);
  pop.style.left = left + 'px';
  pop.style.top = top + 'px';
  $('#pop-close').focus({preventScroll: true});
}

/* ---------- ייצוא ICS ---------- */
function exportICS() {
  const start = noon(new Date()), end = addDays(start, 400);
  const byDay = fetchEvents(start, end);
  const lines = ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Jewish Calendar//HE', 'CALSCALE:GREGORIAN', 'X-WR-CALNAME:יומן עברי – ' + curCity().name, 'X-WR-TIMEZONE:' + curCity().tz];
  const pad = n => String(n).padStart(2, '0');
  const dstr = d => `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}`;
  const utc = d => `${d.getUTCFullYear()}${pad(d.getUTCMonth() + 1)}${pad(d.getUTCDate())}T${pad(d.getUTCHours())}${pad(d.getUTCMinutes())}00Z`;
  const t = s => s.replace(/[\\;,]/g, m => '\\' + m);
  let n = 0;
  for (const [, list] of byDay) for (const e of list) {
    if (!state.filters[e.cat] || e.cat === 'daf') continue;
    lines.push('BEGIN:VEVENT', `UID:jcal-${dstr(e.date)}-${n++}@jewish-calendar`, `DTSTAMP:${utc(new Date())}`);
    if (e.time) lines.push(`DTSTART:${utc(e.time)}`, `DTEND:${utc(new Date(e.time.getTime() + 15 * 60000))}`, `SUMMARY:${t(e.label)}`);
    else lines.push(`DTSTART;VALUE=DATE:${dstr(e.date)}`, `DTEND;VALUE=DATE:${dstr(addDays(e.date, 1))}`, `SUMMARY:${t(e.full)}`);
    lines.push('TRANSP:TRANSPARENT', 'END:VEVENT');
  }
  lines.push('END:VCALENDAR');
  const blob = new Blob([lines.join('\r\n')], {type: 'text/calendar;charset=utf-8'});
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'jewish-calendar.ics';
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 2000);
}

/* ---------- הגדרות ---------- */
function applyTheme() {
  document.documentElement.dataset.theme = state.theme;
}
function fillSettings() {
  const sel = $('#set-city');
  const groups = {};
  CITIES.forEach(c => (groups[c.grp] ||= []).push(c));
  let h = '';
  if (state.custom) h += `<option value="custom">${esc(state.custom.name)}</option>`;
  for (const [g, list] of Object.entries(groups)) h += `<optgroup label="${g}">${list.map(c => `<option value="${c.id}">${c.name}</option>`).join('')}</optgroup>`;
  sel.innerHTML = h;
  sel.value = state.cityId;
  $('#set-mins').value = candleMins();
  $('#set-havdalah').value = state.havdalah;
  $('#set-theme').value = state.theme;
}
function settingsChanged() { evCache.clear(); save(); render(); }

/* ---------- אירועים ---------- */
function bind() {
  $('#btn-today').onclick = () => { state.cursor = noon(new Date()); state.mini = state.cursor; render(); };
  $('#btn-prev').onclick = () => shift(-1);
  $('#btn-next').onclick = () => shift(1);
  $('#btn-menu').onclick = () => toggleSidebar();
  $('#scrim').onclick = () => toggleSidebar(false);
  $('#btn-ics').onclick = exportICS;
  const vm = $('#view-menu'), vb = $('#btn-view');
  vb.onclick = ev => { ev.stopPropagation(); vm.hidden = !vm.hidden; vb.setAttribute('aria-expanded', String(!vm.hidden)); };
  vm.onclick = ev => { const b = ev.target.closest('[data-view]'); if (b) { vm.hidden = true; vb.setAttribute('aria-expanded', 'false'); setView(b.dataset.view); } };
  const dlg = $('#settings');
  const openSettings = () => { fillSettings(); dlg.showModal(); };
  $('#btn-settings').onclick = openSettings;
  $('#btn-city').onclick = openSettings;
  $('#set-city').onchange = e => { state.cityId = e.target.value; state.candleMins = null; $('#set-mins').value = candleMins(); settingsChanged(); };
  $('#set-mins').onchange = e => { const v = parseInt(e.target.value, 10); state.candleMins = isNaN(v) ? null : Math.min(90, Math.max(0, v)); settingsChanged(); };
  $('#set-havdalah').onchange = e => { state.havdalah = e.target.value; settingsChanged(); };
  $('#set-theme').onchange = e => { state.theme = e.target.value; applyTheme(); save(); };
  $('#btn-geo').onclick = () => {
    const msg = $('#geo-msg'); msg.hidden = false; msg.textContent = 'מאתר מיקום…';
    if (!navigator.geolocation) { msg.textContent = 'הדפדפן לא תומך באיתור מיקום.'; return; }
    navigator.geolocation.getCurrentPosition(p => {
      const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
      state.custom = {name: 'המיקום שלי', lat: +p.coords.latitude.toFixed(4), lon: +p.coords.longitude.toFixed(4), tz, il: tz === 'Asia/Jerusalem'};
      state.cityId = 'custom'; state.candleMins = null;
      fillSettings(); msg.textContent = 'המיקום עודכן.'; settingsChanged();
    }, () => { msg.textContent = 'לא ניתן לאתר מיקום – בדקו את ההרשאות בדפדפן.'; }, {timeout: 10000});
  };
  dlg.addEventListener('click', e => { if (e.target === dlg) dlg.close(); });

  document.addEventListener('click', ev => {
    if (!$('#view-menu').hidden && !ev.target.closest('.view-wrap')) { $('#view-menu').hidden = true; $('#btn-view').setAttribute('aria-expanded', 'false'); }
    if (!pop.hidden && !ev.target.closest('#popover') && !ev.target.closest('[data-ev]')) hidePopover();
    if (ev.target.closest('#pop-close')) { hidePopover(); return; }
    const evBtn = ev.target.closest('[data-ev]');
    if (evBtn && evBtn.dataset.ev !== '') { ev.stopPropagation(); showPopover(evRegistry[+evBtn.dataset.ev], evBtn); return; }
    const goBtn = ev.target.closest('[data-goday]');
    if (goBtn) {
      const cell = goBtn.closest('[data-ts]');
      const ts = goBtn.dataset.ts || (cell && cell.dataset.ts);
      if (ts) goDay(new Date(+ts));
      return;
    }
    // לחיצה על תא ריק בחודש
    const cell = ev.target.closest('.mgrid .cell');
    if (cell && !ev.target.closest('.pill')) { goDay(new Date(+cell.dataset.ts)); return; }
    const md = ev.target.closest('.md');
    if (md) {
      state.cursor = noon(new Date(+md.dataset.ts)); state.mini = state.cursor;
      render(); if (innerWidth < 900) toggleSidebar(false); return;
    }
    if (ev.target.closest('#mini-prev')) { state.mini = addMonths(state.mini, -1); renderMini(); return; }
    if (ev.target.closest('#mini-next')) { state.mini = addMonths(state.mini, 1); renderMini(); return; }
  });
  document.addEventListener('change', ev => {
    const f = ev.target.dataset && ev.target.dataset.filter;
    if (f) { state.filters[f] = ev.target.checked; save(); render(); }
  });
  document.addEventListener('keydown', ev => {
    if (ev.key === 'Escape') { hidePopover(); $('#view-menu').hidden = true; if (innerWidth < 900) toggleSidebar(false); return; }
    if (ev.target.closest('input,select,textarea,dialog') || ev.ctrlKey || ev.metaKey || ev.altKey) return;
    const k = ev.key.toLowerCase();
    if (k === 't') $('#btn-today').click();
    else if (k === 'j' || k === 'p') shift(-1);
    else if (k === 'k' || k === 'n') shift(1);
    else if (k === 'm') setView('month');
    else if (k === 'w') setView('week');
    else if (k === 'd') setView('day');
    else if (k === 'a') setView('agenda');
  });
  let rt;
  addEventListener('resize', () => { clearTimeout(rt); rt = setTimeout(fitCells, 100); });
}
function toggleSidebar(force) {
  const open = force ?? !document.body.classList.contains('side-open');
  document.body.classList.toggle('side-open', open);
  $('#scrim').hidden = !(open && innerWidth < 900);
  $('#btn-menu').setAttribute('aria-expanded', String(open));
}

applyTheme();
bind();
if (innerWidth >= 900) document.body.classList.add('side-open');
render();
// קו "עכשיו" וכרטיס היום מתעדכנים כל דקה
setInterval(() => {
  const nl = $('.nowline');
  if (nl) nl.style.top = (minutesOfDay(new Date()) * PX_H / 60) + 'px';
}, 60000);
