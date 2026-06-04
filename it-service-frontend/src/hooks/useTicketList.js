import { useState, useEffect, useCallback, useRef } from 'react';
import api from '../services/api';

const DEFAULT_PAGE_SIZE = 20;

/**
 * Generic hook for paginated + filtered + sorted ticket list endpoints.
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

  // Stable reference for extraParams to avoid infinite re-fetch loops
  const extraParamsRef = useRef(extraParams);
  useEffect(() => { extraParamsRef.current = extraParams; }, [extraParams]);

  const [page, setPage]           = useState(0);
  const [size, setSize]           = useState(initialSize);
  const [sortBy, setSortBy]       = useState(initialSortBy);
  const [sortDir, setSortDir]     = useState(initialSortDir);

  // Filters
  const [status, setStatus]       = useState([]);
  const [priority, setPriority]   = useState([]);
  const [search, setSearch]       = useState('');
  const [productIds, setProductIds] = useState([]);
  const [agentIds, setAgentIds]    = useState([]);
  const [topicIds, setTopicIds]   = useState([]);
  const [slaStatuses, setSlaStatuses] = useState([]);
  const [csatRatings, setCsatRatings] = useState([]);
  const [dateFrom, setDateFrom]    = useState('');
  const [dateTo, setDateTo]       = useState('');

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
  }, [endpoint, page, size, sortBy, sortDir,
      status, priority, search, productIds, agentIds, topicIds, slaStatuses, csatRatings, dateFrom, dateTo]);

  useEffect(() => { fetch(); }, [fetch]);

  // Reset to page 0 on filter/sort change
  const reset = (setter) => (v) => { setter(v); setPage(0); };

  const toggleSort = (field) => {
    if (sortBy === field) {
      reset(setSortDir)(sortDir === 'asc' ? 'desc' : 'asc');
    } else {
      setSortBy(field);
      reset(setSortDir)('desc');
      setPage(0);
    }
  };

  const clearFilters = () => {
    setStatus([]);
    setPriority([]);
    setSearch('');
    setProductIds([]);
    setAgentIds([]);
    setTopicIds([]);
    setSlaStatuses([]);
    setCsatRatings([]);
    setDateFrom('');
    setDateTo('');
    setPage(0);
  };

  return {
    // Data
    tickets:    data?.content ?? [],
    totalPages: data?.totalPages ?? 0,
    totalItems: data?.totalElements ?? 0,
    loading,
    error,
    refetch: fetch,

    // Pagination
    page, setPage,
    size, setSize: reset(setSize),

    // Sort
    sortBy, sortDir,
    toggleSort,

    // Filters
    status,    setStatus:    reset(setStatus),
    priority,  setPriority:  reset(setPriority),
    search,    setSearch:    reset(setSearch),
    productIds, setProductIds: reset(setProductIds),
    agentIds,   setAgentIds:  reset(setAgentIds),
    topicIds,   setTopicIds:  reset(setTopicIds),
    slaStatuses, setSlaStatuses: reset(setSlaStatuses),
    csatRatings, setCsatRatings: reset(setCsatRatings),
    dateFrom,  setDateFrom:  reset(setDateFrom),
    dateTo,    setDateTo:    reset(setDateTo),
    clearFilters,
  };
}
