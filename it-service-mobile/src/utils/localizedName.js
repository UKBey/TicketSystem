import i18n from '../i18n';

/**
 * Bilingual (tr/en) name helpers for products and ticket topics — mirrors the web
 * frontend's utils/localizedName.js. The API returns both variants (`nameTr`/`nameEn`,
 * `productNameTr`/`productNameEn`, `topicNameTr`/`topicNameEn`); the UI shows the
 * variant matching the active language and falls back to the other one when empty.
 */

/** Picks the variant matching the active UI language, falling back to the other. */
export function pickLocalized(tr, en, lang = i18n.language) {
  const trFirst = (lang || '').toLowerCase().startsWith('tr');
  const primary = trFirst ? tr : en;
  const fallback = trFirst ? en : tr;
  return primary || fallback || '';
}

/** Resolves `obj[<base>Tr]` / `obj[<base>En]` (e.g. localizedName(ticket, 'topicName')). */
export function localizedName(obj, base = 'name') {
  if (!obj) return '';
  return pickLocalized(obj[`${base}Tr`], obj[`${base}En`]);
}

/** Returns a copy sorted alphabetically by the localized display name (locale-aware). */
export function sortByLocalizedName(arr, base = 'name') {
  return [...(arr ?? [])].sort((a, b) =>
    localizedName(a, base).localeCompare(localizedName(b, base), i18n.language));
}
