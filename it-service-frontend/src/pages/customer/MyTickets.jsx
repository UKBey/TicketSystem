import { useState } from 'react';
import { Plus } from 'lucide-react';
import { useTicketList } from '../../hooks/useTicketList';
import TicketTable from '../../components/TicketTable';
import TicketFilters from '../../components/TicketFilters';
import PaginationBar from '../../components/PaginationBar';
import CreateTicketModal from '../../components/CreateTicketModal';

const TABS = [
  { key: 'active',  label: 'Active',  statusFilter: '' },
  { key: 'closed',  label: 'Closed',  statusFilter: 'CLOSED' },
];

export default function MyTickets() {
  const [modalOpen, setModalOpen] = useState(false);
  const [tab, setTab] = useState('active');

  const {
    tickets, totalPages, totalItems, loading, error,
    page, setPage, size, setSize,
    sortBy, sortDir, toggleSort,
    status, setStatus,
    priority, setPriority,
    refetch,
  } = useTicketList('/tickets', { sortBy: 'createdAt', sortDir: 'desc' });

  // Tab switch: override status filter
  const handleTabChange = (t) => {
    setTab(t);
    setStatus(t === 'closed' ? 'CLOSED' : '');
  };

  const handleTicketCreated = () => refetch();

  // Count badges — use totalItems only for the active tab (approximate)
  const activeCount = tab === 'active' ? totalItems : null;
  const closedCount = tab === 'closed' ? totalItems : null;

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
        {TABS.map((t, i) => {
          const active = tab === t.key;
          const count  = t.key === 'active' ? activeCount : closedCount;
          return (
            <button
              key={t.key}
              className={`inline-flex items-center gap-2 px-4 py-2 text-sm font-medium border transition-colors cursor-pointer ${
                i === 0 ? 'rounded-l-lg' : 'border-l-0 rounded-r-lg'
              } ${active ? 'bg-primary-500 text-white border-primary-500' : 'border-[var(--border-color)] hover:bg-[var(--bg-surface-hover)]'}`}
              style={!active ? { color: 'var(--text-secondary)', backgroundColor: 'var(--bg-surface)' } : {}}
              onClick={() => handleTabChange(t.key)}
            >
              {t.label}
              {!loading && count !== null && (
                <span className={`inline-flex items-center justify-center min-w-[20px] h-5 px-1.5 text-[11px] font-bold rounded-full ${
                  active ? 'bg-white/20' : 'bg-[var(--bg-surface-secondary)]'
                }`}>
                  {count}
                </span>
              )}
            </button>
          );
        })}
      </div>

      {error && (
        <div className="rounded-lg px-4 py-3 mb-4 text-sm font-medium bg-danger-50 text-danger-600 dark:bg-danger-500/10 dark:text-danger-400">
          {error}
        </div>
      )}

      <div className="rounded-xl border overflow-hidden" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
        <TicketFilters
          status={status}   onStatus={setStatus}
          priority={priority} onPriority={setPriority}
          hideStatus={tab === 'closed'}
        />

        {loading ? (
          <div className="flex items-center justify-center py-20">
            <div className="h-8 w-8 rounded-full border-[3px] animate-spin" style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
          </div>
        ) : (
          <TicketTable
            tickets={tickets}
            showSla={tab === 'active'}
            sortBy={sortBy} sortDir={sortDir} onSort={toggleSort}
          />
        )}

        <PaginationBar
          page={page} totalPages={totalPages} totalItems={totalItems}
          size={size} onPageChange={setPage} onSizeChange={setSize}
        />
      </div>

      <CreateTicketModal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        onCreated={handleTicketCreated}
      />
    </>
  );
}
