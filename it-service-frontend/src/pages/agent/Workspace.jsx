import { useState, useEffect } from 'react';
import api from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import { getAgentLimits } from '../../services/api';
import TicketTable from '../../components/TicketTable';

export default function Workspace() {
  const { user, hasRole } = useAuth();
  const [tickets, setTickets] = useState([]);
  const [agentLimits, setAgentLimits] = useState([]);
  const [loading, setLoading] = useState(true);
  const currentUserId = user?.sub || user?.id;
  const isAgentAdmin = hasRole('AGENT_ADMIN');

  useEffect(() => {
    const fetchData = async () => {
      try {
        const ticketsRes = await api.get('/tickets/my-assigned');
        setTickets(ticketsRes.data.filter((t) => t.status !== 'CLOSED'));

        // Limit bilgisi yalnızca AGENT_ADMIN için çekilir (AGENT rolü bu endpoint'e erişemez)
        if (isAgentAdmin) {
          try {
            const limitsRes = await getAgentLimits(currentUserId);
            setAgentLimits(limitsRes.data);
          } catch (err) {
            console.error('Could not load agent limits:', err);
          }
        }
      } catch (err) {
        console.error('Could not load workspace data:', err);
      } finally {
        setLoading(false);
      }
    };
    if (currentUserId) {
      fetchData();
    }
  }, [currentUserId, isAgentAdmin]);

  return (
    <>
      <div className="mb-6">
        <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>Workspace</h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>Tickets you have claimed.</p>
      </div>

      {/* Active Ticket Summary by Product */}
      {!loading && agentLimits.length > 0 && (
        <div className="mb-6 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {agentLimits.map(limit => {
            const activeCount = tickets.filter(t => t.productId === limit.productId).length;
            const effectiveLimit = limit.effectiveLimit;

            return (
              <div 
                key={limit.productId} 
                className="rounded-lg border p-4" 
                style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}
              >
                <p className="text-xs font-semibold mb-2" style={{ color: 'var(--text-tertiary)' }}>
                  {limit.productName}
                </p>
                <div className="flex items-center gap-2">
                  <span className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
                    {activeCount}
                  </span>
                  {effectiveLimit && (
                    <>
                      <span style={{ color: 'var(--text-tertiary)' }}>/</span>
                      <span className="text-lg font-semibold" style={{ color: 'var(--text-secondary)' }}>
                        {effectiveLimit}
                      </span>
                    </>
                  )}
                  <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>
                    aktif bilet
                  </span>
                </div>
                {effectiveLimit && activeCount >= effectiveLimit && (
                  <p className="text-xs mt-2 font-semibold text-danger-600 dark:text-danger-400">
                    Limit aşıldı
                  </p>
                )}
              </div>
            );
          })}
        </div>
      )}

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
