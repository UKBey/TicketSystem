import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import api from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import { useTicketList } from '../../hooks/useTicketList';
import TicketTable from '../../components/TicketTable';
import TicketFilters from '../../components/TicketFilters';
import PaginationBar from '../../components/PaginationBar';
import AgentSelectionModal from '../../components/AgentSelectionModal';

export default function Pool() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const isAgentAdmin = hasRole('AGENT_ADMIN');

  const [assignModal, setAssignModal] = useState({ open: false, ticketId: null, productId: null });

  const {
    tickets, totalPages, totalItems, loading, error,
    page, setPage, size, setSize,
    sortBy, sortDir, toggleSort,
    priority, setPriority,
    search, setSearch,
    productIds, setProductIds,
    topicIds, setTopicIds,
    slaStatuses, setSlaStatuses,
    dateFrom, setDateFrom,
    dateTo, setDateTo,
    clearFilters,
    refetch,
  } = useTicketList('/tickets/pool', { sortBy: 'createdAt', sortDir: 'desc' });

  const handleClaim = async (ticketId) => {
    try {
      await api.put(`/tickets/${ticketId}/claim`);
      refetch();
      navigate(`/tickets/${ticketId}`);
    } catch (err) {
      if (err.response?.status === 409 && err.response?.data?.error === 'TICKET_LIMIT_EXCEEDED') {
        alert(`Limit exceeded: ${err.response.data.message}`);
      } else {
        alert(err.response?.data?.message || 'Could not claim ticket.');
      }
    }
  };

  const handleOpenAssign = (ticket) => {
    setAssignModal({ open: true, ticketId: ticket.id, productId: ticket.productId });
  };

  const handleAssignSuccess = () => {
    setAssignModal({ open: false, ticketId: null, productId: null });
    refetch();
  };

  return (
    <>
      <div className="mb-6">
        <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>{t('pool.title')}</h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>{t('pool.subtitle')}</p>
      </div>

      {error && (
        <div className="rounded-lg px-4 py-3 mb-4 text-sm font-medium bg-danger-50 text-danger-600 dark:bg-danger-500/10 dark:text-danger-400">
          {error}
        </div>
      )}

      <div className="rounded-xl border overflow-hidden" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
        <TicketFilters
          priority={priority}   onPriority={setPriority}
          search={search}       onSearch={setSearch}
          productIds={productIds} onProductIds={setProductIds}
          topicIds={topicIds}     onTopicIds={setTopicIds}
          slaStatuses={slaStatuses} onSlaStatuses={setSlaStatuses}
          dateFrom={dateFrom}   onDateFrom={setDateFrom}
          dateTo={dateTo}       onDateTo={setDateTo}
          onClear={clearFilters}
          hideStatus
          hideAgent
        />

        {loading ? (
          <div className="flex items-center justify-center py-20">
            <div className="h-8 w-8 rounded-full border-[3px] animate-spin" style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
          </div>
        ) : (
          <TicketTable
            tickets={tickets}
            showClaimButton
            onClaim={handleClaim}
            showSla
            showAssignButton={isAgentAdmin}
            onAssign={handleOpenAssign}
            sortBy={sortBy} sortDir={sortDir} onSort={toggleSort}
          />
        )}

        <PaginationBar
          page={page} totalPages={totalPages} totalItems={totalItems}
          size={size} onPageChange={setPage} onSizeChange={setSize}
        />
      </div>

      <AgentSelectionModal
        isOpen={assignModal.open}
        onClose={() => setAssignModal({ open: false, ticketId: null, productId: null })}
        onSuccess={handleAssignSuccess}
        ticketId={assignModal.ticketId}
        productId={assignModal.productId}
      />
    </>
  );
}
