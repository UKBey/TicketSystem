import { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  FlatList,
  Pressable,
  StyleSheet,
  ActivityIndicator,
  RefreshControl,
} from 'react-native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import { getProducts, getProductTopics } from '../api/products';

/** Ürünler — liste; bir ürüne dokununca konuları (topic) yüklenip gösterilir. */
export default function ProductsScreen() {
  const { theme } = useTheme();
  const { t } = useTranslation();

  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [expanded, setExpanded] = useState(null);
  const [topicsById, setTopicsById] = useState({});

  const load = useCallback(async (isRefresh = false) => {
    if (isRefresh) setRefreshing(true);
    else setLoading(true);
    try {
      const res = await getProducts();
      setProducts(res.data ?? []);
    } catch (e) {
      setProducts([]);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const toggle = (product) => {
    const next = expanded === product.id ? null : product.id;
    setExpanded(next);
    if (next && topicsById[product.id] === undefined) {
      getProductTopics(product.id)
        .then((res) => setTopicsById((m) => ({ ...m, [product.id]: res.data ?? [] })))
        .catch(() => setTopicsById((m) => ({ ...m, [product.id]: [] })));
    }
  };

  const renderItem = ({ item }) => {
    const open = expanded === item.id;
    const topics = topicsById[item.id];
    return (
      <Pressable
        onPress={() => toggle(item)}
        style={[styles.card, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}
      >
        <View style={styles.cardTop}>
          <Text style={[styles.name, { color: theme.textPrimary }]}>
            {open ? '▾ ' : '▸ '}
            {item.name}
          </Text>
          {item.isActive === false && (
            <Text style={[styles.inactive, { color: theme.textTertiary }]}>
              {t('products.inactive', 'Pasif')}
            </Text>
          )}
        </View>
        {!!item.description && (
          <Text style={[styles.desc, { color: theme.textSecondary }]}>{item.description}</Text>
        )}
        {open && (
          <View style={styles.topics}>
            <Text style={[styles.topicsLabel, { color: theme.textSecondary }]}>
              {t('products.topics', 'Konular')}
            </Text>
            {topics === undefined ? (
              <ActivityIndicator color={theme.primary} />
            ) : topics.length === 0 ? (
              <Text style={{ color: theme.textTertiary, fontSize: 13 }}>
                {t('products.noTopics', 'Konu yok.')}
              </Text>
            ) : (
              topics.map((tp) => (
                <Text key={tp.id} style={[styles.topic, { color: theme.textPrimary, borderColor: theme.border }]}>
                  {tp.name}
                </Text>
              ))
            )}
          </View>
        )}
      </Pressable>
    );
  };

  return (
    <View style={[styles.container, { backgroundColor: theme.bgBody }]}>
      {loading ? (
        <ActivityIndicator style={styles.center} size="large" color={theme.primary} />
      ) : (
        <FlatList
          data={products}
          keyExtractor={(p) => String(p.id)}
          renderItem={renderItem}
          contentContainerStyle={styles.list}
          refreshControl={
            <RefreshControl refreshing={refreshing} onRefresh={() => load(true)} tintColor={theme.primary} />
          }
          ListEmptyComponent={
            <Text style={[styles.center, { color: theme.textTertiary }]}>
              {t('products.empty', 'Ürün yok.')}
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
  name: { fontSize: 15, fontWeight: '600', flex: 1 },
  inactive: { fontSize: 11, fontWeight: '600' },
  desc: { fontSize: 13, lineHeight: 18 },
  topics: { gap: 6, marginTop: 4 },
  topicsLabel: { fontSize: 12, fontWeight: '700' },
  topic: { fontSize: 13, borderWidth: 1, borderRadius: 8, paddingHorizontal: 10, paddingVertical: 6 },
  center: { marginTop: 48, textAlign: 'center', alignSelf: 'center' },
});
