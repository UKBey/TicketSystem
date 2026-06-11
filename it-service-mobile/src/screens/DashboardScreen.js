import { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  ScrollView,
  StyleSheet,
  ActivityIndicator,
  RefreshControl,
} from 'react-native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import {
  getDashboardSummary,
  getStatusDistribution,
  getAgentPerformance,
  getPrioritySlaMetrics,
  getProductMetrics,
  getCsatMetrics,
  getAlertsBacklog,
  getWorklogCompletion,
} from '../api/metrics';
import { statusColor, statusLabel, priorityLabel } from '../utils/format';
import { localizedName } from '../utils/localizedName';
import { PRIORITY_COLORS } from '../theme/theme';

const n = (v) => (v == null || Number.isNaN(Number(v)) ? 0 : Number(v));
const fmtNum = (v) => String(Math.round(n(v)));
const fmtHours = (v) => `${n(v).toFixed(1)} sa`;
const fmtPct = (v) => `${n(v).toFixed(1)}%`;
const fmt1 = (v) => n(v).toFixed(1);
const rankBadge = (i) => (i === 0 ? '🥇' : i === 1 ? '🥈' : i === 2 ? '🥉' : `#${i + 1}`);

// Süpervizör (lead/admin) rozeti — LEAD_AGENT ve ADMIN rolleri.
const isLeadOrAdmin = (role) =>
  role === 'LEAD_AGENT' || role === 'ADMIN';

const STATUS_ROWS = [
  { key: 'NEW', field: 'newCount' },
  { key: 'IN_PROGRESS', field: 'inProgressCount' },
  { key: 'WAITING_FOR_CUSTOMER', field: 'waitingForCustomerCount' },
  { key: 'RESOLVED', field: 'resolvedCount' },
  { key: 'CLOSED', field: 'closedCount' },
];

const PRIORITY_ROWS = [
  { key: 'CRITICAL', field: 'critical' },
  { key: 'HIGH', field: 'high' },
  { key: 'MEDIUM', field: 'medium' },
  { key: 'LOW', field: 'low' },
];

/** İnce orantı çubuğu. */
function Bar({ theme, pct, color }) {
  return (
    <View style={[styles.barTrack, { backgroundColor: theme.bgSurfaceSecondary }]}>
      <View
        style={[
          styles.barFill,
          { width: `${Math.min(100, Math.max(n(pct), 2))}%`, backgroundColor: color },
        ]}
      />
    </View>
  );
}

/** Etiketli küçük istatistik hücresi (agent kartlarında). */
function Stat({ theme, label, value }) {
  return (
    <View style={styles.statCell}>
      <Text style={[styles.statLabel, { color: theme.textTertiary }]}>{label}</Text>
      <Text style={[styles.statValue, { color: theme.textPrimary }]}>{value}</Text>
    </View>
  );
}

