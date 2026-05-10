import '@testing-library/jest-dom';
import { vi } from 'vitest';
import en from '../locales/en.json';

// ---------------------------------------------------------------------------
// i18next global mock
// Testlerde gerçek i18next instance'ı başlatılmaz; t() çağrıları locale JSON
// dosyalarından çevrilmiş değerleri döndürmesi için aşağıdaki mock kullanılır.
// ---------------------------------------------------------------------------

// Nokta-ayrımlı key'i (örn: "notification.title") JSON'dan çözen yardımcı.
function resolve(obj, key) {
  return key.split('.').reduce((acc, part) => (acc && acc[part] !== undefined ? acc[part] : null), obj);
}

// i18next interpolation: {{variable}} → değer
function interpolate(str, opts) {
  if (!str || typeof str !== 'string' || !opts) return str;
  return str.replace(/\{\{(\w+)\}\}/g, (_, k) => (opts[k] !== undefined ? opts[k] : `{{${k}}}`));
}

function t(key, opts) {
  const val = resolve(en, key);
  if (val && typeof val === 'string') return interpolate(val, opts);
  // key bulunamazsa key'in kendisini döndür (i18next varsayılan davranışı)
  return key;
}

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t,
    i18n: {
      language: 'en',
      changeLanguage: vi.fn(),
    },
  }),
  Trans: ({ i18nKey }) => i18nKey,
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../i18n', () => ({
  default: {
    language: 'en',
    changeLanguage: vi.fn(),
  },
}));

// Recharts uses ResizeObserver internally
globalThis.ResizeObserver = class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
};

// Recharts / some chart libs query matchMedia
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
});
