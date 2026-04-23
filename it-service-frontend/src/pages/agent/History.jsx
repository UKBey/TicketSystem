import { useState, useEffect } from 'react';
import api from '../../services/api';
import TicketTable from '../../components/TicketTable';

export default function History() {
  const [tickets, setTickets] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchHistory = async () => {
      try {
        const res = await api.get('/tickets/my-assigned');
        setTickets(res.data.filter((t) => t.status === 'CLOSED'));
      } catch (err) {
        console.error('Could not load history:', err);
      } finally {
        setLoading(false);
      }
    };
    fetchHistory();
  }, []);

  return (
    <>
      <div className="mb-6">
        <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>History</h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>Previously closed tickets you worked on.</p>
      </div>

      <div className="rounded-xl border overflow-hidden" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
        {loading ? (
          <div className="flex items-center justify-center py-20">
            <div className="h-8 w-8 rounded-full border-[3px] animate-spin" style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
          </div>
        ) : (
          <TicketTable tickets={tickets} showSla />
        )}
      </div>
    </>
  );
}
