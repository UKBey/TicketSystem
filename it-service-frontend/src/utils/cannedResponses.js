/**
 * Helpers for the Canned Responses (Hazır Yanıtlar) feature: placeholder filling,
 * tr/en variant selection, and the (client-side) recently-used list.
 *
 * Placeholders are stored raw on the server ({@code {{...}}}) and filled here at
 * insertion time from the ticket/agent context the frontend already holds.
 */
import { formatDate } from './dateFormat';
import { localizedName } from './localizedName';

/** Supported merge-field tokens (used by the management editor's "insert placeholder" helper). */
export const PLACEHOLDER_TOKENS = [
  'musteri.ad',
  'agent.ad',
  'bilet.no',
  'urun',
  'konu',
  'tarih',
];

const TOKEN_RE = /\{\{\s*([\w.]+)\s*\}\}/g;

/**
 * Builds the placeholder value map from the ticket and the current agent.
 * @param {{ ticket?: object, user?: object, language?: string }} args
 */
export function buildPlaceholderContext({ ticket, user } = {}) {
  const ticketNo = ticket?.id != null ? `TCK-${String(ticket.id).padStart(3, '0')}` : '';
  return {
    'musteri.ad': ticket?.customerName || '',
    'agent.ad': user?.name || '',
    'bilet.no': ticketNo,
    urun: localizedName(ticket, 'productName') || '',
    konu: localizedName(ticket, 'topicName') || '',
    tarih: formatDate(new Date()),
  };
}

/**
 * Replaces {@code {{token}}} occurrences with their context values. Unknown tokens or
 * tokens whose value is empty are left intact (raw) so they can be flagged to the agent.
 */
export function fillPlaceholders(text, ctx) {
  if (!text) return '';
  return text.replace(TOKEN_RE, (match, key) => {
    const val = ctx?.[key];
    return val !== undefined && val !== null && val !== '' ? String(val) : match;
  });
}

/** Returns the list of {@code {{...}}} placeholders still present in the (already filled) text. */
export function findUnfilledPlaceholders(text) {
  if (!text) return [];
  const out = [];
  const re = new RegExp(TOKEN_RE.source, 'g');
  let m;
  while ((m = re.exec(text)) !== null) out.push(m[0]);
  return out;
}

/** Languages a template provides content for, e.g. {@code ['tr', 'en']}. */
export function availableLangs(tpl) {
  const langs = [];
  if (tpl?.contentTr && tpl.contentTr.trim()) langs.push('tr');
  if (tpl?.contentEn && tpl.contentEn.trim()) langs.push('en');
  return langs;
}

/**
 * Picks the content variant for the preferred language, falling back to whichever
 * variant exists. Returns {@code { lang, content }} ({@code lang} is {@code null} when empty).
 */
export function pickContent(tpl, preferred) {
  const langs = availableLangs(tpl);
  if (langs.length === 0) return { lang: null, content: '' };
  const lang = langs.includes(preferred) ? preferred : langs[0];
  return { lang, content: lang === 'tr' ? tpl.contentTr : tpl.contentEn };
}

/** Whether a template suits the given comment type (EXTERNAL/INTERNAL). BOTH always suits. */
export function suitsCommentType(tpl, commentType) {
  if (!tpl?.visibility || tpl.visibility === 'BOTH') return true;
  return tpl.visibility === commentType;
}

// ---------------------------------------------------------------------------
// Recently-used — a per-user, client-side list (a pure UI nicety; favorites are
// the server-persisted concept). Stored in localStorage, newest first.
// ---------------------------------------------------------------------------

const RECENT_PREFIX = 'cannedRecent';
const RECENT_MAX = 8;

export function getRecentIds(userId) {
  if (!userId) return [];
  try {
    const raw = localStorage.getItem(`${RECENT_PREFIX}:${userId}`);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function pushRecentId(userId, id) {
  if (!userId || id == null) return;
  try {
    const list = getRecentIds(userId).filter((x) => x !== id);
    list.unshift(id);
    localStorage.setItem(`${RECENT_PREFIX}:${userId}`, JSON.stringify(list.slice(0, RECENT_MAX)));
  } catch {
    /* localStorage unavailable — recently-used is best-effort */
  }
}
