import { useState, useCallback, useEffect } from 'react';
import {
  View,
  Text,
  FlatList,
  Pressable,
  TextInput,
  Modal,
  StyleSheet,
  ActivityIndicator,
  RefreshControl,
} from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
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
import SheetBackdrop from '../components/SheetBackdrop';

const STATUSES = ['NEW', 'IN_PROGRESS', 'WAITING_FOR_CUSTOMER', 'RESOLVED', 'CLOSED'];
const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const PAGE_SIZE = 20;

/** Rol bazlı bilet listesi — arama, durum/öncelik filtresi ve sayfalama. */
export default function TicketListScreen({ navigation, route }) {
  const { theme } = useTheme();
  const { t } = useTranslation();

  const endpoint = route.params?.endpoint || '/tickets';
  const baseStatus = route.params?.status; // History → CLOSED (kilitli)

  const [tickets, setTickets] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(null);

  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState([]);
  const [priorityFilter, setPriorityFilter] = useState([]);

  const [filterOpen, setFilterOpen] = useState(false);
  const [draftStatus, setDraftStatus] = useState([]);
  const [draftPriority, setDraftPriority] = useState([]);

  // Arama girişini geciktir — her tuşta istek atma.
  useEffect(() => {
    const id = setTimeout(() => setDebouncedSearch(search.trim()), 400);
    return () => clearTimeout(id);
  }, [search]);

  const load = useCallback(
    async (pageArg, isRefresh = false) => {
      if (isRefresh) setRefreshing(true);
      else setLoading(true);
      setError(null);
      try {
        const status = baseStatus ? [baseStatus] : statusFilter;
        const res = await getTickets({
          endpoint,
          page: pageArg,
          size: PAGE_SIZE,
          status: status.length ? status : undefined,
          priority: priorityFilter.length ? priorityFilter : undefined,
          search: debouncedSearch || undefined,
        });
        setTickets(res.data?.content ?? []);
        setTotalPages(Math.max(1, res.data?.totalPages ?? 1));
        setPage(pageArg);
      } catch (e) {
        setError(t('ticketList.error', 'Biletler yüklenemedi.'));
        setTickets([]);
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [endpoint, baseStatus, statusFilter, priorityFilter, debouncedSearch, t],
  );

  // Odaklanınca ve filtre/arama değişince ilk sayfayı yükler.
  useFocusEffect(
    useCallback(() => {
      load(0);
    }, [load]),
  );

  const openFilter = () => {
    setDraftStatus(statusFilter);
    setDraftPriority(priorityFilter);
    setFilterOpen(true);
  };

  const applyFilter = () => {
    setStatusFilter(draftStatus);
    setPriorityFilter(draftPriority);
    setFilterOpen(false);
  };

  const toggleDraft = (arr, setArr, val) =>
    setArr(arr.includes(val) ? arr.filter((x) => x !== val) : [...arr, val]);

  const activeCount = (baseStatus ? 0 : statusFilter.length) + priorityFilter.length;

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
        <Text style={[styles.metaText, { color: theme.textTertiary }]}>{item.productName || '—'}</Text>
        <Text style={[styles.metaText, { color: theme.textTertiary }]}>{formatDate(item.createdAt)}</Text>
      </View>
    </Pressable>
  );

  return (
    <View style={[styles.container, { backgroundColor: theme.bgBody }]}>
      <View style={styles.toolbar}>
        <View style={[styles.searchWrap, { backgroundColor: theme.bgInput, borderColor: theme.border }]}>
          <Ionicons name="search" size={16} color={theme.textTertiary} />
          <TextInput
            value={search}
            onChangeText={setSearch}
            placeholder={t('ticketList.search', 'Bilet ara...')}
            placeholderTextColor={theme.textTertiary}
            style={[styles.searchInput, { color: theme.textPrimary }]}
          />
          {search.length > 0 && (
            <Pressable onPress={() => setSearch('')} hitSlop={6}>
              <Ionicons name="close-circle" size={16} color={theme.textTertiary} />
            </Pressable>
          )}
        </View>
        <Pressable
          onPress={openFilter}
          style={[styles.filterBtn, { borderColor: activeCount > 0 ? theme.primary : theme.border }]}
        >
          <Ionicons
            name="options-outline"
            size={18}
            color={activeCount > 0 ? theme.primary : theme.textSecondary}
          />
          {activeCount > 0 && (
            <View style={[styles.filterBadge, { backgroundColor: theme.primary }]}>
              <Text style={styles.filterBadgeText}>{activeCount}</Text>
            </View>
          )}
        </Pressable>
      </View>

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
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="on-drag"
          refreshControl={
            <RefreshControl
              refreshing={refreshing}
              onRefresh={() => load(page, true)}
              tintColor={theme.primary}
            />
          }
          ListEmptyComponent={
            <Text style={[styles.center, { color: theme.textTertiary }]}>
              {t('ticketList.empty', 'Bilet yok.')}
            </Text>
          }
        />
      )}

      {totalPages > 1 && !loading && !error && (
        <View style={[styles.pager, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
          <Pressable
            disabled={page <= 0}
            onPress={() => load(page - 1)}
            style={[styles.pagerBtn, { opacity: page <= 0 ? 0.4 : 1 }]}
          >
            <Ionicons name="chevron-back" size={18} color={theme.primary} />
            <Text style={{ color: theme.primary, fontWeight: '600' }}>
              {t('common.previous', 'Önceki')}
            </Text>
          </Pressable>
          <Text style={{ color: theme.textSecondary, fontSize: 13, fontWeight: '600' }}>
            {page + 1} / {totalPages}
          </Text>
          <Pressable
            disabled={page >= totalPages - 1}
            onPress={() => load(page + 1)}
            style={[styles.pagerBtn, { opacity: page >= totalPages - 1 ? 0.4 : 1 }]}
          >
            <Text style={{ color: theme.primary, fontWeight: '600' }}>
              {t('common.next', 'Sonraki')}
            </Text>
            <Ionicons name="chevron-forward" size={18} color={theme.primary} />
          </Pressable>
        </View>
      )}

      {/* Filtre modalı */}
      <Modal
        visible={filterOpen}
        transparent
        animationType="slide"
        onRequestClose={() => setFilterOpen(false)}
      >
        <SheetBackdrop>
          <View style={[styles.sheet, { backgroundColor: theme.bgSurface }]}>
            <Text style={[styles.sheetTitle, { color: theme.textPrimary }]}>
              {t('ticketList.filters', 'Filtreler')}
            </Text>

            {!baseStatus && (
              <>
                <Text style={[styles.filterLabel, { color: theme.textSecondary }]}>
                  {t('ticketList.statusLabel', 'Durum')}
                </Text>
                <View style={styles.chipWrap}>
                  {STATUSES.map((s) => {
                    const on = draftStatus.includes(s);
                    return (
                      <Pressable
                        key={s}
                        onPress={() => toggleDraft(draftStatus, setDraftStatus, s)}
                        style={[
                          styles.chip,
                          {
                            borderColor: on ? theme.primary : theme.border,
                            backgroundColor: on ? theme.primary : 'transparent',
                          },
                        ]}
                      >
                        <Text
                          style={{
                            color: on ? theme.onPrimary : theme.textSecondary,
                            fontSize: 12,
                            fontWeight: '600',
                          }}
                        >
                          {statusLabel(s, t)}
                        </Text>
                      </Pressable>
                    );
                  })}
                </View>
              </>
            )}

            <Text style={[styles.filterLabel, { color: theme.textSecondary }]}>
              {t('ticketList.priorityLabel', 'Öncelik')}
            </Text>
            <View style={styles.chipWrap}>
              {PRIORITIES.map((p) => {
                const on = draftPriority.includes(p);
                return (
                  <Pressable
                    key={p}
                    onPress={() => toggleDraft(draftPriority, setDraftPriority, p)}
                    style={[
                      styles.chip,
                      {
                        borderColor: on ? theme.primary : theme.border,
                        backgroundColor: on ? theme.primary : 'transparent',
                      },
                    ]}
                  >
                    <Text
                      style={{
                        color: on ? theme.onPrimary : theme.textSecondary,
                        fontSize: 12,
                        fontWeight: '600',
                      }}
                    >
                      {priorityLabel(p, t)}
                    </Text>
                  </Pressable>
                );
              })}
            </View>

            <View style={styles.sheetActions}>
              <Pressable
                onPress={() => {
                  setDraftStatus([]);
                  setDraftPriority([]);
                }}
                style={[styles.sheetBtn, { borderWidth: 1, borderColor: theme.border }]}
              >
                <Text style={{ color: theme.textSecondary, fontWeight: '600' }}>
                  {t('ticketList.clearFilters', 'Temizle')}
                </Text>
              </Pressable>
              <Pressable
                onPress={applyFilter}
                style={[styles.sheetBtn, { backgroundColor: theme.primary }]}
              >
                <Text style={{ color: theme.onPrimary, fontWeight: '700' }}>
                  {t('ticketList.apply', 'Uygula')}
                </Text>
              </Pressable>
            </View>
          </View>
        </SheetBackdrop>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  toolbar: { flexDirection: 'row', gap: 8, padding: 12, paddingBottom: 8 },
  searchWrap: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    borderWidth: 1,
    borderRadius: 10,
    paddingHorizontal: 10,
    height: 42,
  },
  searchInput: { flex: 1, fontSize: 14 },
  filterBtn: {
    width: 42,
    height: 42,
    borderRadius: 10,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  filterBadge: {
    position: 'absolute',
    top: -5,
    right: -5,
    minWidth: 18,
    height: 18,
    borderRadius: 9,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 4,
  },
  filterBadgeText: { color: '#fff', fontSize: 10, fontWeight: '700' },
  list: { padding: 12, paddingTop: 4, gap: 10, flexGrow: 1 },
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
  pager: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderTopWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  pagerBtn: { flexDirection: 'row', alignItems: 'center', gap: 2 },
  sheet: { borderTopLeftRadius: 20, borderTopRightRadius: 20, padding: 20, gap: 10 },
  sheetTitle: { fontSize: 17, fontWeight: '700' },
  filterLabel: { fontSize: 13, fontWeight: '600', marginTop: 4 },
  chipWrap: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: { paddingHorizontal: 12, paddingVertical: 7, borderRadius: 999, borderWidth: 1 },
  sheetActions: { flexDirection: 'row', gap: 10, marginTop: 8 },
  sheetBtn: { flex: 1, height: 46, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
});
