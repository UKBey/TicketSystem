import { useState, useEffect, useMemo } from 'react';
import { Plus } from 'lucide-react';
import api from '../../services/api';
import TicketTable from '../../components/TicketTable';
import CreateTicketModal from '../../components/CreateTicketModal';

export default function MyTickets() {
  const [tickets, setTickets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [tab, setTab] = useState('active'); // 'active' | 'closed'

  const fetchTickets = async () => {
    try {
      const res = await api.get('/tickets');
      setTickets(res.data);
    } catch (err) {
      console.error('Could not load tickets:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTickets();
  }, []);

  const handleTicketCreated = (newTicket) => {
    setTickets((prev) => [newTicket, ...prev]);
  };

  // Biletleri acik ve kapanmis olarak ayirir.
  const activeTickets = useMemo(
    () => tickets.filter((t) => t.status !== 'CLOSED'),
    [tickets]
  );
  const closedTickets = useMemo(
    () => tickets.filter((t) => t.status === 'CLOSED'),
    [tickets]
  );

  const displayedTickets = tab === 'active' ? activeTickets : closedTickets;

  return (
    <>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>My Tickets</h1>
          <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>View and manage your support requests.</p>
        </div>
        <button
          onClick={() => setModalOpen(true)}
          className="inline-flex items-center gap-2 rounded-lg px-4 py-2.5 text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-all duration-200 hover:shadow-lg hover:shadow-primary-500/25 cursor-pointer"
        >
          <Plus className="h-4 w-4" />
          New Ticket
        </button>
      </div>

      {/* Tabs */}
      <div className="flex gap-0 mb-5">
        <button
          className={`inline-flex items-center gap-2 px-4 py-2 text-sm font-medium border transition-colors cursor-pointer rounded-l-lg ${
            tab === 'active'
              ? 'bg-primary-500 text-white border-primary-500'
              : 'border-[var(--border-color)] hover:bg-[var(--bg-surface-hover)]'
          }`}
          style={tab !== 'active' ? { color: 'var(--text-secondary)', backgroundColor: 'var(--bg-surface)' } : {}}
          onClick={() => setTab('active')}
        >
          Active
          {!loading && (
            <span className={`inline-flex items-center justify-center min-w-[20px] h-5 px-1.5 text-[11px] font-bold rounded-full ${
              tab === 'active' ? 'bg-white/20' : 'bg-[var(--bg-surface-secondary)]'
            }`}>
              {activeTickets.length}
            </span>
          )}
        </button>
        <button
          className={`inline-flex items-center gap-2 px-4 py-2 text-sm font-medium border border-l-0 transition-colors cursor-pointer rounded-r-lg ${
            tab === 'closed'
              ? 'bg-primary-500 text-white border-primary-500'
              : 'border-[var(--border-color)] hover:bg-[var(--bg-surface-hover)]'
          }`}
          style={tab !== 'closed' ? { color: 'var(--text-secondary)', backgroundColor: 'var(--bg-surface)' } : {}}
          onClick={() => setTab('closed')}
        >
          Closed
          {!loading && (
            <span className={`inline-flex items-center justify-center min-w-[20px] h-5 px-1.5 text-[11px] font-bold rounded-full ${
              tab === 'closed' ? 'bg-white/20' : 'bg-[var(--bg-surface-secondary)]'
            }`}>
              {closedTickets.length}
            </span>
          )}
        </button>
      </div>

      {/* Table card */}
      <div className="rounded-xl border overflow-hidden" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
        {loading ? (
          <div className="flex items-center justify-center py-20">
            <div className="h-8 w-8 rounded-full border-[3px] animate-spin" style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
          </div>
        ) : (
          <TicketTable tickets={displayedTickets} showSla={tab === 'active'} />
        )}
      </div>

      <CreateTicketModal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        onCreated={handleTicketCreated}
      />
    </>
  );
}
