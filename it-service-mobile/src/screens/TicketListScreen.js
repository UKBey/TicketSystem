import { useState, useCallback } from 'react';
import {
  View,
  Text,
  FlatList,
  Pressable,
  StyleSheet,
  ActivityIndicator,
  RefreshControl,
} from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import { getTickets } from '../api/tickets';
import {
  formatDate,
  statusColor,
  priorityColor,
  statusLabel,
  priorityLabel,
} from '../utils/format';

/** Rol bazlı bilet listesi — çek-yenile, satıra dokun → detay. */
export default function TicketListScreen({ navigation, route }) {
  const { theme } = useTheme();
  const { t } = useTranslation();

  // Endpoint ve status sekmeden (MainTabs initialParams) gelir.
  const endpoint = route.params?.endpoint || '/tickets';
  const status = route.params?.status;

  const [tickets, setTickets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(
    async (isRefresh = false) => {
      if (isRefresh) setRefreshing(true);
      else setLoading(true);
      setError(null);
      try {
        const res = await getTickets({ endpoint, status });
        setTickets(res.data?.content ?? []);
      } catch (e) {
        setError(t('ticketList.error', 'Biletler yüklenemedi.'));
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [endpoint, status, t],
  );

  // Ekran her odaklandığında listeyi tazeler (detay/oluşturma sonrası dönüşte güncel kalır).
  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  const renderItem = ({ item }) => (
    <Pressable
      onPress={() => navigation.navigate('TicketDetail', { id: item.id, title: item.title })}
      style={({ pressed }) => [
        styles.card,
        { backgroundColor: theme.bgSurface, borderColor: theme.border, opacity: pressed ? 0.7 : 1 },
      ]}
    >
      <View style={styles.cardTop}>
        <Text style={[styles.cardId, { color: theme.textTertiary }]}>#{item.id}</Text>
        <View style={[styles.badge, { backgroundColor: statusColor(item.status) }]}>
          <Text style={styles.badgeText}>{statusLabel(item.status, t)}</Text>
        </View>
      </View>
      <Text style={[styles.cardTitle, { color: theme.textPrimary }]} numberOfLines={2}>
        {item.title}
      </Text>
      <View style={styles.cardMeta}>
        <Text style={[styles.metaText, { color: priorityColor(item.priority) }]}>
          {priorityLabel(item.priority, t)}
        </Text>
        <Text style={[styles.metaText, { color: theme.textTertiary }]}>
          {item.productName || '—'}
        </Text>
        <Text style={[styles.metaText, { color: theme.textTertiary }]}>
          {formatDate(item.createdAt)}
        </Text>
      </View>
    </Pressable>
  );

  return (
    <View style={[styles.container, { backgroundColor: theme.bgBody }]}>
      {loading ? (
        <ActivityIndicator style={styles.center} size="large" color={theme.primary} />
      ) : error ? (
        <Text style={[styles.center, styles.error, { color: theme.danger }]}>{error}</Text>
      ) : (
        <FlatList
          data={tickets}
          keyExtractor={(item) => String(item.id)}
          renderItem={renderItem}
          contentContainerStyle={styles.list}
          refreshControl={
            <RefreshControl refreshing={refreshing} onRefresh={() => load(true)} tintColor={theme.primary} />
          }
          ListEmptyComponent={
            <Text style={[styles.center, { color: theme.textTertiary }]}>
              {t('ticketList.empty', 'Bilet yok.')}
            </Text>
          }
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  list: { padding: 12, gap: 10, flexGrow: 1 },
  card: { borderRadius: 12, borderWidth: 1, padding: 14, gap: 8 },
  cardTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  cardId: { fontSize: 12, fontWeight: '600' },
  cardTitle: { fontSize: 15, fontWeight: '600' },
  cardMeta: { flexDirection: 'row', gap: 12, flexWrap: 'wrap' },
  metaText: { fontSize: 12, fontWeight: '500' },
  badge: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 999 },
  badgeText: { color: '#fff', fontSize: 11, fontWeight: '700' },
  center: { marginTop: 48, textAlign: 'center', alignSelf: 'center' },
  error: { fontSize: 14 },
});
