import { lazy, Suspense, useCallback, useEffect, useState } from 'react';
import { useParams, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ArrowLeft, CalendarRange, CheckCircle2, Clock3, Package, RefreshCw, ShieldAlert, Star, TicketCheck } from 'lucide-react';
import metricService from '../../services/metricService';
import KpiCard from '../../components/dashboard/KpiCard';
import StatusDistributionChart from '../../components/dashboard/StatusDistributionChart';
import AgentPerformanceTable from '../../components/dashboard/AgentPerformanceTable';
import RecentTicketsList from '../../components/dashboard/RecentTicketsList';
import SkeletonLoader from '../../components/SkeletonLoader';
import ErrorBoundary from '../../components/ErrorBoundary';
import Reveal from '../../components/Reveal';
import { localizedName } from '../../utils/localizedName';
import { useToast } from '../../context/ToastContext';

const TicketTimelineChart = lazy(() => import('../../components/dashboard/TicketTimelineChart'));

const DATE_RANGE_OPTIONS = [7, 30, 90, null];
const DEFAULT_DATE_RANGE = 30;

const formatNumber = (v) => new Intl.NumberFormat('en-US').format(v ?? 0);
const formatHours = (v) => `${Number(v ?? 0).toFixed(1)}h`;

const PRIORITY_KEYS = ['critical', 'high', 'medium', 'low'];
const PRIORITY_COLORS = {
  critical: '#dc2626',
  high: '#ea580c',
  medium: '#d97706',
  low: '#16a34a',
};

/**
 * Tek bir ürünün dashboard'u. Products panel'inden ya da yönetici dashboard'undaki
 * ürün tablosundan açılır. Ürün adı önce dashboard yanıtından (tr/en), yoksa
 * yönlendirme state'inden aktif dile göre çözülür.
 */
