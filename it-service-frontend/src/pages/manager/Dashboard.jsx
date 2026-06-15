import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { formatTime } from '../../utils/dateFormat';
import { ArrowUpRight, CalendarRange, Clock3, LayoutDashboard, RefreshCw, ShieldAlert, Star } from 'lucide-react';
import metricService from '../../services/metricService';
import KpiCard from '../../components/dashboard/KpiCard';
import StatusDistributionChart from '../../components/dashboard/StatusDistributionChart';
import AgentPerformanceTable from '../../components/dashboard/AgentPerformanceTable';
import PrioritySLAChart from '../../components/dashboard/PrioritySLAChart';
import ProductMetricsChart from '../../components/dashboard/ProductMetricsChart';
import CSATGaugeChart from '../../components/dashboard/CSATGaugeChart';
import WorklogCompletionChart from '../../components/dashboard/WorklogCompletionChart';
import CompletionMeters from '../../components/dashboard/CompletionMeters';
import TopAgentsBar from '../../components/dashboard/TopAgentsBar';
import AlertBanner from '../../components/dashboard/AlertBanner';
import ErrorBoundary from '../../components/ErrorBoundary';
import SkeletonLoader from '../../components/SkeletonLoader';
import Reveal from '../../components/Reveal';
import { usePolling } from '../../hooks/usePolling';
import { useToast } from '../../context/ToastContext';

const TicketTimelineChart = lazy(() => import('../../components/dashboard/TicketTimelineChart'));

const DEFAULT_SUMMARY = {
  totalOpenTickets: 0,
  newTicketsLast24Hours: 0,
  slaBreachedCount: 0,
  slaBreachedPercentage: 0,
  avgResponseTimeHours: 0,
  csatAverage: 0,
  csatTotalResponses: 0,
  priorityDistribution: {
    critical: 0,
    high: 0,
    medium: 0,
    low: 0,
  },
};

function formatNumber(value) {
  return new Intl.NumberFormat('en-US').format(value ?? 0);
}

function formatHours(value) {
  if (value === null || value === undefined) {
    return '0.0h';
  }
  return `${Number(value).toFixed(1)}h`;
}

const DATE_RANGE_OPTIONS = [7, 30, 90, null];
const DEFAULT_DATE_RANGE = 30;

