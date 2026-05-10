import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import api from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import { useTicketList } from '../../hooks/useTicketList';
import { StatusBadge, PriorityBadge } from '../../components/Badges';
import SlaTimerBadge from '../../components/SlaTimerBadge';
import TicketFilters from '../../components/TicketFilters';
import PaginationBar from '../../components/PaginationBar';
import AgentSelectionModal from '../../components/AgentSelectionModal';
import { AlertTriangle, ArrowUp, ArrowDown, ArrowUpDown, Inbox, Users } from 'lucide-react';

function SortTh({ field, label, invertArrow = false, sortBy, sortDir, toggleSort }) {
  return (
    <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b"
      style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>
      <button type="button" onClick={() => toggleSort(field)}
        className="inline-flex items-center gap-1 cursor-pointer hover:opacity-80 transition-opacity"
        style={{ color: sortBy === field ? '#3b82f6' : 'var(--text-tertiary)' }}>
        {label}
        {sortBy === field
          ? (() => {
              const displayDir = invertArrow ? (sortDir === 'asc' ? 'desc' : 'asc') : sortDir;
              return displayDir === 'asc' ? <ArrowUp className="h-3 w-3" /> : <ArrowDown className="h-3 w-3" />;
            })()
          : <ArrowUpDown className="h-3 w-3" />
        }
      </button>
    </th>
  );
}

