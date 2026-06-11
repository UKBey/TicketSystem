import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import { useTicketList } from '../hooks/useTicketList';
import TicketTable from '../components/TicketTable';
import TicketFilters from '../components/TicketFilters';
import PaginationBar from '../components/PaginationBar';
import ProductTopicsSection from '../components/ProductTopicsSection';
import { localizedName } from '../utils/localizedName';
import { ArrowLeft, Package, AlertTriangle, Ticket, Activity, CheckCircle, Settings } from 'lucide-react';

export default function ProductPage() {
  const { t } = useTranslation();
  const { id } = useParams();
  const navigate = useNavigate();
  const { isLeadAgent, isAdmin, isManager } = useAuth();
  // "Ürünleri yönet" kısayolu sistem-config sayfasına gider (admin/manager).
  const canManageProducts = isAdmin || isManager;
  // Konu (topic) yönetimi ürün içeriğidir — lead agent veya admin.
  const canManageTopics = isLeadAgent || isAdmin;

  const [product, setProduct] = useState(null);
  const [productLoading, setProductLoading] = useState(true);

  const {
    tickets, totalPages, totalItems, loading: ticketsLoading, error,
    page, setPage, size, setSize,
    sortBy, sortDir, toggleSort,
    status, setStatus,
    priority, setPriority,
    search, setSearch,
    agentIds, setAgentIds,
    topicIds, setTopicIds,
    slaStatuses, setSlaStatuses,
    dateFrom, setDateFrom,
    dateTo, setDateTo,
    clearFilters,
  } = useTicketList(`/tickets/by-product/${id}`, { sortBy: 'createdAt', sortDir: 'desc' });

  useEffect(() => {
    api.get(`/products/${id}`)
      .then((res) => setProduct(res.data))
      .catch((err) => console.error('Could not load product:', err))
      .finally(() => setProductLoading(false));
  }, [id]);

  if (productLoading) {
    return (
      <div className="flex items-center justify-center py-40">
        <div className="h-8 w-8 rounded-full border-[3px] animate-spin"
          style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
      </div>
    );
  }

  if (!product) {
    return (
      <div className="flex flex-col items-center justify-center py-24 gap-3" style={{ color: 'var(--text-tertiary)' }}>
        <Package className="h-12 w-12 opacity-30" />
        <p className="text-sm">{t('product.notFound')}</p>
        <button onClick={() => navigate('/products')}
          className="mt-2 text-sm font-medium text-primary-500 hover:underline cursor-pointer">
          {t('product.backToProducts')}
        </button>
      </div>
    );
  }

  return (
    <>
      <div className="mb-6">
        <button onClick={() => navigate('/products')}
          className="inline-flex items-center gap-1.5 text-sm font-medium mb-4 transition-colors cursor-pointer"
          style={{ color: 'var(--text-secondary)' }}
          onMouseEnter={(e) => (e.currentTarget.style.color = 'var(--text-primary)')}
          onMouseLeave={(e) => (e.currentTarget.style.color = 'var(--text-secondary)')}>
          <ArrowLeft className="h-4 w-4" />
          {t('product.backToProducts')}
        </button>

        <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3">
          <div className="flex items-center gap-3 min-w-0">
            <div className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-xl"
              style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
              <Package className="h-6 w-6" style={{ color: 'var(--text-secondary)' }} />
            </div>
            <div className="min-w-0">
              <h1 className="text-xl sm:text-2xl font-bold break-words" style={{ color: 'var(--text-primary)' }}>{localizedName(product)}</h1>
              <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold mt-1 ${
                product.isActive
                  ? 'bg-accent-100 text-accent-700 dark:bg-accent-500/20 dark:text-accent-300'
                  : 'bg-slate-100 text-slate-600 dark:bg-slate-700/50 dark:text-slate-300'
              }`}>
                {product.isActive ? t('product.statusActive') : t('product.statusInactive')}
              </span>
            </div>
          </div>
          {canManageProducts && (
            <button onClick={() => navigate('/products')}
              className="inline-flex items-center justify-center gap-2 rounded-lg border px-3 py-2 text-sm font-medium transition-colors cursor-pointer self-start sm:self-auto flex-shrink-0"
              style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>
              <Settings className="h-4 w-4" />
              {t('product.manageProducts')}
            </button>
          )}
        </div>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4 mb-6">
        <StatCard label={t('product.statTotal')}      value={totalItems}                                                                                    icon={Ticket}        color="#3b82f6" />
        <StatCard label={t('product.statActive')}     value={tickets.filter(tk => ['NEW','IN_PROGRESS','WAITING_FOR_CUSTOMER'].includes(tk.status)).length}  icon={Activity}      color="#f59e0b" />
        <StatCard label={t('product.statSlaBreached')} value={tickets.filter(tk => tk.slaBreached).length}                                                   icon={AlertTriangle}  color="#ef4444" />
        <StatCard label={t('product.statResolved')}   value={tickets.filter(tk => tk.status === 'RESOLVED').length}                                          icon={CheckCircle}   color="#10b981" />
      </div>

      <ProductTopicsSection productId={id} isAdmin={canManageTopics} />

      {error && (
        <div className="rounded-lg px-4 py-3 mb-4 text-sm font-medium bg-danger-50 text-danger-600 dark:bg-danger-500/10 dark:text-danger-400">
          {error}
        </div>
      )}

      <div className="rounded-xl border overflow-hidden"
        style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
        <div className="px-4 sm:px-6 py-4 border-b text-sm font-semibold flex flex-wrap items-center justify-between gap-2"
          style={{ borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}>
          <span>{t('product.ticketsSection')}</span>
          <span className="text-xs font-normal" style={{ color: 'var(--text-tertiary)' }}>{totalItems} total</span>
        </div>

        <TicketFilters
          status={status}       onStatus={setStatus}
          priority={priority}   onPriority={setPriority}
          search={search}       onSearch={setSearch}
          agentIds={agentIds}   onAgentIds={setAgentIds}
          topicIds={topicIds}   onTopicIds={setTopicIds}
          slaStatuses={slaStatuses} onSlaStatuses={setSlaStatuses}
          dateFrom={dateFrom}   onDateFrom={setDateFrom}
          dateTo={dateTo}       onDateTo={setDateTo}
          onClear={clearFilters}
          hideProduct
          scopedProductId={Number(id)}
        />

        {ticketsLoading ? (
          <div className="flex items-center justify-center py-16">
            <div className="h-8 w-8 rounded-full border-[3px] animate-spin"
              style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
          </div>
        ) : (
          <TicketTable
            tickets={tickets}
            showSla
            showTopic={false}
            forceShowClaimers={false}
            sortBy={sortBy}
            sortDir={sortDir}
            onSort={toggleSort}
            emptyTitle="product.noTickets"
            emptySubtitle="ticket.empty.subtitle"
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

function StatCard({ label, value, icon: Icon, color }) {
  return (
    <div className="rounded-xl border p-5"
      style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
      <div className="flex items-center justify-between mb-3">
        <span className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--text-tertiary)' }}>
          {label}
        </span>
        <div className="flex h-8 w-8 items-center justify-center rounded-lg" style={{ backgroundColor: `${color}18` }}>
          <Icon className="h-4 w-4" style={{ color }} />
        </div>
      </div>
      <p className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>{value}</p>
    </div>
  );
}