export default function Dashboard() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const toast = useToast();

  // Leaderboard'da bir ajana tıklayınca o ajanın performans chart'larına git.
  const handleAgentClick = useCallback((agent) => {
    if (!agent?.agentId) return;
    navigate(`/users/${agent.agentId}/performance`, {
      state: { user: { id: agent.agentId, fullName: agent.agentName, role: agent.role, roles: agent.role ? [agent.role] : [] } },
    });
  }, [navigate]);

  // Ürün metrik tablosunda bir ürüne tıklayınca o ürünün dashboard'una git.
  const handleProductClick = useCallback((product) => {
    if (!product?.productId) return;
    navigate(`/products/${product.productId}/dashboard`, {
      state: { product: { id: product.productId, nameTr: product.productNameTr, nameEn: product.productNameEn } },
    });
  }, [navigate]);

  const [dateRange, setDateRange] = useState(DEFAULT_DATE_RANGE);
  const [summary, setSummary] = useState(DEFAULT_SUMMARY);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [lastUpdated, setLastUpdated] = useState(null);
  const [statusDistribution, setStatusDistribution] = useState(null);
  const [statusLoading, setStatusLoading] = useState(true);
  const [agentPerformance, setAgentPerformance] = useState(null);
  const [agentLoading, setAgentLoading] = useState(true);
  const [ticketTimeline, setTicketTimeline] = useState({ timeline: [] });
  const [timelineLoading, setTimelineLoading] = useState(true);
  const [prioritySlaMetrics, setPrioritySlaMetrics] = useState({ priorityMetrics: [] });
  const [prioritySlaLoading, setPrioritySlaLoading] = useState(true);
  const [productMetrics, setProductMetrics] = useState({ productMetrics: [] });
  const [productLoading, setProductLoading] = useState(true);
  const [csatMetrics, setCsatMetrics] = useState(null);
  const [csatLoading, setCsatLoading] = useState(true);
  const [worklogCompletion, setWorklogCompletion] = useState(null);
  const [worklogLoading, setWorklogLoading] = useState(true);
  const [alertsData, setAlertsData] = useState(null);
  const [alertsLoading, setAlertsLoading] = useState(true);

  const loadSummary = useCallback(async ({ silent = false } = {}) => {
    try {
      if (silent) {
        setRefreshing(true);
      } else {
        setLoading(true);
      }

      // null (All time) → 0: backend pencereyi ilk veri tarihinden başlatır.
      const timelineDays = dateRange ?? 0;
      const worklogDays = dateRange ?? 0;
      const [summaryRes, statusRes, agentRes, timelineRes, prioritySlaRes, productRes, csatRes, worklogRes] =
        await Promise.allSettled([
          metricService.getDashboardSummary(),
          metricService.getStatusDistribution(),
          metricService.getAgentPerformance(),
          metricService.getTicketTimeline(timelineDays),
          metricService.getPrioritySLAMetrics(dateRange),
          metricService.getProductMetrics(dateRange),
          metricService.getCSATMetrics(3),
          metricService.getWorklogCompletion(worklogDays),
        ]);

      if (summaryRes.status === 'fulfilled') setSummary({ ...DEFAULT_SUMMARY, ...summaryRes.value });
      else toast.error(summaryRes.reason?.response?.data?.message || t('dashboard.loadError'));

      if (statusRes.status     === 'fulfilled') setStatusDistribution(statusRes.value);
      if (agentRes.status      === 'fulfilled') setAgentPerformance(agentRes.value);
      if (timelineRes.status   === 'fulfilled') setTicketTimeline(timelineRes.value ?? { timeline: [] });
      if (prioritySlaRes.status === 'fulfilled') setPrioritySlaMetrics(prioritySlaRes.value ?? { priorityMetrics: [] });
      if (productRes.status    === 'fulfilled') setProductMetrics(productRes.value ?? { productMetrics: [] });
      if (csatRes.status       === 'fulfilled') setCsatMetrics(csatRes.value ?? null);
      if (worklogRes.status    === 'fulfilled') setWorklogCompletion(worklogRes.value ?? null);

      setLastUpdated(new Date());
    } catch (requestError) {
      console.error('Dashboard summary could not be loaded:', requestError);
      toast.error(requestError.response?.data?.message || t('dashboard.loadError'));
    } finally {
      setLoading(false);
      setRefreshing(false);
      setStatusLoading(false);
      setAgentLoading(false);
      setTimelineLoading(false);
      setPrioritySlaLoading(false);
      setProductLoading(false);
      setCsatLoading(false);
      setWorklogLoading(false);
    }
  }, [t, dateRange, toast]);

  const loadAlerts = useCallback(async () => {
    try {
      const data = await metricService.getAlertsAndBacklog();
      setAlertsData(data ?? null);
    } catch {
      // alerts are non-critical; don't show a top-level error
    } finally {
      setAlertsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadSummary();
    loadAlerts();
  }, [loadSummary, loadAlerts]);

  usePolling(loadAlerts, 30_000);

  const kpis = useMemo(() => ([
    {
      title: t('dashboard.kpiOpenTickets'),
      value: formatNumber(summary.totalOpenTickets),
      detail: t('dashboard.kpiOpenDetail', { count: formatNumber(summary.newTicketsLast24Hours) }),
      icon: LayoutDashboard,
      accent: 'bg-primary-500',
    },
    {
      title: t('dashboard.kpiSlaTitle'),
      value: `${formatNumber(summary.slaBreachedCount)}`,
      detail: t('dashboard.kpiSlaDetail', { pct: summary.slaBreachedPercentage?.toFixed(1) ?? '0.0' }),
      icon: ShieldAlert,
      accent: 'bg-danger-500',
    },
    {
      title: t('dashboard.kpiResolutionTitle'),
      value: formatHours(summary.avgResponseTimeHours),
      detail: t('dashboard.kpiResolutionDetail'),
      icon: Clock3,
      accent: 'bg-warning-500',
    },
    {
      title: t('dashboard.kpiCsatTitle'),
      value: `${Number(summary.csatAverage ?? 0).toFixed(1)}/5`,
      detail: t('dashboard.kpiCsatDetail', { count: formatNumber(summary.csatTotalResponses) }),
      icon: Star,
      accent: 'bg-accent-500',
    },
  ]), [summary, t]);

  const syncLabel = lastUpdated
    ? t('dashboard.lastUpdated', { time: formatTime(lastUpdated) })
    : t('dashboard.notYetSynced');

  return (
    <div className="space-y-6 animate-fade-in">
      <section className="overflow-hidden rounded-3xl border shadow-[0_20px_60px_rgba(15,23,42,0.08)]" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
        <div className="relative px-4 py-5 sm:px-8 sm:py-8">
          <div className="absolute inset-0 bg-gradient-to-br from-primary-50 via-transparent to-accent-50 opacity-70 dark:from-primary-500/10 dark:to-accent-500/10" />
          <div className="relative flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
            <div className="max-w-2xl">
              <div className="mb-3 inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em]" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>
                <LayoutDashboard className="h-3.5 w-3.5" />
                {t('dashboard.badge')}
              </div>
              <h1 className="text-2xl font-black tracking-tight sm:text-4xl" style={{ color: 'var(--text-primary)' }}>
                {t('dashboard.heading')}
              </h1>
              <p className="mt-3 max-w-xl text-sm leading-6 sm:text-base" style={{ color: 'var(--text-secondary)' }}>
                {t('dashboard.description')}
              </p>
              <div className="mt-5 flex flex-wrap items-center gap-2 text-xs sm:gap-3 sm:text-sm" style={{ color: 'var(--text-secondary)' }}>
                <span className="inline-flex items-center gap-2 rounded-full border px-3 py-1.5" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
                  <ArrowUpRight className="h-3.5 w-3.5" />
                  {t('dashboard.liveSummary')}
                </span>
                <span className="inline-flex items-center gap-2 rounded-full border px-3 py-1.5" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
                  <RefreshCw className="h-3.5 w-3.5" />
                  {syncLabel}
                </span>
              </div>
            </div>

            <div className="flex w-full flex-col items-stretch gap-2 sm:w-auto sm:flex-row sm:items-center">
              <label
                className="inline-flex items-center gap-2 rounded-xl border px-3 py-2 text-sm font-semibold"
                style={{
                  backgroundColor: 'var(--bg-surface)',
                  borderColor: 'var(--border-color)',
                  color: 'var(--text-primary)',
                  boxShadow: 'var(--shadow-sm)',
                }}
              >
                <CalendarRange className="h-4 w-4" aria-hidden="true" />
                <span className="sr-only">{t('dashboard.dateRange.label')}</span>
                <select
                  value={dateRange ?? 'all'}
                  onChange={(event) => {
                    const value = event.target.value;
                    setDateRange(value === 'all' ? null : Number(value));
                  }}
                  className="bg-transparent text-sm font-semibold focus:outline-none"
                  style={{ color: 'var(--text-primary)' }}
                  aria-label={t('dashboard.dateRange.label')}
                >
                  {DATE_RANGE_OPTIONS.map((option) => (
                    <option key={option ?? 'all'} value={option ?? 'all'}>
                      {option == null
                        ? t('dashboard.dateRange.allTime')
                        : t('dashboard.dateRange.lastDays', { count: option })}
                    </option>
                  ))}
                </select>
              </label>

              <button
                type="button"
                onClick={() => loadSummary({ silent: true })}
                disabled={refreshing}
                className="inline-flex w-full items-center justify-center gap-2 rounded-xl border px-4 py-2.5 text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-60 sm:w-auto"
                style={{
                  backgroundColor: 'var(--bg-surface)',
                  borderColor: 'var(--border-color)',
                  color: 'var(--text-primary)',
                  boxShadow: 'var(--shadow-sm)',
                }}
              >
                <RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin' : ''}`} />
                {refreshing ? t('dashboard.refreshing') : t('dashboard.refresh')}
              </button>
            </div>
          </div>
        </div>
      </section>

      <AlertBanner data={alertsData} loading={alertsLoading} />

      <ErrorBoundary>
        <section className="grid grid-cols-2 gap-3 sm:gap-4 md:grid-cols-4">
          {loading ? (
            Array.from({ length: 4 }).map((_, i) => <KpiCard key={i} loading />)
          ) : (
            kpis.map((item) => (
              <KpiCard
                key={item.title}
                title={item.title}
                value={item.value}
                detail={item.detail}
                icon={item.icon}
                accent={item.accent}
                loading={loading}
              />
            ))
          )}
        </section>
      </ErrorBoundary>

      <Reveal as="section">
        <StatusDistributionChart data={statusDistribution} loading={statusLoading} />
      </Reveal>

      <section className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Reveal className="w-full min-w-0">
          <Suspense fallback={<SkeletonLoader lines={6} />}>
            <TicketTimelineChart data={ticketTimeline} loading={timelineLoading} />
          </Suspense>
        </Reveal>
        <Reveal className="w-full min-w-0" delay={80}>
          <PrioritySLAChart data={prioritySlaMetrics} loading={prioritySlaLoading} />
        </Reveal>
      </section>

      <section className="grid grid-cols-1 gap-4 xl:grid-cols-[3fr_2fr]">
        <Reveal className="w-full min-w-0">
          <AgentPerformanceTable data={agentPerformance} loading={agentLoading} onAgentClick={handleAgentClick} />
        </Reveal>
        <Reveal className="w-full min-w-0" delay={80}>
          <ProductMetricsChart data={productMetrics} loading={productLoading} onProductClick={handleProductClick} />
        </Reveal>
      </section>

      <section className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Reveal className="w-full min-w-0">
          <WorklogCompletionChart data={worklogCompletion} loading={worklogLoading} />
        </Reveal>
        <Reveal className="flex flex-col gap-4" delay={80}>
          <CompletionMeters data={worklogCompletion} loading={worklogLoading} />
          <TopAgentsBar data={worklogCompletion} loading={worklogLoading} onAgentClick={handleAgentClick} />
        </Reveal>
      </section>

      <Reveal as="section">
        <CSATGaugeChart data={csatMetrics} loading={csatLoading} />
      </Reveal>
    </div>
  );
}
