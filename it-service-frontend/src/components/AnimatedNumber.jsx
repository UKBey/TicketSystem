import { useEffect, useRef, useState } from 'react';

/**
 * Formatlanmis bir deger string'indeki ilk sayisal token'i 0'dan (ya da onceki
 * degerden) hedefe dogru animasyonla sayar; prefix/suffix'i oldugu gibi korur.
 *
 * Ornekler:  "1,234" → "1,234" |  "4.2/5" → "4.2/5" |  "2.3h" → "2.3h" |  "%85" → "%85"
 *
 * Tum dashboard degerleri en-US formatinda uretildigi icin (virgul = binlik,
 * nokta = ondalik) parse locale'den bagimsizdir. Sayisal token bulunamazsa ya
 * da kullanici hareket azaltma istiyorsa deger oldugu gibi/aninda gosterilir.
 */

const DEFAULT_DURATION = 900;
const NUMERIC_TOKEN = /-?\d[\d,]*(?:\.\d+)?/;
const easeOutCubic = (t) => 1 - Math.pow(1 - t, 3);

function parseValue(value) {
  if (typeof value !== 'string' && typeof value !== 'number') return null;
  const str = String(value);
  const match = str.match(NUMERIC_TOKEN);
  if (!match) return null;

  const token = match[0];
  const target = parseFloat(token.replace(/,/g, ''));
  if (!Number.isFinite(target)) return null;

  const dotIndex = token.indexOf('.');
  return {
    target,
    decimals: dotIndex === -1 ? 0 : token.length - dotIndex - 1,
    useGrouping: token.includes(','),
    prefix: str.slice(0, match.index),
    suffix: str.slice(match.index + token.length),
  };
}

export default function AnimatedNumber({ value, duration = DEFAULT_DURATION }) {
  const parsed = parseValue(value);
  const target = parsed ? parsed.target : null;

  const prefersReducedMotion =
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  const willAnimate = target != null && !prefersReducedMotion && target !== 0;

  // animatedValue == null  →  animasyon yok, hedef dogrudan gosterilir.
  // Ilk boyamada "son deger flash'i"ni onlemek icin 0'dan basla.
  const [animatedValue, setAnimatedValue] = useState(() => (willAnimate ? 0 : null));
  // Bir sonraki animasyonun baslangici: ilk mount'ta 0, sonrasinda onceki hedef.
  const fromRef = useRef(0);

  useEffect(() => {
    if (target == null || prefersReducedMotion) {
      fromRef.current = target ?? 0;
      return undefined;
    }

    const from = fromRef.current;
    if (from === target) {
      // Zaten hedefte — animasyona gerek yok.
      return undefined;
    }

    let rafId = 0;
    let startTs = null;
    const step = (ts) => {
      if (startTs == null) startTs = ts;
      const progress = Math.min(1, (ts - startTs) / duration);
      setAnimatedValue(from + (target - from) * easeOutCubic(progress));
      if (progress < 1) {
        rafId = requestAnimationFrame(step);
      } else {
        fromRef.current = target;
        setAnimatedValue(null); // bitti → hedefe sabitlen
      }
    };

    rafId = requestAnimationFrame(step);
    return () => cancelAnimationFrame(rafId);
  }, [target, duration, prefersReducedMotion]);

  if (parsed == null) return value;

  const shown = animatedValue == null ? target : animatedValue;
  const formatted = new Intl.NumberFormat('en-US', {
    minimumFractionDigits: parsed.decimals,
    maximumFractionDigits: parsed.decimals,
    useGrouping: parsed.useGrouping,
  }).format(shown);

  return (
    <>
      {parsed.prefix}
      {formatted}
      {parsed.suffix}
    </>
  );
}
