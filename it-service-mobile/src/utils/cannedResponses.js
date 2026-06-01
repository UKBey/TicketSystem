/**
 * Hazır Yanıtlar (canned responses) yardımcıları — placeholder doldurma, tr/en
 * varyant seçimi ve görünürlük eşleşmesi. Web tarafıyla aynı mantık (saf JS).
 * Placeholder'lar sunucuda ham ({{...}}) saklanır; ekleme anında istemcide doldurulur.
 */

export const PLACEHOLDER_TOKENS = [
  'musteri.ad',
  'agent.ad',
  'bilet.no',
  'urun',
  'konu',
  'tarih',
];

const TOKEN_RE = /\{\{\s*([\w.]+)\s*\}\}/g;

export function buildPlaceholderContext({ ticket, user, language } = {}) {
  const ticketNo = ticket?.id != null ? `TCK-${String(ticket.id).padStart(3, '0')}` : '';
  const locale = language === 'tr' ? 'tr-TR' : 'en-US';
  let date = '';
  try {
    date = new Date().toLocaleDateString(locale, { year: 'numeric', month: 'long', day: 'numeric' });
  } catch {
    date = new Date().toLocaleDateString();
  }
  return {
    'musteri.ad': ticket?.customerName || '',
    'agent.ad': user?.name || '',
    'bilet.no': ticketNo,
    urun: ticket?.productName || '',
    konu: ticket?.topicName || '',
    tarih: date,
  };
}

export function fillPlaceholders(text, ctx) {
  if (!text) return '';
  return text.replace(TOKEN_RE, (match, key) => {
    const val = ctx?.[key];
    return val !== undefined && val !== null && val !== '' ? String(val) : match;
  });
}

export function findUnfilledPlaceholders(text) {
  if (!text) return [];
  const out = [];
  const re = new RegExp(TOKEN_RE.source, 'g');
  let m;
  while ((m = re.exec(text)) !== null) out.push(m[0]);
  return out;
}

export function availableLangs(tpl) {
  const langs = [];
  if (tpl?.contentTr && tpl.contentTr.trim()) langs.push('tr');
  if (tpl?.contentEn && tpl.contentEn.trim()) langs.push('en');
  return langs;
}

export function pickContent(tpl, preferred) {
  const langs = availableLangs(tpl);
  if (langs.length === 0) return { lang: null, content: '' };
  const lang = langs.includes(preferred) ? preferred : langs[0];
  return { lang, content: lang === 'tr' ? tpl.contentTr : tpl.contentEn };
}

export function suitsCommentType(tpl, commentType) {
  if (!tpl?.visibility || tpl.visibility === 'BOTH') return true;
  return tpl.visibility === commentType;
}
