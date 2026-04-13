import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { StatusBadge, PriorityBadge } from './Badges';
import SlaTimerBadge from './SlaTimerBadge';

export default function TicketTable({ tickets, showClaimButton, onClaim, showSla = false }) {
  const navigate = useNavigate();
  const [currentDate, setCurrentDate] = useState(Date.now());

  useEffect(() => {
    if (!showSla) return undefined;

    const timer = setInterval(() => setCurrentDate(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [showSla]);

  const formatDate = (dateStr) => {
    if (!dateStr) return '—';
    const date = new Date(dateStr);
    return date.toLocaleDateString('tr-TR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  if (!tickets || tickets.length === 0) {
    return (
      <div className="empty-state">
        <div className="empty-state-icon">📭</div>
        <h3>Bilet bulunamadı</h3>
        <p>Henüz bu kategoride bilet bulunmuyor.</p>
      </div>
    );
  }

  return (
    <table className="data-table">
      <thead>
        <tr>
          <th>ID</th>
          <th>Başlık</th>
          <th>Durum</th>
          <th>Öncelik</th>
          {showSla && <th>SLA Durumu</th>}
          <th>Oluşturma Tarihi</th>
          {showClaimButton && <th>İşlem</th>}
        </tr>
      </thead>
      <tbody>
        {tickets.map((ticket) => (
          <tr key={ticket.id} onClick={() => navigate(`/tickets/${ticket.id}`)}>
            <td className="ticket-id">TCK-{String(ticket.id).padStart(3, '0')}</td>
            <td>
              {ticket.title}
              {ticket.slaBreached && (
                <span className="badge badge-sla-breach" style={{ marginLeft: 8 }}>
                  ⚠ SLA
                </span>
              )}
            </td>
            <td><StatusBadge status={ticket.status} /></td>
            <td><PriorityBadge priority={ticket.priority} /></td>
            {showSla && <td><SlaTimerBadge ticket={ticket} now={currentDate} /></td>}
            <td>{formatDate(ticket.createdAt)}</td>
            {showClaimButton && (
              <td>
                <button
                  className="btn btn-primary btn-sm"
                  onClick={(e) => {
                    e.stopPropagation();
                    onClaim(ticket.id);
                  }}
                >
                  Üzerime Al
                </button>
              </td>
            )}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
