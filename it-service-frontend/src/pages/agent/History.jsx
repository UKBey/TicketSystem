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
        console.error('Geçmiş biletler yüklenemedi:', err);
      } finally {
        setLoading(false);
      }
    };
    fetchHistory();
  }, []);

  return (
    <>
      <div className="page-header">
        <h1 className="page-title">History</h1>
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
    </>
  );
}
