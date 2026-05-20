import api from './client';

/** Bilete ait dosya eklerinin metadata listesi. */
export const getAttachments = (ticketId) => api.get(`/tickets/${ticketId}/attachments`);

/**
 * Bilete dosya ekler. file: expo-document-picker asset'i { uri, name, mimeType }.
 * Çok parçalı (multipart) form olarak gönderilir; `file` alan adı backend ile eşleşir.
 */
export const uploadAttachment = (ticketId, file) => {
  const form = new FormData();
  form.append('file', {
    uri: file.uri,
    name: file.name || `file-${Date.now()}`,
    type: file.mimeType || 'application/octet-stream',
  });
  return api.post(`/tickets/${ticketId}/attachments`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    transformRequest: (data) => data,
  });
};
