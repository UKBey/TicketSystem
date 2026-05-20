import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import AsyncStorage from '@react-native-async-storage/async-storage';
import en from './locales/en.json';
import tr from './locales/tr.json';

export const LANGUAGE_KEY = 'language';

i18n.use(initReactI18next).init({
  resources: {
    en: { translation: en },
    tr: { translation: tr },
  },
  lng: 'tr',
  fallbackLng: 'en',
  supportedLngs: ['en', 'tr'],
  interpolation: { escapeValue: false },
  // Hermes Intl uyumsuzluklarına karşı v4 JSON formatı.
  compatibilityJSON: 'v4',
});

// Kaydedilmiş dil tercihini (varsa) yükle — i18n init senkron, bu asenkron tamamlanır.
AsyncStorage.getItem(LANGUAGE_KEY)
  .then((saved) => {
    if (saved && saved !== i18n.language) i18n.changeLanguage(saved);
  })
  .catch(() => {});

/** Dili değiştirir ve tercihi kalıcı olarak saklar. */
export async function setLanguage(lng) {
  await AsyncStorage.setItem(LANGUAGE_KEY, lng);
  await i18n.changeLanguage(lng);
}

export default i18n;
