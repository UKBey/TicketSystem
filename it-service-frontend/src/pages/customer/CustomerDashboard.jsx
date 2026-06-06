import { lazy, Suspense, useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { CalendarRange, CheckCircle2, Clock3, LayoutDashboard, RefreshCw, ShieldAlert, Star, TicketCheck } from 'lucide-react';
import metricService from '../../services/metricService';
import KpiCard from '../../components/dashboard/KpiCard';
import StatusDistributionChart from '../../components/dashboard/StatusDistributionChart';
import RecentTicketsList from '../../components/dashboard/RecentTicketsList';
import SkeletonLoader from '../../components/SkeletonLoader';
import ErrorBoundary from '../../components/ErrorBoundary';

const TicketTimelineChart = lazy(() => import('../../components/dashboard/TicketTimelineChart'));

const DATE_RANGE_OPTIONS = [7, 30, 90, null];
const DEFAULT_DATE_RANGE = 30;

const formatNumber = (v) => new Intl.NumberFormat('en-US').format(v ?? 0);
const formatHours = (v) => `${Number(v ?? 0).toFixed(1)}h`;

/**
 * Müşteri genel bakış dashboard'u.
 *
 * @param {string|null} viewUserId   Set edilirse BAŞKA bir müşterinin (oversight) verisi çekilir;
 *                                   null ise oturum açan müşterinin kendi verisi.
 * @param {string|null} viewUserName Oversight modunda başlıkta gösterilecek müşteri adı.
 */
export default function CustomerDashboard({ viewUserId = null, viewUserName = null }) {
  const { t } = useTranslation();
  const [dateRange, setDateRange] = useState(DEFAULT_DATE_RANGE);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async ({ silent = false } = {}) => {
    try {
      if (silent) setRefreshing(true); else setLoading(true);
      setError('');
      // null (All time) → 0: backend pencereyi ilk veri tarihinden başlatır.
      const res = viewUserId
        ? await metricService.getUserCustomerDashboard(viewUserId, dateRange ?? 0)
        : await metricService.getMyCustomerDashboard(dateRange ?? 0);
      setData(res);
    } catch (err) {
      console.error('Customer dashboard could not be loaded:', err);
      setError(err.response?.data?.message || t('customerDashboard.loadError'));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [dateRange, t, viewUserId]);

  useEffect(() => { load(); }, [load]);

  const kpis = [
    {
      title: t('customerDashboard.kpiOpen'),
      value: formatNumber(data?.openTickets),
      detail: t('customerDashboard.kpiOpenDetail', { count: formatNumber(data?.totalTickets) }),
      icon: TicketCheck,
      accent: 'bg-primary-500',
    },
    {
      title: t('customerDashboard.kpiResolved'),
      value: formatNumber(data?.resolvedTickets),
      detail: t('customerDashboard.kpiResolvedDetail', { hours: formatHours(data?.avgResolutionHours) }),
      icon: CheckCircle2,
      accent: 'bg-accent-500',
    },
    {
      title: t('customerDashboard.kpiSla'),
      value: formatNumber(data?.slaBreachedCount),
      detail: t('customerDashboard.kpiSlaDetail'),
      icon: ShieldAlert,
      accent: 'bg-danger-500',
    },
    {
      title: t('customerDashboard.kpiCsat'),
      value: `${Number(data?.csatAverage ?? 0).toFixed(1)}/5`,
      detail: t('customerDashboard.kpiCsatDetail', { count: formatNumber(data?.csatCount) }),
      icon: Star,
      accent: 'bg-warning-500',
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
                {t('customerDashboard.badge')}
              </div>
              <h1 className="text-2xl font-black tracking-tight sm:text-4xl" style={{ color: 'var(--text-primary)' }}>
                {viewUserName || t('customerDashboard.heading')}
              </h1>
              <p className="mt-3 max-w-xl text-sm leading-6 sm:text-base" style={{ color: 'var(--text-secondary)' }}>
                {viewUserId ? t('userPerformance.customerSubtitle') : t('customerDashboard.description')}
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
                    <option key={opt ?? 'all'} value={opt ?? 'all'}>
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

      {error && (
        <div className="rounded-2xl border px-4 py-3 text-sm font-medium" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'rgba(239, 68, 68, 0.25)', color: 'var(--color-danger-600)' }}>
          {error}
        </div>
      )}

      <ErrorBoundary>
        <section className="grid grid-cols-2 gap-3 sm:gap-4 md:grid-cols-4">
          {loading
            ? Array.from({ length: 4 }).map((_, i) => <div key={i}><SkeletonLoader lines={3} /></div>)
            : kpis.map((item) => (
                <KpiCard key={item.title} title={item.title} value={item.value} detail={item.detail} icon={item.icon} accent={item.accent} loading={loading} />
              ))}
        </section>
      </ErrorBoundary>

      <section>
        <StatusDistributionChart data={data?.statusDistribution} loading={loading} />
      </section>

      <section>
        <Suspense fallback={<SkeletonLoader lines={6} />}>
          <TicketTimelineChart
            data={data?.timeline}
            loading={loading}
            badgeLabel={t('customerDashboard.trendBadge')}
            title={t('customerDashboard.trendTitle')}
            subtitle={t('customerDashboard.trendSubtitle')}
            seriesLabels={{ created: t('customerDashboard.seriesOpened') }}
          />
        </Suspense>
      </section>

      <section>
        <RecentTicketsList
          tickets={data?.recentTickets}
          loading={loading}
          title={t('customerDashboard.recentTitle')}
          emptyText={t('customerDashboard.recentEmpty')}
        />
      </section>
    </div>
  );
}
