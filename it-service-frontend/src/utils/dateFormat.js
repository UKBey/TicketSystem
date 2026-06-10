/**
 * Tek-tip, kullanıcı-ayarlı tarih biçimlendirme. Sitedeki TÜM tarih gösterimleri
 * buradan geçer; kullanıcı tercih ettiği formatı Tercihler sayfasından seçer.
 *
 * Format bir "preset anahtarı"dır (backend `users.preferred_date_format` ile aynı set).
 * Aktif format modül seviyesinde tutulur ({@link setActiveDateFormat} ile DateFormatProvider
 * günceller) — böylece hem React bileşenleri hem de React olmayan yardımcılar (PDF üretimi,
 * util'ler) tek bir `formatDate(...)` çağrısıyla doğru formatı alır. Format değişince açık
 * görünümler bir sonraki render'da yeni formatı gösterir.
 */
import i18n from '../i18n';

export const DATE_FORMATS = ['DMY_SLASH', 'MDY_SLASH', 'YMD_DASH', 'DMY_DOT', 'MED'];
export const DEFAULT_DATE_FORMAT = 'DMY_SLASH';

// Aktif format — DateFormatProvider senkronlar; util'ler buradan okur.
let _active = (() => {
  try {
    const s = localStorage.getItem('dateFormat');
    return DATE_FORMATS.includes(s) ? s : DEFAULT_DATE_FORMAT;
  } catch {
    return DEFAULT_DATE_FORMAT;
  }
})();

/** Aktif tarih formatını ayarlar (DateFormatProvider çağırır). */
export function setActiveDateFormat(fmt) {
  if (DATE_FORMATS.includes(fmt)) _active = fmt;
}

/** O an aktif tarih formatı preset anahtarı. */
export function getActiveDateFormat() {
  return _active;
}

const pad = (n) => String(n).padStart(2, '0');
const lang = () => (i18n.language?.startsWith('tr') ? 'tr' : 'en');

function toDate(value) {
  if (value == null || value === '') return null;
  const d = value instanceof Date ? value : new Date(value);
  return Number.isNaN(d.getTime()) ? null : d;
}

/** Bir Date'in yalnızca tarih kısmını verilen preset'e göre biçimler. */
function datePart(d, fmt) {
  const day = pad(d.getDate());
  const month = pad(d.getMonth() + 1);
  const year = d.getFullYear();
  switch (fmt) {
    case 'MDY_SLASH': return `${month}/${day}/${year}`;
    case 'YMD_DASH':  return `${year}-${month}-${day}`;
    case 'DMY_DOT':   return `${day}.${month}.${year}`;
    case 'MED':       // ay-adlı, dile göre: "31 Ara 2026" / "Dec 31, 2026"
      return d.toLocaleDateString(lang() === 'tr' ? 'tr-TR' : 'en-US',
        { day: 'numeric', month: 'short', year: 'numeric' });
    case 'DMY_SLASH':
    default:          return `${day}/${month}/${year}`;
  }
}

/** Tarih (saatsiz), aktif formatta. Boş/geçersiz → '—'. */
export function formatDate(value, fmt = _active) {
  const d = toDate(value);
  return d ? datePart(d, fmt) : '—';
}

/** Tarih + saat (24s, HH:mm), aktif formatta. Boş/geçersiz → '—'. */
export function formatDateTime(value, fmt = _active) {
  const d = toDate(value);
  return d ? `${datePart(d, fmt)} ${pad(d.getHours())}:${pad(d.getMinutes())}` : '—';
}

/** Yalnızca saat (24s, HH:mm). Boş/geçersiz → '—'. */
export function formatTime(value) {
  const d = toDate(value);
  return d ? `${pad(d.getHours())}:${pad(d.getMinutes())}` : '—';
}
