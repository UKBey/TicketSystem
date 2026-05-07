import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import { StatusBadge, PriorityBadge } from '../../components/Badges';
import SlaTimerBadge from '../../components/SlaTimerBadge';
import { AlertTriangle, Inbox, Users } from 'lucide-react';

export default function TeamTickets() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [tickets, setTickets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [joiningId, setJoiningId] = useState(null);
  const [tickSeconds, setTickSeconds] = useState(0);

  const currentUserId = user?.sub || user?.id;

  useEffect(() => {
    if (!currentUserId) return;
    const fetchTeam = async () => {
      try {
        const res = await api.get('/tickets/team');
        setTickets(res.data.filter((t) => !t.claimers?.some((c) => c.agentId === currentUserId)));
      } catch (err) {
        console.error('Could not load team tickets:', err);
      } finally {
        setLoading(false);
      }
    };
    fetchTeam();
  }, [currentUserId]);

  useEffect(() => {
    const timer = setInterval(() => setTickSeconds((v) => v + 1), 1000);
    return () => clearInterval(timer);
  }, []);

  const handleJoin = async (ticketId, e) => {
    e.stopPropagation();
    setJoiningId(ticketId);
    try {
      await api.put(`/tickets/${ticketId}/claim`);
      setTickets((prev) => prev.filter((t) => t.id !== ticketId));
    } catch (err) {
      alert(err.response?.data?.message || 'Could not join ticket.');
    } finally {
      setJoiningId(null);
    }
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('en-US', {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit',
    });
  };

  return (
    <>
      <div className="mb-6">
        <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
          Team Tickets
        </h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>
          Active tickets in your authorized products. Join any ticket to collaborate.
        </p>
      </div>

      <div
        className="rounded-xl border overflow-hidden"
        style={{
          backgroundColor: 'var(--bg-surface)',
          borderColor: 'var(--border-color)',
          boxShadow: 'var(--shadow-sm)',
        }}
      >
        {loading ? (
          <div className="flex items-center justify-center py-20">
            <div
              className="h-8 w-8 rounded-full border-[3px] animate-spin"
              style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }}
            />
          </div>
        ) : tickets.length === 0 ? (
          <div
            className="flex flex-col items-center justify-center py-16 px-8"
            style={{ color: 'var(--text-tertiary)' }}
          >
            <Inbox className="h-12 w-12 mb-4 opacity-30" />
            <h3 className="text-lg font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>
              No active team tickets
            </h3>
            <p className="text-sm">No claimed tickets in your product area right now.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
                  {['ID', 'Title', 'Status', 'Priority', 'SLA', 'Claimers', 'Created', 'Action'].map((h) => (
                    <th
                      key={h}
                      className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b"
                      style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}
                    >
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {tickets.map((ticket) => (
                    <tr
                      key={ticket.id}
                      onClick={() => navigate(`/tickets/${ticket.id}`)}
                      className="cursor-pointer transition-colors duration-150"
                      style={{ borderBottom: '1px solid var(--border-color-light)' }}
                      onMouseEnter={(e) =>
                        (e.currentTarget.style.backgroundColor = 'var(--bg-surface-hover)')
                      }
                      onMouseLeave={(e) =>
                        (e.currentTarget.style.backgroundColor = 'transparent')
                      }
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
                      <td className="px-4 py-3">
                        <StatusBadge status={ticket.status} />
                      </td>
                      <td className="px-4 py-3">
                        <PriorityBadge priority={ticket.priority} />
                      </td>
                      <td className="px-4 py-3">
                        <SlaTimerBadge ticket={ticket} tickSeconds={tickSeconds} />
                      </td>
                      <td className="px-4 py-3">
                        <ClaimerAvatars claimers={ticket.claimers} currentUserId={currentUserId} />
                      </td>
                      <td className="px-4 py-3 text-sm" style={{ color: 'var(--text-secondary)' }}>
                        {formatDate(ticket.createdAt)}
                      </td>
                      <td className="px-4 py-3">
                        <button
                          className="inline-flex items-center gap-1 rounded-lg px-3 py-1.5 text-xs font-semibold text-white bg-emerald-600 hover:bg-emerald-700 transition-colors cursor-pointer disabled:opacity-50"
                          disabled={joiningId === ticket.id}
                          onClick={(e) => handleJoin(ticket.id, e)}
                        >
                          <Users className="h-3 w-3" />
                          {joiningId === ticket.id ? 'Joining…' : 'Join'}
                        </button>
                      </td>
                    </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  );
}

function ClaimerAvatars({ claimers, currentUserId }) {
  if (!claimers || claimers.length === 0) {
    return <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>—</span>;
  }
  return (
    <div className="flex flex-wrap gap-1">
      {claimers.map((c) => (
        <span
          key={c.agentId}
          title={c.agentName}
          className="inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium"
          style={
            c.agentId === currentUserId
              ? { backgroundColor: '#dbeafe', color: '#1d4ed8' }
              : { backgroundColor: 'var(--bg-surface-secondary)', color: 'var(--text-secondary)' }
          }
        >
          {c.agentName?.split(' ')[0] ?? 'Agent'}
          {c.agentId === currentUserId && ' (you)'}
        </span>
      ))}
    </div>
  );
}
