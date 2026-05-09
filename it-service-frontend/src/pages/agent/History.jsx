import { useTicketList } from '../../hooks/useTicketList';
import TicketTable from '../../components/TicketTable';
import TicketFilters from '../../components/TicketFilters';
import PaginationBar from '../../components/PaginationBar';

export default function History() {
  const {
    tickets, totalPages, totalItems, loading, error,
    page, setPage, size, setSize,
    sortBy, sortDir, toggleSort,
    priority, setPriority,
    search, setSearch,
    productId, setProductId,
    slaStatus, setSlaStatus,
    dateFrom, setDateFrom,
    dateTo, setDateTo,
    clearFilters,
  } = useTicketList('/tickets/my-assigned', {
    sortBy: 'createdAt',
    sortDir: 'desc',
    extraParams: { status: 'CLOSED' },
  });

  return (
    <>
      <div className="mb-6">
        <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>History</h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>Previously closed tickets you worked on.</p>
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
          productId={productId} onProductId={setProductId}
          slaStatus={slaStatus} onSlaStatus={setSlaStatus}
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
