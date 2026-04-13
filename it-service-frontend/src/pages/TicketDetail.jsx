import { useState, useEffect, useRef } from 'react';
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

  // Durum geçis seçenekleri (rol ve mevcut duruma göre filtrelenir)
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
  }, [id]);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [comments]);

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
    return <div className="empty-state"><h3>Bilet bulunamadi</h3></div>;
  }

  const ticketCode = `TCK-${String(ticket.id).padStart(3, '0')}`;
  const allowedStatuses = STATUS_OPTIONS[ticket.status] || [];
  const isAgent = hasRole('AGENT') || hasRole('MANAGER');
  const isCustomer = hasRole('CUSTOMER');

  return (
    <div className="ticket-detail">
      {/* ------ SOL: Ana Içerik ------ */}
      <div className="ticket-detail-main">
        {/* Back Link */}
        <a className="back-link" onClick={() => navigate(-1)}>
          ← Back
        </a>

        {/* Ticket Header */}
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

        {/* Chat Area */}
        <div className="card">
          <div className="chat-area">
            {comments.length === 0 && (
              <div className="empty-state" style={{ padding: 'var(--space-8)' }}>
                <p>Henüz yorum bulunmuyor.</p>
              </div>
            )}
            {comments.map((c) => {
              const isOwnComment = c.authorId === user?.id;
              const isInternal = c.type === 'INTERNAL';

              let messageClass = 'chat-message-customer';
              if (isOwnComment && !isInternal) messageClass = 'chat-message-agent';
              if (isInternal) messageClass = 'chat-message-internal';

              return (
                <div key={c.id} className={`chat-message ${messageClass}`}>
                  <div className="chat-author">
                    {c.authorName || (c.authorId === ticket.customerId ? ticket.customerName : 'Agent')}
                    {isInternal && <span className="badge badge-internal">Internal</span>}
                  </div>
                  <div>{c.message}</div>
                  <div className="chat-time">{formatShortDate(c.createdAt)}</div>
                </div>
              );
            })}
            <div ref={chatEndRef} />
          </div>

          {/* Comment Input */}
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

      {/* ------ SAG: Detay Paneli ------ */}
      <div className="ticket-detail-aside">
        {/* Status Actions (Agent/Manager Only) */}
        {isAgent && allowedStatuses.length > 0 && (
          <div className="card">
            <div className="card-header">Status Actions</div>
            <div className="card-body">
              <div style={{ display: 'flex', gap: '8px', marginBottom: '8px' }}>
                {allowedStatuses.includes('WAITING_FOR_CUSTOMER') && (
                  <button className="btn btn-outline" style={{ flex: 1 }} onClick={() => handleStatusChange('WAITING_FOR_CUSTOMER')}>
                    Waiting
                  </button>
                )}
                {allowedStatuses.includes('RESOLVED') && (
                  <button className="btn btn-success" style={{ flex: 1 }} onClick={() => handleStatusChange('RESOLVED')}>
                    Resolved
                  </button>
                )}
              </div>
              <button className="btn btn-outline" style={{ width: '100%', borderColor: 'var(--color-border)' }} onClick={() => setExtraActionsOpen(true)}>
                Extra Actions ⚙️
              </button>
            </div>
          </div>
        )}

        {/* Ticket Details Card */}
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

        {/* Customer Resolution Approval */}
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

      {/* Extra Actions Modal */}
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

      {/* Csat Modal */}
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
