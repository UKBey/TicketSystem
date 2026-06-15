/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { flushSync } from 'react-dom';
import api from '../services/api';
import keycloak from '../keycloak';

const ThemeContext = createContext(null);

// `theme` cookie'sinden light/dark degerini okur (yoksa null).
function readThemeCookie() {
  const match = document.cookie.match(/(?:^|;\s*)theme=(light|dark)(?:;|$)/);
  return match ? match[1] : null;
}

// Temayi DOM'a uygular: html sinifi + localStorage + domain-scoped cookie.
// Hem ilk yuklemede (effect) hem de gecis sirasinda senkron cagrilir.
function applyThemeToDom(theme) {
  const root = document.documentElement;
  root.classList.remove('light', 'dark');
  root.classList.add(theme);
  localStorage.setItem('theme', theme);
  // Cookie domain-scoped — Keycloak giris temasi bu cookie'yi okur.
  document.cookie = 'theme=' + theme + '; path=/; max-age=31536000; samesite=lax';
}

function prefersReducedMotion() {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
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
    applyThemeToDom(theme);
  }, [theme]);

  const persist = (value) => {
    // Login sayfasinda token yoktur; istek 401 doner ve api interceptor'i
    // kullaniciyi Keycloak login ekranina yonlendirir. Yalnizca kimlik
    // dogrulanmissa sunucuya yaz — aksi halde tema sadece localStorage/cookie'de kalir.
    if (!keycloak.authenticated || !keycloak.token) return;
    api.put('/users/me/theme', null, { params: { theme: value } }).catch((err) => {
      console.warn('Tema tercihi backend\'e kaydedilemedi:', err);
    });
  };

  // Yeni temayi uygular. View Transitions API destekleniyorsa ve kullanici
  // hareket azaltma istemiyorsa, `origin` noktasindan acilan dairesel bir
  // reveal ile gecis yapar; aksi halde aninda gecer.
  const applyTheme = useCallback((next, origin) => {
    persist(next);

    const supportsViewTransition = typeof document.startViewTransition === 'function';
    if (!supportsViewTransition || prefersReducedMotion()) {
      setThemeState(next);
      return;
    }

    // Dairenin merkezi: tetikleyen butonun merkezi (yoksa ekran ust-ortasi).
    const x = origin?.x ?? window.innerWidth / 2;
    const y = origin?.y ?? 0;
    // En uzak koseye ulasan yaricap — daire tum ekrani kaplasin.
    const endRadius = Math.hypot(
      Math.max(x, window.innerWidth - x),
      Math.max(y, window.innerHeight - y),
    );

    const transition = document.startViewTransition(() => {
      // VT, callback'ten once eski DOM'un anlik goruntusunu alir; burada
      // senkron olarak yeni temaya geciyoruz (flushSync ile React'i zorlayarak).
      flushSync(() => {
        applyThemeToDom(next);
        setThemeState(next);
      });
    });

    transition.ready
      .then(() => {
        document.documentElement.animate(
          {
            clipPath: [
              `circle(0px at ${x}px ${y}px)`,
              `circle(${endRadius}px at ${x}px ${y}px)`,
            ],
          },
          {
            duration: 480,
            easing: 'cubic-bezier(0.4, 0, 0.2, 1)',
            // Yeni tema katmanini (ust katman) dairesel olarak ac.
            pseudoElement: '::view-transition-new(root)',
          },
        );
      })
      .catch(() => {
        /* Gecis atlandi/iptal edildi — tema yine de uygulanmis olur. */
      });
  }, []);

  const toggleTheme = useCallback(
    (origin) => {
      applyTheme(theme === 'light' ? 'dark' : 'light', origin);
    },
    [theme, applyTheme],
  );

  const setTheme = useCallback(
    (value, origin) => {
      if (value !== 'light' && value !== 'dark') return;
      applyTheme(value, origin);
    },
    [applyTheme],
  );

  return (
    <ThemeContext.Provider value={{ theme, toggleTheme, setTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used within ThemeProvider');
  return ctx;
}
