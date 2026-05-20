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
import { getTicket, getComments } from '../api/tickets';
import {
  formatDate,
  statusColor,
  priorityColor,
  statusLabel,
  priorityLabel,
} from '../utils/format';

/** Bilet detayı — alanlar + yorum listesi. Çek-yenile destekli. */
export default function TicketDetailScreen({ route }) {
  const { id } = route.params;
  const { theme } = useTheme();
  const { t } = useTranslation();

  const [ticket, setTicket] = useState(null);
  const [comments, setComments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(
    async (isRefresh = false) => {
      if (isRefresh) setRefreshing(true);
      else setLoading(true);
      setError(null);
      try {
        const [tRes, cRes] = await Promise.all([getTicket(id), getComments(id)]);
        setTicket(tRes.data);
        setComments(cRes.data ?? []);
      } catch (e) {
        setError(t('ticketDetail.error', 'Bilet yüklenemedi.'));
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [id, t],
  );

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
  if (error || !ticket) {
    return (
      <View style={[styles.full, { backgroundColor: theme.bgBody }]}>
        <Text style={{ color: theme.danger }}>{error || '—'}</Text>
      </View>
    );
  }

  return (
    <ScrollView
      style={{ backgroundColor: theme.bgBody }}
      contentContainerStyle={styles.content}
      refreshControl={
        <RefreshControl refreshing={refreshing} onRefresh={() => load(true)} tintColor={theme.primary} />
      }
    >
      <View style={[styles.card, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
        <View style={styles.row}>
          <Text style={[styles.id, { color: theme.textTertiary }]}>#{ticket.id}</Text>
          <View style={[styles.badge, { backgroundColor: statusColor(ticket.status) }]}>
            <Text style={styles.badgeText}>{statusLabel(ticket.status, t)}</Text>
          </View>
        </View>
        <Text style={[styles.title, { color: theme.textPrimary }]}>{ticket.title}</Text>
        {!!ticket.description && (
          <Text style={[styles.desc, { color: theme.textSecondary }]}>{ticket.description}</Text>
        )}
      </View>

      <View style={[styles.card, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
        <Field label={t('ticketDetail.priority', 'Öncelik')} theme={theme}
          value={priorityLabel(ticket.priority, t)} valueColor={priorityColor(ticket.priority)} />
        <Field label={t('ticketDetail.product', 'Ürün')} theme={theme} value={ticket.productName} />
        <Field label={t('ticketDetail.topic', 'Konu')} theme={theme} value={ticket.topicName} />
        <Field label={t('ticketDetail.customer', 'Müşteri')} theme={theme} value={ticket.customerName} />
        <Field label={t('ticketDetail.created', 'Oluşturulma')} theme={theme}
          value={formatDate(ticket.createdAt)} />
      </View>

      <Text style={[styles.section, { color: theme.textPrimary }]}>
        {t('ticketDetail.comments', 'Yorumlar')} ({comments.length})
      </Text>

      {comments.length === 0 ? (
        <Text style={{ color: theme.textTertiary, textAlign: 'center', marginTop: 8 }}>
          {t('ticketDetail.noComments', 'Henüz yorum yok.')}
        </Text>
      ) : (
        comments.map((c) => (
          <View
            key={c.id}
            style={[styles.comment, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}
          >
            <View style={styles.row}>
              <Text style={[styles.cAuthor, { color: theme.textPrimary }]}>
                {c.authorName || '—'}
              </Text>
              <Text style={[styles.cDate, { color: theme.textTertiary }]}>
                {formatDate(c.createdAt)}
              </Text>
            </View>
            {c.type === 'INTERNAL' && (
              <Text style={[styles.internal, { color: theme.warning }]}>
                {t('ticketDetail.internal', 'Dahili')}
              </Text>
            )}
            <Text style={[styles.cMsg, { color: theme.textSecondary }]}>{c.message}</Text>
          </View>
        ))
      )}
    </ScrollView>
  );
}

function Field({ label, value, theme, valueColor }) {
  return (
    <View style={styles.field}>
      <Text style={[styles.fieldLabel, { color: theme.textSecondary }]}>{label}</Text>
      <Text style={[styles.fieldValue, { color: valueColor || theme.textPrimary }]}>
        {value || '—'}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  full: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  content: { padding: 14, gap: 14 },
  card: { borderRadius: 12, borderWidth: 1, padding: 16, gap: 10 },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  id: { fontSize: 12, fontWeight: '600' },
  title: { fontSize: 18, fontWeight: '700' },
  desc: { fontSize: 14, lineHeight: 20 },
  badge: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 999 },
  badgeText: { color: '#fff', fontSize: 11, fontWeight: '700' },
  field: { flexDirection: 'row', justifyContent: 'space-between', gap: 16 },
  fieldLabel: { fontSize: 13 },
  fieldValue: { fontSize: 13, fontWeight: '600', flexShrink: 1, textAlign: 'right' },
  section: { fontSize: 16, fontWeight: '700', marginTop: 4 },
  comment: { borderRadius: 10, borderWidth: 1, padding: 12, gap: 4 },
  cAuthor: { fontSize: 13, fontWeight: '700' },
  cDate: { fontSize: 11 },
  cMsg: { fontSize: 14, lineHeight: 19 },
  internal: { fontSize: 10, fontWeight: '700' },
});
