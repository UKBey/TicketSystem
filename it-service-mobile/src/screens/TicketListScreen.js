import { useState, useCallback, useEffect } from 'react';
import {
  View,
  Text,
  FlatList,
  Pressable,
  TextInput,
  Modal,
  ScrollView,
  StyleSheet,
  ActivityIndicator,
  RefreshControl,
} from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import { getTickets } from '../api/tickets';
import { getProducts, getProductTopics } from '../api/products';
import { getAgents } from '../api/users';
import {
  formatDate,
  statusColor,
  priorityColor,
  statusLabel,
  priorityLabel,
} from '../utils/format';
import SheetBackdrop from '../components/SheetBackdrop';
import SlaBadge from '../components/SlaBadge';
import PickerField from '../components/PickerField';

const STATUSES = ['NEW', 'IN_PROGRESS', 'WAITING_FOR_CUSTOMER', 'RESOLVED', 'CLOSED'];
const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const SLA_VALUES = ['BREACHED', 'ACTIVE', 'PAUSED'];
const DATE_PRESETS = [0, 7, 30, 90];
const PAGE_SIZE = 20;

/** Çok seçimli filtre çipleri satırı. */
function ChipGroup({ theme, options, selected, onToggle }) {
  return (
    <View style={styles.chipWrap}>
      {options.map((o) => {
        const on = selected.includes(o.value);
        return (
          <Pressable
            key={String(o.value)}
            onPress={() => onToggle(o.value)}
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
              {o.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

/** Rol bazlı bilet listesi — arama, sekmeye özel filtreler ve sayfalama. */
export default function TicketListScreen({ navigation, route }) {
  const { theme } = useTheme();
  const { t } = useTranslation();

  const endpoint = route.params?.endpoint || '/tickets';
  const baseStatus = route.params?.status; // History → CLOSED (kilitli)
  const filterCfg = route.params?.filters || ['status', 'priority', 'date'];
  const statusOptions = route.params?.statusOptions || STATUSES;
  const has = (f) => filterCfg.includes(f);

  const [tickets, setTickets] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(null);

  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');

  // Uygulanmış filtreler
  const [statusF, setStatusF] = useState([]);
  const [priorityF, setPriorityF] = useState([]);
  const [slaF, setSlaF] = useState([]);
  const [productF, setProductF] = useState(null);
  const [topicF, setTopicF] = useState(null);
  const [agentF, setAgentF] = useState(null);
  const [dateF, setDateF] = useState(null);

  // Filtre modalı + taslak değerler
  const [filterOpen, setFilterOpen] = useState(false);
  const [dStatus, setDStatus] = useState([]);
  const [dPriority, setDPriority] = useState([]);
  const [dSla, setDSla] = useState([]);
  const [dProduct, setDProduct] = useState(null);
  const [dTopic, setDTopic] = useState(null);
  const [dAgent, setDAgent] = useState(null);
  const [dDate, setDDate] = useState(null);

  const [products, setProducts] = useState([]);
  const [agents, setAgents] = useState([]);
  const [topics, setTopics] = useState([]);

  // Filtre verilerini (ürün/agent) bir kez yükle.
  useEffect(() => {
    if (has('product')) {
      getProducts().then((r) => setProducts(r.data ?? [])).catch(() => {});
    }
    if (has('agent')) {
      getAgents().then((r) => setAgents(r.data ?? [])).catch(() => {});
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Taslak ürün değişince o ürünün konularını yükle.
  useEffect(() => {
    if (!has('topic') || !dProduct) {
      setTopics([]);
      return;
    }
    getProductTopics(dProduct)
      .then((r) => setTopics(r.data ?? []))
      .catch(() => setTopics([]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dProduct]);

  // Arama girişini geciktir.
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
        const status = baseStatus ? [baseStatus] : statusF;
        let dateFrom;
        if (dateF != null) {
          const d = new Date();
          d.setHours(0, 0, 0, 0);
          if (dateF > 0) d.setDate(d.getDate() - dateF);
          dateFrom = d.toISOString();
        }
        const res = await getTickets({
          endpoint,
          page: pageArg,
          size: PAGE_SIZE,
          status: status.length ? status : undefined,
          priority: priorityF.length ? priorityF : undefined,
          slaStatus: slaF.length ? slaF : undefined,
          productId: productF ? [productF] : undefined,
          topicId: topicF ? [topicF] : undefined,
          agentId: agentF ? [agentF] : undefined,
          search: debouncedSearch || undefined,
          dateFrom,
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
    [endpoint, baseStatus, statusF, priorityF, slaF, productF, topicF, agentF, dateF, debouncedSearch, t],
  );

  useFocusEffect(
    useCallback(() => {
      load(0);
    }, [load]),
  );

  const openFilter = () => {
    setDStatus(statusF);
    setDPriority(priorityF);
    setDSla(slaF);
    setDProduct(productF);
    setDTopic(topicF);
    setDAgent(agentF);
    setDDate(dateF);
    setFilterOpen(true);
  };

  const applyFilter = () => {
    setStatusF(dStatus);
    setPriorityF(dPriority);
    setSlaF(dSla);
    setProductF(dProduct);
    setTopicF(dTopic);
    setAgentF(dAgent);
    setDateF(dDate);
    setFilterOpen(false);
  };

  const clearFilter = () => {
    setDStatus([]);
    setDPriority([]);
    setDSla([]);
    setDProduct(null);
    setDTopic(null);
    setDAgent(null);
    setDDate(null);
  };

  const toggleIn = (arr, setArr, val) =>
    setArr(arr.includes(val) ? arr.filter((x) => x !== val) : [...arr, val]);

  const activeCount =
    (baseStatus ? 0 : statusF.length) +
    priorityF.length +
    slaF.length +
    (productF ? 1 : 0) +
    (topicF ? 1 : 0) +
    (agentF ? 1 : 0) +
    (dateF != null ? 1 : 0);

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
        <View style={styles.cardTopRight}>
          <SlaBadge slaInfo={item.slaInfo} />
          <View style={[styles.badge, { backgroundColor: statusColor(item.status) }]}>
            <Text style={styles.badgeText}>{statusLabel(item.status, t)}</Text>
          </View>
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

  const productOptions = [
    { label: t('ticket.filters.allProducts', 'Tüm ürünler'), value: null },
    ...products.map((p) => ({ label: p.name, value: p.id })),
  ];
  const topicOptions = [
    { label: t('ticket.filters.allTopics', 'Tüm konular'), value: null },
    ...topics.map((tp) => ({ label: tp.name, value: tp.id })),
  ];
  const agentOptions = [
    { label: t('ticket.filters.allAgents', 'Tüm ajanlar'), value: null },
    ...agents.map((a) => ({ label: a.fullName || a.email || String(a.id), value: a.id })),
  ];

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
        <SheetBackdrop onClose={() => setFilterOpen(false)}>
          <View style={[styles.sheet, { backgroundColor: theme.bgSurface }]}>
            <Text style={[styles.sheetTitle, { color: theme.textPrimary }]}>
              {t('ticketList.filters', 'Filtreler')}
            </Text>

            <ScrollView style={styles.sheetScroll} keyboardShouldPersistTaps="handled">
              {has('status') && !baseStatus && (
                <>
                  <Text style={[styles.filterLabel, { color: theme.textSecondary }]}>
                    {t('ticketList.statusLabel', 'Durum')}
                  </Text>
                  <ChipGroup
                    theme={theme}
                    options={statusOptions.map((s) => ({ value: s, label: statusLabel(s, t) }))}
                    selected={dStatus}
                    onToggle={(v) => toggleIn(dStatus, setDStatus, v)}
                  />
                </>
              )}

              {has('priority') && (
                <>
                  <Text style={[styles.filterLabel, { color: theme.textSecondary }]}>
                    {t('ticketList.priorityLabel', 'Öncelik')}
                  </Text>
                  <ChipGroup
                    theme={theme}
                    options={PRIORITIES.map((p) => ({ value: p, label: priorityLabel(p, t) }))}
                    selected={dPriority}
                    onToggle={(v) => toggleIn(dPriority, setDPriority, v)}
                  />
                </>
              )}

              {has('sla') && (
                <>
                  <Text style={[styles.filterLabel, { color: theme.textSecondary }]}>SLA</Text>
                  <ChipGroup
                    theme={theme}
                    options={[
                      { value: 'BREACHED', label: t('ticket.filters.slaBreached', 'SLA İhlal Edildi') },
                      { value: 'ACTIVE', label: t('ticket.filters.slaActive', 'SLA Aktif') },
                      { value: 'PAUSED', label: t('ticket.filters.slaPaused', 'SLA Duraklatıldı') },
                    ]}
                    selected={dSla}
                    onToggle={(v) => toggleIn(dSla, setDSla, v)}
                  />
                </>
              )}

              {has('product') && (
                <View style={styles.pickerWrap}>
                  <PickerField
                    label={t('ticket.filters.product', 'Ürün')}
                    placeholder={t('ticket.filters.allProducts', 'Tüm ürünler')}
                    value={dProduct}
                    onChange={(v) => {
                      setDProduct(v);
                      setDTopic(null);
                    }}
                    options={productOptions}
                  />
                </View>
              )}

              {has('topic') && (
                <View style={styles.pickerWrap}>
                  <PickerField
                    label={t('ticket.filters.allTopics', 'Konu')}
                    placeholder={
                      dProduct
                        ? t('ticket.filters.allTopics', 'Tüm konular')
                        : t('ticket.filters.selectProductFirst', 'Önce ürün seçin')
                    }
                    value={dTopic}
                    onChange={setDTopic}
                    disabled={!dProduct}
                    options={topicOptions}
                  />
                </View>
              )}

              {has('agent') && (
                <View style={styles.pickerWrap}>
                  <PickerField
                    label={t('ticket.filters.allAgents', 'Ajan')}
                    placeholder={t('ticket.filters.allAgents', 'Tüm ajanlar')}
                    value={dAgent}
                    onChange={setDAgent}
                    options={agentOptions}
                  />
                </View>
              )}

              {has('date') && (
                <>
                  <Text style={[styles.filterLabel, { color: theme.textSecondary }]}>
                    {t('ticket.filters.dateRange', 'Tarih aralığı')}
                  </Text>
                  <ChipGroup
                    theme={theme}
                    options={DATE_PRESETS.map((d) => ({
                      value: d,
                      label:
                        d === 0
                          ? t('ticket.filters.presetToday', 'Bugün')
                          : t(`ticket.filters.presetLast${d}`, `Son ${d} gün`),
                    }))}
                    selected={dDate != null ? [dDate] : []}
                    onToggle={(v) => setDDate(dDate === v ? null : v)}
                  />
                </>
              )}
            </ScrollView>

            <View style={styles.sheetActions}>
              <Pressable
                onPress={clearFilter}
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
  cardTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8 },
  cardTopRight: { flexDirection: 'row', alignItems: 'center', gap: 6, flexShrink: 1 },
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
  sheetScroll: { maxHeight: 420 },
  filterLabel: { fontSize: 13, fontWeight: '600', marginTop: 10, marginBottom: 6 },
  chipWrap: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: { paddingHorizontal: 12, paddingVertical: 7, borderRadius: 999, borderWidth: 1 },
  pickerWrap: { marginTop: 10 },
  sheetActions: { flexDirection: 'row', gap: 10, marginTop: 6 },
  sheetBtn: { flex: 1, height: 46, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
});
