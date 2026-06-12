import { useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';

/**
 * URL query string'ini tek kaynak (single source of truth) olarak kullanan küçük yardımcı.
 *
 * Filtre / sayfa / sıralama state'ini URL'de tutan liste sayfaları bunu kullanır; böylece
 * F5 (yenileme), yer imi, paylaşılan link ve geri-ileri butonu o görünümü korur.
 *
 * Okuyucular:
 *   - str(key, def)  → tek string param (yoksa def)
 *   - num(key, def)  → sayıya çevrilmiş param (yoksa def)
 *   - arr(key)       → çoklu (tekrarlı) param → string dizisi
 *
 * Yazıcı:
 *   - setParams(changes, { resetPage })
 *       changes: { key: value } — value dizi ise tekrarlı param yazılır.
 *       Boş string / null / undefined ya da boş dizi → param URL'den SİLİNİR (adres temiz kalır;
 *       varsayılan değerleri yazmamak için çağıran taraf default'a eşitse '' geçer).
 *       resetPage (varsayılan true): filtre/sıralama değişiminde 'page' paramını siler.
 *   Tüm değişiklikler tek setSearchParams çağrısında atomik uygulanır (ardışık çağrılar
 *   birbirini ezmesin diye) ve { replace: true } ile yazılır (her tuş vuruşu history'yi şişirmez).
 */
export function useUrlState() {
  const [searchParams, setSearchParams] = useSearchParams();

  const setParams = useCallback((changes, { resetPage = true } = {}) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (resetPage) next.delete('page');
      for (const [key, value] of Object.entries(changes)) {
        next.delete(key);
        if (Array.isArray(value)) {
          value.forEach((v) => next.append(key, v));
        } else if (value !== '' && value !== null && value !== undefined) {
          next.set(key, value);
        }
      }
      return next;
    }, { replace: true });
  }, [setSearchParams]);

  return {
    searchParams,
    str: (key, def = '') => searchParams.get(key) ?? def,
    num: (key, def) => { const v = searchParams.get(key); return v !== null ? Number(v) : def; },
    arr: (key) => searchParams.getAll(key),
    setParams,
  };
}
