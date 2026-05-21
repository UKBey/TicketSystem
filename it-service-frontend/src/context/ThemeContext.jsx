/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import api from '../services/api';

const ThemeContext = createContext(null);

// `theme` cookie'sinden light/dark degerini okur (yoksa null).
function readThemeCookie() {
  const match = document.cookie.match(/(?:^|;\s*)theme=(light|dark)(?:;|$)/);
  return match ? match[1] : null;
}

export function ThemeProvider({ children }) {
  const [theme, setThemeState] = useState(() => {
    const stored = localStorage.getItem('theme');
    if (stored === 'dark' || stored === 'light') return stored;
    // localStorage origin'e (port'a) bagli — Keycloak giris sayfasina ulasmaz.
    // Cookie domain'e bagli oldugu icin orada da gecerli; localStorage yoksa cookie'yi dene.
    const cookieTheme = readThemeCookie();
    if (cookieTheme === 'dark' || cookieTheme === 'light') return cookieTheme;
    if (window.matchMedia('(prefers-color-scheme: dark)').matches) return 'dark';
    return 'light';
  });

  useEffect(() => {
    const root = document.documentElement;
    root.classList.remove('light', 'dark');
    root.classList.add(theme);
    localStorage.setItem('theme', theme);
    // Cookie domain-scoped — Keycloak giris temasi bu cookie'yi okur.
    document.cookie = 'theme=' + theme + '; path=/; max-age=31536000; samesite=lax';
  }, [theme]);

  // Sunucudan gelen değeri sessizce uygula — API'ye geri yazma yok.
  const applyServerTheme = useCallback((value) => {
    if (value === 'dark' || value === 'light') {
      setThemeState(value);
    }
  }, []);

  const persist = (value) => {
    api.put('/users/me/theme', null, { params: { theme: value } }).catch((err) => {
      console.warn('Tema tercihi backend\'e kaydedilemedi:', err);
    });
  };

  const toggleTheme = useCallback(() => {
    setThemeState((prev) => {
      const next = prev === 'light' ? 'dark' : 'light';
      persist(next);
      return next;
    });
  }, []);

  const setTheme = useCallback((value) => {
    if (value !== 'light' && value !== 'dark') return;
    setThemeState(value);
    persist(value);
  }, []);

  return (
    <ThemeContext.Provider value={{ theme, toggleTheme, setTheme, applyServerTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used within ThemeProvider');
  return ctx;
}
