import { useCallback, useEffect, useRef, useState } from 'react';
import api from '../services/api';

/**
 * Sunucu taraflı sayfalı/filtreli listeler için genel veri çekme hook'u.
 *
 * `useTicketList`'in jenerik kardeşi: verilen `path` + `params` ile backend'den bir
 * Spring `Page` çeker. `params` içeriği değişince (filtre/arama/sıralama/sayfa) yeniden
 * istek atar. `initialLoading` yalnızca ilk yüklemede (henüz veri yokken) true olur;
 * sonraki refetch'lerde önceki veri korunur — böylece [[ListLoadingOverlay]] tabloyu
 * söküp sayfayı çökertmeden, üstte ince ilerleme çubuğuyla yenileyebilir.
 *
 * @param {string} path    API yolu, örn. '/canned-responses/paged'
 * @param {object} params  query parametreleri; '' / null / undefined atlanır, dizi → tekrarlı param
 * @returns {{ data, items, totalPages, totalItems, loading, initialLoading, error, refetch }}
 */
export function usePagedFetch(path, params) {
  const key = JSON.stringify(params);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Monotonic istek kimliği — bayat yanıtların yeni sonucu ezmesini engeller.
  const reqRef = useRef(0);

  const fetch = useCallback(async () => {
    // path falsy ise (örn. henüz ürün seçilmedi) istek atma.
    if (!path) {
      setData(null);
      setLoading(false);
      return;
    }
    const reqId = ++reqRef.current;
    setLoading(true);
    setError('');
    try {
      const qs = new URLSearchParams();
      Object.entries(params).forEach(([k, v]) => {
        if (v === undefined || v === null || v === '') return;
        if (Array.isArray(v)) v.forEach((item) => qs.append(k, item));
        else qs.append(k, v);
      });
      const res = await api.get(`${path}?${qs.toString()}`);
      if (reqId !== reqRef.current) return; // bayat yanıt — yoksay
      setData(res.data);
    } catch (err) {
      if (reqId !== reqRef.current) return;
      console.error(`usePagedFetch error [${path}]:`, err);
      setError(err.response?.data?.message || 'Could not load data.');
    } finally {
      if (reqId === reqRef.current) setLoading(false);
    }
    // params içeriği (key) değişince yeniden çek.
  }, [path, key]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { fetch(); }, [fetch]);

  return {
    data,
    items:      data?.content ?? [],
    totalPages: data?.totalPages ?? 0,
    totalItems: data?.totalElements ?? 0,
    loading,
    initialLoading: loading && data === null,
    error,
    refetch: fetch,
  };
}
