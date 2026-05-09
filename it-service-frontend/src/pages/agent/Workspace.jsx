import { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { getAgentLimits } from '../../services/api';
import { useTicketList } from '../../hooks/useTicketList';
import TicketTable from '../../components/TicketTable';
import TicketFilters from '../../components/TicketFilters';
import PaginationBar from '../../components/PaginationBar';

export default function Workspace() {
  const { user, hasRole } = useAuth();
  const [agentLimits, setAgentLimits] = useState([]);
  const currentUserId = user?.sub || user?.id;
  const isAgentAdmin = hasRole('AGENT_ADMIN');

  const {
    tickets, totalPages, totalItems, loading, error,
    page, setPage, size, setSize,
    sortBy, sortDir, toggleSort,
    status, setStatus,
    priority, setPriority,
  } = useTicketList('/tickets/my-assigned', {
    sortBy: 'createdAt',
    sortDir: 'desc',
  });

  const activeTickets = tickets.filter((t) => t.status !== 'CLOSED');

  useEffect(() => {
    if (!isAgentAdmin || !currentUserId) return;
    getAgentLimits(currentUserId)
      .then((res) => setAgentLimits(res.data))
      .catch((err) => console.error('Could not load agent limits:', err));
  }, [currentUserId, isAgentAdmin]);

  return (
    <>
      <div className="mb-6">
        <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>Workspace</h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>Tickets you have claimed.</p>
      </div>

      {!loading && agentLimits.length > 0 && (
        <div className="mb-6 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {agentLimits.map(limit => {
            const activeCount = activeTickets.filter(t => t.productId === limit.productId).length;
            const effectiveLimit = limit.effectiveLimit;
            return (
              <div key={limit.productId} className="rounded-lg border p-4"
                style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
                <p className="text-xs font-semibold mb-2" style={{ color: 'var(--text-tertiary)' }}>
                  {limit.productName}
                </p>
                <div className="flex items-center gap-2">
                  <span className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>{activeCount}</span>
                  {effectiveLimit && (
                    <>
                      <span style={{ color: 'var(--text-tertiary)' }}>/</span>
                      <span className="text-lg font-semibold" style={{ color: 'var(--text-secondary)' }}>{effectiveLimit}</span>
                    </>
                  )}
                  <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>active tickets</span>
                </div>
                {effectiveLimit && activeCount >= effectiveLimit && (
                  <p className="text-xs mt-2 font-semibold text-danger-600 dark:text-danger-400">Limit exceeded</p>
                )}
              </div>
            );
          })}
        </div>
      )}

      {error && (
        <div className="rounded-lg px-4 py-3 mb-4 text-sm font-medium bg-danger-50 text-danger-600 dark:bg-danger-500/10 dark:text-danger-400">
          {error}
        </div>
      )}

      <div className="rounded-xl border overflow-hidden" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
        <TicketFilters
          status={status}   onStatus={setStatus}
          priority={priority} onPriority={setPriority}
        />

        {loading ? (
          <div className="flex items-center justify-center py-20">
            <div className="h-8 w-8 rounded-full border-[3px] animate-spin" style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
          </div>
        ) : (
          <TicketTable
            tickets={tickets}
            showSla
            currentUserId={currentUserId}
            sortBy={sortBy} sortDir={sortDir} onSort={toggleSort}
          />
        )}

        <PaginationBar
          page={page} totalPages={totalPages} totalItems={totalItems}
          size={size} onPageChange={setPage} onSizeChange={setSize}
        />
      </div>
    </>
  );
}
