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
  search,
} = {}) {
  const qs = new URLSearchParams();
  qs.set('page', String(page));
  qs.set('size', String(size));
  qs.set('sortBy', sortBy);
  qs.set('sortDir', sortDir);
  if (status) {
    (Array.isArray(status) ? status : [status]).forEach((s) => qs.append('status', s));
  }
  if (priority) {
    (Array.isArray(priority) ? priority : [priority]).forEach((p) => qs.append('priority', p));
  }
  if (search) qs.set('search', search);
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

/** Durum değiştirir. body: { status } veya { status:'RESOLVED', reasonCode, note }. */
export const changeStatus = (id, body) => api.put(`/tickets/${id}/status`, body);

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
