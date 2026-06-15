import { useCallback, useEffect, useRef, useState } from 'react';
import api from '../services/api';
import { useUrlState } from './useUrlState';

const DEFAULT_PAGE_SIZE = 20;

// URL query parametre anahtarları. İsimler backend API sözleşmesiyle birebir aynı
// (status, priority, productId, ...) — böylece URL hem paylaşılabilir hem okunabilir.
const ARRAY_KEYS = ['status', 'priority', 'productId', 'agentId', 'topicId', 'slaStatus', 'csatRating'];

/**
 * Generic hook for paginated + filtered + sorted ticket list endpoints.
 *
 * Tüm filtre/sayfa/sıralama state'i URL query string'inde tutulur (useSearchParams).
 * Böylece F5 / yer imi / link paylaşımı / geri-ileri butonu filtreleri korur.
 * Dışarıya verilen arayüz state'in URL'de olduğunu gizler — tüketici sayfalar değişmez.
 *
 * @param {string} endpoint  - API path, e.g. '/tickets', '/tickets/pool'
 * @param {object} [opts]
 * @param {number} [opts.size]
 * @param {string} [opts.sortBy]
 * @param {string} [opts.sortDir]
 * @param {object} [opts.extraParams] - Extra static params (e.g. { productId: 1 })
 */
export function useTicketList(endpoint, opts = {}) {
  const {
    size: initialSize = DEFAULT_PAGE_SIZE,
    sortBy: initialSortBy = 'createdAt',
    sortDir: initialSortDir = 'desc',
    extraParams = {},
  } = opts;

  // Ref keeps the latest extraParams without closing over a stale value.
  // extraParamsKey is a serialized snapshot used solely as a fetch dependency —
  // it changes only when content changes, avoiding the infinite-loop that a raw
  // object reference would cause while still re-triggering fetch on tab switches.
  const extraParamsRef = useRef(extraParams);
  useEffect(() => { extraParamsRef.current = extraParams; }, [extraParams]);
  const extraParamsKey = JSON.stringify(extraParams);

  // ── URL'den oku (varsayılanlar URL'e yazılmaz, böylece adres temiz kalır) ──
  const { searchParams, str, num, arr, setParams } = useUrlState();

  const page        = num('page', 0);
  const size        = num('size', initialSize);
  const sortBy      = str('sortBy', initialSortBy);
  const sortDir     = str('sortDir', initialSortDir);

  const status      = arr('status');
  const priority    = arr('priority');
  const search      = str('search');
  const productIds  = arr('productId');
  const agentIds    = arr('agentId');
  const topicIds    = arr('topicId');
  const slaStatuses = arr('slaStatus');
  const csatRatings = arr('csatRating');
  const dateFrom    = str('dateFrom');
  const dateTo      = str('dateTo');

  const [data, setData]     = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError]   = useState('');

  const fetch = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      // Array parametreler (status, priority) için URLSearchParams kullan
      // Axios params objesi array'leri status[]=NEW şeklinde gönderir, backend bunu kabul etmez.
      const qs = new URLSearchParams();
      qs.set('page', page);
      qs.set('size', size);
      qs.set('sortBy', sortBy);
      qs.set('sortDir', sortDir);
      if (status.length)     status.forEach(v => qs.append('status', v));
      if (priority.length)   priority.forEach(v => qs.append('priority', v));
      if (search)            qs.set('search', search);
      if (productIds.length) productIds.forEach(v => qs.append('productId', v));
      if (agentIds.length)   agentIds.forEach(v => qs.append('agentId', v));
      if (topicIds.length)   topicIds.forEach(v => qs.append('topicId', v));
      if (slaStatuses.length) slaStatuses.forEach(v => qs.append('slaStatus', v));
      if (csatRatings.length) csatRatings.forEach(v => qs.append('csatRating', v));
      if (dateFrom)          qs.set('dateFrom', dateFrom);
      if (dateTo)    qs.set('dateTo', dateTo);
      Object.entries(extraParamsRef.current).forEach(([k, v]) => {
        if (v === undefined || v === null || v === '') return;
        // Kullanıcı kendi status seçimini yapmışsa sayfanın varsayılan status kapsamını override etsin.
        if (k === 'status' && status.length) return;
        if (Array.isArray(v)) v.forEach(item => qs.append(k, item));
        else qs.set(k, v);
      });
      const res = await api.get(`${endpoint}?${qs.toString()}`);
      setData(res.data);
    } catch (err) {
      console.error(`useTicketList fetch error [${endpoint}]:`, err);
      setError(err.response?.data?.message || 'Could not load tickets.');
    } finally {
      setLoading(false);
    }
    // searchParams ya da extraParams içeriği değişince yeniden çek.
  }, [endpoint, searchParams, extraParamsKey]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { fetch(); }, [fetch]);

  // ── Setter'lar: URL'e yaz, filtre/sıralama değişiminde page 0'a döner ──────────
  const setPage = (v) => setParams({ page: v ? v : '' }, { resetPage: false });
  const setSize = (v) => setParams({ size: v === initialSize ? '' : v });

  const toggleSort = (field) => {
    const nextDir = sortBy === field ? (sortDir === 'asc' ? 'desc' : 'asc') : 'desc';
    setParams({
      sortBy:  field === initialSortBy ? '' : field,
      sortDir: nextDir === initialSortDir ? '' : nextDir,
    });
  };

  const clearFilters = () => setParams({
    status: [], priority: [], search: '', productId: [], agentId: [],
    topicId: [], slaStatus: [], csatRating: [], dateFrom: '', dateTo: '',
  });

  return {
    // Data
    tickets:    data?.content ?? [],
    totalPages: data?.totalPages ?? 0,
    totalItems: data?.totalElements ?? 0,
    loading,
    // İlk yükleme (henüz hiç veri yok) — sonraki filtre/sıralama refetch'lerinde
    // false kalır, böylece önceki liste ekranda tutulup boş-liste/scroll zıplaması önlenir.
    initialLoading: loading && data === null,
    error,
    refetch: fetch,

    // Pagination
    page, setPage,
    size, setSize,

    // Sort
    sortBy, sortDir,
    toggleSort,

    // Filters
    status,    setStatus:    (v) => setParams({ status: v }),
    priority,  setPriority:  (v) => setParams({ priority: v }),
    search,    setSearch:    (v) => setParams({ search: v }),
    productIds, setProductIds: (v) => setParams({ productId: v }),
    agentIds,   setAgentIds:  (v) => setParams({ agentId: v }),
    topicIds,   setTopicIds:  (v) => setParams({ topicId: v }),
    slaStatuses, setSlaStatuses: (v) => setParams({ slaStatus: v }),
    csatRatings, setCsatRatings: (v) => setParams({ csatRating: v }),
    dateFrom,  setDateFrom:  (v) => setParams({ dateFrom: v }),
    dateTo,    setDateTo:    (v) => setParams({ dateTo: v }),
    setDateRange: (from, to) => setParams({ dateFrom: from, dateTo: to }),
    clearFilters,
  };
}
