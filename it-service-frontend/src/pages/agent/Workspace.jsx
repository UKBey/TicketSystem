import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../../context/AuthContext';
import { getAgentLimits } from '../../services/api';
import { localizedName } from '../../utils/localizedName';
import { useTicketList } from '../../hooks/useTicketList';
import TicketTable from '../../components/TicketTable';
import TicketFilters from '../../components/TicketFilters';
import PaginationBar from '../../components/PaginationBar';
import ListLoadingOverlay from '../../components/ListLoadingOverlay';

export default function Workspace() {
  const { t } = useTranslation();
  const { user, isLeadAgent, isAdmin } = useAuth();
  const [agentLimits, setAgentLimits] = useState([]);
  const currentUserId = user?.sub || user?.id;
  // Süpervizör görünümü (kendi ürün-bazlı bilet limitleri kartları) — lead agent veya admin.
  const showLimitCards = isLeadAgent || isAdmin;

  // NEW Pool'da, CLOSED History'de listelenir — Workspace yalnız aktif statüleri kapsar.
  const WORKSPACE_STATUSES = ['IN_PROGRESS', 'WAITING_FOR_CUSTOMER', 'RESOLVED'];

  const {
    tickets, totalPages, totalItems, loading, initialLoading, error,
    page, setPage, size, setSize,
    sortBy, sortDir, toggleSort,
    status, setStatus,
    priority, setPriority,
    search, setSearch,
    productIds, setProductIds,
    topicIds, setTopicIds,
    slaStatuses, setSlaStatuses,
    dateFrom, setDateFrom,
    dateTo, setDateTo,
    setDateRange,
    clearFilters,
  } = useTicketList('/tickets/my-assigned', {
    sortBy: 'createdAt',
    sortDir: 'desc',
    extraParams: { status: WORKSPACE_STATUSES },
  });

  useEffect(() => {
    if (!showLimitCards || !currentUserId) return;
    getAgentLimits(currentUserId)
      .then((res) => setAgentLimits(res.data))
      .catch((err) => console.error('Could not load agent limits:', err));
  }, [currentUserId, showLimitCards]);

  return (
    <>
      <div className="mb-6">
        <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>{t('workspace.title')}</h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>{t('workspace.subtitle')}</p>
      </div>

      {!loading && agentLimits.length > 0 && (
        <div className="mb-6 grid grid-cols-2 md:grid-cols-2 lg:grid-cols-3 gap-3 sm:gap-4">
          {agentLimits.map(limit => {
            const activeCount = tickets.filter(tk => tk.productId === limit.productId).length;
            const effectiveLimit = limit.effectiveLimit;
            return (
              <div key={limit.productId} className="rounded-lg border p-4 min-w-0"
                style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
                <p className="text-xs font-semibold mb-2 truncate" style={{ color: 'var(--text-tertiary)' }} title={localizedName(limit, 'productName')}>{localizedName(limit, 'productName')}</p>
                <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
                  <span className="text-xl sm:text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>{activeCount}</span>
                  {effectiveLimit && (
                    <>
                      <span style={{ color: 'var(--text-tertiary)' }}>/</span>
                      <span className="text-base sm:text-lg font-semibold" style={{ color: 'var(--text-secondary)' }}>{effectiveLimit}</span>
                    </>
                  )}
                  <span className="text-xs break-words" style={{ color: 'var(--text-tertiary)' }}>{t('workspace.activeTickets')}</span>
                </div>
                {effectiveLimit && activeCount >= effectiveLimit && (
                  <p className="text-xs mt-2 font-semibold text-danger-600 dark:text-danger-400">{t('workspace.limitExceeded')}</p>
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

      <div data-tour="ws-list" className="rounded-xl border overflow-hidden" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
        <TicketFilters
          status={status}       onStatus={setStatus}
          priority={priority}   onPriority={setPriority}
          search={search}       onSearch={setSearch}
          productIds={productIds} onProductIds={setProductIds}
          topicIds={topicIds}     onTopicIds={setTopicIds}
          slaStatuses={slaStatuses} onSlaStatuses={setSlaStatuses}
          dateFrom={dateFrom}   onDateFrom={setDateFrom}
          dateTo={dateTo}       onDateTo={setDateTo}
          onDateRange={setDateRange}
          onClear={clearFilters}
          statusOptions={WORKSPACE_STATUSES}
          hideAgent
        />

        <ListLoadingOverlay initial={initialLoading} loading={loading}>
          <TicketTable
            tickets={tickets}
            showSla
            currentUserId={currentUserId}
            sortBy={sortBy} sortDir={sortDir} onSort={toggleSort}
          />
        </ListLoadingOverlay>

        <PaginationBar
          page={page} totalPages={totalPages} totalItems={totalItems}
          size={size} onPageChange={setPage} onSizeChange={setSize}
        />
      </div>
    </>
  );
}
