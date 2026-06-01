import { describe, it, expect, beforeEach } from 'vitest';
import {
  fillPlaceholders,
  findUnfilledPlaceholders,
  availableLangs,
  pickContent,
  suitsCommentType,
  buildPlaceholderContext,
  getRecentIds,
  pushRecentId,
} from '../../utils/cannedResponses';

describe('fillPlaceholders', () => {
  const ctx = { 'musteri.ad': 'Ahmet', 'agent.ad': 'Ayşe', urun: 'VPN' };

  it('replaces known tokens with context values', () => {
    expect(fillPlaceholders('Merhaba {{musteri.ad}}, ben {{agent.ad}}', ctx))
      .toBe('Merhaba Ahmet, ben Ayşe');
  });

  it('tolerates whitespace inside braces', () => {
    expect(fillPlaceholders('Ürün: {{ urun }}', ctx)).toBe('Ürün: VPN');
  });

  it('leaves unknown or empty tokens raw', () => {
    expect(fillPlaceholders('Konu: {{konu}}', { konu: '' })).toBe('Konu: {{konu}}');
    expect(fillPlaceholders('X {{bilinmeyen}}', ctx)).toBe('X {{bilinmeyen}}');
  });

  it('returns empty string for nullish input', () => {
    expect(fillPlaceholders('', ctx)).toBe('');
    expect(fillPlaceholders(null, ctx)).toBe('');
  });
});

describe('findUnfilledPlaceholders', () => {
  it('lists remaining placeholders', () => {
    expect(findUnfilledPlaceholders('Merhaba {{konu}} ve {{x}}')).toEqual(['{{konu}}', '{{x}}']);
  });
  it('returns empty when none remain', () => {
    expect(findUnfilledPlaceholders('Tamamen düz metin')).toEqual([]);
  });
});

describe('availableLangs / pickContent', () => {
  it('reports the languages that have content', () => {
    expect(availableLangs({ contentTr: 'tr', contentEn: 'en' })).toEqual(['tr', 'en']);
    expect(availableLangs({ contentTr: '  ', contentEn: 'en' })).toEqual(['en']);
    expect(availableLangs({})).toEqual([]);
  });

  it('picks the preferred language when available', () => {
    const tpl = { contentTr: 'Merhaba', contentEn: 'Hello' };
    expect(pickContent(tpl, 'en')).toEqual({ lang: 'en', content: 'Hello' });
    expect(pickContent(tpl, 'tr')).toEqual({ lang: 'tr', content: 'Merhaba' });
  });

  it('falls back to the other language when the preferred is missing', () => {
    const tpl = { contentTr: '', contentEn: 'Hello' };
    expect(pickContent(tpl, 'tr')).toEqual({ lang: 'en', content: 'Hello' });
  });

  it('returns empty when no content exists', () => {
    expect(pickContent({}, 'tr')).toEqual({ lang: null, content: '' });
  });
});

describe('suitsCommentType', () => {
  it('BOTH suits either', () => {
    expect(suitsCommentType({ visibility: 'BOTH' }, 'EXTERNAL')).toBe(true);
    expect(suitsCommentType({ visibility: 'BOTH' }, 'INTERNAL')).toBe(true);
  });
  it('matches exact visibility', () => {
    expect(suitsCommentType({ visibility: 'EXTERNAL' }, 'EXTERNAL')).toBe(true);
    expect(suitsCommentType({ visibility: 'EXTERNAL' }, 'INTERNAL')).toBe(false);
    expect(suitsCommentType({ visibility: 'INTERNAL' }, 'INTERNAL')).toBe(true);
  });
});

describe('buildPlaceholderContext', () => {
  it('maps ticket and user fields and pads the ticket number', () => {
    const ctx = buildPlaceholderContext({
      ticket: { id: 7, customerName: 'Ali', productName: 'CRM', topicName: 'Login' },
      user: { name: 'Agent Smith' },
      language: 'en',
    });
    expect(ctx['musteri.ad']).toBe('Ali');
    expect(ctx['agent.ad']).toBe('Agent Smith');
    expect(ctx['bilet.no']).toBe('TCK-007');
    expect(ctx.urun).toBe('CRM');
    expect(ctx.konu).toBe('Login');
    expect(typeof ctx.tarih).toBe('string');
    expect(ctx.tarih.length).toBeGreaterThan(0);
  });

  it('uses empty strings for missing fields', () => {
    const ctx = buildPlaceholderContext({ ticket: {}, user: {}, language: 'tr' });
    expect(ctx['musteri.ad']).toBe('');
    expect(ctx['bilet.no']).toBe('');
  });
});

describe('recently-used list (localStorage)', () => {
  beforeEach(() => localStorage.clear());

  it('stores newest first and dedupes', () => {
    pushRecentId('u1', 3);
    pushRecentId('u1', 5);
    pushRecentId('u1', 3); // moves 3 to front
    expect(getRecentIds('u1')).toEqual([3, 5]);
  });

  it('caps the list length', () => {
    for (let i = 0; i < 20; i += 1) pushRecentId('u1', i);
    expect(getRecentIds('u1').length).toBeLessThanOrEqual(8);
    expect(getRecentIds('u1')[0]).toBe(19);
  });

  it('is per-user and safe with no userId', () => {
    pushRecentId('u1', 1);
    expect(getRecentIds('u2')).toEqual([]);
    expect(getRecentIds(undefined)).toEqual([]);
  });
});
