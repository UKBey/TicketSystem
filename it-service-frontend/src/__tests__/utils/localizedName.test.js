import { describe, it, expect, beforeEach } from 'vitest';
import i18n from '../../i18n';
import { pickLocalized, localizedName, sortByLocalizedName } from '../../utils/localizedName';

describe('pickLocalized', () => {
  it('returns the variant of the requested language', () => {
    expect(pickLocalized('Kurulum', 'Installation', 'tr')).toBe('Kurulum');
    expect(pickLocalized('Kurulum', 'Installation', 'en')).toBe('Installation');
  });

  it('falls back to the other variant when the requested one is empty', () => {
    expect(pickLocalized(null, 'Installation', 'tr')).toBe('Installation');
    expect(pickLocalized('Kurulum', '', 'en')).toBe('Kurulum');
  });

  it('handles region-suffixed language codes', () => {
    expect(pickLocalized('Kurulum', 'Installation', 'tr-TR')).toBe('Kurulum');
    expect(pickLocalized('Kurulum', 'Installation', 'en-US')).toBe('Installation');
  });

  it('returns empty string when both variants are missing', () => {
    expect(pickLocalized(null, undefined, 'tr')).toBe('');
  });
});

// setup.js '../i18n' modülünü düz bir { language } nesnesi olarak mock'lar;
// dil değişimi changeLanguage yerine language alanına yazılarak simüle edilir.
describe('localizedName', () => {
  beforeEach(() => { i18n.language = 'en'; });

  it('resolves nameTr/nameEn pairs from an object', () => {
    expect(localizedName({ nameTr: 'Şifre', nameEn: 'Password' })).toBe('Password');
  });

  it('supports custom base keys (e.g. ticket.topicName*)', () => {
    expect(localizedName({ topicNameTr: 'Şifre', topicNameEn: 'Password' }, 'topicName')).toBe('Password');
  });

  it('follows the active i18n language', () => {
    i18n.language = 'tr';
    expect(localizedName({ nameTr: 'Şifre', nameEn: 'Password' })).toBe('Şifre');
  });

  it('returns empty string for null objects', () => {
    expect(localizedName(null)).toBe('');
  });
});

describe('sortByLocalizedName', () => {
  it('sorts by the localized display name without mutating the input', () => {
    i18n.language = 'en';
    const input = [
      { nameEn: 'Zebra' },
      { nameTr: 'Anahtar' }, // EN yok → TR'ye düşer
      { nameEn: 'Monitoring' },
    ];
    const sorted = sortByLocalizedName(input);
    expect(sorted.map((x) => x.nameEn ?? x.nameTr)).toEqual(['Anahtar', 'Monitoring', 'Zebra']);
    expect(input[0].nameEn).toBe('Zebra');
  });
});
