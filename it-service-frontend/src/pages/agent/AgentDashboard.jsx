import { lazy, Suspense, useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Briefcase, CalendarRange, CheckCircle2, Clock3, LayoutDashboard, RefreshCw, Star } from 'lucide-react';
import metricService from '../../services/metricService';
import KpiCard from '../../components/dashboard/KpiCard';
import StatusDistributionChart from '../../components/dashboard/StatusDistributionChart';
import RecentTicketsList from '../../components/dashboard/RecentTicketsList';
import SkeletonLoader from '../../components/SkeletonLoader';
import ErrorBoundary from '../../components/ErrorBoundary';
import Reveal from '../../components/Reveal';
import { useToast } from '../../context/ToastContext';

const TicketTimelineChart = lazy(() => import('../../components/dashboard/TicketTimelineChart'));
const WorklogTrendChart = lazy(() => import('../../components/dashboard/WorklogTrendChart'));
const AgentCsatChart = lazy(() => import('../../components/dashboard/AgentCsatChart'));

const DATE_RANGE_OPTIONS = [7, 30, 90, null];
const DEFAULT_DATE_RANGE = 30;

const formatNumber = (v) => new Intl.NumberFormat('en-US').format(v ?? 0);
const formatHours = (v) => `${Number(v ?? 0).toFixed(1)}h`;

/**
 * Ajan performans dashboard'u.
 *
 * @param {string|null} viewUserId   Set edilirse BAŞKA bir ajanın (oversight) verisi çekilir;
 *                                   null ise oturum açan ajanın kendi verisi.
 * @param {string|null} viewUserName Oversight modunda başlıkta gösterilecek ajan adı.
 */
export default function AgentDashboard({ viewUserId = null, viewUserName = null }) {
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
      const res = viewUserId
        ? await metricService.getUserAgentDashboard(viewUserId, dateRange ?? 0)
        : await metricService.getMyAgentDashboard(dateRange ?? 0);
      setData(res);
    } catch (err) {
      console.error('Agent dashboard could not be loaded:', err);
      toast.error(err.response?.data?.message || t('agentDashboard.loadError'));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [dateRange, t, viewUserId, toast]);

  useEffect(() => { load(); }, [load]);

  const worklogHours = ((data?.worklogMinutesInRange ?? 0) / 60).toFixed(1);

  const kpis = [
    {
      title: t('agentDashboard.kpiActive'),
      value: formatNumber(data?.activeTickets),
      detail: t('agentDashboard.kpiActiveDetail', { count: formatNumber(data?.totalClaimed) }),
      icon: Briefcase,
      accent: 'bg-primary-500',
    },
    {
      title: t('agentDashboard.kpiResolved'),
      value: formatNumber(data?.resolvedInRange),
      detail: t('agentDashboard.kpiResolvedDetail', { hours: worklogHours }),
      icon: CheckCircle2,
      accent: 'bg-accent-500',
    },
    {
      title: t('agentDashboard.kpiResolution'),
      value: formatHours(data?.avgResolutionHours),
      detail: t('agentDashboard.kpiResolutionDetail', { rate: Number(data?.slaBreachRate ?? 0).toFixed(1) }),
      icon: Clock3,
      accent: 'bg-warning-500',
    },
    {
      title: t('agentDashboard.kpiCsat'),
      value: `${Number(data?.csatAverage ?? 0).toFixed(1)}/5`,
      detail: t('agentDashboard.kpiCsatDetail', { count: formatNumber(data?.csatCount) }),
      icon: Star,
      accent: 'bg-accent-500',
    },
  ];

  return (
    <div className="space-y-6 animate-fade-in">
      <section className="overflow-hidden rounded-3xl border shadow-[0_20px_60px_rgba(15,23,42,0.08)]" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
        <div className="relative px-4 py-5 sm:px-8 sm:py-8">
          <div className="absolute inset-0 bg-gradient-to-br from-primary-50 via-transparent to-accent-50 opacity-70 dark:from-primary-500/10 dark:to-accent-500/10" />
          <div className="relative flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
            <div className="max-w-2xl">
              <div className="mb-3 inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em]" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>
                <LayoutDashboard className="h-3.5 w-3.5" />
                {t('agentDashboard.badge')}
              </div>
              <h1 className="text-2xl font-black tracking-tight sm:text-4xl" style={{ color: 'var(--text-primary)' }}>
                {viewUserName || t('agentDashboard.heading')}
              </h1>
              <p className="mt-3 max-w-xl text-sm leading-6 sm:text-base" style={{ color: 'var(--text-secondary)' }}>
                {viewUserId ? t('userPerformance.agentSubtitle') : t('agentDashboard.description')}
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
        <section data-tour="perf-kpis" className="grid grid-cols-2 gap-3 sm:gap-4 md:grid-cols-4">
          {loading
            ? Array.from({ length: 4 }).map((_, i) => <KpiCard key={i} loading />)
            : kpis.map((item) => (
                <KpiCard key={item.title} title={item.title} value={item.value} detail={item.detail} icon={item.icon} accent={item.accent} loading={loading} />
              ))}
        </section>
      </ErrorBoundary>

      <Reveal as="section" data-tour="perf-charts">
        <StatusDistributionChart data={data?.statusDistribution} loading={loading} />
      </Reveal>

      <Reveal as="section">
        <Suspense fallback={<SkeletonLoader lines={6} />}>
          <TicketTimelineChart
            data={data?.timeline}
            loading={loading}
            badgeLabel={t('agentDashboard.trendBadge')}
            title={t('agentDashboard.trendTitle')}
            subtitle={t('agentDashboard.trendSubtitle')}
            seriesLabels={{ created: t('agentDashboard.seriesClaimed') }}
          />
        </Suspense>
      </Reveal>

      <Reveal as="section">
        <Suspense fallback={<SkeletonLoader lines={6} />}>
          <WorklogTrendChart
            data={data?.worklogTimeline}
            loading={loading}
            badgeLabel={t('agentDashboard.worklogChartBadge')}
            title={t('agentDashboard.worklogChartTitle')}
            subtitle={t('agentDashboard.worklogChartSubtitle')}
            emptyText={t('agentDashboard.worklogChartEmpty')}
            hoursLabel={t('agentDashboard.worklogChartHours')}
          />
        </Suspense>
      </Reveal>

      <Reveal as="section">
        <Suspense fallback={<SkeletonLoader lines={6} />}>
          <AgentCsatChart
            data={data?.csat}
            loading={loading}
            badgeLabel={t('agentDashboard.csatChartBadge')}
            title={t('agentDashboard.csatChartTitle')}
            subtitle={t('agentDashboard.csatChartSubtitle')}
            responsesLabel={t('agentDashboard.csatChartResponses', { count: data?.csat?.totalResponses ?? 0 })}
            emptyText={t('agentDashboard.csatChartEmpty')}
            distributionLabel={t('agentDashboard.csatChartDistribution')}
            trendLabel={t('agentDashboard.csatChartTrend')}
          />
        </Suspense>
      </Reveal>

      <Reveal as="section">
        <RecentTicketsList
          tickets={data?.recentTickets}
          loading={loading}
          title={t('agentDashboard.recentTitle')}
          emptyText={t('agentDashboard.recentEmpty')}
        />
      </Reveal>
    </div>
  );
}
