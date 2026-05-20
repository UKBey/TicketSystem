import { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  TextInput,
  FlatList,
  Pressable,
  Modal,
  Switch,
  StyleSheet,
  ActivityIndicator,
  ScrollView,
  Alert,
} from 'react-native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import {
  getUsers,
  createUser,
  getAssignableRoles,
  updateUserRoles,
  updateUserStatus,
} from '../api/users';
import PickerField from '../components/PickerField';

const EMPTY_FORM = { username: '', email: '', firstName: '', lastName: '', password: '', role: null };

/** Kullanıcı yönetimi — liste, arama, durum değiştirme, rol düzenleme, yeni kullanıcı. */
export default function UserManagementScreen() {
  const { theme } = useTheme();
  const { t } = useTranslation();

  const [users, setUsers] = useState([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [roles, setRoles] = useState([]);

  const [createOpen, setCreateOpen] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [saving, setSaving] = useState(false);

  const [editUser, setEditUser] = useState(null);
  const [editRole, setEditRole] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getUsers({ page: 0, size: 100, search: search || undefined });
      setUsers(res.data?.content ?? res.data ?? []);
    } catch (e) {
      setUsers([]);
    } finally {
      setLoading(false);
    }
  }, [search]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    getAssignableRoles()
      .then((res) => setRoles(res.data ?? []))
      .catch(() => setRoles([]));
  }, []);

  const roleOptions = roles.map((r) => ({ label: r, value: r }));

  const submitCreate = async () => {
    const { username, email, firstName, lastName, password, role } = form;
    if (!username.trim() || !email.trim() || !firstName.trim() || !lastName.trim() || !password || !role) {
      Alert.alert(t('userManagement.required', 'Tüm alanlar zorunludur.'));
      return;
    }
    setSaving(true);
    try {
      await createUser({
        username: username.trim(),
        email: email.trim(),
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        password,
        roles: [role],
      });
      setCreateOpen(false);
      setForm(EMPTY_FORM);
      load();
    } catch (e) {
      Alert.alert(e?.response?.data?.message || t('userManagement.createFailed', 'Oluşturulamadı.'));
    } finally {
      setSaving(false);
    }
  };

  const submitRole = async () => {
    if (!editRole) return;
    setSaving(true);
    try {
      const res = await updateUserRoles(editUser.id, [editRole]);
      setUsers((prev) =>
        prev.map((u) => (u.id === editUser.id ? { ...u, role: res.data?.role ?? editRole } : u)),
      );
      setEditUser(null);
    } catch (e) {
      Alert.alert(e?.response?.data?.message || t('userManagement.roleFailed', 'Rol güncellenemedi.'));
    } finally {
      setSaving(false);
    }
  };

  const toggleStatus = async (user) => {
    const next = !user.isActive;
    setUsers((prev) => prev.map((u) => (u.id === user.id ? { ...u, isActive: next } : u)));
    try {
      await updateUserStatus(user.id, next);
    } catch (e) {
      setUsers((prev) => prev.map((u) => (u.id === user.id ? { ...u, isActive: !next } : u)));
      Alert.alert(t('userManagement.statusFailed', 'Durum güncellenemedi.'));
    }
  };

  const renderItem = ({ item }) => (
    <View style={[styles.card, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
      <View style={styles.cardTop}>
        <Text style={[styles.name, { color: theme.textPrimary }]}>
          {item.firstName} {item.lastName}
        </Text>
        <Switch
          value={!!item.isActive}
          onValueChange={() => toggleStatus(item)}
          trackColor={{ true: theme.success, false: theme.border }}
        />
      </View>
      <Text style={[styles.email, { color: theme.textSecondary }]}>{item.email}</Text>
      <View style={styles.cardBottom}>
        <View style={[styles.roleBadge, { backgroundColor: theme.primary }]}>
          <Text style={styles.roleText}>{item.role || '—'}</Text>
        </View>
        <Pressable
          onPress={() => {
            setEditUser(item);
            setEditRole(item.role || null);
          }}
        >
          <Text style={{ color: theme.primary, fontWeight: '600', fontSize: 13 }}>
            {t('userManagement.editRole', 'Rol Değiştir')}
          </Text>
        </Pressable>
      </View>
    </View>
  );

  return (
    <View style={[styles.container, { backgroundColor: theme.bgBody }]}>
      <View style={styles.toolbar}>
        <TextInput
          value={search}
          onChangeText={setSearch}
          placeholder={t('userManagement.search', 'Ara...')}
          placeholderTextColor={theme.textTertiary}
          style={[styles.searchInput, { backgroundColor: theme.bgInput, borderColor: theme.border, color: theme.textPrimary }]}
        />
        <Pressable
          onPress={() => {
            setForm(EMPTY_FORM);
            setCreateOpen(true);
          }}
          style={[styles.newBtn, { backgroundColor: theme.primary }]}
        >
          <Text style={{ color: theme.onPrimary, fontWeight: '700' }}>
            + {t('userManagement.new', 'Yeni')}
          </Text>
        </Pressable>
      </View>

      {loading ? (
        <ActivityIndicator style={{ marginTop: 32 }} size="large" color={theme.primary} />
      ) : (
        <FlatList
          data={users}
          keyExtractor={(u) => String(u.id)}
          renderItem={renderItem}
          contentContainerStyle={styles.list}
          ListEmptyComponent={
            <Text style={{ color: theme.textTertiary, textAlign: 'center', marginTop: 32 }}>
              {t('userManagement.empty', 'Kullanıcı yok.')}
            </Text>
          }
        />
      )}

      {/* Yeni kullanıcı modalı */}
      <Modal visible={createOpen} transparent animationType="slide" onRequestClose={() => setCreateOpen(false)}>
        <View style={[styles.backdrop, { backgroundColor: theme.overlay }]}>
          <View style={[styles.sheet, { backgroundColor: theme.bgSurface }]}>
            <Text style={[styles.sheetTitle, { color: theme.textPrimary }]}>
              {t('userManagement.createUser', 'Yeni Kullanıcı')}
            </Text>
            <ScrollView style={{ maxHeight: 420 }} keyboardShouldPersistTaps="handled">
              <FormInput label={t('userManagement.username', 'Kullanıcı adı')}
                value={form.username} onChangeText={(v) => setForm((f) => ({ ...f, username: v }))}
                theme={theme} autoCapitalize="none" />
              <FormInput label={t('userManagement.email', 'E-posta')}
                value={form.email} onChangeText={(v) => setForm((f) => ({ ...f, email: v }))}
                theme={theme} autoCapitalize="none" keyboardType="email-address" />
              <FormInput label={t('userManagement.firstName', 'Ad')}
                value={form.firstName} onChangeText={(v) => setForm((f) => ({ ...f, firstName: v }))}
                theme={theme} />
              <FormInput label={t('userManagement.lastName', 'Soyad')}
                value={form.lastName} onChangeText={(v) => setForm((f) => ({ ...f, lastName: v }))}
                theme={theme} />
              <FormInput label={t('userManagement.password', 'Şifre (8+ karakter, 1 büyük harf, 1 rakam)')}
                value={form.password} onChangeText={(v) => setForm((f) => ({ ...f, password: v }))}
                theme={theme} secureTextEntry />
              <View style={{ marginBottom: 10 }}>
                <PickerField
                  label={t('userManagement.role', 'Rol')}
                  placeholder={t('userManagement.selectRole', 'Rol seç')}
                  value={form.role}
                  onChange={(v) => setForm((f) => ({ ...f, role: v }))}
                  options={roleOptions}
                />
              </View>
            </ScrollView>
            <SheetButtons
              theme={theme}
              t={t}
              busy={saving}
              onCancel={() => setCreateOpen(false)}
              onConfirm={submitCreate}
            />
          </View>
        </View>
      </Modal>

      {/* Rol düzenleme modalı */}
      <Modal visible={!!editUser} transparent animationType="slide" onRequestClose={() => setEditUser(null)}>
        <View style={[styles.backdrop, { backgroundColor: theme.overlay }]}>
          <View style={[styles.sheet, { backgroundColor: theme.bgSurface }]}>
            <Text style={[styles.sheetTitle, { color: theme.textPrimary }]}>
              {t('userManagement.editRole', 'Rol Değiştir')}
            </Text>
            <PickerField
              label={t('userManagement.role', 'Rol')}
              placeholder={t('userManagement.selectRole', 'Rol seç')}
              value={editRole}
              onChange={setEditRole}
              options={roleOptions}
            />
            <SheetButtons
              theme={theme}
              t={t}
              busy={saving}
              onCancel={() => setEditUser(null)}
              onConfirm={submitRole}
            />
          </View>
        </View>
      </Modal>
    </View>
  );
}

function FormInput({ label, theme, ...props }) {
  return (
    <View style={{ marginBottom: 10, gap: 5 }}>
      <Text style={{ fontSize: 13, fontWeight: '600', color: theme.textSecondary }}>{label}</Text>
      <TextInput
        placeholderTextColor={theme.textTertiary}
        style={[styles.input, { backgroundColor: theme.bgInput, borderColor: theme.border, color: theme.textPrimary }]}
        {...props}
      />
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
  toolbar: { flexDirection: 'row', gap: 8, padding: 12 },
  searchInput: { flex: 1, borderWidth: 1, borderRadius: 10, paddingHorizontal: 12, height: 42, fontSize: 14 },
  newBtn: { paddingHorizontal: 14, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
  list: { paddingHorizontal: 12, paddingBottom: 16, gap: 10, flexGrow: 1 },
  card: { borderRadius: 12, borderWidth: 1, padding: 14, gap: 8 },
  cardTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  name: { fontSize: 15, fontWeight: '700', flex: 1 },
  email: { fontSize: 13 },
  cardBottom: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  roleBadge: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 999 },
  roleText: { color: '#fff', fontSize: 11, fontWeight: '700' },
  backdrop: { flex: 1, justifyContent: 'flex-end' },
  sheet: { borderTopLeftRadius: 20, borderTopRightRadius: 20, padding: 20, gap: 12 },
  sheetTitle: { fontSize: 17, fontWeight: '700' },
  input: { borderWidth: 1, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 10, fontSize: 14 },
  sheetActions: { flexDirection: 'row', gap: 10 },
  sheetBtn: { flex: 1, height: 46, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
});
