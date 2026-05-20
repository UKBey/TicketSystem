import { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  TextInput,
  FlatList,
  Pressable,
  Modal,
  StyleSheet,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import { getUsers, assignProduct, removeProduct } from '../api/users';
import { getProducts } from '../api/products';
import AgentLimitsSheet from '../components/AgentLimitsSheet';
import RoleFilterChips from '../components/RoleFilterChips';

const fullNameOf = (u) =>
  u.fullName || `${u.firstName ?? ''} ${u.lastName ?? ''}`.trim() || '—';

/** Yönetim paneli — kullanıcı ürün yetkileri ve agent bilet limitleri. */
export default function AdminPanelScreen() {
  const { theme } = useTheme();
  const { t } = useTranslation();

  const [users, setUsers] = useState([]);
  const [products, setProducts] = useState([]);
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busyUserId, setBusyUserId] = useState(null);
  const [addUser, setAddUser] = useState(null);
  const [limitsUser, setLimitsUser] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getUsers({
        page: 0,
        size: 100,
        search: search || undefined,
        role: roleFilter || undefined,
      });
      setUsers(res.data?.content ?? res.data ?? []);
    } catch {
      setUsers([]);
    } finally {
      setLoading(false);
    }
  }, [search, roleFilter]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    getProducts()
      .then((res) => setProducts(res.data ?? []))
      .catch(() => setProducts([]));
  }, []);

  const assign = async (userId, productId) => {
    setBusyUserId(userId);
    try {
      const res = await assignProduct(userId, productId);
      setUsers((prev) => prev.map((u) => (u.id === userId ? res.data : u)));
    } catch (e) {
      Alert.alert(e?.response?.data?.message || t('admin.panel.errorAssign', 'Ürün atanamadı.'));
    } finally {
      setBusyUserId(null);
      setAddUser(null);
    }
  };

  const remove = (user, product) => {
    Alert.alert(
      t('admin.panel.confirmRemove', 'Bu ürün yetkisini kaldırmak istediğinizden emin misiniz?'),
      product.name,
      [
        { text: t('common.cancel', 'İptal'), style: 'cancel' },
        {
          text: t('form.delete', 'Sil'),
          style: 'destructive',
          onPress: async () => {
            try {
              const res = await removeProduct(user.id, product.id);
              setUsers((prev) => prev.map((u) => (u.id === user.id ? res.data : u)));
            } catch (e) {
              Alert.alert(
                e?.response?.data?.message || t('admin.panel.errorRemove', 'Ürün yetkisi kaldırılamadı.'),
              );
            }
          },
        },
      ],
    );
  };

  const renderItem = ({ item }) => {
    const isAgent = item.role === 'AGENT' || item.role === 'AGENT_ADMIN';
    const authorized = item.authorizedProducts || [];
    return (
      <View style={[styles.card, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
        <View style={styles.cardTop}>
          <Text style={[styles.name, { color: theme.textPrimary }]} numberOfLines={1}>
            {fullNameOf(item)}
          </Text>
          <View style={[styles.roleBadge, { backgroundColor: theme.primary }]}>
            <Text style={styles.roleText}>{item.role || '—'}</Text>
          </View>
        </View>
        <Text style={[styles.email, { color: theme.textSecondary }]}>{item.email}</Text>

        <Text style={[styles.sectionLabel, { color: theme.textTertiary }]}>
          {t('admin.panel.colAuthorized', 'Yetkili Ürünler')}
        </Text>
        {authorized.length === 0 ? (
          <Text style={{ color: theme.textTertiary, fontSize: 13 }}>
            {t('admin.panel.noProducts', 'Ürün yok')}
          </Text>
        ) : (
          <View style={styles.chipWrap}>
            {authorized.map((p) => (
              <Pressable
                key={p.id}
                onPress={() => remove(item, p)}
                style={[styles.chip, { backgroundColor: theme.bgSurfaceSecondary, borderColor: theme.border }]}
              >
                <Text style={[styles.chipText, { color: theme.textPrimary }]}>{p.name}</Text>
                <Ionicons name="close-circle" size={15} color={theme.textTertiary} />
              </Pressable>
            ))}
          </View>
        )}

        <View style={styles.actionRow}>
          <Pressable
            onPress={() => setAddUser(item)}
            disabled={busyUserId === item.id}
            style={[styles.actBtn, { backgroundColor: theme.primary, opacity: busyUserId === item.id ? 0.6 : 1 }]}
          >
            {busyUserId === item.id ? (
              <ActivityIndicator color="#fff" size="small" />
            ) : (
              <Text style={styles.actBtnText}>+ {t('admin.panel.addProduct', 'Ürün Ekle')}</Text>
            )}
          </Pressable>
          {isAgent && (
            <Pressable
              onPress={() => setLimitsUser(item)}
              style={[styles.actBtn, styles.actBtnOutline, { borderColor: theme.border }]}
            >
              <Ionicons name="options-outline" size={15} color={theme.textSecondary} />
              <Text style={[styles.actBtnText, { color: theme.textSecondary }]}>
                {t('admin.panel.agentLimits', 'Bilet Limitleri')}
              </Text>
            </Pressable>
          )}
        </View>
      </View>
    );
  };

  const unassigned = addUser
    ? products.filter(
        (p) => !(addUser.authorizedProducts || []).some((ap) => ap.id === p.id),
      )
    : [];

  return (
    <View style={[styles.container, { backgroundColor: theme.bgBody }]}>
      <View style={styles.toolbar}>
        <TextInput
          value={search}
          onChangeText={setSearch}
          placeholder={t('admin.panel.searchPlaceholder', 'Ad veya e-posta ara…')}
          placeholderTextColor={theme.textTertiary}
          autoCapitalize="none"
          style={[
            styles.searchInput,
            { backgroundColor: theme.bgInput, borderColor: theme.border, color: theme.textPrimary },
          ]}
        />
      </View>

      <RoleFilterChips value={roleFilter} onChange={setRoleFilter} />

      {loading ? (
        <ActivityIndicator style={{ marginTop: 32 }} size="large" color={theme.primary} />
      ) : (
        <FlatList
          data={users}
          keyExtractor={(u) => String(u.id)}
          renderItem={renderItem}
          contentContainerStyle={styles.list}
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="on-drag"
          ListEmptyComponent={
            <Text style={{ color: theme.textTertiary, textAlign: 'center', marginTop: 32 }}>
              {search ? t('admin.panel.noUsersFiltered', 'Eşleşen kullanıcı yok.') : t('admin.panel.noUsers', 'Kullanıcı bulunamadı.')}
            </Text>
          }
        />
      )}

      {/* Ürün ekleme modalı */}
      <Modal
        visible={!!addUser}
        transparent
        animationType="slide"
        onRequestClose={() => setAddUser(null)}
      >
        <Pressable
          style={[styles.backdrop, { backgroundColor: theme.overlay }]}
          onPress={() => setAddUser(null)}
        >
          <Pressable
            style={[styles.sheet, { backgroundColor: theme.bgSurface }]}
            onPress={(e) => e.stopPropagation()}
          >
            <Text style={[styles.sheetTitle, { color: theme.textPrimary }]}>
              {t('admin.panel.selectProduct', 'Ürün seçin')}
            </Text>
            {unassigned.length === 0 ? (
              <Text style={{ color: theme.textTertiary, paddingVertical: 16 }}>
                {t('admin.panel.noProducts', 'Ürün yok')}
              </Text>
            ) : (
              <FlatList
                data={unassigned}
                keyExtractor={(p) => String(p.id)}
                style={{ maxHeight: 340 }}
                renderItem={({ item }) => (
                  <Pressable
                    onPress={() => assign(addUser.id, item.id)}
                    style={[styles.option, { borderBottomColor: theme.border }]}
                  >
                    <Text style={{ fontSize: 15, color: theme.textPrimary }}>{item.name}</Text>
                  </Pressable>
                )}
              />
            )}
            <Pressable
              onPress={() => setAddUser(null)}
              style={[styles.closeBtn, { borderWidth: 1, borderColor: theme.border }]}
            >
              <Text style={{ color: theme.textSecondary, fontWeight: '600' }}>
                {t('common.cancel', 'İptal')}
              </Text>
            </Pressable>
          </Pressable>
        </Pressable>
      </Modal>

      <AgentLimitsSheet
        visible={!!limitsUser}
        user={limitsUser}
        onClose={() => setLimitsUser(null)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  toolbar: { padding: 12 },
  searchInput: { borderWidth: 1, borderRadius: 10, paddingHorizontal: 12, height: 42, fontSize: 14 },
  list: { paddingHorizontal: 12, paddingBottom: 16, gap: 10, flexGrow: 1 },
  card: { borderRadius: 12, borderWidth: 1, padding: 14, gap: 8 },
  cardTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8 },
  name: { fontSize: 15, fontWeight: '700', flex: 1 },
  email: { fontSize: 13 },
  roleBadge: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 999 },
  roleText: { color: '#fff', fontSize: 10, fontWeight: '700' },
  sectionLabel: { fontSize: 11, fontWeight: '700', textTransform: 'uppercase', marginTop: 2 },
  chipWrap: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: 8,
    paddingVertical: 4,
  },
  chipText: { fontSize: 12, fontWeight: '500' },
  actionRow: { flexDirection: 'row', gap: 8, marginTop: 4 },
  actBtn: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
    height: 40,
    borderRadius: 9,
  },
  actBtnOutline: { backgroundColor: 'transparent', borderWidth: 1 },
  actBtnText: { color: '#fff', fontSize: 13, fontWeight: '700' },
  backdrop: { flex: 1, justifyContent: 'flex-end' },
  sheet: { borderTopLeftRadius: 20, borderTopRightRadius: 20, padding: 20, gap: 10 },
  sheetTitle: { fontSize: 17, fontWeight: '700' },
  option: { paddingVertical: 14, borderBottomWidth: StyleSheet.hairlineWidth },
  closeBtn: { height: 46, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
});
