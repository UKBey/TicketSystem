import { useState, useEffect } from 'react';
import api from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import TicketTable from '../../components/TicketTable';

export default function Workspace() {
  const { user } = useAuth();
  const [tickets, setTickets] = useState([]);
  const [loading, setLoading] = useState(true);
  const currentUserId = user?.sub || user?.id;

  useEffect(() => {
    const fetchAssigned = async () => {
      try {
        const res = await api.get('/tickets/my-assigned');
        // Workspace listesinde kapanmis biletler gizlenir.
        setTickets(res.data.filter((t) => t.status !== 'CLOSED'));
      } catch (err) {
        console.error('Could not load assigned tickets:', err);
      } finally {
        setLoading(false);
      }
    };
    fetchAssigned();
  }, []);

  return (
    <>
      <div className="mb-6">
        <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>Workspace</h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>Tickets you have claimed.</p>
      </div>

      <div className="rounded-xl border overflow-hidden" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
        {loading ? (
          <div className="flex items-center justify-center py-20">
            <div className="h-8 w-8 rounded-full border-[3px] animate-spin" style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
          </div>
        ) : (
          <TicketTable tickets={tickets} showSla currentUserId={currentUserId} />
        )}
      </div>
    </>
  );
}