/** Yönetici gösterge paneli — web Dashboard ile işlevsel eşdeğer. */
export default function DashboardScreen() {
  const { theme } = useTheme();
  const { t } = useTranslation();

  const [summary, setSummary] = useState({});
  const [statusDist, setStatusDist] = useState({});
  const [agentPerf, setAgentPerf] = useState({});
  const [prioritySla, setPrioritySla] = useState([]);
  const [products, setProducts] = useState([]);
  const [csat, setCsat] = useState({});
  const [alerts, setAlerts] = useState({});
  const [worklog, setWorklog] = useState({});
  const [updatedAt, setUpdatedAt] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async (isRefresh = false) => {
    if (isRefresh) setRefreshing(true);
    else setLoading(true);
    try {
      const results = await Promise.allSettled([
        getDashboardSummary(),
        getStatusDistribution(),
        getAgentPerformance(),
        getPrioritySlaMetrics(),
        getProductMetrics(),
        getCsatMetrics(3),
        getAlertsBacklog(),
        getWorklogCompletion(30),
      ]);
      const data = (i) => (results[i].status === 'fulfilled' ? results[i].value?.data : null);
      setSummary(data(0) ?? {});
      setStatusDist(data(1) ?? {});
      setAgentPerf(data(2) ?? {});
      setPrioritySla(data(3)?.priorityMetrics ?? []);
      setProducts(data(4)?.productMetrics ?? []);
      setCsat(data(5) ?? {});
      setAlerts(data(6) ?? {});
      setWorklog(data(7) ?? {});
      setUpdatedAt(new Date());
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  if (loading) {
    return (
      <View style={[styles.full, { backgroundColor: theme.bgBody }]}>
        <ActivityIndicator size="large" color={theme.primary} />
      </View>
    );
  }

  const empty = (
    <Text style={[styles.empty, { color: theme.textTertiary }]}>
      {t('dashboard.noData', 'Veri yok.')}
    </Text>
  );

  const cardStyle = [styles.card, { backgroundColor: theme.bgSurface, borderColor: theme.border }];

  // KPI kartları
  const kpis = [
    {
      label: t('dashboard.kpiOpenTickets', 'Açık Biletler'),
      value: fmtNum(summary.totalOpenTickets),
      detail: t('dashboard.kpiOpenDetail', 'Son 24s: {{count}}', {
        count: fmtNum(summary.newTicketsLast24Hours),
      }),
      color: theme.primary,
    },
    {
      label: t('dashboard.kpiSla', 'SLA İhlali'),
      value: fmtNum(summary.slaBreachedCount),
      detail: fmtPct(summary.slaBreachedPercentage),
      color: theme.danger,
    },
    {
      label: t('dashboard.kpiResolution', 'Ort. Çözüm'),
      value: fmtHours(summary.avgResponseTimeHours),
      detail: t('dashboard.kpiResolutionDetail', 'Ortalama yanıt'),
      color: theme.warning,
    },
    {
      label: t('dashboard.kpiCsat', 'CSAT'),
      value: `${fmt1(summary.csatAverage)}/5`,
      detail: t('dashboard.kpiCsatDetail', '{{count}} yanıt', {
        count: fmtNum(summary.csatTotalResponses),
      }),
      color: theme.success,
    },
  ];

  // Durum dağılımı
  const statusMax = Math.max(
    1,
    ...STATUS_ROWS.map((r) => n(statusDist[r.field])),
  );
  const statusTotal = n(statusDist.totalCount);

  // Öncelik dağılımı (özet içinden)
  const pd = summary.priorityDistribution || {};
  const priorityMax = Math.max(1, ...PRIORITY_ROWS.map((r) => n(pd[r.field])));

  // Agent performansı
  const agents = Array.isArray(agentPerf.agents) ? agentPerf.agents : [];

  // Uyarılar / backlog
  const backlog = alerts.backlogMetrics || {};
  const breachedCount = (alerts.breachedSLA || []).length;
  const upcomingCount = (alerts.upcomingBreach || []).length;
  const waitingCount = (alerts.waitingTooLong || []).length;

  // CSAT detay
  const ratingDist = csat.ratingDistribution || {};
  const topComments = Array.isArray(csat.topComments) ? csat.topComments : [];

  // Worklog tamamlanma
  const rates = worklog.completionRates || {};

  return (
    <ScrollView
      style={{ backgroundColor: theme.bgBody }}
      contentContainerStyle={styles.content}
      refreshControl={
        <RefreshControl
          refreshing={refreshing}
          onRefresh={() => load(true)}
          tintColor={theme.primary}
        />
      }
    >
      {updatedAt && (
        <Text style={[styles.updated, { color: theme.textTertiary }]}>
          {t('dashboard.lastUpdated', 'Son güncelleme: {{time}}', {
            time: updatedAt.toLocaleTimeString(undefined, {
              hour: '2-digit',
              minute: '2-digit',
            }),
          })}
        </Text>
      )}

      {/* KPI kartları */}
      <View style={styles.grid}>
        {kpis.map((k) => (
          <View
            key={k.label}
            style={[styles.metricCard, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}
          >
            <Text style={[styles.metricValue, { color: k.color }]}>{k.value}</Text>
            <Text style={[styles.metricLabel, { color: theme.textPrimary }]}>{k.label}</Text>
            <Text style={[styles.metricDetail, { color: theme.textTertiary }]}>{k.detail}</Text>
          </View>
        ))}
      </View>

      {/* Uyarılar */}
      <Text style={[styles.section, { color: theme.textPrimary }]}>
        {t('dashboard.alertsSection', 'Uyarılar')}
      </Text>
      <View style={cardStyle}>
        <View style={styles.alertGrid}>
          <View style={styles.alertCell}>
            <Text style={[styles.alertValue, { color: theme.danger }]}>{breachedCount}</Text>
            <Text style={[styles.alertLabel, { color: theme.textSecondary }]}>
              {t('dashboard.alertBreached', 'SLA aşıldı')}
            </Text>
          </View>
          <View style={styles.alertCell}>
            <Text style={[styles.alertValue, { color: theme.warning }]}>{upcomingCount}</Text>
            <Text style={[styles.alertLabel, { color: theme.textSecondary }]}>
              {t('dashboard.alertUpcoming', 'Yaklaşan')}
            </Text>
          </View>
          <View style={styles.alertCell}>
            <Text style={[styles.alertValue, { color: theme.primary }]}>{waitingCount}</Text>
            <Text style={[styles.alertLabel, { color: theme.textSecondary }]}>
              {t('dashboard.alertWaiting', 'Uzun bekleyen')}
            </Text>
          </View>
        </View>
        <View style={[styles.divider, { backgroundColor: theme.border }]} />
        <View style={styles.row}>
          <Text style={[styles.rowLabel, { color: theme.textSecondary }]}>
            {t('dashboard.unassigned', 'Atanmamış bilet')}
          </Text>
          <Text style={[styles.rowValue, { color: theme.textPrimary }]}>
            {fmtNum(backlog.unassignedCount)}
          </Text>
        </View>
        <View style={styles.row}>
          <Text style={[styles.rowLabel, { color: theme.textSecondary }]}>
            {t('dashboard.newWaiting', 'Bekleyen yeni bilet')}
          </Text>
          <Text style={[styles.rowValue, { color: theme.textPrimary }]}>
            {fmtNum(backlog.newTicketsWaiting)}
          </Text>
        </View>
        <View style={styles.row}>
          <Text style={[styles.rowLabel, { color: theme.textSecondary }]}>
            {t('dashboard.avgWaiting', 'Ort. bekleme süresi')}
          </Text>
          <Text style={[styles.rowValue, { color: theme.textPrimary }]}>
            {fmtHours(backlog.avgWaitingHours)}
          </Text>
        </View>
      </View>

      {/* Durum dağılımı */}
      <Text style={[styles.section, { color: theme.textPrimary }]}>
        {t('dashboard.statusDist', 'Durum Dağılımı')}
      </Text>
      <View style={cardStyle}>
        {STATUS_ROWS.map((r) => {
          const count = n(statusDist[r.field]);
          return (
            <View key={r.key} style={styles.barRow}>
              <View style={styles.barHeader}>
                <View style={styles.rowLeft}>
                  <View style={[styles.dot, { backgroundColor: statusColor(r.key) }]} />
                  <Text style={[styles.rowLabel, { color: theme.textPrimary }]}>
                    {statusLabel(r.key, t)}
                  </Text>
                </View>
                <Text style={[styles.rowValue, { color: theme.textPrimary }]}>{count}</Text>
              </View>
              <Bar theme={theme} pct={(count / statusMax) * 100} color={statusColor(r.key)} />
            </View>
          );
        })}
        <View style={[styles.divider, { backgroundColor: theme.border }]} />
        <View style={styles.row}>
          <Text style={[styles.rowLabel, { color: theme.textSecondary }]}>
            {t('dashboard.totalTickets', 'Toplam bilet')}
          </Text>
          <Text style={[styles.rowValue, { color: theme.textPrimary }]}>{statusTotal}</Text>
        </View>
      </View>

      {/* Öncelik dağılımı */}
      <Text style={[styles.section, { color: theme.textPrimary }]}>
        {t('dashboard.priorityDist', 'Öncelik Dağılımı')}
      </Text>
      <View style={cardStyle}>
        {PRIORITY_ROWS.map((r) => {
          const count = n(pd[r.field]);
          const color = PRIORITY_COLORS[r.key];
          return (
            <View key={r.key} style={styles.barRow}>
              <View style={styles.barHeader}>
                <View style={styles.rowLeft}>
                  <View style={[styles.dot, { backgroundColor: color }]} />
                  <Text style={[styles.rowLabel, { color: theme.textPrimary }]}>
                    {priorityLabel(r.key, t)}
                  </Text>
                </View>
                <Text style={[styles.rowValue, { color: theme.textPrimary }]}>{count}</Text>
              </View>
              <Bar theme={theme} pct={(count / priorityMax) * 100} color={color} />
            </View>
          );
        })}
      </View>

      {/* Priority-SLA metrikleri */}
      <Text style={[styles.section, { color: theme.textPrimary }]}>
        {t('dashboard.prioritySla', 'Öncelik-SLA Metrikleri')}
      </Text>
      <View style={cardStyle}>
        {prioritySla.length === 0
          ? empty
          : prioritySla.map((p, i) => (
              <View
                key={p.priority ?? i}
                style={[
                  styles.block,
                  i > 0 && { borderTopWidth: 1, borderTopColor: theme.border, paddingTop: 10 },
                ]}
              >
                <View style={styles.barHeader}>
                  <View style={styles.rowLeft}>
                    <View
                      style={[styles.dot, { backgroundColor: PRIORITY_COLORS[p.priority] || theme.textTertiary }]}
                    />
                    <Text style={[styles.rowLabel, { color: theme.textPrimary, fontWeight: '700' }]}>
                      {priorityLabel(p.priority, t)}
                    </Text>
                  </View>
                  <Text style={[styles.rowValue, { color: theme.textSecondary }]}>
                    {fmtNum(p.ticketCount)} {t('dashboard.tickets', 'bilet')}
                  </Text>
                </View>
                <View style={styles.statRow}>
                  <Stat
                    theme={theme}
                    label={t('dashboard.slaTarget', 'SLA hedefi')}
                    value={`${fmtNum(p.slaTargetHours)} sa`}
                  />
                  <Stat
                    theme={theme}
                    label={t('dashboard.avgResolution', 'Ort. çözüm')}
                    value={fmtHours(p.avgResolutionHours)}
                  />
                  <Stat
                    theme={theme}
                    label={t('dashboard.onTime', 'Zamanında')}
                    value={fmtPct(p.onTimePercentage)}
                  />
                  <Stat
                    theme={theme}
                    label={t('dashboard.breach', 'İhlal')}
                    value={`${fmtNum(p.breachCount)} (${fmtPct(p.breachPercentage)})`}
                  />
                </View>
              </View>
            ))}
      </View>

      {/* Agent performansı */}
      <Text style={[styles.section, { color: theme.textPrimary }]}>
        {t('dashboard.agentPerf', 'Agent Performansı')}
      </Text>
      <View style={cardStyle}>
        {agents.length === 0
          ? empty
          : agents.map((a, i) => (
              <View
                key={a.agentId ?? i}
                style={[
                  styles.block,
                  i > 0 && { borderTopWidth: 1, borderTopColor: theme.border, paddingTop: 10 },
                ]}
              >
                <View style={styles.agentHeader}>
                  <Text style={[styles.rank, { color: theme.textSecondary }]}>{rankBadge(i)}</Text>
                  <Text
                    style={[styles.agentName, { color: theme.textPrimary }]}
                    numberOfLines={1}
                  >
                    {a.agentName || '—'}
                  </Text>
                  {isLeadOrAdmin(a.role) && (
                    <View style={[styles.roleBadge, { backgroundColor: theme.primary }]}>
                      <Text style={styles.roleBadgeText}>
                        {t('dashboard.lead', 'Lead')}
                      </Text>
                    </View>
                  )}
                </View>
                <View style={styles.statRow}>
                  <Stat
                    theme={theme}
                    label={t('dashboard.active', 'Aktif')}
                    value={fmtNum(a.activeTickets)}
                  />
                  <Stat
                    theme={theme}
                    label={t('dashboard.resolved24h', 'Çözülen 24s')}
                    value={fmtNum(a.resolvedLast24Hours)}
                  />
                  <Stat
                    theme={theme}
                    label={t('dashboard.avgResolution', 'Ort. çözüm')}
                    value={fmtHours(a.avgResolutionHours)}
                  />
                  <Stat
                    theme={theme}
                    label={t('dashboard.kpiCsat', 'CSAT')}
                    value={fmt1(a.csatAverage)}
                  />
                  <Stat
                    theme={theme}
                    label={t('dashboard.slaBreach', 'SLA ihlali')}
                    value={fmtNum(a.slaBreachedCount)}
                  />
                  <Stat
                    theme={theme}
                    label={t('dashboard.worklog7d', 'Worklog 7g')}
                    value={`${fmtNum(a.worklogMinutesLast7Days)} dk`}
                  />
                </View>
              </View>
            ))}
      </View>

      {/* Ürün metrikleri */}
      <Text style={[styles.section, { color: theme.textPrimary }]}>
        {t('dashboard.productMetrics', 'Ürün Metrikleri')}
      </Text>
      <View style={cardStyle}>
        {products.length === 0
          ? empty
          : products.map((p, i) => (
              <View
                key={p.productId ?? i}
                style={[
                  styles.block,
                  i > 0 && { borderTopWidth: 1, borderTopColor: theme.border, paddingTop: 10 },
                ]}
              >
                <Text style={[styles.rowLabel, { color: theme.textPrimary, fontWeight: '700' }]}>
                  {localizedName(p, 'productName') || '—'}
                </Text>
                <View style={styles.statRow}>
                  <Stat
                    theme={theme}
                    label={t('dashboard.totalTickets', 'Toplam')}
                    value={fmtNum(p.totalTickets)}
                  />
                  <Stat
                    theme={theme}
                    label={t('dashboard.open', 'Açık')}
                    value={fmtNum(p.openTickets)}
                  />
                  <Stat
                    theme={theme}
                    label={t('dashboard.avgResolution', 'Ort. çözüm')}
                    value={fmtHours(p.avgResolutionHours)}
                  />
                  <Stat
                    theme={theme}
                    label={t('dashboard.kpiCsat', 'CSAT')}
                    value={fmt1(p.csatAverage)}
                  />
                  <Stat
                    theme={theme}
                    label={t('dashboard.slaBreach', 'SLA ihlali')}
                    value={`${fmtNum(p.slaBreachCount)} (${fmtPct(p.slaBreachPercentage)})`}
                  />
                </View>
              </View>
            ))}
      </View>

      {/* Worklog & tamamlanma */}
      <Text style={[styles.section, { color: theme.textPrimary }]}>
        {t('dashboard.completion', 'Tamamlanma')}
      </Text>
      <View style={cardStyle}>
        <View style={styles.statRow}>
          <Stat
            theme={theme}
            label={t('dashboard.created', 'Oluşturulan')}
            value={fmtNum(rates.totalCreated)}
          />
          <Stat
            theme={theme}
            label={t('dashboard.resolvedTotal', 'Çözülen')}
            value={fmtNum(rates.totalResolved)}
          />
          <Stat
            theme={theme}
            label={t('dashboard.closedTotal', 'Kapatılan')}
            value={fmtNum(rates.totalClosed)}
          />
          <Stat
            theme={theme}
            label={t('dashboard.completionRate', 'Tamamlanma')}
            value={fmtPct(rates.completionRate)}
          />
          <Stat
            theme={theme}
            label={t('dashboard.avgResolution', 'Ort. çözüm')}
            value={fmtHours(rates.avgResolutionHours)}
          />
          <Stat
            theme={theme}
            label={t('dashboard.slaCompliance', 'SLA uyumu')}
            value={fmtPct(rates.slaComplianceRate)}
          />
        </View>
      </View>

      {/* CSAT detay */}
      <Text style={[styles.section, { color: theme.textPrimary }]}>
        {t('dashboard.csatDetail', 'CSAT Detayı')}
      </Text>
      <View style={cardStyle}>
        <View style={styles.row}>
          <Text style={[styles.rowLabel, { color: theme.textSecondary }]}>
            {t('dashboard.csatAverage', 'Ortalama puan')}
          </Text>
          <Text style={[styles.rowValue, { color: theme.textPrimary }]}>
            {fmt1(csat.averageRating)}/5
          </Text>
        </View>
        <View style={styles.row}>
          <Text style={[styles.rowLabel, { color: theme.textSecondary }]}>
            {t('dashboard.csatResponses', 'Toplam yanıt')}
          </Text>
          <Text style={[styles.rowValue, { color: theme.textPrimary }]}>
            {fmtNum(csat.totalResponses)}
          </Text>
        </View>
        <View style={[styles.divider, { backgroundColor: theme.border }]} />
        {[5, 4, 3, 2, 1].map((star) => {
          const count = n(ratingDist[star]);
          const total = n(csat.totalResponses) || 1;
          return (
            <View key={star} style={styles.barRow}>
              <View style={styles.barHeader}>
                <Text style={[styles.rowLabel, { color: theme.textPrimary }]}>
                  {'★'.repeat(star)}
                </Text>
                <Text style={[styles.rowValue, { color: theme.textSecondary }]}>{count}</Text>
              </View>
              <Bar theme={theme} pct={(count / total) * 100} color={theme.warning} />
            </View>
          );
        })}
        {topComments.length > 0 && (
          <>
            <View style={[styles.divider, { backgroundColor: theme.border }]} />
            <Text style={[styles.subLabel, { color: theme.textSecondary }]}>
              {t('dashboard.topComments', 'Öne çıkan yorumlar')}
            </Text>
            {topComments.map((c, i) => (
              <Text key={i} style={[styles.comment, { color: theme.textPrimary }]}>
                “{c}”
              </Text>
            ))}
          </>
        )}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  full: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  content: { padding: 14, gap: 10, paddingBottom: 32 },
  updated: { fontSize: 12, textAlign: 'right' },
  section: { fontSize: 16, fontWeight: '700', marginTop: 8 },
  empty: { fontSize: 14 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  metricCard: {
    flexGrow: 1,
    minWidth: '45%',
    borderRadius: 12,
    borderWidth: 1,
    padding: 14,
    alignItems: 'center',
    gap: 3,
  },
  metricValue: { fontSize: 24, fontWeight: '800' },
  metricLabel: { fontSize: 13, fontWeight: '600', textAlign: 'center' },
  metricDetail: { fontSize: 11, textAlign: 'center' },
  card: { borderRadius: 12, borderWidth: 1, padding: 14, gap: 10 },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  rowLeft: { flexDirection: 'row', alignItems: 'center', gap: 8, flexShrink: 1 },
  rowLabel: { fontSize: 14 },
  rowValue: { fontSize: 14, fontWeight: '700' },
  dot: { width: 10, height: 10, borderRadius: 5 },
  divider: { height: 1, marginVertical: 2 },
  barRow: { gap: 6 },
  barHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  barTrack: { height: 8, borderRadius: 4, overflow: 'hidden' },
  barFill: { height: 8, borderRadius: 4 },
  block: { gap: 8 },
  statRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  statCell: { minWidth: '28%', gap: 2 },
  statLabel: { fontSize: 11 },
  statValue: { fontSize: 14, fontWeight: '700' },
  alertGrid: { flexDirection: 'row', justifyContent: 'space-around' },
  alertCell: { alignItems: 'center', gap: 2 },
  alertValue: { fontSize: 22, fontWeight: '800' },
  alertLabel: { fontSize: 12 },
  agentHeader: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  rank: { fontSize: 14, fontWeight: '700', minWidth: 28 },
  agentName: { fontSize: 15, fontWeight: '700', flexShrink: 1 },
  roleBadge: { paddingHorizontal: 8, paddingVertical: 2, borderRadius: 10 },
  roleBadgeText: { color: '#ffffff', fontSize: 10, fontWeight: '700' },
  subLabel: { fontSize: 13, fontWeight: '600' },
  comment: { fontSize: 13, fontStyle: 'italic', lineHeight: 18 },
});