export default function ProductDashboard() {
  const { productId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const toast = useToast();

  const [dateRange, setDateRange] = useState(DEFAULT_DATE_RANGE);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async ({ silent = false } = {}) => {
    try {
      if (silent) setRefreshing(true); else setLoading(true);
      // null (All time) → 0: backend pencereyi ilk veri tarihinden başlatır.
      const res = await metricService.getProductDashboard(productId, dateRange ?? 0);
      setData(res);
    } catch (err) {
      console.error('Product dashboard could not be loaded:', err);
      toast.error(err.response?.data?.message || t('productDashboard.loadError'));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [productId, dateRange, t, toast]);

  useEffect(() => { load(); }, [load]);

  const productName = localizedName(data, 'productName') || localizedName(location.state?.product) || `#${productId}`;

  const handleAgentClick = useCallback((agent) => {
    if (!agent?.agentId) return;
    navigate(`/users/${agent.agentId}/performance`, {
      state: { user: { id: agent.agentId, fullName: agent.agentName, role: agent.role, roles: agent.role ? [agent.role] : [] } },
    });
  }, [navigate]);

  const kpis = [
    {
      title: t('productDashboard.kpiOpen'),
      value: formatNumber(data?.openTickets),
      detail: t('productDashboard.kpiOpenDetail', { count: formatNumber(data?.totalTickets) }),
      icon: TicketCheck,
      accent: 'bg-primary-500',
    },
    {
      title: t('productDashboard.kpiResolved'),
      value: formatNumber(data?.resolvedTickets),
      detail: t('productDashboard.kpiResolvedDetail', { hours: formatHours(data?.avgResolutionHours) }),
      icon: CheckCircle2,
      accent: 'bg-accent-500',
    },
    {
      title: t('productDashboard.kpiSla'),
      value: formatNumber(data?.slaBreachedCount),
      detail: t('productDashboard.kpiSlaDetail', { rate: Number(data?.slaBreachRate ?? 0).toFixed(1) }),
      icon: ShieldAlert,
      accent: 'bg-danger-500',
    },
    {
      title: t('productDashboard.kpiCsat'),
      value: `${Number(data?.csatAverage ?? 0).toFixed(1)}/5`,
      detail: t('productDashboard.kpiCsatDetail', { count: formatNumber(data?.csatCount) }),
      icon: Star,
      accent: 'bg-warning-500',
    },
  ];

  return (
    <div className="space-y-6 animate-fade-in">
      <button
        type="button"
        onClick={() => navigate(-1)}
        className="inline-flex items-center gap-2 rounded-lg border px-3 py-2 text-sm font-semibold transition-colors cursor-pointer hover:bg-primary-50 dark:hover:bg-primary-500/10"
        style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)', backgroundColor: 'var(--bg-surface)' }}
      >
        <ArrowLeft className="h-4 w-4" />
        {t('productDashboard.back')}
      </button>

      <section className="overflow-hidden rounded-3xl border shadow-[0_20px_60px_rgba(15,23,42,0.08)]" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
        <div className="relative px-4 py-5 sm:px-8 sm:py-8">
          <div className="absolute inset-0 bg-gradient-to-br from-primary-50 via-transparent to-accent-50 opacity-70 dark:from-primary-500/10 dark:to-accent-500/10" />
          <div className="relative flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
            <div className="max-w-2xl">
              <div className="mb-3 inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em]" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>
                <Package className="h-3.5 w-3.5" />
                {t('productDashboard.badge')}
              </div>
              <h1 className="text-2xl font-black tracking-tight sm:text-4xl" style={{ color: 'var(--text-primary)' }}>
                {productName}
              </h1>
              <p className="mt-3 max-w-xl text-sm leading-6 sm:text-base" style={{ color: 'var(--text-secondary)' }}>
                {t('productDashboard.description')}
              </p>
            </div>

            <div className="flex w-full flex-col items-stretch gap-2 sm:w-auto sm:flex-row sm:items-center">
              <label className="inline-flex items-center gap-2 rounded-xl border px-3 py-2 text-sm font-semibold" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', boxShadow: 'var(--shadow-sm)' }}>
                <CalendarRange className="h-4 w-4" aria-hidden="true" />
                <span className="sr-only">{t('dashboard.dateRange.label')}</span>
                <select
                  value={dateRange ?? 'all'}
                  onChange={(e) => setDateRange(e.target.value === 'all' ? null : Number(e.target.value))}
                  className="bg-transparent text-sm font-semibold focus:outline-none"
                  style={{ color: 'var(--text-primary)' }}
                  aria-label={t('dashboard.dateRange.label')}
                >
                  {DATE_RANGE_OPTIONS.map((opt) => (
                    <option
                      key={opt ?? 'all'}
                      value={opt ?? 'all'}
                      style={{ backgroundColor: 'var(--bg-input)', color: 'var(--text-primary)' }}
                    >
                      {opt == null ? t('dashboard.dateRange.allTime') : t('dashboard.dateRange.lastDays', { count: opt })}
                    </option>
                  ))}
                </select>
              </label>
              <button
                type="button"
                onClick={() => load({ silent: true })}
                disabled={refreshing}
                className="inline-flex w-full items-center justify-center gap-2 rounded-xl border px-4 py-2.5 text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-60 sm:w-auto"
                style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', boxShadow: 'var(--shadow-sm)' }}
              >
                <RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin' : ''}`} />
                {refreshing ? t('dashboard.refreshing') : t('dashboard.refresh')}
              </button>
            </div>
          </div>
        </div>
      </section>

      <ErrorBoundary>
        <section className="grid grid-cols-2 gap-3 sm:gap-4 md:grid-cols-4">
          {loading
            ? Array.from({ length: 4 }).map((_, i) => <KpiCard key={i} loading />)
            : kpis.map((item) => (
                <KpiCard key={item.title} title={item.title} value={item.value} detail={item.detail} icon={item.icon} accent={item.accent} loading={loading} />
              ))}
        </section>
      </ErrorBoundary>

      {/* Açık biletlerin öncelik dağılımı */}
      <section className="rounded-2xl border p-4 sm:p-5" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
        <h3 className="mb-4 text-sm font-bold" style={{ color: 'var(--text-primary)' }}>
          {t('productDashboard.priorityTitle')}
        </h3>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {PRIORITY_KEYS.map((key) => (
            <div key={key} className="rounded-xl border px-3 py-2.5" style={{ borderColor: 'var(--border-color-light)', backgroundColor: 'var(--bg-surface-secondary)' }}>
              <div className="flex items-center gap-2">
                <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: PRIORITY_COLORS[key] }} />
                <span className="text-[11px] font-semibold uppercase tracking-wide" style={{ color: 'var(--text-tertiary)' }}>
                  {t(`ticket.priority.${key}`)}
                </span>
              </div>
              <div className="mt-1 text-xl font-black" style={{ color: 'var(--text-primary)' }}>
                {loading ? '—' : formatNumber(data?.priorityDistribution?.[key])}
              </div>
            </div>
          ))}
        </div>
      </section>

      <Reveal as="section">
        <StatusDistributionChart data={data?.statusDistribution} loading={loading} />
      </Reveal>

      <Reveal as="section">
        <Suspense fallback={<SkeletonLoader lines={6} />}>
          <TicketTimelineChart
            data={data?.timeline}
            loading={loading}
            badgeLabel={t('productDashboard.trendBadge')}
            title={t('productDashboard.trendTitle')}
            subtitle={t('productDashboard.trendSubtitle')}
          />
        </Suspense>
      </Reveal>

      <Reveal as="section">
        <AgentPerformanceTable data={data?.topAgents} loading={loading} onAgentClick={handleAgentClick} />
      </Reveal>

      <Reveal as="section">
        <RecentTicketsList
          tickets={data?.recentTickets}
          loading={loading}
          title={t('productDashboard.recentTitle')}
          emptyText={t('productDashboard.recentEmpty')}
        />
      </Reveal>
    </div>
  );
}
