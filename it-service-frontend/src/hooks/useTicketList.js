import { useState, useEffect, useCallback } from 'react';
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

  const [page, setPage]           = useState(0);
  const [size, setSize]           = useState(initialSize);
  const [sortBy, setSortBy]       = useState(initialSortBy);
  const [sortDir, setSortDir]     = useState(initialSortDir);

  // Filters
  const [status, setStatus]       = useState('');
  const [priority, setPriority]   = useState('');
  const [search, setSearch]       = useState('');
  const [productId, setProductId] = useState('');
  const [agentId, setAgentId]     = useState('');
  const [slaStatus, setSlaStatus] = useState('');
  const [dateFrom, setDateFrom]   = useState('');
  const [dateTo, setDateTo]       = useState('');

  const [data, setData]     = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError]   = useState('');

  const fetch = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const params = {
        page,
        size,
        sortBy,
        sortDir,
        ...(status    ? { status }    : {}),
        ...(priority  ? { priority }  : {}),
        ...(search    ? { search }    : {}),
        ...(productId ? { productId } : {}),
        ...(agentId   ? { agentId }   : {}),
        ...(slaStatus ? { slaStatus } : {}),
        ...(dateFrom  ? { dateFrom }  : {}),
        ...(dateTo    ? { dateTo }    : {}),
        ...extraParams,
      };
      const res = await api.get(endpoint, { params });
      setData(res.data);
    } catch (err) {
      console.error(`useTicketList fetch error [${endpoint}]:`, err);
      setError(err.response?.data?.message || 'Could not load tickets.');
    } finally {
      setLoading(false);
    }
  }, [endpoint, page, size, sortBy, sortDir,
      status, priority, search, productId, agentId, slaStatus, dateFrom, dateTo,
      JSON.stringify(extraParams)]); // eslint-disable-line

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
    setStatus('');
    setPriority('');
    setSearch('');
    setProductId('');
    setAgentId('');
    setSlaStatus('');
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
    productId, setProductId: reset(setProductId),
    agentId,   setAgentId:   reset(setAgentId),
    slaStatus, setSlaStatus: reset(setSlaStatus),
    dateFrom,  setDateFrom:  reset(setDateFrom),
    dateTo,    setDateTo:    reset(setDateTo),
    clearFilters,
  };
}
