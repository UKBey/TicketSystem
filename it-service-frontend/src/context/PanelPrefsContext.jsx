/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { savePanelPreferences } from '../services/api';
import keycloak from '../keycloak';

/**
 * Agent/lead kullanıcıların sol menüdeki ticket panellerini (workspace, pool, history,
 * team, all-tickets) açıp kapatma tercihlerini yönetir. Tarih formatı desenine benzer
 * (localStorage + sunucuya yazma); giriş sonrası DB'deki değer {@link applyServerPanelPrefs}
 * ile uygulanır (cihazlar arası senkron için).
 *
 * Varsayılan: tüm paneller görünür. Bir anahtar tercih kümesinde yoksa görünür sayılır —
 * böylece bu özelliği hiç kullanmamış (ya da modali olmayan) kullanıcılar etkilenmez.
 */
export const PANEL_KEYS = ['workspace', 'pool', 'history', 'team', 'allTickets'];

const STORAGE_KEY = 'panelPrefs';

function readStored() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

const PanelPrefsContext = createContext(null);

export function PanelPrefsProvider({ children }) {
  const [prefs, setPrefs] = useState(readStored);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(prefs));
  }, [prefs]);

  const persist = (value) => {
    if (!keycloak.authenticated || !keycloak.token) return;
    savePanelPreferences(JSON.stringify(value)).catch((err) => {
      console.warn('Panel tercihleri backend\'e kaydedilemedi:', err);
    });
  };

  // Bir panelin görünür olup olmadığı — yalnızca açıkça false ise gizli.
  const isPanelVisible = useCallback(
    (key) => prefs[key] !== false,
    [prefs]
  );

  // Kullanıcı seçimi: state güncelle + sunucuya yaz.
  const setPanelVisible = useCallback((key, visible) => {
    if (!PANEL_KEYS.includes(key)) return;
    setPrefs((prev) => {
      const next = { ...prev, [key]: visible };
      persist(next);
      return next;
    });
  }, []);

  // Sunucudan gelen değeri uygula (yeniden PUT etmeden) — giriş hidrasyonu.
  const applyServerPanelPrefs = useCallback((jsonString) => {
    if (!jsonString) return;
    try {
      const parsed = JSON.parse(jsonString);
      if (parsed && typeof parsed === 'object') setPrefs(parsed);
    } catch {
      // Bozuk kayıt — varsayılanlarda kal.
    }
  }, []);

  return (
    <PanelPrefsContext.Provider value={{ prefs, isPanelVisible, setPanelVisible, applyServerPanelPrefs }}>
      {children}
    </PanelPrefsContext.Provider>
  );
}

export function usePanelPrefs() {
  const ctx = useContext(PanelPrefsContext);
  if (!ctx) throw new Error('usePanelPrefs must be used within PanelPrefsProvider');
  return ctx;
}
