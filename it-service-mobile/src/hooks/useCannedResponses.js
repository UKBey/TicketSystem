import { useState, useEffect, useCallback } from 'react';
import {
  getCannedResponses,
  favoriteCannedResponse,
  unfavoriteCannedResponse,
} from '../api/cannedResponses';

/**
 * Geçerli ajanın görebildiği hazır yanıtları yükler (opsiyonel ürün kapsamıyla) ve
 * favori toggle'ı sağlar. Composer'daki picker ve favori chip'leri tek kaynaktan besler.
 */
export function useCannedResponses({ productId, enabled = true } = {}) {
  const [templates, setTemplates] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);

  const refetch = useCallback(() => {
    if (!enabled) return;
    setLoading(true);
    setError(false);
    getCannedResponses(productId ? { productId } : {})
      .then((res) => setTemplates(Array.isArray(res.data) ? res.data : []))
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, [productId, enabled]);

  useEffect(() => { refetch(); }, [refetch]);

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

  return { templates, loading, error, toggleFavorite, refetch };
}
