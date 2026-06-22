import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import api from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { useTicketList } from '../../hooks/useTicketList';
import TicketTable from '../../components/TicketTable';
import TicketFilters from '../../components/TicketFilters';
import PaginationBar from '../../components/PaginationBar';
import AgentSelectionModal from '../../components/AgentSelectionModal';
import ListLoadingOverlay from '../../components/ListLoadingOverlay';
import Button from '../../components/Button';
import { Users } from 'lucide-react';

export default function TeamTickets() {
  const { t } = useTranslation();
  const toast = useToast();
  const navigate = useNavigate();
  const { user, isLeadAgent, isAdmin } = useAuth();
  const [joiningId, setJoiningId] = useState(null);
  const [assignModal, setAssignModal] = useState({ open: false, ticketId: null, productId: null, excludeAgentIds: [] });

  const currentUserId = user?.sub || user?.id;
  // Bilet atama yetkisi — lead agent veya admin.
  const canAssign = isLeadAgent || isAdmin;

  const {
    tickets, totalPages, totalItems, loading, initialLoading, error,
    page, setPage, size, setSize,
    sortBy, sortDir, toggleSort,
    status, setStatus,
    priority, setPriority,
    search, setSearch,
    productIds, setProductIds,
    agentIds, setAgentIds,
    topicIds, setTopicIds,
    slaStatuses, setSlaStatuses,
    dateFrom, setDateFrom,
    dateTo, setDateTo,
    setDateRange,
    clearFilters,
    refetch,
  } = useTicketList('/tickets/team', { sortBy: 'createdAt', sortDir: 'desc' });

  // Kullanıcının zaten claim aldığı biletleri bu listeden gizler — onlar Workspace'te.
  const displayedTickets = tickets.filter(
    (tk) => !tk.claimers?.some((c) => c.agentId === currentUserId)
  );

  const handleJoin = async (ticketId) => {
    setJoiningId(ticketId);
    try {
      await api.put(`/tickets/${ticketId}/claim`);
      navigate(`/tickets/${ticketId}`);
    } catch (err) {
      toast.error(err.response?.data?.message || t('ticketDetail.joinFailed'));
      setJoiningId(null);
    }
  };

  const handleOpenAssign = (ticket) => {
    setAssignModal({
      open: true,
      ticketId: ticket.id,
      productId: ticket.productId,
      excludeAgentIds: ticket.claimers?.map((c) => c.agentId) ?? [],
    });
  };

  const handleAssignSuccess = () => {
    setAssignModal({ open: false, ticketId: null, productId: null, excludeAgentIds: [] });
    refetch();
  };

  const renderRowActions = (ticket) => (
    <>
      <Button
        type="button"
        variant="success"
        size="sm"
        fullWidth
        disabled={joiningId === ticket.id}
        onClick={(e) => { e.stopPropagation(); handleJoin(ticket.id); }}
        className="lg:w-auto lg:py-1.5 gap-1"
      >
        <Users className="h-3 w-3" />
        {joiningId === ticket.id ? t('teamTickets.joining') : t('teamTickets.join')}
      </Button>
      {canAssign && (
        <Button
          type="button"
          variant="warning"
          size="sm"
          fullWidth
          onClick={(e) => { e.stopPropagation(); handleOpenAssign(ticket); }}
          className="lg:w-auto lg:py-1.5"
        >
          {t('ticket.actions.assign')}
        </Button>
      )}
    </>
  );

  return (
    <>
      <div className="mb-6">
        <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>{t('teamTickets.title')}</h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>
          {t('teamTickets.subtitle')}
        </p>
      </div>

      {error && (
        <div className="rounded-lg px-4 py-3 mb-4 text-sm font-medium bg-danger-50 text-danger-600 dark:bg-danger-500/10 dark:text-danger-400">
          {error}
        </div>
      )}

      <div data-tour="team-list" className="rounded-xl border overflow-hidden"
        style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>

        <TicketFilters
          status={status}       onStatus={setStatus}
          priority={priority}   onPriority={setPriority}
          search={search}       onSearch={setSearch}
          productIds={productIds} onProductIds={setProductIds}
          agentIds={agentIds}     onAgentIds={setAgentIds}
          topicIds={topicIds}     onTopicIds={setTopicIds}
          slaStatuses={slaStatuses} onSlaStatuses={setSlaStatuses}
          dateFrom={dateFrom}   onDateFrom={setDateFrom}
          dateTo={dateTo}       onDateTo={setDateTo}
          onDateRange={setDateRange}
          onClear={clearFilters}
          statusOptions={['IN_PROGRESS', 'WAITING_FOR_CUSTOMER', 'RESOLVED']}
        />

        <ListLoadingOverlay initial={initialLoading} loading={loading}>
          <TicketTable
            tickets={displayedTickets}
            showSla
            currentUserId={currentUserId}
            showTopic={false}
            forceShowClaimers
            emptyTitle="teamTickets.emptyTitle"
            emptySubtitle="teamTickets.emptySubtitle"
            renderActions={renderRowActions}
            sortBy={sortBy} sortDir={sortDir} onSort={toggleSort}
          />
        </ListLoadingOverlay>

        <PaginationBar
          page={page} totalPages={totalPages} totalItems={totalItems}
          size={size} onPageChange={setPage} onSizeChange={setSize}
        />
      </div>

      <AgentSelectionModal
        isOpen={assignModal.open}
        onClose={() => setAssignModal({ open: false, ticketId: null, productId: null, excludeAgentIds: [] })}
        onSuccess={handleAssignSuccess}
        ticketId={assignModal.ticketId}
        productId={assignModal.productId}
        excludeAgentIds={assignModal.excludeAgentIds}
      />
    </>
  );
}
