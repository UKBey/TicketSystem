import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { StatusBadge, PriorityBadge } from './Badges';
import SlaTimerBadge from './SlaTimerBadge';
import { AlertTriangle, Inbox } from 'lucide-react';

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
    return date.toLocaleDateString('en-US', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  if (!tickets || tickets.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-16 px-8" style={{ color: 'var(--text-tertiary)' }}>
        <Inbox className="h-12 w-12 mb-4 opacity-30" />
        <h3 className="text-lg font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>No tickets found</h3>
        <p className="text-sm">There are no tickets in this category yet.</p>
      </div>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full">
        <thead>
          <tr style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
            <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b" style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>ID</th>
            <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b" style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>Title</th>
            <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b" style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>Status</th>
            <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b" style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>Priority</th>
            {showSla && <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b" style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>SLA</th>}
            <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b" style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>Created</th>
            {showClaimButton && <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b" style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>Action</th>}
          </tr>
        </thead>
        <tbody>
          {tickets.map((ticket) => (
            <tr
              key={ticket.id}
              onClick={() => navigate(`/tickets/${ticket.id}`)}
              className="cursor-pointer transition-colors duration-150"
              style={{ borderBottom: '1px solid var(--border-color-light)' }}
              onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = 'var(--bg-surface-hover)')}
              onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
            >
              <td className="px-4 py-3 text-sm font-semibold text-primary-500">
                TCK-{String(ticket.id).padStart(3, '0')}
              </td>
              <td className="px-4 py-3 text-sm" style={{ color: 'var(--text-primary)' }}>
                <div className="flex items-center gap-2">
                  <span className="font-medium">{ticket.title}</span>
                  {ticket.slaBreached && (
                    <span
                      className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-bold"
                      style={{ backgroundColor: '#fee2e2', color: '#991b1b' }}
                    >
                      <AlertTriangle className="h-3 w-3" />
                      SLA
                    </span>
                  )}
                </div>
              </td>
              <td className="px-4 py-3"><StatusBadge status={ticket.status} /></td>
              <td className="px-4 py-3"><PriorityBadge priority={ticket.priority} /></td>
              {showSla && <td className="px-4 py-3"><SlaTimerBadge ticket={ticket} now={currentDate} /></td>}
              <td className="px-4 py-3 text-sm" style={{ color: 'var(--text-secondary)' }}>{formatDate(ticket.createdAt)}</td>
              {showClaimButton && (
                <td className="px-4 py-3">
                  <button
                    className="inline-flex items-center rounded-lg px-3 py-1.5 text-xs font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors cursor-pointer"
                    onClick={(e) => {
                      e.stopPropagation();
                      onClaim(ticket.id);
                    }}
                  >
                    Claim
                  </button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
