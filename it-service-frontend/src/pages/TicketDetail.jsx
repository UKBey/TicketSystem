import { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { StatusBadge, PriorityBadge } from '../components/Badges';
import ActionReasonModal from '../components/ActionReasonModal';
import AgentSelectionModal from '../components/AgentSelectionModal';
import api from '../services/api';
import { closeTicket as closeTicketWithNote, unclaimTicket as unclaimTicketWithNote, generateAiSummary, getLatestAiSummary } from '../services/api';
import i18n from '../i18n';
import {
  ArrowLeft,
  Send,
  Paperclip,
  X,
  Clock,
  Plus,
  Trash2,
  Download,
  Star,
  CheckCircle2,
  Settings2,
  FileText,
  Image,
  FileArchive,
  File,
  AlertTriangle,
  Sparkles,
  ChevronDown,
  ChevronUp,
} from 'lucide-react';

// 60 dakikadan küçükse "Xm Ys", büyükse "Xh Ym" formatında gösterir.
function formatSlaTime(ms) {
  const totalSecs = Math.floor(ms / 1000);
  const totalMins = Math.floor(totalSecs / 60);
  if (totalMins < 60) {
    const secs = totalSecs % 60;
    return `${totalMins}m ${secs}s`;
  }
  const hours = Math.floor(totalMins / 60);
  const mins  = totalMins % 60;
  return mins > 0 ? `${hours}h ${mins}m` : `${hours}h`;
}

export default function TicketDetail() {
  const { t } = useTranslation();
  const { id } = useParams();
  const navigate = useNavigate();
  const { user, hasRole } = useAuth();
  const { theme } = useTheme();
  const isDark = theme === 'dark';
  const chatEndRef = useRef(null);

  const [ticket, setTicket] = useState(null);
  const [comments, setComments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState('');
  const [commentType, setCommentType] = useState('EXTERNAL');
  const [sending, setSending] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  const [extraActionsOpen, setExtraActionsOpen] = useState(false);
  const [csatModalOpen, setCsatModalOpen] = useState(false);
  const [csatRating, setCsatRating] = useState(5);
  const [csatComment, setCsatComment] = useState('');

  const [submittingCsat, setSubmittingCsat] = useState(false);
  const [slaInfo, setSlaInfo] = useState(null);
  const [currentDate, setCurrentDate] = useState(Date.now());
  const [reasonModal, setReasonModal] = useState({
    isOpen: false,
    action: null,
  });

  const [assignModal, setAssignModal] = useState(false);

  const [attachments, setAttachments] = useState([]);
  const [uploading, setUploading] = useState(false);
  const fileInputRef = useRef(null);

  const [worklogs, setWorklogs] = useState([]);
  const [worklogMinutes, setWorklogMinutes] = useState('');
  const [worklogDescription, setWorklogDescription] = useState('');
  const [addingWorklog, setAddingWorklog] = useState(false);
  const [worklogFormOpen, setWorklogFormOpen] = useState(false);

  const [resolutionNote, setResolutionNote] = useState(null);
  const [resolutionNoteText, setResolutionNoteText] = useState('');
  const [resolveModalOpen, setResolveModalOpen] = useState(false);
  const [savingResolutionNote, setSavingResolutionNote] = useState(false);

  // AI Summary state
  const [aiSummary, setAiSummary] = useState(null);
  const [aiSummaryLoading, setAiSummaryLoading] = useState(false);
  const [aiSummaryExpanded, setAiSummaryExpanded] = useState(true);
  const [aiSummaryError, setAiSummaryError] = useState(null);

  // Audit history collapse state — varsayılan kapalı
  const [auditHistoryExpanded, setAuditHistoryExpanded] = useState(false);

  const STATUS_OPTIONS = {
    NEW: ['IN_PROGRESS'],
    IN_PROGRESS: ['NEW', 'WAITING_FOR_CUSTOMER', 'RESOLVED', 'CLOSED'],
    WAITING_FOR_CUSTOMER: ['IN_PROGRESS'],
    RESOLVED: ['IN_PROGRESS', 'CLOSED'],
    CLOSED: [],
  };

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

  const fetchResolutionNote = useCallback(async () => {
    try {
      const res = await api.get(`/tickets/${id}/resolution-note`);
      setResolutionNote(res.data);
      setResolutionNoteText(res.data.note || '');
    } catch {
      setResolutionNote(null);
    }
  }, [id]);

  const fetchLatestAiSummary = useCallback(async () => {
    try {
      const res = await getLatestAiSummary(id);
      setAiSummary(res.data);
    } catch {
      // 404 = henüz özet yok, sessizce geç
      setAiSummary(null);
    }
  }, [id]);

  const handleGenerateAiSummary = async () => {
    setAiSummaryLoading(true);
    setAiSummaryError(null);
    try {
      const lang = i18n.language?.startsWith('tr') ? 'tr' : 'en';
      const res = await generateAiSummary(id, lang);
      setAiSummary(res.data);
      setAiSummaryExpanded(true);
    } catch (err) {
      const status = err.response?.status;
      const data = err.response?.data;
      if (status === 429) {
        const seconds = Math.ceil(data?.retryAfterSeconds ?? 10);
        setAiSummaryError(t('ticketDetail.aiSummaryRateLimit', { seconds }));
      } else {
        setAiSummaryError(data?.detail || t('ticketDetail.aiSummaryError'));
      }
    } finally {
      setAiSummaryLoading(false);
    }
  };

  const fetchWorklogs = useCallback(async () => {
    try {
      const res = await api.get(`/tickets/${id}/worklogs`);
      setWorklogs(res.data);
    } catch (err) {
      console.debug('Could not load worklogs:', err);
    }
  }, [id]);

  useEffect(() => {
    fetchTicket();
    fetchComments();
    fetchAttachments();
    fetchWorklogs();
    fetchResolutionNote();
    if (hasRole('AGENT') || hasRole('AGENT_ADMIN')) {
      fetchLatestAiSummary();
    }
  }, [id, fetchTicket, fetchComments, fetchAttachments, fetchWorklogs, fetchResolutionNote, fetchLatestAiSummary, hasRole]);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [comments]);

  useEffect(() => {
    if (ticket) {
      const fetchSla = async () => {
         try {
           const res = await api.get(`/tickets/${id}/sla-timer`);
           const data = res.data;
           data.fetchTime = Date.now();
           setSlaInfo(data);
         } catch (e) { console.error('SLA fetch error', e); }
      };
      fetchSla();
    }
  }, [ticket?.status, id, hasRole, ticket]);

  useEffect(() => {
    const timer = setInterval(() => setCurrentDate(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  const timeline = useMemo(() => {
    const items = [
      ...comments.map((c) => ({ ...c, _type: 'comment' })),
      ...attachments.map((a) => ({ ...a, _type: 'attachment' })),
    ];
    items.sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
    return items;
  }, [comments, attachments]);

  const handleFileUpload = async (file) => {
    if (!file) return;
    setUploading(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      const res = await api.post(`/tickets/${id}/attachments`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      setAttachments((prev) => [...prev, res.data]);
    } catch (err) {
      alert(err.response?.data?.message || 'Could not upload file.');
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const handleResolveClick = () => {
    if (resolutionNote) {
      setResolutionNoteText(resolutionNote.note);
    } else {
      setResolutionNoteText('');
    }
    setResolveModalOpen(true);
  };

  const handleSubmitResolve = async () => {
    if (!resolutionNoteText.trim()) return;
    setSavingResolutionNote(true);
    try {
      if (resolutionNote) {
        await api.put(`/tickets/${id}/resolution-note`, { note: resolutionNoteText.trim() });
      } else {
        await api.post(`/tickets/${id}/resolution-note`, { note: resolutionNoteText.trim() });
      }
      const res = await api.put(`/tickets/${id}/status`, { status: 'RESOLVED' });
      setTicket(res.data);
      setResolveModalOpen(false);
      fetchResolutionNote();
    } catch (err) {
      alert(err.response?.data?.message || 'Could not save resolution note or update status.');
    } finally {
      setSavingResolutionNote(false);
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

  const handleAddWorklog = async () => {
    const mins = parseInt(worklogMinutes, 10);
    if (!mins || mins <= 0) return;
    setAddingWorklog(true);
    try {
      const res = await api.post(`/tickets/${id}/worklogs`, {
        minutes: mins,
        description: worklogDescription.trim() || null,
      });
      setWorklogs((prev) => [...prev, res.data]);
      setWorklogMinutes('');
      setWorklogDescription('');
      setWorklogFormOpen(false);
    } catch (err) {
      alert(err.response?.data?.message || 'Could not add worklog.');
    } finally {
      setAddingWorklog(false);
    }
  };

  const handleDeleteWorklog = async (worklogId) => {
    if (!confirm(t('ticketDetail.confirmDeleteWorklog'))) return;
    try {
      await api.delete(`/tickets/${id}/worklogs/${worklogId}`);
      setWorklogs((prev) => prev.filter((w) => w.id !== worklogId));
    } catch (err) {
      alert(err.response?.data?.message || 'Could not delete worklog.');
    }
  };

  const formatMinutes = (mins) => {
    if (!mins) return '0m';
    const h = Math.floor(mins / 60);
    const m = mins % 60;
    if (h === 0) return `${m}m`;
    if (m === 0) return `${h}h`;
    return `${h}h ${m}m`;
  };

  const getFileIcon = (fileType) => {
    if (!fileType) return <File className="h-5 w-5" />;
    if (fileType.startsWith('image/')) return <Image className="h-5 w-5" />;
    if (fileType.includes('pdf')) return <FileText className="h-5 w-5 text-danger-500" />;
    if (fileType.includes('zip') || fileType.includes('rar') || fileType.includes('tar')) return <FileArchive className="h-5 w-5" />;
    if (fileType.includes('word') || fileType.includes('document')) return <FileText className="h-5 w-5 text-primary-500" />;
    if (fileType.includes('sheet') || fileType.includes('excel')) return <FileText className="h-5 w-5 text-accent-500" />;
    return <File className="h-5 w-5" />;
  };

  const handleSendComment = async () => {
    if (!message.trim() || cooldown > 0) return;
    setSending(true);
    try {
      const res = await api.post(`/tickets/${id}/comments`, {
        message,
        type: commentType,
      });
      setComments((prev) => [...prev, res.data]);
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

  const handleSubmitCsat = async () => {
    setSubmittingCsat(true);
    try {
      await api.post(`/tickets/${id}/csat`, {
        rating: csatRating,
        comment: csatComment
      });
      setCsatModalOpen(false);
      fetchTicket();
    } catch (err) {
      alert(err.response?.data?.message || 'Could not submit survey.');
    } finally {
      setSubmittingCsat(false);
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

  const handleUnclaim = async (note) => {
    try {
      const res = await unclaimTicketWithNote(id, note);
      setTicket(res.data);
    } catch (err) {
      alert(err.response?.data?.message || 'Could not release ticket.');
    }
  };

  const handleCloseTicket = async (note) => {
    try {
      const res = await closeTicketWithNote(id, note);
      setTicket(res.data);
    } catch (err) {
      alert(err.response?.data?.message || 'Could not close ticket.');
    }
  };

  const handleAssignSuccess = () => {
    fetchTicket();
  };

  const openReasonModal = (action) => {
    setReasonModal({ isOpen: true, action });
    setExtraActionsOpen(false);
  };

  const closeReasonModal = () => {
    setReasonModal({ isOpen: false, action: null });
  };

  const handleReasonConfirm = async (note) => {
    if (reasonModal.action === 'UNCLAIM') {
      await handleUnclaim(note);
    } else if (reasonModal.action === 'CLOSE') {
      await handleCloseTicket(note);
    }
    closeReasonModal();
  };

  const reasonModalConfig =
    reasonModal.action === 'CLOSE'
      ? {
          title: t('ticketDetail.closeTicketTitle'),
          description: t('ticketDetail.closeTicketDesc'),
          confirmLabel: t('ticketDetail.closeTicketLabel'),
          confirmVariant: 'danger',
        }
      : reasonModal.action === 'UNCLAIM'
      ? {
          title: t('ticketDetail.releaseTitle'),
          description: t('ticketDetail.releaseDesc'),
          confirmLabel: t('ticketDetail.releaseLabel'),
          confirmVariant: 'warning',
        }
      : {
          title: '',
          description: '',
          confirmLabel: '',
          confirmVariant: 'primary',
        };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendComment();
    }
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return '—';
    const date = new Date(dateStr);
    return date.toLocaleDateString('en-US', {
      month: 'numeric',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: true,
    });
  };

  const formatShortDate = (dateStr) => {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    return date.toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: true,
    });
  };

  const statusLabel = (status) => {
    const map = {
      NEW: t('ticketDetail.statusNew'),
      IN_PROGRESS: t('ticketDetail.statusInProgress'),
      WAITING_FOR_CUSTOMER: t('ticketDetail.statusWaiting'),
      RESOLVED: t('ticketDetail.statusResolved'),
      CLOSED: t('ticketDetail.statusClosed'),
    };
    return map[status] || status;
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-40">
        <div className="h-8 w-8 rounded-full border-[3px] animate-spin" style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
      </div>
    );
  }

  if (!ticket) {
    return (
      <div className="flex flex-col items-center justify-center py-20" style={{ color: 'var(--text-tertiary)' }}>
        <h3 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>{t('ticketDetail.ticketNotFound')}</h3>
      </div>
    );
  }

  const ticketCode = `TCK-${String(ticket.id).padStart(3, '0')}`;
  const allowedStatuses = STATUS_OPTIONS[ticket.status] || [];
  const isAgent = hasRole('AGENT') || hasRole('AGENT_ADMIN');
  const isAgentAdmin = hasRole('AGENT_ADMIN');
  const isCustomer = hasRole('CUSTOMER');
  const auditLogs = Array.isArray(ticket.auditLogs)
    ? ticket.auditLogs
    : Array.isArray(ticket.ticketAuditLogs)
      ? ticket.ticketAuditLogs
      : [];

  const getAuditActionLabel = (actionType) => {
    const labels = {
      UNCLAIM: t('ticketDetail.auditReleased'),
      CLOSE: t('ticketDetail.auditClosed'),
      CLAIM: t('ticketDetail.auditClaimed'),
    };
    return labels[actionType] || actionType || t('ticketDetail.auditUpdated');
  };

  const getAuditActionStyles = (actionType) => {
    if (actionType === 'CLOSE') {
      return {
        backgroundColor: isDark ? 'rgba(239,68,68,0.18)' : '#fee2e2',
        color: isDark ? '#fca5a5' : '#991b1b',
      };
    }
    if (actionType === 'UNCLAIM') {
      return {
        backgroundColor: isDark ? 'rgba(245,158,11,0.18)' : '#fef3c7',
        color: isDark ? '#fde68a' : '#92400e',
      };
    }
    return {
      backgroundColor: isDark ? 'rgba(59,130,246,0.18)' : '#dbeafe',
      color: isDark ? '#93c5fd' : '#1d4ed8',
    };
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-6">
      {/* Left column */}
      <div className="flex flex-col gap-5 min-w-0">
        <button
          onClick={() => navigate(-1)}
          className="inline-flex items-center gap-1.5 text-sm font-medium transition-colors hover:text-primary-500 cursor-pointer self-start"
          style={{ color: 'var(--text-secondary)' }}
        >
          <ArrowLeft className="h-4 w-4" />
          {t('ticketDetail.back')}
        </button>

        <div>
          <div className="flex items-center gap-3 flex-wrap">
            <h1 className="text-xl font-bold" style={{ color: 'var(--text-primary)' }}>{ticketCode}</h1>
            <PriorityBadge priority={ticket.priority} />
            <StatusBadge status={ticket.status} />
          </div>
          <div className="text-lg font-semibold mt-1" style={{ color: 'var(--text-primary)' }}>
            {ticket.title}
          </div>
          <div className="flex items-center gap-2 mt-2 text-sm flex-wrap" style={{ color: 'var(--text-secondary)' }}>
            <span>👤 {ticket.customerName || ticket.customerId}</span>
            <span style={{ color: 'var(--text-tertiary)' }}>•</span>
            <span>Product: {ticket.productName || ticket.productId}</span>
          </div>
        </div>

        {/* Timeline / chat area */}
        <div className="rounded-xl border overflow-hidden" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
          <div className="flex flex-col gap-4 p-5 min-h-[300px] max-h-[500px] overflow-y-auto">
            {timeline.length === 0 && (
              <div className="flex items-center justify-center py-8" style={{ color: 'var(--text-tertiary)' }}>
                <p className="text-sm">{t('ticketDetail.noComments')}</p>
              </div>
            )}
            {timeline.map((item) => {
              const itemAuthorId = item._type === 'attachment' ? item.uploaderId : item.authorId;
              const itemAuthorName = item._type === 'attachment' ? null : item.authorName;
              const isOwnItem = itemAuthorId === user?.id;
              const isInternal = item.type === 'INTERNAL';

              const isRight = isOwnItem && !isInternal;
              const displayName = itemAuthorName || (itemAuthorId === ticket.customerId ? ticket.customerName : 'Agent');

              let bubbleBg, bubbleText;
              if (isInternal) {
                bubbleBg = 'border';
                bubbleText = '';
              } else if (isRight) {
                bubbleBg = 'bg-primary-500';
                bubbleText = 'text-white';
              } else {
                bubbleBg = 'border';
                bubbleText = '';
              }

              return (
                <div
                  key={`${item._type}-${item.id}`}
                  className={`max-w-[70%] rounded-xl px-4 py-3 text-sm animate-fade-in ${bubbleBg} ${bubbleText} ${
                    isInternal ? 'self-end' : isRight ? 'self-end' : 'self-start'
                  }`}
                  style={
                    isInternal
                      ? { backgroundColor: isDark ? 'rgba(245,158,11,0.08)' : '#fffbeb', borderColor: isDark ? 'rgba(245,158,11,0.2)' : '#fde68a' }
                      : !isRight
                        ? { backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)' }
                        : {}
                  }
                >
                  <div className="flex items-center gap-2 mb-1">
                    <span className={`text-xs font-semibold ${isRight && !isInternal ? 'text-white/80' : ''}`} style={!isRight || isInternal ? { color: 'var(--text-secondary)' } : {}}>
                      {displayName}
                    </span>
                    {isInternal && (
                      <span
                        className="inline-flex items-center rounded-full px-1.5 py-0.5 text-[10px] font-bold"
                        style={{ backgroundColor: isDark ? 'rgba(245,158,11,0.2)' : '#fef3c7', color: isDark ? '#fde68a' : '#92400e' }}
                      >
                        {t('ticketDetail.internal')}
                      </span>
                    )}
                  </div>

                  {item._type === 'attachment' ? (
                    <div className="flex flex-col gap-2">
                      <div
                        className="flex items-center gap-2 cursor-pointer"
                        onClick={() => handleDownloadAttachment(item)}
                        title="Click to download"
                      >
                        {getFileIcon(item.fileType)}
                        <span className="font-semibold text-sm">{item.fileName}</span>
                      </div>
                      <button
                        className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium transition-colors cursor-pointer border"
                        style={{ borderColor: 'rgba(255,255,255,0.15)', backgroundColor: 'rgba(255,255,255,0.1)' }}
                        onClick={() => handleDownloadAttachment(item)}
                      >
                        <Download className="h-3 w-3" />
                        {t('ticketDetail.download')}
                      </button>
                    </div>
                  ) : (
                    <div style={!isRight || isInternal ? { color: 'var(--text-primary)' } : {}}>{item.message}</div>
                  )}

                  <div className={`text-[11px] mt-1 ${isRight && !isInternal ? 'text-white/60' : ''}`} style={!isRight || isInternal ? { color: 'var(--text-tertiary)' } : {}}>
                    {formatShortDate(item.createdAt)}
                  </div>
                </div>
              );
            })}
            <div ref={chatEndRef} />
          </div>

          {/* Comment input area */}
          {ticket.status !== 'CLOSED' && !(isCustomer && ticket.status === 'RESOLVED') && (
            <div className="border-t px-5 py-4" style={{ borderColor: 'var(--border-color)' }}>
              {isAgent && (
                <div className="flex gap-2 mb-3">
                  <button
                    className={`rounded-full px-3.5 py-1.5 text-xs font-semibold border transition-colors cursor-pointer ${
                      commentType === 'EXTERNAL'
                        ? 'bg-primary-500 text-white border-primary-500'
                        : ''
                    }`}
                    style={commentType !== 'EXTERNAL' ? { borderColor: 'var(--border-color)', color: 'var(--text-secondary)', backgroundColor: 'transparent' } : {}}
                    onClick={() => setCommentType('EXTERNAL')}
                  >
                    {t('ticketDetail.replyToCustomer')}
                  </button>
                  <button
                    className="rounded-full px-3.5 py-1.5 text-xs font-semibold border transition-colors cursor-pointer"
                    style={
                      commentType === 'INTERNAL'
                        ? { backgroundColor: isDark ? 'rgba(245,158,11,0.2)' : '#fef3c7', color: isDark ? '#fde68a' : '#92400e', borderColor: isDark ? 'rgba(245,158,11,0.3)' : '#fcd34d' }
                        : { borderColor: 'var(--border-color)', color: 'var(--text-secondary)', backgroundColor: 'transparent' }
                    }
                    onClick={() => setCommentType('INTERNAL')}
                  >
                    {t('ticketDetail.internalNote')}
                  </button>
                </div>
              )}
              <div className="flex items-center gap-2">
                <input
                  type="text"
                  placeholder={t('ticketDetail.messagePlaceholder')}
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                  onKeyDown={handleKeyDown}
                  disabled={sending || cooldown > 0}
                  className="flex-1 rounded-lg border px-3 py-2.5 text-sm outline-none transition-all focus:ring-2"
                  style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
                />
                <input
                  ref={fileInputRef}
                  type="file"
                  style={{ display: 'none' }}
                  onChange={(e) => handleFileUpload(e.target.files?.[0])}
                />
                <button
                  className="flex h-10 w-10 items-center justify-center rounded-lg border transition-colors cursor-pointer"
                  style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)', backgroundColor: 'transparent' }}
                  title="Attach File"
                  onClick={() => fileInputRef.current?.click()}
                  disabled={uploading}
                  type="button"
                >
                  {uploading ? (
                    <div className="h-4 w-4 rounded-full border-2 animate-spin" style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
                  ) : (
                    <Paperclip className="h-4 w-4" />
                  )}
                </button>
                <button
                  className="flex h-10 items-center gap-2 rounded-lg px-4 text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
                  onClick={handleSendComment}
                  disabled={sending || !message.trim() || cooldown > 0}
                >
                  <Send className="h-4 w-4" />
                  {cooldown > 0 ? `${cooldown}s` : t('ticketDetail.send')}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Right column */}
      <div className="flex flex-col gap-4">
        {/* Status actions card */}
        {isAgent && allowedStatuses.length > 0 && (
          <div className="rounded-xl border" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
            <div className="px-5 py-3 border-b text-sm font-semibold" style={{ borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}>
              {t('ticketDetail.statusActions')}
            </div>
            <div className="p-4 space-y-2">
              {(() => {
                const currentUserId = user?.sub || user?.id;
                const hasClaimed = ticket?.claimers?.some((c) => c.agentId === currentUserId);
                const canDoStatusActions = !isAgentAdmin || hasClaimed;
                return canDoStatusActions ? (
                  <div className="flex gap-2">
                    {(allowedStatuses.includes('WAITING_FOR_CUSTOMER') || ticket.status === 'WAITING_FOR_CUSTOMER') && (
                      <button
                        className={`flex-1 rounded-lg px-3 py-2 text-xs font-semibold transition-colors cursor-pointer ${
                          ticket.status === 'WAITING_FOR_CUSTOMER'
                            ? 'bg-primary-500 text-white'
                            : 'border'
                        }`}
                        style={ticket.status !== 'WAITING_FOR_CUSTOMER' ? { borderColor: 'var(--border-color)', color: 'var(--text-secondary)' } : {}}
                        onClick={() => handleStatusChange(ticket.status === 'WAITING_FOR_CUSTOMER' ? 'IN_PROGRESS' : 'WAITING_FOR_CUSTOMER')}
                      >
                        {ticket.status === 'WAITING_FOR_CUSTOMER' ? t('ticketDetail.resume') : t('ticketDetail.waiting')}
                      </button>
                    )}
                    {(allowedStatuses.includes('RESOLVED') || ticket.status === 'RESOLVED') && (
                      <button
                        className={`flex-1 rounded-lg px-3 py-2 text-xs font-semibold transition-colors cursor-pointer ${
                          ticket.status === 'RESOLVED'
                            ? 'bg-danger-500 text-white hover:bg-danger-600'
                            : 'bg-accent-500 text-white hover:bg-accent-600'
                        }`}
                        onClick={() => ticket.status === 'RESOLVED' ? handleStatusChange('IN_PROGRESS') : handleResolveClick()}
                      >
                        {ticket.status === 'RESOLVED' ? t('ticketDetail.reopen') : t('ticketDetail.resolve')}
                      </button>
                    )}
                  </div>
                ) : null;
              })()}
              {(() => {
                const currentUserId = user?.sub || user?.id;
                const hasClaimed = ticket?.claimers?.some((c) => c.agentId === currentUserId);
                const noClaimer = !ticket?.claimers || ticket.claimers.length === 0;
                if (hasClaimed || ticket?.status === 'CLOSED') return null;
                return (
                  <button
                    className={`w-full rounded-lg px-3 py-2 text-xs font-semibold text-white transition-colors cursor-pointer ${
                      noClaimer
                        ? 'bg-primary-500 hover:bg-primary-600'
                        : 'bg-accent-500 hover:bg-accent-600'
                    }`}
                    onClick={handleClaim}
                  >
                    {noClaimer ? t('ticketDetail.claim') : t('ticketDetail.join')}
                  </button>
                );
              })()}
              {isAgentAdmin && ticket.status !== 'CLOSED' && (
                <button
                  className="w-full rounded-lg px-3 py-2 text-xs font-semibold text-white bg-amber-500 hover:bg-amber-600 transition-colors cursor-pointer"
                  onClick={() => setAssignModal(true)}
                >
                  {t('ticketDetail.assignToAgent')}
                </button>
              )}
              {(() => {
                const currentUserId = user?.sub || user?.id;
                const hasClaimed = ticket?.claimers?.some((c) => c.agentId === currentUserId);
                const hasUnclaim = (allowedStatuses.includes('NEW') || ticket?.status === 'WAITING_FOR_CUSTOMER')
                  && hasClaimed;
                const hasClose = allowedStatuses.includes('CLOSED')
                  && (!isAgentAdmin || hasClaimed);
                const hasExtraContent = hasUnclaim || hasClose;
                if (!hasExtraContent) return null;
                return (
                  <button
                    className="w-full rounded-lg border px-3 py-2 text-xs font-medium transition-colors cursor-pointer flex items-center justify-center gap-1.5"
                    style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                    onClick={() => setExtraActionsOpen(true)}
                  >
                    <Settings2 className="h-3.5 w-3.5" />
                    {t('ticketDetail.extraActions')}
                  </button>
                );
              })()}
            </div>
          </div>
        )}

        {/* Ticket details card */}
        <div className="rounded-xl border" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
          <div className="px-5 py-3 border-b text-sm font-semibold" style={{ borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}>
            {t('ticketDetail.ticketDetails')}
          </div>
          <div className="p-5 space-y-4">
            <DetailRow label={t('ticketDetail.created')} value={formatDate(ticket.createdAt)} />
            {!isCustomer && (
              <div>
                <div className="text-xs font-medium mb-1" style={{ color: 'var(--text-tertiary)' }}>{t('ticketDetail.claimers')}</div>
                {ticket.claimers && ticket.claimers.length > 0 ? (
                  <div className="flex flex-wrap gap-1">
                    {ticket.claimers.map((c) => (
                      <span
                        key={c.agentId}
                        className="inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium"
                        style={{ backgroundColor: 'var(--bg-surface-secondary)', color: 'var(--text-secondary)' }}
                      >
                        {c.agentName}
                      </span>
                    ))}
                  </div>
                ) : (
                  <span className="text-sm" style={{ color: 'var(--text-tertiary)' }}>{t('ticketDetail.unassigned')}</span>
                )}
              </div>
            )}
            <DetailRow label={t('ticketDetail.statusLabel')} value={statusLabel(ticket.status)} />
            <div>
              <div className="text-xs font-medium mb-1" style={{ color: 'var(--text-tertiary)' }}>{t('ticketDetail.priority')}</div>
              <PriorityBadge priority={ticket.priority} />
            </div>

            {/* SLA remaining */}
            {slaInfo && (
              <div>
                <div className="text-xs font-medium mb-1" style={{ color: 'var(--text-tertiary)' }}>{t('ticketDetail.slaRemaining')}</div>
                <div>
                  {(() => {
                      const slaState = slaInfo.slaState;

                      if (slaState === 'completed') {
                        return <span className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold" style={{ backgroundColor: isDark ? 'rgba(100,116,139,0.3)' : '#f1f5f9', color: isDark ? '#cbd5e1' : '#475569' }}>{t('ticketDetail.slaCompleted')}</span>;
                      }

                      if (slaState === 'expired') {
                        return <span className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-bold animate-pulse-subtle" style={{ backgroundColor: isDark ? 'rgba(239,68,68,0.2)' : '#fee2e2', color: isDark ? '#fca5a5' : '#991b1b' }}><AlertTriangle className="h-3 w-3 mr-1" />{t('ticketDetail.slaExpired')}</span>;
                      }

                      if (slaState === 'paused') {
                        return <span className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold" style={{ backgroundColor: isDark ? 'rgba(100,116,139,0.3)' : '#f1f5f9', color: isDark ? '#cbd5e1' : '#475569' }}>{formatSlaTime(slaInfo.remainingMs)} ({t('ticketDetail.slaPaused')})</span>;
                      }

                      // slaState === 'active' — client-side countdown
                      const elapsedSinceFetch = currentDate - slaInfo.fetchTime;
                      const diff = slaInfo.remainingMs - elapsedSinceFetch;

                      if (diff <= 0) return <span className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-bold animate-pulse-subtle" style={{ backgroundColor: isDark ? 'rgba(239,68,68,0.2)' : '#fee2e2', color: isDark ? '#fca5a5' : '#991b1b' }}><AlertTriangle className="h-3 w-3 mr-1" />{t('ticketDetail.slaExpired')}</span>;
                      const totalMins = Math.floor(diff / 60000);
                      let badgeStyle = { backgroundColor: isDark ? 'rgba(34,197,94,0.2)' : '#dcfce7', color: isDark ? '#86efac' : '#166534' };
                      let extraCls = '';
                      if (totalMins < 1) { badgeStyle = { backgroundColor: isDark ? 'rgba(239,68,68,0.2)' : '#fee2e2', color: isDark ? '#fca5a5' : '#991b1b' }; extraCls = 'animate-pulse-subtle font-bold'; }
                      else if (totalMins < 2) { badgeStyle = { backgroundColor: isDark ? 'rgba(245,158,11,0.2)' : '#fef3c7', color: isDark ? '#fde68a' : '#92400e' }; }
                      return <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${extraCls}`} style={badgeStyle}>{formatSlaTime(diff)}</span>;
                  })()}
                </div>
              </div>
            )}

            {ticket.resolvedAt && <DetailRow label={t('ticketDetail.resolvedAt')} value={formatDate(ticket.resolvedAt)} />}
            {ticket.closedAt && <DetailRow label={t('ticketDetail.closedAt')} value={formatDate(ticket.closedAt)} />}
            {resolutionNote && (
              <div>
                <div className="text-xs font-medium mb-1 flex items-center gap-1" style={{ color: 'var(--text-tertiary)' }}>
                  <CheckCircle2 className="h-3 w-3 text-accent-500" />
                  {t('ticketDetail.resolutionNote')}
                </div>
                <div className="text-xs leading-relaxed whitespace-pre-wrap" style={{ color: 'var(--text-secondary)' }}>
                  {resolutionNote.note}
                </div>
                <div className="text-[11px] mt-1" style={{ color: 'var(--text-tertiary)' }}>
                  {resolutionNote.agentId} · {formatShortDate(resolutionNote.updatedAt || resolutionNote.createdAt)}
                </div>
              </div>
            )}

            {!isCustomer && (
            <div className="pt-2 border-t" style={{ borderColor: 'var(--border-color)' }}>
              <button
                className="w-full flex items-center justify-between mb-2 cursor-pointer group"
                onClick={() => setAuditHistoryExpanded((v) => !v)}
              >
                <div className="text-xs font-medium flex items-center gap-1" style={{ color: 'var(--text-tertiary)' }}>
                  <Clock className="h-3 w-3" />
                  {t('ticketDetail.auditHistory')}
                  {auditLogs.length > 0 && (
                    <span
                      className="ml-1 inline-flex items-center rounded-full px-1.5 py-0.5 text-[10px] font-bold"
                      style={{ backgroundColor: 'var(--bg-surface-secondary)', color: 'var(--text-tertiary)' }}
                    >
                      {auditLogs.length}
                    </span>
                  )}
                </div>
                {auditHistoryExpanded
                  ? <ChevronUp className="h-3.5 w-3.5 shrink-0" style={{ color: 'var(--text-tertiary)' }} />
                  : <ChevronDown className="h-3.5 w-3.5 shrink-0" style={{ color: 'var(--text-tertiary)' }} />
                }
              </button>
              {auditHistoryExpanded && (
                <>
                  {auditLogs.length > 0 ? (
                    <div className="space-y-2">
                      {auditLogs.map((entry) => (
                        <div key={entry.id} className="rounded-lg border px-3 py-2.5" style={{ borderColor: 'var(--border-color-light)', backgroundColor: 'var(--bg-surface-secondary)' }}>
                          <div className="flex items-start justify-between gap-3">
                            <div className="min-w-0">
                              <div className="flex items-center gap-2 flex-wrap">
                                <span className="inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-bold" style={getAuditActionStyles(entry.actionType)}>
                                  {getAuditActionLabel(entry.actionType)}
                                </span>
                                <span className="text-xs font-semibold" style={{ color: 'var(--text-primary)' }}>
                                  {entry.actorName || entry.actorId || 'Unknown actor'}
                                </span>
                              </div>
                              <div className="text-xs mt-1 leading-relaxed whitespace-pre-wrap" style={{ color: 'var(--text-secondary)' }}>
                                {entry.note || t('ticketDetail.noNote')}
                              </div>
                            </div>
                            <div className="text-[11px] shrink-0 text-right" style={{ color: 'var(--text-tertiary)' }}>
                              {formatShortDate(entry.createdAt)}
                            </div>
                          </div>
                          <div className="mt-2 flex items-center gap-2 text-[11px]" style={{ color: 'var(--text-tertiary)' }}>
                            <span>{entry.previousState || '—'}</span>
                            <span>→</span>
                            <span>{entry.newState || '—'}</span>
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="rounded-lg border border-dashed px-3 py-3 text-xs leading-relaxed" style={{ borderColor: 'var(--border-color)', color: 'var(--text-tertiary)' }}>
                      {t('ticketDetail.auditHistoryPlaceholder')}
                    </div>
                  )}
                </>
              )}
            </div>
            )}
          </div>
        </div>

        {/* Worklog card */}
        {isAgent && (
          <div className="rounded-xl border" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
            <div className="flex items-center justify-between px-5 py-3 border-b" style={{ borderColor: 'var(--border-color)' }}>
              <span className="text-sm font-semibold flex items-center gap-1.5" style={{ color: 'var(--text-primary)' }}>
                <Clock className="h-4 w-4" style={{ color: 'var(--text-tertiary)' }} />
                {t('ticketDetail.worklogs', { count: worklogs.length })}
              </span>
              {worklogs.length > 0 && (
                <span className="inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-bold bg-primary-100 text-primary-700 dark:bg-primary-500/20 dark:text-primary-300">
                  {t('ticketDetail.worklogTotal', { value: formatMinutes(worklogs.reduce((sum, w) => sum + w.minutes, 0)) })}
                </span>
              )}
            </div>
            <div className="p-4">
              {worklogs.length === 0 && !worklogFormOpen && (
                <div className="text-center py-3 text-xs" style={{ color: 'var(--text-tertiary)' }}>
                  {t('ticketDetail.noWorklogs')}
                </div>
              )}
              {worklogs.length > 0 && (
                <div className="space-y-2">
                  {worklogs.map((w) => (
                    <div key={w.id} className="rounded-lg border p-3 transition-colors" style={{ borderColor: 'var(--border-color-light)' }}
                      onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = 'var(--bg-surface-hover)')}
                      onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                    >
                      <div className="flex items-center justify-between">
                        <span className="text-sm font-bold text-primary-500">{formatMinutes(w.minutes)}</span>
                        <button
                          className="rounded p-1 transition-colors cursor-pointer hover:bg-danger-50 hover:text-danger-500"
                          style={{ color: 'var(--text-tertiary)' }}
                          title="Delete"
                          onClick={() => handleDeleteWorklog(w.id)}
                        >
                          <Trash2 className="h-3 w-3" />
                        </button>
                      </div>
                      {w.description && (
                        <div className="text-xs mt-1 leading-relaxed" style={{ color: 'var(--text-primary)' }}>{w.description}</div>
                      )}
                      <div className="text-[11px] mt-1" style={{ color: 'var(--text-tertiary)' }}>
                        {w.agentId} · {formatShortDate(w.createdAt)}
                      </div>
                    </div>
                  ))}
                </div>
              )}
              {ticket.status !== 'CLOSED' && (
                <>
                  {!worklogFormOpen ? (
                    <button
                      className={`w-full rounded-lg border px-3 py-2 text-xs font-medium transition-colors cursor-pointer flex items-center justify-center gap-1.5 ${worklogs.length > 0 ? 'mt-3' : ''}`}
                      style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                      onClick={() => setWorklogFormOpen(true)}
                    >
                      <Plus className="h-3 w-3" />
                      {t('ticketDetail.addWorklog')}
                    </button>
                  ) : (
                    <div className={`rounded-lg border p-3 space-y-2 ${worklogs.length > 0 ? 'mt-3' : ''}`} style={{ borderColor: 'var(--border-color)', backgroundColor: 'var(--bg-surface-secondary)' }}>
                      <div>
                        <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-secondary)' }}>{t('ticketDetail.worklogDuration')}</label>
                        <input
                          type="number"
                          min="1"
                          placeholder="e.g. 30"
                          value={worklogMinutes}
                          onChange={(e) => setWorklogMinutes(e.target.value)}
                          className="w-full rounded-lg border px-3 py-1.5 text-sm outline-none transition-all focus:ring-2"
                          style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
                        />
                      </div>
                      <div>
                        <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-secondary)' }}>{t('ticketDetail.worklogDescription')}</label>
                        <textarea
                          rows="2"
                          placeholder={t('ticketDetail.worklogPlaceholder')}
                          value={worklogDescription}
                          onChange={(e) => setWorklogDescription(e.target.value)}
                          className="w-full rounded-lg border px-3 py-1.5 text-sm outline-none transition-all focus:ring-2 resize-y min-h-[48px]"
                          style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
                        />
                      </div>
                      <div className="flex gap-2">
                        <button
                          className="flex-1 rounded-lg px-3 py-1.5 text-xs font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors disabled:opacity-50 cursor-pointer"
                          onClick={handleAddWorklog}
                          disabled={addingWorklog || !worklogMinutes || parseInt(worklogMinutes, 10) <= 0}
                        >
                          {addingWorklog ? t('ticketDetail.worklogSaving') : t('ticketDetail.worklogSave')}
                        </button>
                        <button
                          className="rounded-lg border px-3 py-1.5 text-xs font-medium transition-colors cursor-pointer"
                          style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                          onClick={() => { setWorklogFormOpen(false); setWorklogMinutes(''); setWorklogDescription(''); }}
                        >
                          {t('form.cancel')}
                        </button>
                      </div>
                    </div>
                  )}
                </>
              )}
            </div>
          </div>
        )}

        {/* AI Summary card — sadece AGENT ve AGENT_ADMIN görebilir */}
        {isAgent && (
          <div className="rounded-xl border" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
            {/* Kart başlığı */}
            <div className="flex items-center justify-between px-5 py-3 border-b" style={{ borderColor: 'var(--border-color)' }}>
              <span className="text-sm font-semibold flex items-center gap-1.5" style={{ color: 'var(--text-primary)' }}>
                <Sparkles className="h-4 w-4 text-violet-500" />
                {t('ticketDetail.aiSummaryTitle')}
              </span>
              <div className="flex items-center gap-1.5">
                {aiSummary && (
                  <button
                    className="flex h-6 w-6 items-center justify-center rounded transition-colors cursor-pointer"
                    style={{ color: 'var(--text-tertiary)' }}
                    onClick={() => setAiSummaryExpanded((v) => !v)}
                    title={aiSummaryExpanded ? t('ticketDetail.aiSummaryCollapse') : t('ticketDetail.aiSummaryExpand')}
                  >
                    {aiSummaryExpanded ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                  </button>
                )}
              </div>
            </div>

            <div className="p-4 space-y-3">
              {/* Özet içeriği */}
              {aiSummary && aiSummaryExpanded && (
                <div
                  className="rounded-lg p-3 text-xs leading-relaxed whitespace-pre-wrap"
                  style={{ backgroundColor: isDark ? 'rgba(139,92,246,0.08)' : '#f5f3ff', color: 'var(--text-primary)', borderLeft: '3px solid #8b5cf6' }}
                >
                  {aiSummary.summary}
                </div>
              )}

              {/* Meta bilgi */}
              {aiSummary && (
                <div className="flex items-center justify-between text-[11px]" style={{ color: 'var(--text-tertiary)' }}>
                  <span>{aiSummary.model}</span>
                  <span>{formatShortDate(aiSummary.createdAt)}</span>
                </div>
              )}

              {/* Hata mesajı */}
              {aiSummaryError && (
                <div
                  className="rounded-lg px-3 py-2 text-xs"
                  style={{ backgroundColor: isDark ? 'rgba(239,68,68,0.1)' : '#fee2e2', color: isDark ? '#fca5a5' : '#991b1b' }}
                >
                  {aiSummaryError}
                </div>
              )}

              {/* Özet yok placeholder */}
              {!aiSummary && !aiSummaryLoading && !aiSummaryError && (
                <div className="text-center py-2 text-xs" style={{ color: 'var(--text-tertiary)' }}>
                  {t('ticketDetail.aiSummaryEmpty')}
                </div>
              )}

              {/* Oluştur / Yenile butonu */}
              <button
                className="w-full rounded-lg px-3 py-2 text-xs font-semibold transition-colors cursor-pointer flex items-center justify-center gap-1.5 disabled:opacity-50 disabled:cursor-not-allowed"
                style={
                  aiSummaryLoading
                    ? { backgroundColor: 'var(--bg-surface-secondary)', color: 'var(--text-tertiary)', border: '1px solid var(--border-color)' }
                    : { background: 'linear-gradient(135deg, #7c3aed, #6d28d9)', color: '#fff' }
                }
                onClick={handleGenerateAiSummary}
                disabled={aiSummaryLoading}
              >
                {aiSummaryLoading ? (
                  <>
                    <div className="h-3 w-3 rounded-full border-2 animate-spin" style={{ borderColor: 'rgba(255,255,255,0.3)', borderTopColor: '#fff' }} />
                    {t('ticketDetail.aiSummaryGenerating')}
                  </>
                ) : (
                  <>
                    <Sparkles className="h-3 w-3" />
                    {aiSummary ? t('ticketDetail.aiSummaryRegenerate') : t('ticketDetail.aiSummaryGenerate')}
                  </>
                )}
              </button>
            </div>
          </div>
        )}

        {/* Customer CSAT prompt */}
        {isCustomer && ticket.status === 'RESOLVED' && (
          <div className="rounded-xl border" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
            <div className="px-5 py-3 border-b text-sm font-semibold" style={{ borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}>
              {t('ticketDetail.resolvedQuestion')}
            </div>
            <div className="p-4">
              <p className="text-xs mb-4 leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
                {t('ticketDetail.resolvedDesc')}
              </p>
              <div className="flex gap-2">
                <button
                  className="flex-1 rounded-lg px-3 py-2 text-xs font-semibold text-white bg-accent-500 hover:bg-accent-600 transition-colors cursor-pointer"
                  onClick={() => setCsatModalOpen(true)}
                >
                  {t('ticketDetail.yesResolved')}
                </button>
                <button
                  className="flex-1 rounded-lg px-3 py-2 text-xs font-semibold text-white bg-danger-500 hover:bg-danger-600 transition-colors cursor-pointer"
                  onClick={() => handleStatusChange('IN_PROGRESS')}
                >
                  {t('ticketDetail.noResolved')}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Extra actions modal */}
      {extraActionsOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in" style={{ backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)' }} onClick={() => setExtraActionsOpen(false)}>
          <div
            className="w-full max-w-sm rounded-xl border animate-slide-up"
            style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-xl)' }}
            onClick={e => e.stopPropagation()}
          >
            <div className="flex items-center justify-between px-6 py-4 border-b" style={{ borderColor: 'var(--border-color)' }}>
              <h3 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>{t('ticketDetail.extraActions')}</h3>
              <button onClick={() => setExtraActionsOpen(false)} className="flex h-8 w-8 items-center justify-center rounded-lg transition-colors cursor-pointer hover:bg-danger-50 hover:text-danger-500" style={{ color: 'var(--text-tertiary)' }}>
                <X className="h-5 w-5" />
              </button>
            </div>
            <div className="p-5 space-y-3">
              {allowedStatuses.includes('NEW') && ticket?.claimers?.some((c) => c.agentId === (user?.sub || user?.id)) && (
                <button
                  className="w-full rounded-lg border px-4 py-2.5 text-sm font-medium transition-colors cursor-pointer"
                  style={{ borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
                  onClick={() => openReasonModal('UNCLAIM')}
                >
                  {t('ticketDetail.unclaimRelease')}
                </button>
              )}
              {ticket?.status === 'WAITING_FOR_CUSTOMER' && ticket?.claimers?.some((c) => c.agentId === (user?.sub || user?.id)) && (
                <button
                  className="w-full rounded-lg border px-4 py-2.5 text-sm font-medium transition-colors cursor-pointer"
                  style={{ borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
                  onClick={() => openReasonModal('UNCLAIM')}
                >
                  {t('ticketDetail.unclaimRelease')}
                </button>
              )}
              {allowedStatuses.includes('CLOSED') && (!isAgentAdmin || ticket?.claimers?.some((c) => c.agentId === (user?.sub || user?.id))) && (
                <button
                  className="w-full rounded-lg px-4 py-2.5 text-sm font-semibold text-white bg-danger-500 hover:bg-danger-600 transition-colors cursor-pointer"
                  onClick={() => openReasonModal('CLOSE')}
                >
                  {t('ticketDetail.closeTicket')}
                </button>
              )}
              {!allowedStatuses.includes('NEW') && ticket?.status !== 'WAITING_FOR_CUSTOMER' && !allowedStatuses.includes('CLOSED') && (
                <div className="text-center py-4 text-sm" style={{ color: 'var(--text-tertiary)' }}>
                  {t('ticketDetail.noExtraActions')}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* CSAT modal */}
      {csatModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in" style={{ backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)' }} onClick={() => !submittingCsat && setCsatModalOpen(false)}>
          <div
            className="w-full max-w-md rounded-xl border animate-slide-up"
            style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-xl)' }}
            onClick={e => e.stopPropagation()}
          >
            <div className="flex items-center justify-between px-6 py-4 border-b" style={{ borderColor: 'var(--border-color)' }}>
              <h3 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>{t('ticketDetail.csatTitle')}</h3>
              <button onClick={() => !submittingCsat && setCsatModalOpen(false)} className="flex h-8 w-8 items-center justify-center rounded-lg transition-colors cursor-pointer hover:bg-danger-50 hover:text-danger-500" style={{ color: 'var(--text-tertiary)' }}>
                <X className="h-5 w-5" />
              </button>
            </div>
            <div className="px-6 py-5 space-y-4">
              <p className="text-sm" style={{ color: 'var(--text-secondary)' }}>{t('ticketDetail.csatQuestion')}</p>
              <div className="flex gap-2 justify-center py-2">
                {[1, 2, 3, 4, 5].map(star => (
                  <button
                    key={star}
                    type="button"
                    className="transition-all duration-200 hover:scale-110 cursor-pointer"
                    style={{ fontSize: '32px', background: 'none', border: 'none', outline: 'none', color: csatRating >= star ? '#f59e0b' : 'var(--text-tertiary)' }}
                    onClick={() => setCsatRating(star)}
                  >
                    <Star className="h-8 w-8" fill={csatRating >= star ? '#f59e0b' : 'none'} />
                  </button>
                ))}
              </div>
              <textarea
                placeholder={t('ticketDetail.csatPlaceholder')}
                rows="3"
                value={csatComment}
                onChange={e => setCsatComment(e.target.value)}
                className="w-full rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2 resize-y"
                style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
              />
            </div>
            <div className="flex justify-end gap-3 px-6 py-4 border-t" style={{ borderColor: 'var(--border-color)' }}>
              <button
                disabled={submittingCsat}
                onClick={() => setCsatModalOpen(false)}
                className="rounded-lg border px-4 py-2 text-sm font-semibold transition-colors cursor-pointer disabled:opacity-50"
                style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
              >
                {t('form.cancel')}
              </button>
              <button
                disabled={submittingCsat}
                onClick={handleSubmitCsat}
                className="rounded-lg px-4 py-2 text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors disabled:opacity-50 cursor-pointer"
              >
                {t('ticketDetail.csatSubmit')}
              </button>
            </div>
          </div>
        </div>
      )}

      <ActionReasonModal
        isOpen={reasonModal.isOpen}
        onClose={closeReasonModal}
        onConfirm={handleReasonConfirm}
        title={reasonModalConfig.title}
        description={reasonModalConfig.description}
        confirmLabel={reasonModalConfig.confirmLabel}
        confirmVariant={reasonModalConfig.confirmVariant}
      />

      <AgentSelectionModal
        isOpen={assignModal}
        onClose={() => setAssignModal(false)}
        onSuccess={handleAssignSuccess}
        ticketId={ticket?.id}
        productId={ticket?.productId}
      />

      {/* Resolve modal */}
      {resolveModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in" style={{ backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)' }} onClick={() => !savingResolutionNote && setResolveModalOpen(false)}>
          <div
            className="w-full max-w-md rounded-xl border animate-slide-up"
            style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-xl)' }}
            onClick={e => e.stopPropagation()}
          >
            <div className="flex items-center justify-between px-6 py-4 border-b" style={{ borderColor: 'var(--border-color)' }}>
              <h3 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
                {resolutionNote ? t('ticketDetail.resolveModalUpdateTitle') : t('ticketDetail.resolveModalTitle')}
              </h3>
              <button onClick={() => !savingResolutionNote && setResolveModalOpen(false)} className="flex h-8 w-8 items-center justify-center rounded-lg transition-colors cursor-pointer hover:bg-danger-50 hover:text-danger-500" style={{ color: 'var(--text-tertiary)' }}>
                <X className="h-5 w-5" />
              </button>
            </div>
            <div className="px-6 py-5 space-y-4">
              <p className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                {t('ticketDetail.resolveModalDesc')}
              </p>
              <textarea
                placeholder={t('ticketDetail.resolveModalPlaceholder')}
                rows="4"
                value={resolutionNoteText}
                onChange={(e) => setResolutionNoteText(e.target.value)}
                disabled={savingResolutionNote}
                className="w-full rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2 resize-y min-h-[100px]"
                style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
              />
            </div>
            <div className="flex justify-end gap-3 px-6 py-4 border-t" style={{ borderColor: 'var(--border-color)' }}>
              <button
                disabled={savingResolutionNote}
                onClick={() => setResolveModalOpen(false)}
                className="rounded-lg border px-4 py-2 text-sm font-semibold transition-colors cursor-pointer disabled:opacity-50"
                style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
              >
                {t('form.cancel')}
              </button>
              <button
                disabled={savingResolutionNote || !resolutionNoteText.trim()}
                onClick={handleSubmitResolve}
                className="rounded-lg px-4 py-2 text-sm font-semibold text-white bg-accent-500 hover:bg-accent-600 transition-colors disabled:opacity-50 cursor-pointer"
              >
                {savingResolutionNote ? t('ticketDetail.resolveModalSaving') : t('ticketDetail.resolveModalSave')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function DetailRow({ label, value }) {
  return (
    <div>
      <div className="text-xs font-medium mb-0.5" style={{ color: 'var(--text-tertiary)' }}>{label}</div>
      <div className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>{value}</div>
    </div>
  );
}
