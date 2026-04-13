import { useState, useEffect } from 'react';
import api from '../../services/api';
import TicketTable from '../../components/TicketTable';

export default function Workspace() {
  const [tickets, setTickets] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchAssigned = async () => {
      try {
        const res = await api.get('/tickets/my-assigned');
        // Aktif biletler: CLOSED olmayan
        setTickets(res.data.filter((t) => t.status !== 'CLOSED'));
      } catch (err) {
        console.error('Atanan biletler yüklenemedi:', err);
      } finally {
        setLoading(false);
      }
    };
    fetchAssigned();
  }, []);

  return (
    <>
      <div className="page-header">
        <h1 className="page-title">Workspace</h1>
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
