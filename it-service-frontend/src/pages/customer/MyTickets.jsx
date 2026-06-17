import { useMemo, useState } from 'react';
import { Plus } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useTicketList } from '../../hooks/useTicketList';
import { useUrlState } from '../../hooks/useUrlState';
import TicketTable from '../../components/TicketTable';
import TicketFilters from '../../components/TicketFilters';
import PaginationBar from '../../components/PaginationBar';
import CreateTicketModal from '../../components/CreateTicketModal';
import ListLoadingOverlay from '../../components/ListLoadingOverlay';
import Button from '../../components/Button';

const ACTIVE_STATUSES = ['NEW', 'IN_PROGRESS', 'WAITING_FOR_CUSTOMER', 'RESOLVED'];
const CLOSED_STATUSES = ['CLOSED'];

export default function MyTickets() {
  const { t } = useTranslation();
  const [modalOpen, setModalOpen] = useState(false);
  const { str, setParams: setUrlParams } = useUrlState();
  const tab = str('tab', 'active');

  const TABS = [
    { key: 'active', label: t('ticket.myTickets.tabActive') },
    { key: 'closed', label: t('ticket.myTickets.tabClosed') },
  ];

  // Default status scope per tab. The user's own status filter (if any) still
  // overrides this in useTicketList; we only narrow the *unfiltered* default
  // so the active tab never shows CLOSED and vice versa.
  const extraParams = useMemo(
    () => ({ status: tab === 'closed' ? CLOSED_STATUSES : ACTIVE_STATUSES }),
    [tab],
  );

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
    refetch,
  } = useTicketList('/tickets', { sortBy: 'createdAt', sortDir: 'desc', extraParams });

  const handleTabChange = (tabKey) => {
    // Write tab + clear status in one atomic URL update.
    setUrlParams({ tab: tabKey === 'active' ? '' : tabKey, status: [] });
  };

  return (
    <>
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-6">
        <div>
          <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>{t('ticket.myTickets.title')}</h1>
          <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>{t('ticket.myTickets.subtitle')}</p>
        </div>
        <Button
          onClick={() => setModalOpen(true)}
          data-tour="mytickets-new"
          fullWidth
          className="sm:w-auto py-2.5 hover:shadow-lg hover:shadow-primary-500/25"
        >
          <Plus className="h-4 w-4" />
          {t('ticket.myTickets.newTicket')}
        </Button>
      </div>

      {/* Tabs */}
      <div data-tour="mytickets-tabs" className="flex gap-0 mb-5 overflow-x-auto">
        {TABS.map((tabItem, i) => {
          const active = tab === tabItem.key;
          return (
            <button
              key={tabItem.key}
              className={`inline-flex items-center gap-2 px-4 py-2 text-sm font-medium border transition-colors cursor-pointer ${
                i === 0 ? 'rounded-l-lg' : 'border-l-0 rounded-r-lg'
              } ${active ? 'bg-primary-500 text-white border-primary-500' : 'border-[var(--border-color)] hover:bg-[var(--bg-surface-hover)]'}`}
              style={!active ? { color: 'var(--text-secondary)', backgroundColor: 'var(--bg-surface)' } : {}}
              onClick={() => handleTabChange(tabItem.key)}
            >
              {tabItem.label}
              {active && !loading && (
                <span className="inline-flex items-center justify-center min-w-[20px] h-5 px-1.5 text-[11px] font-bold rounded-full bg-white/20">
                  {totalItems}
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

      <div data-tour="mytickets-list" className="rounded-xl border overflow-hidden" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
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
          hideStatus={tab === 'closed'}
          statusOptions={ACTIVE_STATUSES}
          hideAgent
        />

        <ListLoadingOverlay initial={initialLoading} loading={loading}>
          <TicketTable
            tickets={tickets}
            showSla={tab === 'active'}
            sortBy={sortBy} sortDir={sortDir} onSort={toggleSort}
          />
        </ListLoadingOverlay>

        <PaginationBar
          page={page} totalPages={totalPages} totalItems={totalItems}
          size={size} onPageChange={setPage} onSizeChange={setSize}
        />
      </div>

      <CreateTicketModal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        onCreated={() => refetch()}
      />
    </>
  );
}
