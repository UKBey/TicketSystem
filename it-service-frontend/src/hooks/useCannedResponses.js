import { useState, useEffect, useCallback } from 'react';
import {
  listCannedResponses,
  favoriteCannedResponse,
  unfavoriteCannedResponse,
} from '../services/api';
import { getRecentIds, pushRecentId } from '../utils/cannedResponses';

/**
 * Loads the canned responses visible to the current agent (optionally scoped to a ticket's
 * product) and exposes favorite toggling plus the per-user recently-used list. Shared by the
 * composer picker and the slash autocomplete so they stay in sync from a single fetch.
 *
 * @param {{ productId?: number, userId?: string, enabled?: boolean }} opts
 */
export function useCannedResponses({ productId, userId, enabled = true } = {}) {
  const [templates, setTemplates] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [recentIds, setRecentIds] = useState(() => getRecentIds(userId));

  const fetchTemplates = useCallback(async () => {
    if (!enabled) return;
    setLoading(true);
    setError(false);
    try {
      const res = await listCannedResponses(productId ? { productId } : {});
      setTemplates(Array.isArray(res.data) ? res.data : []);
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  }, [productId, enabled]);

  useEffect(() => {
    fetchTemplates();
  }, [fetchTemplates]);

  useEffect(() => {
    setRecentIds(getRecentIds(userId));
  }, [userId]);

  // Optimistic favorite toggle; reverts on failure.
  const toggleFavorite = useCallback(async (tpl) => {
    const next = !tpl.favorite;
    setTemplates((prev) => prev.map((t) => (t.id === tpl.id ? { ...t, favorite: next } : t)));
    try {
      if (next) await favoriteCannedResponse(tpl.id);
      else await unfavoriteCannedResponse(tpl.id);
    } catch {
      setTemplates((prev) => prev.map((t) => (t.id === tpl.id ? { ...t, favorite: !next } : t)));
    }
  }, []);

  const markUsed = useCallback((id) => {
    pushRecentId(userId, id);
    setRecentIds(getRecentIds(userId));
  }, [userId]);

  return { templates, loading, error, recentIds, toggleFavorite, markUsed, refetch: fetchTemplates };
}
