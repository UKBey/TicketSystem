/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useState, useEffect, useCallback } from 'react';

const CommandPaletteContext = createContext(null);

const RECENT_KEY = 'cmdPaletteRecent';
const RECENT_MAX = 5;

function readRecent() {
  try {
    const raw = localStorage.getItem(RECENT_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

/**
 * Komut paletinin global durumunu yönetir: açık/kapalı state, Ctrl/Cmd+K kısayolu ve
 * "son kullanılanlar" listesi (localStorage'da kalıcı). Navigasyon yapmaz — yalnızca
 * durum tutar; gerçek yönlendirme {@link CommandPalette} bileşeninde (Router içinde) olur.
 *
 * Kısayol her iki değiştiriciyi de kabul eder (metaKey || ctrlKey) — böylece klavye
 * düzeninden bağımsız çalışır; gösterilen etiket ise OS'e göre {@link MOD_KEY_LABEL}.
 */
export function CommandPaletteProvider({ children }) {
  const [open, setOpen] = useState(false);
  const [recent, setRecent] = useState(readRecent);

  const close = useCallback(() => setOpen(false), []);
  const toggle = useCallback(() => setOpen((v) => !v), []);
  const openPalette = useCallback(() => setOpen(true), []);

  // Son kullanılan bir öğeyi listenin başına ekle (anahtara göre tekilleştir, en fazla 5).
  const addRecent = useCallback((entry) => {
    if (!entry?.key || !entry?.to) return;
    setRecent((prev) => {
      const next = [entry, ...prev.filter((e) => e.key !== entry.key)].slice(0, RECENT_MAX);
      try {
        localStorage.setItem(RECENT_KEY, JSON.stringify(next));
      } catch {
        // depolama dolu/erişilemez — sessizce geç
      }
      return next;
    });
  }, []);

  useEffect(() => {
    const handle = (e) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        toggle();
      }
    };
    window.addEventListener('keydown', handle);
    return () => window.removeEventListener('keydown', handle);
  }, [toggle]);

  return (
    <CommandPaletteContext.Provider value={{ open, openPalette, close, toggle, recent, addRecent }}>
      {children}
    </CommandPaletteContext.Provider>
  );
}

export function useCommandPalette() {
  const ctx = useContext(CommandPaletteContext);
  if (!ctx) throw new Error('useCommandPalette must be used within CommandPaletteProvider');
  return ctx;
}
