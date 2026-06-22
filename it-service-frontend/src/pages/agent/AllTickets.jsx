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

export default function AllTickets() {
  const { t } = useTranslation();
  const toast = useToast();
  const navigate = useNavigate();
  const { user, isAgent, isLeadAgent, isAdmin, isManager } = useAuth();
  const currentUserId = user?.sub || user?.id;
  const canSeeCsat = isAdmin || isManager;

  // Eylem yetkileri: claim/join agent-seviyesi (agent veya lead), atama lead/admin.
  const canClaim = isAgent;
  const canAssign = isLeadAgent || isAdmin;
  // Kullanıcının hiçbir eylem hakkı yoksa actions sütunu hiç gösterilmez.
  const showActions = canClaim || canAssign;

  const [joiningId, setJoiningId] = useState(null);
  const [assignModal, setAssignModal] = useState({ open: false, ticketId: null, productId: null });

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
    csatRatings, setCsatRatings,
    dateFrom, setDateFrom,
    dateTo, setDateTo,
    setDateRange,
    clearFilters,
    refetch,
  } = useTicketList('/tickets/all', { sortBy: 'createdAt', sortDir: 'desc' });

  const handleClaim = async (ticketId) => {
    setJoiningId(ticketId);
    try {
      await api.put(`/tickets/${ticketId}/claim`);
      navigate(`/tickets/${ticketId}`);
    } catch (err) {
      if (err.response?.status === 409 && err.response?.data?.error === 'TICKET_LIMIT_EXCEEDED') {
        toast.error(t('ticketDetail.limitExceeded', { message: err.response.data.message }));
      } else {
        toast.error(err.response?.data?.message || t('ticketDetail.claimFailed'));
      }
      setJoiningId(null);
    }
  };

  const handleOpenAssign = (ticket) => {
    setAssignModal({ open: true, ticketId: ticket.id, productId: ticket.productId });
  };

  const handleAssignSuccess = () => {
    setAssignModal({ open: false, ticketId: null, productId: null });
    refetch();
  };

  // Satır eylemleri: claim/join (kullanıcı zaten üstlenmediyse) ve atama. Kapalı
  // biletlerde ikisi de gizlenir; ikisi de boşsa hücre boş kalır.
  const renderRowActions = (ticket) => {
    const alreadyClaimed = ticket.claimers?.some((c) => c.agentId === currentUserId);
    const isTerminal = ticket.status === 'CLOSED';
    const showClaim = canClaim && !alreadyClaimed && !isTerminal;
    const showAssign = canAssign && !isTerminal;
    if (!showClaim && !showAssign) return null;

    const hasClaimers = ticket.claimers?.length > 0;
    return (
      <>
        {/* Claim/join için sabit genişlikli slot — claim gizliyken (zaten üstlenildi
            vb.) lg'de boş yer tutucu kalır ki tüm assign butonları aynı hizada
            başlasın. Mobil dikey kartta boş slot gizlenir. */}
        {canClaim && (
          <div className={`shrink-0 lg:w-24 ${showClaim ? '' : 'hidden lg:block'}`}>
            {showClaim && (
              <Button
                type="button"
                variant="success"
                size="sm"
                fullWidth
                disabled={joiningId === ticket.id}
                onClick={(e) => { e.stopPropagation(); handleClaim(ticket.id); }}
                className="lg:py-1.5 gap-1"
              >
                <Users className="h-3 w-3" />
                {joiningId === ticket.id
                  ? t('teamTickets.joining')
                  : hasClaimers ? t('teamTickets.join') : t('ticket.actions.claim')}
              </Button>
            )}
          </div>
        )}
        {showAssign && (
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
  };

  return (
    <>
      <div className="mb-6">
        <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>{t('allTickets.title')}</h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>
          {t('allTickets.subtitle')}
        </p>
      </div>

      {error && (
        <div className="rounded-lg px-4 py-3 mb-4 text-sm font-medium bg-danger-50 text-danger-600 dark:bg-danger-500/10 dark:text-danger-400">
          {error}
        </div>
      )}

      <div className="rounded-xl border overflow-hidden"
        style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>

        <TicketFilters
          status={status}       onStatus={setStatus}
          priority={priority}   onPriority={setPriority}
          search={search}       onSearch={setSearch}
          productIds={productIds} onProductIds={setProductIds}
          agentIds={agentIds}     onAgentIds={setAgentIds}
          topicIds={topicIds}     onTopicIds={setTopicIds}
          slaStatuses={slaStatuses} onSlaStatuses={setSlaStatuses}
          csatRatings={csatRatings} onCsatRatings={setCsatRatings}
          dateFrom={dateFrom}   onDateFrom={setDateFrom}
          dateTo={dateTo}       onDateTo={setDateTo}
          onDateRange={setDateRange}
          onClear={clearFilters}
          showCsat={canSeeCsat}
          statusOptions={['NEW', 'IN_PROGRESS', 'WAITING_FOR_CUSTOMER', 'RESOLVED', 'CLOSED']}
        />

        <ListLoadingOverlay initial={initialLoading} loading={loading}>
          <TicketTable
            tickets={tickets}
            showSla
            showCsat={canSeeCsat}
            currentUserId={currentUserId}
            showTopic={false}
            forceShowClaimers
            emptyTitle="allTickets.emptyTitle"
            emptySubtitle="allTickets.emptySubtitle"
            renderActions={showActions ? renderRowActions : undefined}
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
        onClose={() => setAssignModal({ open: false, ticketId: null, productId: null })}
        onSuccess={handleAssignSuccess}
        ticketId={assignModal.ticketId}
        productId={assignModal.productId}
      />
    </>
  );
}
