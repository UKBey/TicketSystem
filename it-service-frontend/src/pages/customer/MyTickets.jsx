import { useState, useEffect, useMemo } from 'react';
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
      console.error('Biletler yüklenemedi:', err);
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
      <div className="page-header">
        <h1 className="page-title">My Tickets</h1>
        <button className="btn btn-primary" onClick={() => setModalOpen(true)}>
          + Yeni Bilet
        </button>
      </div>

      <div className="ticket-tabs" style={{ display: 'flex', gap: 0, marginBottom: 'var(--space-4)' }}>
        <button
          className={`ticket-tab-btn ${tab === 'active' ? 'active' : ''}`}
          onClick={() => setTab('active')}
        >
          Açık Biletler
          {!loading && <span className="ticket-tab-count">{activeTickets.length}</span>}
        </button>
        <button
          className={`ticket-tab-btn ${tab === 'closed' ? 'active' : ''}`}
          onClick={() => setTab('closed')}
        >
          Kapanmış Biletler
          {!loading && <span className="ticket-tab-count">{closedTickets.length}</span>}
        </button>
      </div>

      <div className="card">
        {loading ? (
          <div className="card-body">
            <div className="app-loading" style={{ minHeight: 200 }}>
              <div className="spinner" />
            </div>
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