export default function TeamTickets() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { user, hasRole } = useAuth();
  const [joiningId, setJoiningId] = useState(null);
  const [tickSeconds, setTickSeconds] = useState(0);
  const [assignModal, setAssignModal] = useState({ open: false, ticketId: null, productId: null });

  const currentUserId = user?.sub || user?.id;
  const isAgentAdmin = hasRole('AGENT_ADMIN');

  const {
    tickets, totalPages, totalItems, loading, error,
    page, setPage, size, setSize,
    sortBy, sortDir, toggleSort,
    priority, setPriority,
    search, setSearch,
    productIds, setProductIds,
    agentId, setAgentId,
    slaStatuses, setSlaStatuses,
    dateFrom, setDateFrom,
    dateTo, setDateTo,
    clearFilters,
    refetch,
  } = useTicketList('/tickets/team', { sortBy: 'createdAt', sortDir: 'desc' });

  const displayedTickets = tickets.filter(
    (tk) => !tk.claimers?.some((c) => c.agentId === currentUserId)
  );

  useEffect(() => {
    const timer = setInterval(() => setTickSeconds((v) => v + 1), 1000);
    return () => clearInterval(timer);
  }, []);

  const handleJoin = async (ticketId, e) => {
    e.stopPropagation();
    setJoiningId(ticketId);
    try {
      await api.put(`/tickets/${ticketId}/claim`);
      navigate(`/tickets/${ticketId}`);
    } catch (err) {
      alert(err.response?.data?.message || 'Could not join ticket.');
      setJoiningId(null);
    }
  };

  const handleOpenAssign = (ticket, e) => {
    e.stopPropagation();
    setAssignModal({ open: true, ticketId: ticket.id, productId: ticket.productId });
  };

  const handleAssignSuccess = () => {
    setAssignModal({ open: false, ticketId: null, productId: null });
    refetch();
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('en-US', {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit',
    });
  };

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

      <div className="rounded-xl border overflow-hidden"
        style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>

        <TicketFilters
          priority={priority}   onPriority={setPriority}
          search={search}       onSearch={setSearch}
          productIds={productIds} onProductIds={setProductIds}
          agentId={agentId}       onAgentId={setAgentId}
          slaStatuses={slaStatuses} onSlaStatuses={setSlaStatuses}
          dateFrom={dateFrom}   onDateFrom={setDateFrom}
          dateTo={dateTo}       onDateTo={setDateTo}
          onClear={clearFilters}
          hideStatus
        />

        {loading ? (
          <div className="flex items-center justify-center py-20">
            <div className="h-8 w-8 rounded-full border-[3px] animate-spin"
              style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
          </div>
        ) : displayedTickets.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 px-8" style={{ color: 'var(--text-tertiary)' }}>
            <Inbox className="h-12 w-12 mb-4 opacity-30" />
            <h3 className="text-lg font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>{t('teamTickets.emptyTitle')}</h3>
            <p className="text-sm">{t('teamTickets.emptySubtitle')}</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
                  <SortTh field="id"          label={t('ticket.table.id')}       sortBy={sortBy} sortDir={sortDir} toggleSort={toggleSort} />
                  <SortTh field="title"       label={t('ticket.table.title')}    sortBy={sortBy} sortDir={sortDir} toggleSort={toggleSort} />
                  <SortTh field="status"      label={t('ticket.table.status')}   sortBy={sortBy} sortDir={sortDir} toggleSort={toggleSort} />
                  <SortTh field="priority"    label={t('ticket.table.priority')} sortBy={sortBy} sortDir={sortDir} toggleSort={toggleSort} invertArrow />
                  <SortTh field="slaDeadline" label={t('ticket.table.sla')}      sortBy={sortBy} sortDir={sortDir} toggleSort={toggleSort} />
                  <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b"
                    style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>{t('ticket.table.claimers')}</th>
                  <SortTh field="createdAt"   label={t('ticket.table.created')}  sortBy={sortBy} sortDir={sortDir} toggleSort={toggleSort} />
                  <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b"
                    style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>{t('ticket.table.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {displayedTickets.map((ticket) => (
                  <tr key={ticket.id}
                    onClick={() => navigate(`/tickets/${ticket.id}`)}
                    className="cursor-pointer transition-colors duration-150"
                    style={{ borderBottom: '1px solid var(--border-color-light)' }}
                    onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = 'var(--bg-surface-hover)')}
                    onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}>
                    <td className="px-4 py-3 text-sm font-semibold text-primary-500">
                      TCK-{String(ticket.id).padStart(3, '0')}
                    </td>
                    <td className="px-4 py-3 text-sm" style={{ color: 'var(--text-primary)' }}>
                      <div className="flex items-center gap-2 min-w-0">
                        <span className="font-medium truncate" title={ticket.title}>{ticket.title}</span>
                        {ticket.slaBreached && (
                          <span className="inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-bold"
                            style={{ backgroundColor: '#fee2e2', color: '#991b1b' }}>
                            <AlertTriangle className="h-3 w-3" />SLA
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-4 py-3"><StatusBadge status={ticket.status} /></td>
                    <td className="px-4 py-3"><PriorityBadge priority={ticket.priority} /></td>
                    <td className="px-4 py-3"><SlaTimerBadge ticket={ticket} tickSeconds={tickSeconds} /></td>
                    <td className="px-4 py-3">
                      <ClaimerAvatars claimers={ticket.claimers} currentUserId={currentUserId} youLabel={t('ticket.table.you')} />
                    </td>
                    <td className="px-4 py-3 text-sm" style={{ color: 'var(--text-secondary)' }}>
                      {formatDate(ticket.createdAt)}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <button
                          className="inline-flex items-center gap-1 rounded-lg px-3 py-1.5 text-xs font-semibold text-white bg-emerald-600 hover:bg-emerald-700 transition-colors cursor-pointer disabled:opacity-50"
                          disabled={joiningId === ticket.id}
                          onClick={(e) => handleJoin(ticket.id, e)}>
                          <Users className="h-3 w-3" />
                          {joiningId === ticket.id ? t('teamTickets.joining') : t('teamTickets.join')}
                        </button>
                        {isAgentAdmin && (
                          <button
                            className="inline-flex items-center rounded-lg px-3 py-1.5 text-xs font-semibold text-white bg-amber-500 hover:bg-amber-600 transition-colors cursor-pointer"
                            onClick={(e) => handleOpenAssign(ticket, e)}>
                            {t('ticket.actions.assign')}
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
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

function ClaimerAvatars({ claimers, currentUserId, youLabel }) {
  if (!claimers || claimers.length === 0) {
    return <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>—</span>;
  }
  return (
    <div className="flex flex-wrap gap-1">
      {claimers.map((c) => (
        <span key={c.agentId} title={c.agentName}
          className="inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium"
          style={c.agentId === currentUserId
            ? { backgroundColor: '#dbeafe', color: '#1d4ed8' }
            : { backgroundColor: 'var(--bg-surface-secondary)', color: 'var(--text-secondary)' }}>
          {c.agentName?.split(' ')[0] ?? 'Agent'}
          {c.agentId === currentUserId && ` (${youLabel})`}
        </span>
      ))}
    </div>
  );
}
