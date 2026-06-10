/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import api from '../services/api';
import keycloak from '../keycloak';
import { DATE_FORMATS, DEFAULT_DATE_FORMAT, setActiveDateFormat } from '../utils/dateFormat';

const DateFormatContext = createContext(null);

/**
 * Kullanıcının tarih formatı tercihini yönetir. Tema desenine benzer (localStorage +
 * sunucuya yazma); farkı: Keycloak ekranı kaynağı olmadığından giriş sonrası DB'deki
 * değer {@link applyServerDateFormat} ile uygulanır (cihazlar arası senkron için).
 */
export function DateFormatProvider({ children }) {
  const [dateFormat, setDateFormatState] = useState(() => {
    const stored = localStorage.getItem('dateFormat');
    return DATE_FORMATS.includes(stored) ? stored : DEFAULT_DATE_FORMAT;
  });

  useEffect(() => {
    localStorage.setItem('dateFormat', dateFormat);
  }, [dateFormat]);

  const persist = (value) => {
    if (!keycloak.authenticated || !keycloak.token) return;
    api.put('/users/me/date-format', null, { params: { format: value } }).catch((err) => {
      console.warn('Tarih formatı tercihi backend\'e kaydedilemedi:', err);
    });
  };

  // Kullanıcı seçimi: hemen uygula (modül senkron) + state + sunucuya yaz.
  const setDateFormat = useCallback((value) => {
    if (!DATE_FORMATS.includes(value)) return;
    setActiveDateFormat(value);
    setDateFormatState(value);
    persist(value);
  }, []);

  // Sunucudan gelen değeri uygula (yeniden PUT etmeden) — giriş hidrasyonu.
  const applyServerDateFormat = useCallback((value) => {
    if (!DATE_FORMATS.includes(value)) return;
    setActiveDateFormat(value);
    setDateFormatState(value);
  }, []);

  return (
    <DateFormatContext.Provider value={{ dateFormat, setDateFormat, applyServerDateFormat }}>
      {children}
    </DateFormatContext.Provider>
  );
}

export function useDateFormat() {
  const ctx = useContext(DateFormatContext);
  if (!ctx) throw new Error('useDateFormat must be used within DateFormatProvider');
  return ctx;
}
