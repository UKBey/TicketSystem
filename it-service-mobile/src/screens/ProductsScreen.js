import { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  FlatList,
  Pressable,
  Modal,
  Switch,
  TextInput,
  StyleSheet,
  ActivityIndicator,
  RefreshControl,
  Alert,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import { useAuth } from '../auth/AuthContext';
import {
  getProducts,
  createProduct,
  updateProduct,
  deleteProduct,
  getProductTopics,
  createTopic,
  updateTopic,
  deleteTopic,
} from '../api/products';
import SheetBackdrop from '../components/SheetBackdrop';

/** Ürünler — liste, ürün biletleri, konular; admin için ürün/konu ekle-düzenle-sil. */
export default function ProductsScreen({ navigation }) {
  const { theme } = useTheme();
  const { t } = useTranslation();
  const { hasRole } = useAuth();
  const isAdmin = hasRole('AGENT_ADMIN') || hasRole('MANAGER');

  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [expanded, setExpanded] = useState(null);
  const [topicsById, setTopicsById] = useState({});

  const [productForm, setProductForm] = useState(null); // { id?, name, isActive, maxActiveTickets }
  const [topicForm, setTopicForm] = useState(null); // { id?, productId, name, isActive }
  const [saving, setSaving] = useState(false);

  const load = useCallback(async (isRefresh = false) => {
    if (isRefresh) setRefreshing(true);
    else setLoading(true);
    try {
      const res = await getProducts();
      setProducts(res.data ?? []);
    } catch {
      setProducts([]);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const loadTopics = useCallback(
    (productId) => {
      getProductTopics(productId, isAdmin)
        .then((res) => setTopicsById((m) => ({ ...m, [productId]: res.data ?? [] })))
        .catch(() => setTopicsById((m) => ({ ...m, [productId]: [] })));
    },
    [isAdmin],
  );

  const toggleExpand = (product) => {
    const next = expanded === product.id ? null : product.id;
    setExpanded(next);
    if (next && topicsById[product.id] === undefined) loadTopics(product.id);
  };

  const saveProduct = async () => {
    const name = (productForm.name || '').trim();
    if (!name) {
      Alert.alert(t('productPanel.errorNameRequired', 'Ürün adı boş olamaz.'));
      return;
    }
    const maxRaw = String(productForm.maxActiveTickets ?? '').trim();
    const body = {
      name,
      isActive: productForm.isActive,
      maxActiveTickets: maxRaw ? parseInt(maxRaw, 10) : null,
    };
    setSaving(true);
    try {
      if (productForm.id) await updateProduct(productForm.id, body);
      else await createProduct(body);
      setProductForm(null);
      await load();
    } catch (e) {
      Alert.alert(e?.response?.data?.message || t('productPanel.errorSave', 'Ürün kaydedilemedi.'));
    } finally {
      setSaving(false);
    }
  };

  const removeProduct = (product) => {
    Alert.alert(
      t('productPanel.confirmDelete', 'Bu ürünü silmek istediğinizden emin misiniz?'),
      product.name,
      [
        { text: t('common.cancel', 'İptal'), style: 'cancel' },
        {
          text: t('form.delete', 'Sil'),
          style: 'destructive',
          onPress: async () => {
            try {
              await deleteProduct(product.id);
              await load();
            } catch (e) {
              Alert.alert(e?.response?.data?.message || t('productPanel.errorDelete', 'Ürün silinemedi.'));
            }
          },
        },
      ],
    );
  };

  const saveTopic = async () => {
    const name = (topicForm.name || '').trim();
    if (!name) {
      Alert.alert(t('topic.errorNameRequired', 'Konu adı zorunludur.'));
      return;
    }
    const body = { name, isActive: topicForm.isActive };
    const pid = topicForm.productId;
    setSaving(true);
    try {
      if (topicForm.id) await updateTopic(topicForm.id, body);
      else await createTopic(pid, body);
      setTopicForm(null);
      loadTopics(pid);
    } catch (e) {
      Alert.alert(e?.response?.data?.message || t('topic.errorSave', 'Talep konusu kaydedilemedi.'));
    } finally {
      setSaving(false);
    }
  };

  const removeTopic = (productId, topic) => {
    Alert.alert(
      t('topic.confirmDelete', '"{{name}}" konusunu silmek istediğinize emin misiniz?', { name: topic.name }),
      undefined,
      [
        { text: t('common.cancel', 'İptal'), style: 'cancel' },
        {
          text: t('form.delete', 'Sil'),
          style: 'destructive',
          onPress: async () => {
            try {
              await deleteTopic(topic.id);
              loadTopics(productId);
            } catch (e) {
              Alert.alert(e?.response?.data?.message || t('topic.errorDelete', 'Talep konusu silinemedi.'));
            }
          },
        },
      ],
    );
  };

  const renderItem = ({ item }) => {
    const open = expanded === item.id;
    const topics = topicsById[item.id];
    return (
      <View style={[styles.card, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
        <View style={styles.cardHead}>
          <Text style={[styles.name, { color: theme.textPrimary }]} numberOfLines={1}>
            {item.name}
          </Text>
          {isAdmin && (
            <View style={styles.iconRow}>
              <Pressable
                hitSlop={6}
                onPress={() =>
                  setProductForm({
                    id: item.id,
                    name: item.name,
                    isActive: item.isActive !== false,
                    maxActiveTickets: item.maxActiveTickets != null ? String(item.maxActiveTickets) : '',
                  })
                }
              >
                <Ionicons name="create-outline" size={20} color={theme.textSecondary} />
              </Pressable>
              <Pressable hitSlop={6} onPress={() => removeProduct(item)}>
                <Ionicons name="trash-outline" size={20} color={theme.danger} />
              </Pressable>
            </View>
          )}
        </View>
        <Text style={[styles.meta, { color: theme.textTertiary }]}>
          {item.isActive === false
            ? t('productPanel.statusInactive', 'Pasif')
            : t('productPanel.statusActive', 'Aktif')}
          {item.maxActiveTickets != null
            ? ` · ${t('productPanel.colMaxTickets', 'Limit')}: ${item.maxActiveTickets}`
            : ''}
        </Text>

        <View style={styles.btnRow}>
          <Pressable
            onPress={() =>
              navigation.navigate('ProductTickets', {
                endpoint: `/tickets/by-product/${item.id}`,
                title: item.name,
              })
            }
            style={[styles.smallBtn, { backgroundColor: theme.primary }]}
          >
            <Ionicons name="documents-outline" size={14} color="#fff" />
            <Text style={styles.smallBtnText}>{t('product.ticketsSection', 'Biletler')}</Text>
          </Pressable>
          <Pressable
            onPress={() => toggleExpand(item)}
            style={[styles.smallBtn, styles.smallBtnOutline, { borderColor: theme.border }]}
          >
            <Ionicons
              name={open ? 'chevron-up' : 'chevron-down'}
              size={14}
              color={theme.textSecondary}
            />
            <Text style={[styles.smallBtnText, { color: theme.textSecondary }]}>
              {t('products.topics', 'Konular')}
            </Text>
          </Pressable>
        </View>

        {open && (
          <View style={[styles.topicsBox, { borderTopColor: theme.border }]}>
            {topics === undefined ? (
              <ActivityIndicator color={theme.primary} />
            ) : (
              <>
                {topics.length === 0 && (
                  <Text style={{ color: theme.textTertiary, fontSize: 13 }}>
                    {t('topic.emptyUser', 'Bu ürün için tanımlı talep konusu bulunmuyor.')}
                  </Text>
                )}
                {topics.map((tp) => (
                  <View key={tp.id} style={[styles.topicRow, { borderColor: theme.border }]}>
                    <Text
                      style={[
                        styles.topicName,
                        { color: tp.isActive === false ? theme.textTertiary : theme.textPrimary },
                      ]}
                    >
                      {tp.name}
                      {tp.isActive === false ? ` (${t('topic.statusInactive', 'Pasif')})` : ''}
                    </Text>
                    {isAdmin && (
                      <View style={styles.iconRow}>
                        <Pressable
                          hitSlop={6}
                          onPress={() =>
                            setTopicForm({
                              id: tp.id,
                              productId: item.id,
                              name: tp.name,
                              isActive: tp.isActive !== false,
                            })
                          }
                        >
                          <Ionicons name="create-outline" size={18} color={theme.textSecondary} />
                        </Pressable>
                        <Pressable hitSlop={6} onPress={() => removeTopic(item.id, tp)}>
                          <Ionicons name="trash-outline" size={18} color={theme.danger} />
                        </Pressable>
                      </View>
                    )}
                  </View>
                ))}
                {isAdmin && (
                  <Pressable
                    onPress={() => setTopicForm({ productId: item.id, name: '', isActive: true })}
                    style={[styles.addTopicBtn, { borderColor: theme.primary }]}
                  >
                    <Text style={{ color: theme.primary, fontWeight: '700', fontSize: 13 }}>
                      + {t('topic.add', 'Yeni Konu')}
                    </Text>
                  </Pressable>
                )}
              </>
            )}
          </View>
        )}
      </View>
    );
  };

  return (
    <View style={[styles.container, { backgroundColor: theme.bgBody }]}>
      {isAdmin && (
        <View style={styles.toolbar}>
          <Pressable
            onPress={() => setProductForm({ name: '', isActive: true, maxActiveTickets: '' })}
            style={[styles.newBtn, { backgroundColor: theme.primary }]}
          >
            <Text style={{ color: theme.onPrimary, fontWeight: '700' }}>
              + {t('productPanel.newProduct', 'Yeni Ürün')}
            </Text>
          </Pressable>
        </View>
      )}

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
              {t('productPanel.noProducts', 'Ürün bulunamadı.')}
            </Text>
          }
        />
      )}

      {/* Ürün ekleme/düzenleme modalı */}
      <Modal
        visible={!!productForm}
        transparent
        animationType="slide"
        onRequestClose={() => setProductForm(null)}
      >
        <SheetBackdrop onClose={() => setProductForm(null)}>
          <View style={[styles.sheet, { backgroundColor: theme.bgSurface }]}>
            <Text style={[styles.sheetTitle, { color: theme.textPrimary }]}>
              {productForm?.id
                ? t('productPanel.modalEditTitle', 'Ürünü Düzenle')
                : t('productPanel.modalNewTitle', 'Yeni Ürün')}
            </Text>
            <FormField label={t('productPanel.labelName', 'Ürün Adı')} theme={theme}>
              <TextInput
                value={productForm?.name}
                onChangeText={(v) => setProductForm((f) => ({ ...f, name: v }))}
                placeholderTextColor={theme.textTertiary}
                style={[styles.input, { backgroundColor: theme.bgInput, borderColor: theme.border, color: theme.textPrimary }]}
              />
            </FormField>
            <FormField label={t('productPanel.labelMaxTickets', 'Maksimum Eşzamanlı Bilet')} theme={theme}>
              <TextInput
                value={String(productForm?.maxActiveTickets ?? '')}
                onChangeText={(v) => setProductForm((f) => ({ ...f, maxActiveTickets: v }))}
                keyboardType="number-pad"
                placeholder={t('productPanel.placeholderUnlimited', 'Limitsiz')}
                placeholderTextColor={theme.textTertiary}
                style={[styles.input, { backgroundColor: theme.bgInput, borderColor: theme.border, color: theme.textPrimary }]}
              />
            </FormField>
            <View style={styles.switchRow}>
              <Text style={[styles.fieldLabel, { color: theme.textSecondary }]}>
                {t('productPanel.labelActive', 'Aktif')}
              </Text>
              <Switch
                value={!!productForm?.isActive}
                onValueChange={(v) => setProductForm((f) => ({ ...f, isActive: v }))}
                trackColor={{ true: theme.primary, false: theme.border }}
              />
            </View>
            <SheetButtons
              theme={theme}
              t={t}
              busy={saving}
              onCancel={() => setProductForm(null)}
              onConfirm={saveProduct}
            />
          </View>
        </SheetBackdrop>
      </Modal>

      {/* Konu ekleme/düzenleme modalı */}
      <Modal
        visible={!!topicForm}
        transparent
        animationType="slide"
        onRequestClose={() => setTopicForm(null)}
      >
        <SheetBackdrop onClose={() => setTopicForm(null)}>
          <View style={[styles.sheet, { backgroundColor: theme.bgSurface }]}>
            <Text style={[styles.sheetTitle, { color: theme.textPrimary }]}>
              {topicForm?.id
                ? t('topic.modalEditTitle', 'Talep Konusunu Düzenle')
                : t('topic.modalNewTitle', 'Yeni Talep Konusu')}
            </Text>
            <FormField label={t('topic.labelName', 'Konu Adı')} theme={theme}>
              <TextInput
                value={topicForm?.name}
                onChangeText={(v) => setTopicForm((f) => ({ ...f, name: v }))}
                placeholderTextColor={theme.textTertiary}
                style={[styles.input, { backgroundColor: theme.bgInput, borderColor: theme.border, color: theme.textPrimary }]}
              />
            </FormField>
            <View style={styles.switchRow}>
              <Text style={[styles.fieldLabel, { color: theme.textSecondary }]}>
                {t('topic.labelActive', 'Aktif')}
              </Text>
              <Switch
                value={!!topicForm?.isActive}
                onValueChange={(v) => setTopicForm((f) => ({ ...f, isActive: v }))}
                trackColor={{ true: theme.primary, false: theme.border }}
              />
            </View>
            <SheetButtons
              theme={theme}
              t={t}
              busy={saving}
              onCancel={() => setTopicForm(null)}
              onConfirm={saveTopic}
            />
          </View>
        </SheetBackdrop>
      </Modal>
    </View>
  );
}

function FormField({ label, theme, children }) {
  return (
    <View style={styles.group}>
      <Text style={[styles.fieldLabel, { color: theme.textSecondary }]}>{label}</Text>
      {children}
    </View>
  );
}

function SheetButtons({ theme, t, busy, onCancel, onConfirm }) {
  return (
    <View style={styles.sheetActions}>
      <Pressable onPress={onCancel} style={[styles.sheetBtn, { borderWidth: 1, borderColor: theme.border }]}>
        <Text style={{ color: theme.textSecondary, fontWeight: '600' }}>{t('common.cancel', 'İptal')}</Text>
      </Pressable>
      <Pressable
        onPress={onConfirm}
        disabled={busy}
        style={[styles.sheetBtn, { backgroundColor: theme.primary, opacity: busy ? 0.5 : 1 }]}
      >
        {busy ? (
          <ActivityIndicator color={theme.onPrimary} size="small" />
        ) : (
          <Text style={{ color: theme.onPrimary, fontWeight: '700' }}>{t('common.save', 'Kaydet')}</Text>
        )}
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  toolbar: { padding: 12, paddingBottom: 4 },
  newBtn: { height: 42, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
  list: { padding: 12, gap: 10, flexGrow: 1 },
  card: { borderRadius: 12, borderWidth: 1, padding: 14, gap: 8 },
  cardHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  name: { fontSize: 15, fontWeight: '700', flex: 1 },
  iconRow: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  meta: { fontSize: 12 },
  btnRow: { flexDirection: 'row', gap: 8 },
  smallBtn: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 5,
    height: 36,
    borderRadius: 8,
  },
  smallBtnOutline: { backgroundColor: 'transparent', borderWidth: 1 },
  smallBtnText: { color: '#fff', fontSize: 13, fontWeight: '700' },
  topicsBox: { borderTopWidth: 1, paddingTop: 10, gap: 8 },
  topicRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 8,
    gap: 8,
  },
  topicName: { fontSize: 13, flex: 1 },
  addTopicBtn: { borderWidth: 1, borderRadius: 8, paddingVertical: 9, alignItems: 'center' },
  center: { marginTop: 48, textAlign: 'center', alignSelf: 'center' },
  sheet: { borderTopLeftRadius: 20, borderTopRightRadius: 20, padding: 20, gap: 12 },
  sheetTitle: { fontSize: 17, fontWeight: '700' },
  group: { gap: 5 },
  fieldLabel: { fontSize: 13, fontWeight: '600' },
  input: { borderWidth: 1, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 10, fontSize: 14 },
  switchRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  sheetActions: { flexDirection: 'row', gap: 10, marginTop: 4 },
  sheetBtn: { flex: 1, height: 46, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
});
