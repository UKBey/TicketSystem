import { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { StatusBadge, PriorityBadge } from '../components/Badges';
import ActionReasonModal from '../components/ActionReasonModal';
import api from '../services/api';
import { closeTicket as closeTicketWithNote, unclaimTicket as unclaimTicketWithNote } from '../services/api';
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
} from 'lucide-react';

export default function TicketDetail() {
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

  // Dosya ekleri icin durum degiskenleri
  const [attachments, setAttachments] = useState([]);
  const [uploading, setUploading] = useState(false);
  const fileInputRef = useRef(null);

  // Worklog (is kaydi) icin durum degiskenleri
  const [worklogs, setWorklogs] = useState([]);
  const [worklogMinutes, setWorklogMinutes] = useState('');
  const [worklogDescription, setWorklogDescription] = useState('');
  const [addingWorklog, setAddingWorklog] = useState(false);
  const [worklogFormOpen, setWorklogFormOpen] = useState(false);

  // Resolution Note (cozum notu) icin durum degiskenleri
  const [resolutionNote, setResolutionNote] = useState(null);
  const [resolutionNoteText, setResolutionNoteText] = useState('');
  const [resolveModalOpen, setResolveModalOpen] = useState(false);
  const [savingResolutionNote, setSavingResolutionNote] = useState(false);

  // Mevcut durumdan gecilebilecek hedef durum listesi.
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

  // Bilete bagli dosya eklerini sunucudan ceker.
  const fetchAttachments = useCallback(async () => {
    try {
      const res = await api.get(`/tickets/${id}/attachments`);
      setAttachments(res.data);
    } catch (err) {
      console.error('Could not load attachments:', err);
    }
  }, [id]);

  // Cozum notunu sunucudan ceker (varsa).
  const fetchResolutionNote = useCallback(async () => {
    try {
      const res = await api.get(`/tickets/${id}/resolution-note`);
      setResolutionNote(res.data);
      setResolutionNoteText(res.data.note || '');
    } catch {
      // 404 veya 403 donebilir, sessizce atla.
      setResolutionNote(null);
    }
  }, [id]);

  // Worklog listeleme
  const fetchWorklogs = useCallback(async () => {
    try {
      const res = await api.get(`/tickets/${id}/worklogs`);
      setWorklogs(res.data);
    } catch (err) {
      // CUSTOMER rolunde 403 donecektir, sessizce atla.
      console.debug('Could not load worklogs:', err);
    }
  }, [id]);

  useEffect(() => {
    fetchTicket();
    fetchComments();
    fetchAttachments();
    fetchWorklogs();
    fetchResolutionNote();
  }, [id, fetchTicket, fetchComments, fetchAttachments, fetchWorklogs, fetchResolutionNote]);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [comments]);

  useEffect(() => {
    if (ticket) {
      const fetchSla = async () => {
         try {
           const res = await api.get(`/tickets/${id}/sla-timer`);
           const data = res.data;
           data.fetchTime = Date.now(); // Kalan sure degerinin alindigi an istemci saatine gore kaydedilir.
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

  // Yorum ve dosya eklerini createdAt'e gore birlestirip kronolojik siralar.
  const timeline = useMemo(() => {
    const items = [
      ...comments.map((c) => ({ ...c, _type: 'comment' })),
      ...attachments.map((a) => ({ ...a, _type: 'attachment' })),
    ];
    items.sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
    return items;
  }, [comments, attachments]);

  // Secilen dosyayi multipart/form-data olarak bilete yukler.
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

  // Resolved butonuna tiklaninca modal acar; mevcut notu onceden doldurur.
  const handleResolveClick = () => {
    if (resolutionNote) {
      setResolutionNoteText(resolutionNote.note);
    } else {
      setResolutionNoteText('');
    }
    setResolveModalOpen(true);
  };

  // Modal'dan cozum notunu kaydeder/gunceller, ardindan RESOLVED durumuna gecer.
  const handleSubmitResolve = async () => {
    if (!resolutionNoteText.trim()) return;
    setSavingResolutionNote(true);
    try {
      // Not varsa guncelle, yoksa olustur.
      if (resolutionNote) {
        await api.put(`/tickets/${id}/resolution-note`, { note: resolutionNoteText.trim() });
      } else {
        await api.post(`/tickets/${id}/resolution-note`, { note: resolutionNoteText.trim() });
      }
      // Ardindan durum gecisini yap.
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

  // Dosya indirme: icerik blob olarak alinir ve tarayici indirme tetiklenir.
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

  // Yeni worklog ekleme
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

  // Worklog silme
  const handleDeleteWorklog = async (worklogId) => {
    if (!confirm('Are you sure you want to delete this work log?')) return;
    try {
      await api.delete(`/tickets/${id}/worklogs/${worklogId}`);
      setWorklogs((prev) => prev.filter((w) => w.id !== worklogId));
    } catch (err) {
      alert(err.response?.data?.message || 'Could not delete worklog.');
    }
  };

  // Dakikayi okunabilir saat:dakika formatina cevirir.
  const formatMinutes = (mins) => {
    if (!mins) return '0m';
    const h = Math.floor(mins / 60);
    const m = mins % 60;
    if (h === 0) return `${m}m`;
    if (m === 0) return `${h}h`;
    return `${h}h ${m}m`;
  };

  // Dosya tipine gore ikon belirler.
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
      // Yorum sonrasi olasi otomatik statu degisimlerini yansitmak icin bilet yeniden cekilir.
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

  const reasonModalConfig = reasonModal.action === 'CLOSE'
    ? {
        title: 'Close Ticket',
        description: 'Please provide a note before closing this ticket.',
        confirmLabel: 'Close Ticket',
        confirmVariant: 'danger',
      }
    : {
        title: 'Release Ticket',
        description: 'Please provide a note before releasing this ticket.',
        confirmLabel: 'Release Ticket',
        confirmVariant: 'warning',
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
      NEW: 'New',
      IN_PROGRESS: 'In Progress',
      WAITING_FOR_CUSTOMER: 'Waiting for Customer Response',
      RESOLVED: 'Resolved',
      CLOSED: 'Closed',
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
        <h3 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>Ticket not found</h3>
      </div>
    );
  }

  const ticketCode = `TCK-${String(ticket.id).padStart(3, '0')}`;
  const allowedStatuses = STATUS_OPTIONS[ticket.status] || [];
  const isAgent = hasRole('AGENT') || hasRole('AGENT_ADMIN');
  const isCustomer = hasRole('CUSTOMER');
  const auditLogs = Array.isArray(ticket.auditLogs)
    ? ticket.auditLogs
    : Array.isArray(ticket.ticketAuditLogs)
      ? ticket.ticketAuditLogs
      : [];

  const getAuditActionLabel = (actionType) => {
    const labels = {
      UNCLAIM: 'Released',
      CLOSE: 'Closed',
      CLAIM: 'Claimed',
    };
    return labels[actionType] || actionType || 'Updated';
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
      {/* Sol kolon: baslik, yorum akisi ve mesaj giris alani. */}
      <div className="flex flex-col gap-5 min-w-0">
        {/* Onceki ekrana donus baglantisi. */}
        <button
          onClick={() => navigate(-1)}
          className="inline-flex items-center gap-1.5 text-sm font-medium transition-colors hover:text-primary-500 cursor-pointer self-start"
          style={{ color: 'var(--text-secondary)' }}
        >
          <ArrowLeft className="h-4 w-4" />
          Back
        </button>

        {/* Bilet kimligi, oncelik ve temel meta bilgileri. */}
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

        {/* Yorum ve dosya eklerinin kronolojik olarak gosterildigi sohbet alani. */}
        <div className="rounded-xl border overflow-hidden" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
          <div className="flex flex-col gap-4 p-5 min-h-[300px] max-h-[500px] overflow-y-auto">
            {timeline.length === 0 && (
              <div className="flex items-center justify-center py-8" style={{ color: 'var(--text-tertiary)' }}>
                <p className="text-sm">No comments or attachments yet.</p>
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
                        Internal
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
                        Download
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

          {/* Bilet acik oldugu surece yeni yorum gonderim alani. */}
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
                    Reply to Customer
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
                    Internal Note
                  </button>
                </div>
              )}
              <div className="flex items-center gap-2">
                <input
                  type="text"
                  placeholder="Type your message..."
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
                  {cooldown > 0 ? `${cooldown}s` : 'Send'}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Sag kolon: statu aksiyonlari ve detay kartlari. */}
      <div className="flex flex-col gap-4">
        {/* Agent/agent_admin icin durum gecisi butonlari. */}
        {isAgent && allowedStatuses.length > 0 && (
          <div className="rounded-xl border" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
            <div className="px-5 py-3 border-b text-sm font-semibold" style={{ borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}>
              Status Actions
            </div>
            <div className="p-4 space-y-2">
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
                    {ticket.status === 'WAITING_FOR_CUSTOMER' ? 'Resume' : 'Waiting'}
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
                    {ticket.status === 'RESOLVED' ? 'Reopen' : 'Resolve'}
                  </button>
                )}
              </div>
              <button
                className="w-full rounded-lg border px-3 py-2 text-xs font-medium transition-colors cursor-pointer flex items-center justify-center gap-1.5"
                style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                onClick={() => setExtraActionsOpen(true)}
              >
                <Settings2 className="h-3.5 w-3.5" />
                Extra Actions
              </button>
            </div>
          </div>
        )}

        {/* Biletin tarih, atama ve durum detaylari. */}
        <div className="rounded-xl border" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
          <div className="px-5 py-3 border-b text-sm font-semibold" style={{ borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}>
            Ticket Details
          </div>
          <div className="p-5 space-y-4">
            <DetailRow label="Created" value={formatDate(ticket.createdAt)} />
            {!isCustomer && (
              <div>
                <div className="text-xs font-medium mb-1" style={{ color: 'var(--text-tertiary)' }}>Claimers</div>
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
                  <span className="text-sm" style={{ color: 'var(--text-tertiary)' }}>Unassigned</span>
                )}
              </div>
            )}
            <DetailRow label="Status" value={statusLabel(ticket.status)} />
            <div>
              <div className="text-xs font-medium mb-1" style={{ color: 'var(--text-tertiary)' }}>Priority</div>
              <PriorityBadge priority={ticket.priority} />
            </div>

            {/* SLA kalan sure bilgisini anlik olarak gosterir. */}
            {slaInfo && (
              <div>
                <div className="text-xs font-medium mb-1" style={{ color: 'var(--text-tertiary)' }}>SLA Remaining</div>
                <div>
                  {(() => {
                      if (slaInfo.deadlineTimestamp === -1) {
                         if (slaInfo.remainingMs <= 0 && ticket.slaBreached) return <span className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-bold animate-pulse-subtle" style={{ backgroundColor: isDark ? 'rgba(239,68,68,0.2)' : '#fee2e2', color: isDark ? '#fca5a5' : '#991b1b' }}><AlertTriangle className="h-3 w-3 mr-1" />Expired</span>;
                         if (slaInfo.remainingMs > 0) {
                             const diff = slaInfo.remainingMs;
                             const mins = Math.floor(diff / 60000);
                             const secs = Math.floor((diff % 60000) / 1000);
                             return <span className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold" style={{ backgroundColor: isDark ? 'rgba(100,116,139,0.3)' : '#f1f5f9', color: isDark ? '#cbd5e1' : '#475569' }}>{mins}m {secs}s (Paused)</span>;
                         }
                         return <span className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold" style={{ backgroundColor: isDark ? 'rgba(100,116,139,0.3)' : '#f1f5f9', color: isDark ? '#cbd5e1' : '#475569' }}>Completed</span>;
                      }

                      // Kalan sureyi fetch anina gore hesaplayarak istemci/sunucu saat farkini tolere eder.
                      let diff = slaInfo.remainingMs;
                      if (slaInfo.deadlineTimestamp !== -1) {
                          const elapsedSinceFetch = currentDate - slaInfo.fetchTime;
                          diff = slaInfo.remainingMs - elapsedSinceFetch;
                      }
                      
                      if (diff <= 0) return <span className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-bold animate-pulse-subtle" style={{ backgroundColor: isDark ? 'rgba(239,68,68,0.2)' : '#fee2e2', color: isDark ? '#fca5a5' : '#991b1b' }}><AlertTriangle className="h-3 w-3 mr-1" />Expired</span>;
                      const mins = Math.floor(diff / 60000);
                      const secs = Math.floor((diff % 60000) / 1000);
                      let badgeStyle = { backgroundColor: isDark ? 'rgba(34,197,94,0.2)' : '#dcfce7', color: isDark ? '#86efac' : '#166534' };
                      let extraCls = '';
                      if (mins < 1) { badgeStyle = { backgroundColor: isDark ? 'rgba(239,68,68,0.2)' : '#fee2e2', color: isDark ? '#fca5a5' : '#991b1b' }; extraCls = 'animate-pulse-subtle font-bold'; }
                      else if (mins < 2) { badgeStyle = { backgroundColor: isDark ? 'rgba(245,158,11,0.2)' : '#fef3c7', color: isDark ? '#fde68a' : '#92400e' }; }
                      return <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${extraCls}`} style={badgeStyle}>{mins}m {secs}s</span>;
                  })()}
                </div>
              </div>
            )}

            {ticket.resolvedAt && <DetailRow label="Resolved At" value={formatDate(ticket.resolvedAt)} />}
            {ticket.closedAt && <DetailRow label="Closed At" value={formatDate(ticket.closedAt)} />}
            {resolutionNote && (
              <div>
                <div className="text-xs font-medium mb-1 flex items-center gap-1" style={{ color: 'var(--text-tertiary)' }}>
                  <CheckCircle2 className="h-3 w-3 text-accent-500" />
                  Resolution Note
                </div>
                <div className="text-xs leading-relaxed whitespace-pre-wrap" style={{ color: 'var(--text-secondary)' }}>
                  {resolutionNote.note}
                </div>
                <div className="text-[11px] mt-1" style={{ color: 'var(--text-tertiary)' }}>
                  {resolutionNote.agentId} · {formatShortDate(resolutionNote.updatedAt || resolutionNote.createdAt)}
                </div>
              </div>
            )}

            <div className="pt-2 border-t" style={{ borderColor: 'var(--border-color)' }}>
              <div className="text-xs font-medium mb-2 flex items-center gap-1" style={{ color: 'var(--text-tertiary)' }}>
                <Clock className="h-3 w-3" />
                Audit History
              </div>
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
                              {entry.actorId || 'Unknown actor'}
                            </span>
                          </div>
                          <div className="text-xs mt-1 leading-relaxed whitespace-pre-wrap" style={{ color: 'var(--text-secondary)' }}>
                            {entry.note || 'No note provided.'}
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
                  Audit history will appear here once the backend includes ticket audit log entries.
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Agent/Manager icin worklog (is kaydi) yonetim karti. */}
        {isAgent && (
          <div className="rounded-xl border" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
            <div className="flex items-center justify-between px-5 py-3 border-b" style={{ borderColor: 'var(--border-color)' }}>
              <span className="text-sm font-semibold flex items-center gap-1.5" style={{ color: 'var(--text-primary)' }}>
                <Clock className="h-4 w-4" style={{ color: 'var(--text-tertiary)' }} />
                Worklogs ({worklogs.length})
              </span>
              {worklogs.length > 0 && (
                <span className="inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-bold bg-primary-100 text-primary-700 dark:bg-primary-500/20 dark:text-primary-300">
                  Total: {formatMinutes(worklogs.reduce((sum, w) => sum + w.minutes, 0))}
                </span>
              )}
            </div>
            <div className="p-4">
              {worklogs.length === 0 && !worklogFormOpen && (
                <div className="text-center py-3 text-xs" style={{ color: 'var(--text-tertiary)' }}>
                  No work logs yet.
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
                      Add Work Log
                    </button>
                  ) : (
                    <div className={`rounded-lg border p-3 space-y-2 ${worklogs.length > 0 ? 'mt-3' : ''}`} style={{ borderColor: 'var(--border-color)', backgroundColor: 'var(--bg-surface-secondary)' }}>
                      <div>
                        <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-secondary)' }}>Duration (minutes) *</label>
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
                        <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-secondary)' }}>Description</label>
                        <textarea
                          rows="2"
                          placeholder="Brief description of work done..."
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
                          {addingWorklog ? 'Saving...' : 'Save'}
                        </button>
                        <button
                          className="rounded-lg border px-3 py-1.5 text-xs font-medium transition-colors cursor-pointer"
                          style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                          onClick={() => { setWorklogFormOpen(false); setWorklogMinutes(''); setWorklogDescription(''); }}
                        >
                          Cancel
                        </button>
                      </div>
                    </div>
                  )}
                </>
              )}
            </div>
          </div>
        )}

        {/* Musterinin cozum onayi ve CSAT akis girisi. */}
        {isCustomer && ticket.status === 'RESOLVED' && (
          <div className="rounded-xl border" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
            <div className="px-5 py-3 border-b text-sm font-semibold" style={{ borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}>
              Was your issue resolved?
            </div>
            <div className="p-4">
              <p className="text-xs mb-4 leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
                Your ticket has been marked as resolved. You can confirm and provide feedback, or reopen if the issue persists.
              </p>
              <div className="flex gap-2">
                <button
                  className="flex-1 rounded-lg px-3 py-2 text-xs font-semibold text-white bg-accent-500 hover:bg-accent-600 transition-colors cursor-pointer"
                  onClick={() => setCsatModalOpen(true)}
                >
                  Yes, Resolved
                </button>
                <button
                  className="flex-1 rounded-lg px-3 py-2 text-xs font-semibold text-white bg-danger-500 hover:bg-danger-600 transition-colors cursor-pointer"
                  onClick={() => handleStatusChange('IN_PROGRESS')}
                >
                  No, Not Resolved
                </button>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Ikincil durum aksiyonlarini acan modal pencere. */}
      {extraActionsOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in" style={{ backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)' }} onClick={() => setExtraActionsOpen(false)}>
          <div
            className="w-full max-w-sm rounded-xl border animate-slide-up"
            style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-xl)' }}
            onClick={e => e.stopPropagation()}
          >
            <div className="flex items-center justify-between px-6 py-4 border-b" style={{ borderColor: 'var(--border-color)' }}>
              <h3 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>Extra Actions</h3>
              <button onClick={() => setExtraActionsOpen(false)} className="flex h-8 w-8 items-center justify-center rounded-lg transition-colors cursor-pointer hover:bg-danger-50 hover:text-danger-500" style={{ color: 'var(--text-tertiary)' }}>
                <X className="h-5 w-5" />
              </button>
            </div>
            <div className="p-5 space-y-3">
              {allowedStatuses.includes('NEW') && (
                <button
                  className="w-full rounded-lg border px-4 py-2.5 text-sm font-medium transition-colors cursor-pointer"
                  style={{ borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
                  onClick={() => { handleStatusChange('NEW'); setExtraActionsOpen(false); }}
                >
                  Unclaim (Release)
                </button>
              )}
              {ticket?.status === 'WAITING_FOR_CUSTOMER' && ticket?.claimers?.some((c) => c.agentId === (user?.sub || user?.id)) && (
                <button
                  className="w-full rounded-lg border px-4 py-2.5 text-sm font-medium transition-colors cursor-pointer"
                  style={{ borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
                  onClick={() => openReasonModal('UNCLAIM')}
                >
                  Unclaim (Release)
                </button>
              )}
              {allowedStatuses.includes('CLOSED') && (
                <button
                  className="w-full rounded-lg px-4 py-2.5 text-sm font-semibold text-white bg-danger-500 hover:bg-danger-600 transition-colors cursor-pointer"
                  onClick={() => openReasonModal('CLOSE')}
                >
                  Close Ticket
                </button>
              )}
              {!allowedStatuses.includes('NEW') && ticket?.status !== 'WAITING_FOR_CUSTOMER' && !allowedStatuses.includes('CLOSED') && (
                <div className="text-center py-4 text-sm" style={{ color: 'var(--text-tertiary)' }}>
                  No extra actions available for current status.
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Memnuniyet puani ve yorumunun girildigi CSAT modal'i. */}
      {csatModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in" style={{ backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)' }} onClick={() => !submittingCsat && setCsatModalOpen(false)}>
          <div
            className="w-full max-w-md rounded-xl border animate-slide-up"
            style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-xl)' }}
            onClick={e => e.stopPropagation()}
          >
            <div className="flex items-center justify-between px-6 py-4 border-b" style={{ borderColor: 'var(--border-color)' }}>
              <h3 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>Customer Satisfaction Survey</h3>
              <button onClick={() => !submittingCsat && setCsatModalOpen(false)} className="flex h-8 w-8 items-center justify-center rounded-lg transition-colors cursor-pointer hover:bg-danger-50 hover:text-danger-500" style={{ color: 'var(--text-tertiary)' }}>
                <X className="h-5 w-5" />
              </button>
            </div>
            <div className="px-6 py-5 space-y-4">
              <p className="text-sm" style={{ color: 'var(--text-secondary)' }}>How would you rate the resolution process from 1 to 5?</p>
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
                placeholder="Any additional notes (optional)..."
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
                Cancel
              </button>
              <button
                disabled={submittingCsat}
                onClick={handleSubmitCsat}
                className="rounded-lg px-4 py-2 text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors disabled:opacity-50 cursor-pointer"
              >
                Submit & Close
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

      {/* Cozum notu girisi icin modal. Resolved butonuna tiklaninca acilir. */}
      {resolveModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in" style={{ backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)' }} onClick={() => !savingResolutionNote && setResolveModalOpen(false)}>
          <div
            className="w-full max-w-md rounded-xl border animate-slide-up"
            style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-xl)' }}
            onClick={e => e.stopPropagation()}
          >
            <div className="flex items-center justify-between px-6 py-4 border-b" style={{ borderColor: 'var(--border-color)' }}>
              <h3 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
                {resolutionNote ? 'Update Resolution Note' : 'Write Resolution Note'}
              </h3>
              <button onClick={() => !savingResolutionNote && setResolveModalOpen(false)} className="flex h-8 w-8 items-center justify-center rounded-lg transition-colors cursor-pointer hover:bg-danger-50 hover:text-danger-500" style={{ color: 'var(--text-tertiary)' }}>
                <X className="h-5 w-5" />
              </button>
            </div>
            <div className="px-6 py-5 space-y-4">
              <p className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                You must write a resolution note to mark this ticket as resolved.
              </p>
              <textarea
                placeholder="Explain how the issue was resolved..."
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
                Cancel
              </button>
              <button
                disabled={savingResolutionNote || !resolutionNoteText.trim()}
                onClick={handleSubmitResolve}
                className="rounded-lg px-4 py-2 text-sm font-semibold text-white bg-accent-500 hover:bg-accent-600 transition-colors disabled:opacity-50 cursor-pointer"
              >
                {savingResolutionNote ? 'Saving...' : 'Save & Mark Resolved'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// Helper component for detail info rows
function DetailRow({ label, value }) {
  return (
    <div>
      <div className="text-xs font-medium mb-0.5" style={{ color: 'var(--text-tertiary)' }}>{label}</div>
      <div className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>{value}</div>
    </div>
  );
}
