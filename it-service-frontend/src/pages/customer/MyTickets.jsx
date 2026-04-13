import { useState, useEffect } from 'react';
import api from '../../services/api';
import TicketTable from '../../components/TicketTable';
import CreateTicketModal from '../../components/CreateTicketModal';

export default function MyTickets() {
  const [tickets, setTickets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);

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

  return (
    <>
      <div className="page-header">
        <h1 className="page-title">My Tickets</h1>
        <button className="btn btn-primary" onClick={() => setModalOpen(true)}>
          + Yeni Bilet
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
          <TicketTable tickets={tickets} />
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
