import { useState, useEffect, useLayoutEffect, useCallback, useRef, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import api, {
  closeTicket as closeTicketWithNote,
  unclaimTicket as unclaimTicketWithNote,
  updateTicketPriority as updateTicketPriorityApi,
  updateTicketTopic as updateTicketTopicApi,
  listProductTopics,
} from '../services/api';
import { useTicketWebSocket } from './useTicketWebSocket';
import { REASON_CODES } from '../utils/reasonCodes';

export function useTicketDetail(id, hasRole) {
  const { t } = useTranslation();

  const [ticket, setTicket]       = useState(null);
  const [comments, setComments]   = useState([]);
  const [loading, setLoading]     = useState(true);
  const [attachments, setAttachments] = useState([]);
  const [uploading, setUploading] = useState(false);

  const [message, setMessage]         = useState('');
  const [commentType, setCommentType] = useState('EXTERNAL');
  const [sending, setSending]         = useState(false);
  const [cooldown, setCooldown]       = useState(0);

  const [slaInfo, setSlaInfo]         = useState(null);
  const [currentDate, setCurrentDate] = useState(Date.now());

  const [resolveModalOpen, setResolveModalOpen]   = useState(false);

  const [csatModalOpen, setCsatModalOpen] = useState(false);

  const [extraActionsOpen, setExtraActionsOpen] = useState(false);
  const [reasonModal, setReasonModal]           = useState({ isOpen: false, action: null });
  const [assignModal, setAssignModal]           = useState(false);

  const [priorityModalOpen, setPriorityModalOpen] = useState(false);
  const [topicModalOpen, setTopicModalOpen]       = useState(false);
  const [topicsList, setTopicsList]               = useState([]);
  const [topicsLoading, setTopicsLoading]         = useState(false);

  const fileInputRef = useRef(null);
  const chatEndRef   = useRef(null);

  // ---- fetch ----------------------------------------------------------------

  const fetchTicket = useCallback(async () => {
    try {
      const res = await api.get(`/tickets/${id}`);
      setTicket(res.data);
    } catch (err) {
      console.error('Could not load ticket:', err);
    } finally {
      setLoading(false);
    }
  }, [id]);

  const fetchComments = useCallback(async () => {
    try {
      const res = await api.get(`/tickets/${id}/comments`);
      setComments(res.data);
    } catch (err) {
      console.error('Could not load comments:', err);
    }
  }, [id]);

  const fetchAttachments = useCallback(async () => {
    try {
      const res = await api.get(`/tickets/${id}/attachments`);
      setAttachments(res.data);
    } catch (err) {
      console.error('Could not load attachments:', err);
    }
  }, [id]);

  // ---- effects --------------------------------------------------------------

  useEffect(() => {
    fetchTicket();
    fetchComments();
    fetchAttachments();
  }, [id, fetchTicket, fetchComments, fetchAttachments]);


  // Gercek zamanli guncellemeler: backend ticket mutation'larinda
  // /topic/tickets/{id} kanalina event yayinlar. Agent/admin ek olarak
  // /internal alt kanaliyla INTERNAL yorumlari da alir.
  const canSeeInternal = !!(hasRole && (hasRole('AGENT') || hasRole('AGENT_ADMIN')));
  const handleWsComment = useCallback((comment) => {
    setComments((prev) => (prev.some((c) => c.id === comment.id) ? prev : [...prev, comment]));
  }, []);
  const handleWsAttachment = useCallback((att) => {
    setAttachments((prev) => (prev.some((a) => a.id === att.id) ? prev : [...prev, att]));
  }, []);
  useTicketWebSocket(id, {
    onComment: handleWsComment,
    onAttachment: handleWsAttachment,
    onTicketUpdated: fetchTicket,
    includeInternal: canSeeInternal,
  });

  useEffect(() => {
    if (!ticket) return;
    api.get(`/tickets/${id}/sla-timer`)
      .then((res) => { setSlaInfo({ ...res.data, fetchTime: Date.now() }); })
      .catch((e) => console.error('SLA fetch error', e));
  }, [ticket?.status, id, ticket]);

  useEffect(() => {
    const timer = setInterval(() => setCurrentDate(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  // ---- derived --------------------------------------------------------------

  const timeline = useMemo(() => {
    const items = [
      ...comments.map((c) => ({ ...c, _type: 'comment' })),
      ...attachments.map((a) => ({ ...a, _type: 'attachment' })),
    ];
    items.sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
    return items;
  }, [comments, attachments]);

  useLayoutEffect(() => {
    if (loading) return;
    const container = chatEndRef.current?.parentElement;
    if (container) container.scrollTop = container.scrollHeight;
  }, [loading, timeline]);

  // ---- handlers -------------------------------------------------------------

  const handleFileUpload = async (file) => {
    if (!file) return;
    setUploading(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      const res = await api.post(`/tickets/${id}/attachments`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      setAttachments((prev) => (prev.some((a) => a.id === res.data.id) ? prev : [...prev, res.data]));
    } catch (err) {
      alert(err.response?.data?.message || 'Could not upload file.');
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const handleDownloadAttachment = async (attachment) => {
    try {
      const res = await api.get(`/attachments/${attachment.id}`, { responseType: 'blob' });
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', attachment.fileName);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch {
      alert('Could not download file.');
    }
  };

  const handleSendComment = async () => {
    if (!message.trim() || cooldown > 0) return;
    setSending(true);
    try {
      const res = await api.post(`/tickets/${id}/comments`, { message, type: commentType });
      setComments((prev) => (prev.some((c) => c.id === res.data.id) ? prev : [...prev, res.data]));
      setMessage('');
      setCooldown(5);
      const timer = setInterval(() => {
        setCooldown((prev) => {
          if (prev <= 1) { clearInterval(timer); return 0; }
          return prev - 1;
        });
      }, 1000);
      const ticketRes = await api.get(`/tickets/${id}`);
      setTicket(ticketRes.data);
    } catch (err) {
      alert(err.response?.data?.message || 'Could not send comment.');
    } finally {
      setSending(false);
    }
  };

  const handleStatusChange = async (newStatus) => {
    try {
      const res = await api.put(`/tickets/${id}/status`, { status: newStatus });
      setTicket(res.data);
    } catch (err) {
      alert(err.response?.data?.message || 'Could not update status.');
    }
  };

  const handleClaim = async () => {
    try {
      const res = await api.put(`/tickets/${id}/claim`);
      setTicket(res.data);
    } catch (err) {
      if (err.response?.status === 409 && err.response?.data?.error === 'TICKET_LIMIT_EXCEEDED') {
        alert(`Limit exceeded: ${err.response.data.message}`);
      } else {
        alert(err.response?.data?.message || 'Could not claim ticket.');
      }
    }
  };

  const handleUnclaim = async (payload) => {
    try {
      const res = await unclaimTicketWithNote(id, payload);
      setTicket(res.data);
    } catch (err) {
      alert(err.response?.data?.message || 'Could not release ticket.');
    }
  };

  const handleCloseTicket = async (payload) => {
    try {
      const res = await closeTicketWithNote(id, payload);
      setTicket(res.data);
    } catch (err) {
      alert(err.response?.data?.message || 'Could not close ticket.');
    }
  };

  const handleSubmitResolve = async ({ reasonCode, note }) => {
    if (!reasonCode) return;
    const res = await api.put(`/tickets/${id}/status`, { status: 'RESOLVED', reasonCode, note });
    setTicket(res.data);
    setResolveModalOpen(false);
  };

  const handleResolveClick = () => setResolveModalOpen(true);

  const handleSubmitCsat = async (rating, comment) => {
    await api.post(`/tickets/${id}/csat`, { rating, comment });
    fetchTicket();
  };

  const handleAssignSuccess = () => fetchTicket();

  const handlePriorityChange = async ({ value, reasonCode, note }) => {
    try {
      const res = await updateTicketPriorityApi(id, { priority: value, reasonCode, note });
      setTicket(res.data);
    } catch (err) {
      alert(err.response?.data?.message || t('ticketDetail.priorityChangeFailed'));
      throw err;
    }
  };

  const openTopicModal = async () => {
    if (!ticket?.productId) return;
    setTopicModalOpen(true);
    setTopicsLoading(true);
    try {
      const res = await listProductTopics(ticket.productId);
      setTopicsList((res.data || []).filter((tp) => tp.isActive));
    } catch (err) {
      console.error('Could not load topics:', err);
      setTopicsList([]);
    } finally {
      setTopicsLoading(false);
    }
  };

  const handleTopicChange = async ({ value, reasonCode, note }) => {
    try {
      const res = await updateTicketTopicApi(id, { topicId: Number(value), reasonCode, note });
      setTicket(res.data);
    } catch (err) {
      alert(err.response?.data?.message || t('ticketDetail.topicChangeFailed'));
      throw err;
    }
  };

  const openReasonModal = (action) => {
    setReasonModal({ isOpen: true, action });
    setExtraActionsOpen(false);
  };

  const closeReasonModal = () => setReasonModal({ isOpen: false, action: null });

  const handleReasonConfirm = async (payload) => {
    if (reasonModal.action === 'UNCLAIM') await handleUnclaim(payload);
    else if (reasonModal.action === 'CLOSE') await handleCloseTicket(payload);
    closeReasonModal();
  };

  const reasonModalConfig = (() => {
    if (reasonModal.action === 'CLOSE') return {
      title: t('ticketDetail.closeTicketTitle'),
      description: t('ticketDetail.closeTicketDesc'),
      confirmLabel: t('ticketDetail.closeTicketLabel'),
      confirmVariant: 'danger',
      reasonCodes: REASON_CODES.CLOSE,
      reasonTranslationPrefix: 'reasonCode.CLOSE',
    };
    if (reasonModal.action === 'UNCLAIM') return {
      title: t('ticketDetail.releaseTitle'),
      description: t('ticketDetail.releaseDesc'),
      confirmLabel: t('ticketDetail.releaseLabel'),
      confirmVariant: 'warning',
      reasonCodes: REASON_CODES.UNCLAIM,
      reasonTranslationPrefix: 'reasonCode.UNCLAIM',
    };
    return { title: '', description: '', confirmLabel: '', confirmVariant: 'primary', reasonCodes: [], reasonTranslationPrefix: '' };
  })();

  return {
    // data
    ticket, loading, timeline, slaInfo, currentDate,
    // comment form
    message, setMessage, commentType, setCommentType, sending, cooldown,
    // file
    uploading, fileInputRef, chatEndRef,
    // modals
    resolveModalOpen, setResolveModalOpen,
    csatModalOpen, setCsatModalOpen,
    extraActionsOpen, setExtraActionsOpen,
    reasonModal, reasonModalConfig,
    assignModal, setAssignModal,
    priorityModalOpen, setPriorityModalOpen,
    topicModalOpen, setTopicModalOpen, openTopicModal,
    topicsList, topicsLoading,
    // handlers
    handleFileUpload, handleDownloadAttachment,
    handleSendComment, handleStatusChange, handleClaim,
    handleResolveClick, handleSubmitResolve,
    handleSubmitCsat, handleAssignSuccess,
    handlePriorityChange, handleTopicChange,
    openReasonModal, closeReasonModal, handleReasonConfirm,
  };
}
