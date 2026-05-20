import api from './client';

/**
 * AI özeti uç noktaları — llm-service tarafından sağlanır; nginx `/api/ai/*`
 * yolunu llm-service'e yönlendirir. Bilet konuşmasının LLM ile üretilmiş
 * özetini oluşturur / getirir. Yalnızca agent rolündeki kullanıcılar erişebilir.
 */

/** Bilet için yeni bir AI özeti üretir. language: 'tr' | 'en'. */
export const generateAiSummary = (ticketId, language = 'tr') =>
  api.post(`/ai/summaries/tickets/${ticketId}/generate`, null, { params: { language } });

/** Bilete ait en son üretilmiş AI özetini döner (yoksa 404). */
export const getLatestAiSummary = (ticketId) =>
  api.get(`/ai/summaries/tickets/${ticketId}/latest`);
