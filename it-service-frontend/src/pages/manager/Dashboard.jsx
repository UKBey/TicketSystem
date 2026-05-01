import { useEffect, useMemo, useState } from 'react';
import { ArrowUpRight, Clock3, LayoutDashboard, RefreshCw, ShieldAlert, Star } from 'lucide-react';
import metricService from '../../services/metricService';
import KpiCard from '../../components/dashboard/KpiCard';
import DashboardPlaceholderPanel from '../../components/dashboard/DashboardPlaceholderPanel';
import StatusDistributionChart from '../../components/dashboard/StatusDistributionChart';
import AgentPerformanceTable from '../../components/dashboard/AgentPerformanceTable';

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
  return new Intl.NumberFormat('tr-TR').format(value ?? 0);
}

function formatHours(value) {
  if (value === null || value === undefined) {
    return '0.0h';
  }

  return `${Number(value).toFixed(1)}h`;
}

export default function Dashboard() {
  const [summary, setSummary] = useState(DEFAULT_SUMMARY);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [lastUpdated, setLastUpdated] = useState(null);
  const [statusDistribution, setStatusDistribution] = useState(null);
  const [statusLoading, setStatusLoading] = useState(true);
  const [agentPerformance, setAgentPerformance] = useState(null);
  const [agentLoading, setAgentLoading] = useState(true);

  const loadSummary = async ({ silent = false } = {}) => {
    try {
      if (silent) {
        setRefreshing(true);
      } else {
        setLoading(true);
      }

      setError('');
      const [summaryResponse, statusResponse, agentResponse] = await Promise.all([
        metricService.getDashboardSummary(),
        metricService.getStatusDistribution(),
        metricService.getAgentPerformance(),
      ]);

      setSummary({ ...DEFAULT_SUMMARY, ...summaryResponse });
      setStatusDistribution(statusResponse);
      setAgentPerformance(agentResponse);
      setLastUpdated(new Date());
    } catch (requestError) {
      console.error('Dashboard summary could not be loaded:', requestError);
      setError(requestError.response?.data?.message || 'Dashboard metrikleri yüklenemedi.');
    } finally {
      setLoading(false);
      setRefreshing(false);
      setStatusLoading(false);
      setAgentLoading(false);
    }
  };

  useEffect(() => {
    loadSummary();
  }, []);

  const kpis = useMemo(() => ([
    {
      title: 'Açık Bilet',
      value: formatNumber(summary.totalOpenTickets),
      detail: `Son 24 saatte +${formatNumber(summary.newTicketsLast24Hours)} yeni kayıt`,
      icon: LayoutDashboard,
      accent: 'bg-primary-500',
    },
    {
      title: 'SLA Breach',
      value: `${formatNumber(summary.slaBreachedCount)}`,
      detail: `${summary.slaBreachedPercentage?.toFixed(1) ?? '0.0'}% oran`,
      icon: ShieldAlert,
      accent: 'bg-danger-500',
    },
    {
      title: 'Ort. Çözüm Süresi',
      value: formatHours(summary.avgResponseTimeHours),
      detail: 'RESOLVED biletlerin ortalaması',
      icon: Clock3,
      accent: 'bg-warning-500',
    },
    {
      title: 'CSAT',
      value: `${Number(summary.csatAverage ?? 0).toFixed(1)}/5`,
      detail: `${formatNumber(summary.csatTotalResponses)} yanıt`,
      icon: Star,
      accent: 'bg-accent-500',
    },
  ]), [summary]);

  const syncLabel = lastUpdated
    ? lastUpdated.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' })
    : 'Henüz senkronize edilmedi';

  return (
    <div className="space-y-6 animate-fade-in">
      <section className="overflow-hidden rounded-3xl border shadow-[0_20px_60px_rgba(15,23,42,0.08)]" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
        <div className="relative px-6 py-7 sm:px-8 sm:py-8">
          <div className="absolute inset-0 bg-gradient-to-br from-primary-50 via-transparent to-accent-50 opacity-70 dark:from-primary-500/10 dark:to-accent-500/10" />
          <div className="relative flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
            <div className="max-w-2xl">
              <div className="mb-3 inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em]" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>
                <LayoutDashboard className="h-3.5 w-3.5" />
                Manager Dashboard
              </div>
              <h1 className="text-3xl font-black tracking-tight sm:text-4xl" style={{ color: 'var(--text-primary)' }}>
                Operasyonun tek bakışta fotoğrafı.
              </h1>
              <p className="mt-3 max-w-xl text-sm leading-6 sm:text-base" style={{ color: 'var(--text-secondary)' }}>
                Açık bilet hacmini, SLA baskısını ve müşteri memnuniyetini aynı yüzeyde gösteren, hızlı karar vermeye uygun bir kontrol paneli.
              </p>
              <div className="mt-5 flex flex-wrap items-center gap-3 text-xs sm:text-sm" style={{ color: 'var(--text-secondary)' }}>
                <span className="inline-flex items-center gap-2 rounded-full border px-3 py-1.5" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
                  <ArrowUpRight className="h-3.5 w-3.5" />
                  Canlı özet metrikler
                </span>
                <span className="inline-flex items-center gap-2 rounded-full border px-3 py-1.5" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
                  <RefreshCw className="h-3.5 w-3.5" />
                  Son güncelleme: {syncLabel}
                </span>
              </div>
            </div>

            <button
              type="button"
              onClick={() => loadSummary({ silent: true })}
              disabled={refreshing}
              className="inline-flex items-center justify-center gap-2 rounded-xl px-4 py-2.5 text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-60"
              style={{ backgroundColor: 'var(--bg-sidebar)', color: 'var(--text-inverse)' }}
            >
              <RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin' : ''}`} />
              {refreshing ? 'Yenileniyor' : 'Veriyi yenile'}
            </button>
          </div>
        </div>
      </section>

      {error && (
        <div className="rounded-2xl border px-4 py-3 text-sm font-medium" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'rgba(239, 68, 68, 0.25)', color: 'var(--color-danger-600)' }}>
          {error}
        </div>
      )}

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {kpis.map((item) => (
          <KpiCard
            key={item.title}
            title={item.title}
            value={item.value}
            detail={item.detail}
            icon={item.icon}
            accent={item.accent}
            loading={loading}
          />
        ))}
      </section>

      <section className="grid gap-4 xl:grid-cols-[1.5fr_1fr]">
        <StatusDistributionChart data={statusDistribution} loading={statusLoading} />

        <div className="rounded-3xl border p-6 shadow-sm" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
          <h2 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>Hızlı operasyon özeti</h2>
          <p className="mt-1 text-sm" style={{ color: 'var(--text-secondary)' }}>
            Bu alan commit 3+ adımda grafiklere bağlanacak ana çerçeveyi hazırlıyor.
          </p>

          <div className="mt-5 space-y-3">
            <div className="rounded-2xl border px-4 py-3" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color-light)' }}>
              <div className="text-xs uppercase tracking-[0.18em]" style={{ color: 'var(--text-tertiary)' }}>Yeni kayıt</div>
              <div className="mt-1 text-2xl font-black" style={{ color: 'var(--text-primary)' }}>{formatNumber(summary.newTicketsLast24Hours)}</div>
              <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>Son 24 saatte açılan biletler</div>
            </div>
            <div className="rounded-2xl border px-4 py-3" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color-light)' }}>
              <div className="text-xs uppercase tracking-[0.18em]" style={{ color: 'var(--text-tertiary)' }}>SLA baskısı</div>
              <div className="mt-1 text-2xl font-black" style={{ color: 'var(--text-primary)' }}>{formatNumber(summary.slaBreachedCount)}</div>
              <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>{summary.slaBreachedPercentage?.toFixed(1) ?? '0.0'}% open ticket havuzunda risk altında</div>
            </div>
            <div className="rounded-2xl border px-4 py-3" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color-light)' }}>
              <div className="text-xs uppercase tracking-[0.18em]" style={{ color: 'var(--text-tertiary)' }}>CSAT çerçevesi</div>
              <div className="mt-1 text-2xl font-black" style={{ color: 'var(--text-primary)' }}>{Number(summary.csatAverage ?? 0).toFixed(1)}</div>
              <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>{formatNumber(summary.csatTotalResponses)} yanıt üzerinden hesaplandı</div>
            </div>
          </div>
        </div>
      </section>

      <section className="grid gap-4 lg:grid-cols-2">
        <DashboardPlaceholderPanel
          title="Zaman çizgisi grafiği"
          description="Burada günlük ticket trendi, resolved/closed hareketi ve SLA kırılmaları görselleştirilecek."
        />
        <AgentPerformanceTable data={agentPerformance} loading={agentLoading} />
      </section>
    </div>
  );
}
