import api from './client';

/**
 * Sayfalı bilet listesi. Backend yanıtı: { content, totalPages, totalElements }.
 * endpoint role'e göre değişir (customer: /tickets, agent: /tickets/all).
 */
export function getTickets({
  endpoint = '/tickets',
  page = 0,
  size = 20,
  sortBy = 'createdAt',
  sortDir = 'desc',
  status,
  priority,
  slaStatus,
  productId,
  topicId,
  agentId,
  search,
  dateFrom,
  dateTo,
} = {}) {
  const qs = new URLSearchParams();
  qs.set('page', String(page));
  qs.set('size', String(size));
  qs.set('sortBy', sortBy);
  qs.set('sortDir', sortDir);
  const appendAll = (key, val) => {
    if (val == null) return;
    (Array.isArray(val) ? val : [val]).forEach((v) => {
      if (v != null) qs.append(key, String(v));
    });
  };
  appendAll('status', status);
  appendAll('priority', priority);
  appendAll('slaStatus', slaStatus);
  appendAll('productId', productId);
  appendAll('topicId', topicId);
  appendAll('agentId', agentId);
  if (search) qs.set('search', search);
  if (dateFrom) qs.set('dateFrom', dateFrom);
  if (dateTo) qs.set('dateTo', dateTo);
  return api.get(`${endpoint}?${qs.toString()}`);
}

/** Tek bilet detayı. */
export const getTicket = (id) => api.get(`/tickets/${id}`);

/** Bilete ait yorumlar. */
export const getComments = (id) => api.get(`/tickets/${id}/comments`);

/** Yeni yorum gönderir. body: { message, type } — type: 'EXTERNAL' | 'INTERNAL'. */
export const postComment = (id, body) => api.post(`/tickets/${id}/comments`, body);

/** Bileti üstlenir (claim). */
export const claimTicket = (id) => api.put(`/tickets/${id}/claim`);

// Yaşam döngüsü eylemleri — kullanıcı ham statü SEÇMEZ; bir eylem çalıştırır,
// statü backend'de o eylemin guard'ı içinde değişir (kaynak statü sunucuda denetlenir).
/** Müşteri yanıtı beklemeye alır (IN_PROGRESS → WAITING_FOR_CUSTOMER). */
export const waitForCustomer = (id, body) => api.put(`/tickets/${id}/wait`, body);
/** Bekleyen bilete devam eder (WAITING_FOR_CUSTOMER → IN_PROGRESS). */
export const resumeTicket = (id, body) => api.put(`/tickets/${id}/resume`, body);
/** Bileti çözer (IN_PROGRESS → RESOLVED). body: { reasonCode, note }. */
export const resolveTicket = (id, body) => api.put(`/tickets/${id}/resolve`, body);
/** Çözülmüş bileti yeniden açar (RESOLVED → IN_PROGRESS). */
export const reopenTicket = (id, body) => api.put(`/tickets/${id}/reopen`, body);

/** Bileti kapatır. body: { reasonCode, note }. */
export const closeTicket = (id, body) => api.put(`/tickets/${id}/close`, body);

/** Önceliği değiştirir. body: { priority, reasonCode, note }. */
export const changePriority = (id, body) => api.put(`/tickets/${id}/priority`, body);

/** Konuyu değiştirir. body: { topicId, reasonCode, note }. */
export const changeTopic = (id, body) => api.put(`/tickets/${id}/topic`, body);

/** Bileti bir agent'a atar. body: { targetAgentId, note }. */
export const assignTicket = (id, body) => api.put(`/tickets/${id}/assign`, body);

/** CSAT memnuniyet anketi gönderir. body: { rating, comment }. RESOLVED bileti CLOSED yapar. */
export const submitCsat = (id, body) => api.post(`/tickets/${id}/csat`, body);

/** Yeni bilet oluşturur. body: { title, description, priority, productId, topicId }. */
export const createTicket = (body) => api.post('/tickets', body);

/** Bileti bırakır (unclaim). payload: { reasonCode, note }. */
export const unclaimTicket = (id, payload) =>
  api.delete(`/tickets/${id}/claim`, { data: payload });

/** Bilete ait worklog (süre kaydı) listesi. */
export const getWorklogs = (id) => api.get(`/tickets/${id}/worklogs`);

/** Worklog ekler. body: { minutes, description }. */
export const addWorklog = (id, body) => api.post(`/tickets/${id}/worklogs`, body);

/** Worklog siler. */
export const deleteWorklog = (id, worklogId) =>
  api.delete(`/tickets/${id}/worklogs/${worklogId}`);
