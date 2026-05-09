import { useState, useEffect, useCallback } from 'react';
import api from '../services/api';

const DEFAULT_PAGE_SIZE = 20;

/**
 * Generic hook for paginated + filtered + sorted ticket list endpoints.
 *
 * @param {string} endpoint  - API path, e.g. '/tickets', '/tickets/pool'
 * @param {object} [opts]    - Initial options
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

  const [page, setPage]         = useState(0);
  const [size, setSize]         = useState(initialSize);
  const [sortBy, setSortBy]     = useState(initialSortBy);
  const [sortDir, setSortDir]   = useState(initialSortDir);
  const [status, setStatus]     = useState('');
  const [priority, setPriority] = useState('');

  const [data, setData]         = useState(null);   // Spring Page<T>
  const [loading, setLoading]   = useState(true);
  const [error, setError]       = useState('');

  const fetch = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const params = {
        page,
        size,
        sortBy,
        sortDir,
        ...(status   ? { status }   : {}),
        ...(priority ? { priority } : {}),
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
  }, [endpoint, page, size, sortBy, sortDir, status, priority, JSON.stringify(extraParams)]); // eslint-disable-line

  useEffect(() => { fetch(); }, [fetch]);

  // Reset to page 0 when filters/sort change
  const handleSetStatus = (v)   => { setStatus(v);   setPage(0); };
  const handleSetPriority = (v) => { setPriority(v); setPage(0); };
  const handleSetSortBy = (v)   => { setSortBy(v);   setPage(0); };
  const handleSetSortDir = (v)  => { setSortDir(v);  setPage(0); };
  const handleSetSize = (v)     => { setSize(v);     setPage(0); };

  const toggleSort = (field) => {
    if (sortBy === field) {
      handleSetSortDir(sortDir === 'asc' ? 'desc' : 'asc');
    } else {
      handleSetSortBy(field);
      handleSetSortDir('desc');
    }
  };

  return {
    // Data
    tickets:     data?.content ?? [],
    totalPages:  data?.totalPages ?? 0,
    totalItems:  data?.totalElements ?? 0,
    loading,
    error,
    refetch: fetch,

    // Pagination state
    page, setPage,
    size, setSize: handleSetSize,

    // Sort state
    sortBy, sortDir,
    toggleSort,

    // Filter state
    status,   setStatus:   handleSetStatus,
    priority, setPriority: handleSetPriority,
  };
}
