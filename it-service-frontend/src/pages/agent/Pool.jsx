import { useState, useEffect } from 'react';
import api from '../../services/api';
import TicketTable from '../../components/TicketTable';

export default function Pool() {
  const [tickets, setTickets] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchPool = async () => {
    try {
      const res = await api.get('/tickets/pool');
      setTickets(res.data);
    } catch (err) {
      console.error('Havuz biletleri yüklenemedi:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPool();
  }, []);

  const handleClaim = async (ticketId) => {
    try {
      await api.put(`/tickets/${ticketId}/claim`);
      setTickets((prev) => prev.filter((t) => t.id !== ticketId));
    } catch (err) {
      alert(err.response?.data?.message || 'Bilet sahiplenilemedi.');
    }
  };

  return (
    <>
      <div className="page-header">
        <h1 className="page-title">Pool</h1>
      </div>

      <div className="card">
        {loading ? (
          <div className="card-body">
            <div className="app-loading" style={{ minHeight: 200 }}>
              <div className="spinner" />
            </div>
          </div>
        ) : (
          <TicketTable tickets={tickets} showClaimButton onClaim={handleClaim} />
        )}
      </div>
    </>
  );
}
