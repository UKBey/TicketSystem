import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { StatusBadge, PriorityBadge } from '../components/Badges';
import api from '../services/api';

export default function TicketDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user, hasRole, getPrimaryRole } = useAuth();
  const chatEndRef = useRef(null);

  const [ticket, setTicket] = useState(null);
  const [comments, setComments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState('');
  const [commentType, setCommentType] = useState('EXTERNAL');
  const [sending, setSending] = useState(false);
  const [statusUpdating, setStatusUpdating] = useState(false);
  const [extraActionsOpen, setExtraActionsOpen] = useState(false);
  const [csatModalOpen, setCsatModalOpen] = useState(false);
  const [csatRating, setCsatRating] = useState(5);
  const [csatComment, setCsatComment] = useState('');
  
  const [submittingCsat, setSubmittingCsat] = useState(false);
  const [slaInfo, setSlaInfo] = useState(null);
  const [currentDate, setCurrentDate] = useState(Date.now());

  // Dosya ekleri icin durum degiskenleri
  const [attachments, setAttachments] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [dragOver, setDragOver] = useState(false);
  const fileInputRef = useRef(null);

  // Mevcut durumdan gecilebilecek hedef durum listesi.
  const STATUS_OPTIONS = {
    NEW: ['IN_PROGRESS'],
    IN_PROGRESS: ['NEW', 'WAITING_FOR_CUSTOMER', 'RESOLVED', 'CLOSED'],
    WAITING_FOR_CUSTOMER: ['IN_PROGRESS'],
    RESOLVED: ['IN_PROGRESS', 'CLOSED'],
    CLOSED: [],
  };

  useEffect(() => {
    fetchTicket();
    fetchComments();
    fetchAttachments();
  }, [id]);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [comments]);

  useEffect(() => {
    if (ticket && (hasRole('AGENT') || hasRole('MANAGER'))) {
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

  const fetchTicket = async () => {
    try {
      const res = await api.get(`/tickets/${id}`);
      setTicket(res.data);
    } catch (err) {
      console.error('Bilet yüklenemedi:', err);
    } finally {
      setLoading(false);
    }
  };

  const fetchComments = async () => {
    try {
      const res = await api.get(`/tickets/${id}/comments`);
      setComments(res.data);
    } catch (err) {
      console.error('Yorumlar yüklenemedi:', err);
    }
  };

  // Bilete bagli dosya eklerini sunucudan ceker.
  const fetchAttachments = async () => {
    try {
      const res = await api.get(`/tickets/${id}/attachments`);
      setAttachments(res.data);
    } catch (err) {
      console.error('Dosya ekleri yüklenemedi:', err);
    }
  };

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
      alert(err.response?.data?.message || 'Dosya yüklenemedi.');
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
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
    } catch (err) {
      alert('Dosya indirilemedi.');
    }
  };

  // Dosya tipine gore ikon belirler.
  const getFileIcon = (fileType) => {
    if (!fileType) return '📄';
    if (fileType.startsWith('image/')) return '🖼️';
    if (fileType.includes('pdf')) return '📕';
    if (fileType.includes('zip') || fileType.includes('rar') || fileType.includes('tar')) return '📦';
    if (fileType.includes('word') || fileType.includes('document')) return '📝';
    if (fileType.includes('sheet') || fileType.includes('excel')) return '📊';
    if (fileType.includes('text') || fileType.includes('log')) return '📃';
    return '📄';
  };

  // Dosya boyutunu okunabilir formata cevirir.
  const formatFileSize = (bytes) => {
    if (!bytes) return '';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  };

  // Surukleme olaylarini yonetir.
  const handleDragOver = useCallback((e) => {
    e.preventDefault();
    setDragOver(true);
  }, []);

  const handleDragLeave = useCallback((e) => {
    e.preventDefault();
    setDragOver(false);
  }, []);

  const handleDrop = useCallback((e) => {
    e.preventDefault();
    setDragOver(false);
    const file = e.dataTransfer.files?.[0];
    if (file) handleFileUpload(file);
  }, [id]);

  const handleSendComment = async () => {
    if (!message.trim()) return;
    setSending(true);
    try {
      const res = await api.post(`/tickets/${id}/comments`, {
        message,
        type: commentType,
      });
      setComments((prev) => [...prev, res.data]);
      setMessage('');
      // Yorum sonrasi olasi otomatik statu degisimlerini yansitmak icin bilet yeniden cekilir.
      const ticketRes = await api.get(`/tickets/${id}`);
      setTicket(ticketRes.data);
    } catch (err) {
      alert(err.response?.data?.message || 'Yorum gönderilemedi.');
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
      alert(err.response?.data?.message || 'Anket gönderilemedi.');
    } finally {
      setSubmittingCsat(false);
    }
  };

  const handleStatusChange = async (newStatus) => {
    setStatusUpdating(true);
    try {
      const res = await api.put(`/tickets/${id}/status`, { status: newStatus });
      setTicket(res.data);
    } catch (err) {
      alert(err.response?.data?.message || 'Durum güncellenemedi.');
    } finally {
      setStatusUpdating(false);
    }
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
      <div className="app-loading" style={{ minHeight: '60vh' }}>
        <div className="spinner" />
      </div>
    );
  }

  if (!ticket) {
    return <div className="empty-state"><h3>Bilet bulunamadı</h3></div>;
  }

  const ticketCode = `TCK-${String(ticket.id).padStart(3, '0')}`;
  const allowedStatuses = STATUS_OPTIONS[ticket.status] || [];
  const isAgent = hasRole('AGENT') || hasRole('MANAGER');
  const isCustomer = hasRole('CUSTOMER');

  return (
    <div className="ticket-detail">
      {/* Sol kolon: baslik, yorum akisi ve mesaj giris alani. */}
      <div className="ticket-detail-main">
        {/* Onceki ekrana donus baglantisi. */}
        <a className="back-link" onClick={() => navigate(-1)} style={{cursor: 'pointer'}}>
          ← Back
        </a>

        {/* Bilet kimligi, oncelik ve temel meta bilgileri. */}
        <div>
          <div className="ticket-header">
            <h1>{ticketCode}</h1>
            <PriorityBadge priority={ticket.priority} />
          </div>
          <div style={{ fontSize: 'var(--font-size-lg)', fontWeight: 600, marginTop: 'var(--space-1)' }}>
            {ticket.title}
          </div>
          <div className="ticket-meta" style={{ marginTop: 'var(--space-2)' }}>
            <span>👤 {ticket.customerName || ticket.customerId}</span>
            <span className="ticket-meta-separator">•</span>
            <span>Product: {ticket.productName || ticket.productId}</span>
          </div>
        </div>

        {/* Yorum ve dosya eklerinin kronolojik olarak gosterildigi sohbet alani. */}
        <div className="card">
          <div className="chat-area">
            {timeline.length === 0 && (
              <div className="empty-state" style={{ padding: 'var(--space-8)' }}>
                <p>Henüz yorum veya dosya eki bulunmuyor.</p>
              </div>
            )}
            {timeline.map((item) => {
              const itemAuthorId = item._type === 'attachment' ? item.uploaderId : item.authorId;
              const itemAuthorName = item._type === 'attachment' ? null : item.authorName;
              const isOwnItem = itemAuthorId === user?.id;
              const isInternal = item.type === 'INTERNAL'; // Attachments are implicitly EXTERNAL in this case

              let messageClass = 'chat-message-customer';
              if (isOwnItem && !isInternal) messageClass = 'chat-message-agent';
              if (isInternal) messageClass = 'chat-message-internal';

              const displayName = itemAuthorName || (itemAuthorId === ticket.customerId ? ticket.customerName : 'Agent');

              return (
                <div key={`${item._type}-${item.id}`} className={`chat-message ${messageClass}`}>
                  <div className="chat-author">
                    {displayName}
                    {isInternal && <span className="badge badge-internal">Internal</span>}
                  </div>
                  
                  {item._type === 'attachment' ? (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
                      <div 
                        style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', cursor: 'pointer' }}
                        onClick={() => handleDownloadAttachment(item)}
                        title="İndirmek için tıklayın"
                      >
                        <span style={{ fontSize: '1.5rem' }}>{getFileIcon(item.fileType)}</span>
                        <span style={{ fontWeight: 600 }}>{item.fileName}</span>
                      </div>
                      <div style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-1)' }}>
                        <button
                          className="btn btn-sm btn-outline"
                          onClick={() => handleDownloadAttachment(item)}
                          style={{ borderColor: 'rgba(0,0,0,0.1)', background: 'rgba(255,255,255,0.2)' }}
                        >
                          ⬇ İndir
                        </button>
                      </div>
                    </div>
                  ) : (
                    <div>{item.message}</div>
                  )}

                  <div className="chat-time">{formatShortDate(item.createdAt)}</div>
                </div>
              );
            })}
            <div ref={chatEndRef} />
          </div>

          {/* Bilet acik oldugu surece yeni yorum gonderim alani. */}
          {ticket.status !== 'CLOSED' && !(isCustomer && ticket.status === 'RESOLVED') && (
            <div className="comment-input-area">
              {isAgent && (
                <div className="comment-tabs">
                  <button
                    className={`comment-tab ${commentType === 'EXTERNAL' ? 'active' : ''}`}
                    onClick={() => setCommentType('EXTERNAL')}
                  >
                    Reply to Customer
                  </button>
                  <button
                    className={`comment-tab comment-tab-internal ${commentType === 'INTERNAL' ? 'active' : ''}`}
                    onClick={() => setCommentType('INTERNAL')}
                  >
                    Internal Note
                  </button>
                </div>
              )}
              <div className="comment-input-row">
                <input
                  className="form-input"
                  type="text"
                  placeholder="Type your message..."
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                  onKeyDown={handleKeyDown}
                  disabled={sending}
                />
                <input
                  ref={fileInputRef}
                  type="file"
                  style={{ display: 'none' }}
                  onChange={(e) => handleFileUpload(e.target.files?.[0])}
                />
                <button
                  className="btn btn-outline btn-icon"
                  title="Dosya Ekle"
                  onClick={() => fileInputRef.current?.click()}
                  disabled={uploading}
                  type="button"
                >
                  {uploading ? '⏳' : '📎'}
                </button>
                <button
                  className="btn btn-primary"
                  onClick={handleSendComment}
                  disabled={sending || !message.trim()}
                >
                  Send
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Sag kolon: statu aksiyonlari ve detay kartlari. */}
      <div className="ticket-detail-aside">
        {/* Agent/manager icin durum gecisi butonlari. */}
        {isAgent && allowedStatuses.length > 0 && (
          <div className="card">
            <div className="card-header">Status Actions</div>
            <div className="card-body">
                              <div style={{ display: 'flex', gap: '8px', marginBottom: '8px' }}>
                  {(allowedStatuses.includes('WAITING_FOR_CUSTOMER') || ticket.status === 'WAITING_FOR_CUSTOMER') && (
                    <button 
                      className={`btn ${ticket.status === 'WAITING_FOR_CUSTOMER' ? 'btn-primary' : 'btn-outline'}`} 
                      style={{ flex: 1 }} 
                      onClick={() => handleStatusChange(ticket.status === 'WAITING_FOR_CUSTOMER' ? 'IN_PROGRESS' : 'WAITING_FOR_CUSTOMER')}
                    >
                      {ticket.status === 'WAITING_FOR_CUSTOMER' ? 'Resume (In Progress)' : 'Waiting'}
                    </button>
                  )}
                  {(allowedStatuses.includes('RESOLVED') || ticket.status === 'RESOLVED') && (
                    <button 
                      className={`btn ${ticket.status === 'RESOLVED' ? 'btn-danger' : 'btn-success'}`} 
                      style={{ flex: 1 }} 
                      onClick={() => handleStatusChange(ticket.status === 'RESOLVED' ? 'IN_PROGRESS' : 'RESOLVED')}
                    >
                      {ticket.status === 'RESOLVED' ? 'Reopen (In Progress)' : 'Resolved'}
                    </button>
                  )}
                </div>
              <button className="btn btn-outline" style={{ width: '100%', borderColor: 'var(--color-border)' }} onClick={() => setExtraActionsOpen(true)}>
                Extra Actions ⚙️
              </button>
            </div>
          </div>
        )}

        {/* Biletin tarih, atama ve durum detaylari. */}
        <div className="card">
          <div className="card-header">Ticket Details</div>
          <div className="card-body">
            <div className="detail-info-item">
              <div className="detail-info-label">Created</div>
              <div className="detail-info-value">{formatDate(ticket.createdAt)}</div>
            </div>
            {!isCustomer && (
              <div className="detail-info-item">
                <div className="detail-info-label">Assigned To</div>
                <div className="detail-info-value">{ticket.assigneeName || 'Unassigned'}</div>
              </div>
            )}
            <div className="detail-info-item">
              <div className="detail-info-label">Status</div>
              <div className="detail-info-value">{statusLabel(ticket.status)}</div>
            </div>
            <div className="detail-info-item">
              <div className="detail-info-label">Priority</div>
              <div className="detail-info-value"><PriorityBadge priority={ticket.priority} /></div>
            </div>

            {/* SLA kalan sure bilgisini anlik olarak gosterir. */}
            {!isCustomer && slaInfo && (
              <div className="detail-info-item">
                <div className="detail-info-label">SLA Kalan Süre</div>
                <div className="detail-info-value">
                  {(() => {
                      if (slaInfo.deadlineTimestamp === -1) {
                         if (slaInfo.remainingMs <= 0 && ticket.slaBreached) return <span className="badge badge-sla-breach">⚠️ Süresi Doldu</span>;
                         if (slaInfo.remainingMs > 0) {
                             const diff = slaInfo.remainingMs;
                             const mins = Math.floor(diff / 60000);
                             const secs = Math.floor((diff % 60000) / 1000);
                             return <span className="badge" style={{backgroundColor: 'var(--color-bg-tertiary)', color: 'var(--color-text-secondary)'}}>{mins}dk {secs}sn (Duraklatıldı)</span>;
                         }
                         return <span className="badge badge-neutral">Tamamlandı</span>;
                      }

                      // Kalan sureyi fetch anina gore hesaplayarak istemci/sunucu saat farkini tolere eder.
                      let diff = slaInfo.remainingMs;
                      if (slaInfo.deadlineTimestamp !== -1) {
                          const elapsedSinceFetch = currentDate - slaInfo.fetchTime;
                          diff = slaInfo.remainingMs - elapsedSinceFetch;
                      }
                      
                      if (diff <= 0) return <span className="badge badge-sla-breach">⚠️ Süresi Doldu</span>;
                      const mins = Math.floor(diff / 60000);
                      const secs = Math.floor((diff % 60000) / 1000);
                      let badgeClass = 'badge-success';
                      if (mins < 1) badgeClass = 'badge-danger';
                      else if (mins < 2) badgeClass = 'badge-warning';
                      return <span className={`badge ${badgeClass}`}>{mins}dk {secs}sn</span>;
                  })()}
                </div>
              </div>
            )}

            {ticket.resolvedAt && (
              <div className="detail-info-item">
                <div className="detail-info-label">Resolved At</div>
                <div className="detail-info-value">{formatDate(ticket.resolvedAt)}</div>
              </div>
            )}
            {ticket.closedAt && (
              <div className="detail-info-item">
                <div className="detail-info-label">Closed At</div>
                <div className="detail-info-value">{formatDate(ticket.closedAt)}</div>
              </div>
            )}
          </div>
        </div>



        {/* Musterinin cozum onayi ve CSAT akis girisi. */}
        {isCustomer && ticket.status === 'RESOLVED' && (
          <div className="card" style={{ marginTop: 'var(--space-4)' }}>
            <div className="card-header">Sorununuz Çözüldü mü?</div>
            <div className="card-body">
              <p style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)', marginBottom: 'var(--space-3)' }}>
                Biletiniz çözüldü olarak işaretlendi. Doğrulayıp ankete katılabilir veya çözülmediğini belirterek destek sürecini uzatabilirsiniz.
              </p>
              <div style={{ display: 'flex', gap: '8px' }}>
                <button className="btn btn-success" style={{ flex: 1, justifyContent: 'center' }} onClick={() => setCsatModalOpen(true)}>
                  Evet, Çözüldü
                </button>
                <button className="btn btn-danger" style={{ flex: 1, justifyContent: 'center' }} onClick={() => handleStatusChange('IN_PROGRESS')}>
                  Hayır, Çözülmedi
                </button>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Ikincil durum aksiyonlarini acan modal pencere. */}
      {extraActionsOpen && (
        <div className="modal-overlay" onClick={() => setExtraActionsOpen(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Extra Actions</h3>
              <button className="modal-close" onClick={() => setExtraActionsOpen(false)}>×</button>
            </div>
            <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
              {allowedStatuses.includes('NEW') && (
                <button 
                  className="btn btn-outline" 
                  style={{ justifyContent: 'center' }}
                  onClick={() => { handleStatusChange('NEW'); setExtraActionsOpen(false); }}
                >
                  Unclaim (Bırak)
                </button>
              )}
              {allowedStatuses.includes('CLOSED') && (
                <button 
                  className="btn btn-danger" 
                  style={{ justifyContent: 'center' }}
                  onClick={() => { handleStatusChange('CLOSED'); setExtraActionsOpen(false); }}
                >
                  Close Ticket
                </button>
              )}
              {!allowedStatuses.includes('NEW') && !allowedStatuses.includes('CLOSED') && (
                <div style={{ textAlign: 'center', color: 'var(--color-text-secondary)', padding: 'var(--space-4)' }}>
                  Şu anki statüde ekstra bir aksiyon bulunmuyor.
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Memnuniyet puani ve yorumunun girildigi CSAT modal'i. */}
      {csatModalOpen && (
        <div className="modal-overlay" onClick={() => !submittingCsat && setCsatModalOpen(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Müşteri Memnuniyet Anketi</h3>
              <button className="modal-close" onClick={() => !submittingCsat && setCsatModalOpen(false)}>×</button>
            </div>
            <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
              <p>Çözüm sürecini 1 ile 5 arasında nasıl değerlendirirsiniz?</p>
              <div style={{ display: 'flex', gap: '8px', justifyContent: 'center', margin: 'var(--space-3) 0' }}>
                {[1, 2, 3, 4, 5].map(star => (
                  <button 
                    key={star}
                    type="button"
                    style={{ fontSize: '32px', background: 'none', border: 'none', outline: 'none', cursor: 'pointer', color: csatRating >= star ? '#f59e0b' : '#e5e7eb', transition: 'color 0.2s' }}
                    onClick={() => setCsatRating(star)}
                  >
                    ★
                  </button>
                ))}
              </div>
              <textarea 
                className="form-input" 
                placeholder="Eklemek istediğiniz notlar (opsiyonel)..."
                rows="3"
                value={csatComment}
                onChange={e => setCsatComment(e.target.value)}
              />
            </div>
            <div className="modal-footer">
              <button className="btn btn-outline" disabled={submittingCsat} onClick={() => setCsatModalOpen(false)}>İptal</button>
              <button className="btn btn-primary" disabled={submittingCsat} onClick={handleSubmitCsat}>Gönder ve Kapat</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
