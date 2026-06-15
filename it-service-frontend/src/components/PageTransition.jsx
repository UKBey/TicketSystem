import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';

/**
 * Rota degisiminde icerigi yumusak bir fade + yukari kayma ile yeniden
 * canlandirir. Children'i REMOUNT ETMEDEN (yani sayfa state'ini sifirlamadan
 * / yeniden veri cekmeden) ayni DOM dugumunde CSS animasyonunu bastan oynatir.
 * `prefers-reduced-motion` tercihine saygi gosterir.
 *
 * AppLayout kalici oldugu icin (route degisiminde sadece en icteki sayfa swap
 * olur) bu sarmalayici da kalici kalir; pathname degisince animasyon yenilenir.
 */
export default function PageTransition({ children }) {
  const location = useLocation();
  const ref = useRef(null);
  const firstRender = useRef(true);

  useEffect(() => {
    if (firstRender.current) {
      // Ilk mount'ta JSX'teki class zaten animasyonu oynatiyor.
      firstRender.current = false;
      return;
    }
    const el = ref.current;
    if (!el) return;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    el.classList.remove('animate-page-in');
    // Reflow zorla — ayni animasyonu bastan oynatabilmek icin.
    el.getBoundingClientRect();
    el.classList.add('animate-page-in');
  }, [location.pathname]);

  return (
    <div ref={ref} className="animate-page-in">
      {children}
    </div>
  );
}
