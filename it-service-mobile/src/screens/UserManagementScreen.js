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
import { Ionicons } from '@expo/vector-icons';
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
import SheetBackdrop from '../components/SheetBackdrop';
import RoleFilterChips from '../components/RoleFilterChips';

const EMPTY_FORM = { username: '', email: '', firstName: '', lastName: '', password: '', role: null };
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Kullanıcı yönetimi — liste, arama, rol filtresi, durum değiştirme, rol düzenleme, yeni kullanıcı. */
export default function UserManagementScreen() {
  const { theme } = useTheme();
  const { t } = useTranslation();

  const [users, setUsers] = useState([]);
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState(null);
  const [loading, setLoading] = useState(true);
  const [roles, setRoles] = useState([]);

  const [createOpen, setCreateOpen] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [showPw, setShowPw] = useState(false);
  const [saving, setSaving] = useState(false);

  const [editUser, setEditUser] = useState(null);
  const [editRole, setEditRole] = useState(null);

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
    } catch (e) {
      setUsers([]);
    } finally {
      setLoading(false);
    }
  }, [search, roleFilter]);

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
    const username = form.username.trim();
    const email = form.email.trim();
    const firstName = form.firstName.trim();
    const lastName = form.lastName.trim();
    const { password, role } = form;

    if (!username)
      return Alert.alert(t('userManagement.validation.usernameRequired', 'Kullanıcı adı zorunludur.'));
    if (username.length < 3)
      return Alert.alert(t('userManagement.validation.usernameMinLength', 'En az 3 karakter olmalıdır.'));
    if (!email)
      return Alert.alert(t('userManagement.validation.emailRequired', 'E-posta zorunludur.'));
    if (!EMAIL_RE.test(email))
      return Alert.alert(t('userManagement.validation.emailInvalid', 'Geçerli bir e-posta adresi girin.'));
    if (!firstName)
      return Alert.alert(t('userManagement.validation.firstNameRequired', 'Ad zorunludur.'));
    if (!lastName)
      return Alert.alert(t('userManagement.validation.lastNameRequired', 'Soyad zorunludur.'));
    if (!password)
      return Alert.alert(t('userManagement.validation.passwordRequired', 'Şifre zorunludur.'));
    if (password.length < 8 || !/[A-Z]/.test(password) || !/[0-9]/.test(password)) {
      return Alert.alert(
        t('userManagement.validation.passwordWeak', 'En az 8 karakter, 1 büyük harf ve 1 rakam içermelidir.'),
      );
    }
    if (!role)
      return Alert.alert(t('userManagement.validation.rolesRequired', 'En az bir rol seçilmelidir.'));

    setSaving(true);
    try {
      await createUser({ username, email, firstName, lastName, password, roles: [role] });
      setCreateOpen(false);
      setForm(EMPTY_FORM);
      setShowPw(false);
      await load();
      Alert.alert(t('userManagement.success', 'Kullanıcı başarıyla oluşturuldu.'));
    } catch (e) {
      const status = e?.response?.status;
      const msg = e?.response?.data?.message;
      if (status === 409) {
        Alert.alert(
          msg || t('userManagement.validation.emailConflict', 'Bu e-posta veya kullanıcı adı zaten kullanımda.'),
        );
      } else {
        Alert.alert(
          msg || t('userManagement.form.errorGeneral', 'Kullanıcı oluşturulamadı. Lütfen tekrar deneyin.'),
        );
      }
    } finally {
      setSaving(false);
    }
  };

  const submitRole = async () => {
    if (!editRole) {
      Alert.alert(t('userManagement.validation.rolesRequired', 'En az bir rol seçilmelidir.'));
      return;
    }
    setSaving(true);
    try {
      const res = await updateUserRoles(editUser.id, [editRole]);
      setUsers((prev) =>
        prev.map((u) => (u.id === editUser.id ? { ...u, role: res.data?.role ?? editRole } : u)),
      );
      setEditUser(null);
      Alert.alert(t('userManagement.editRole.successMsg', 'Kullanıcı rolleri başarıyla güncellendi.'));
    } catch (e) {
      Alert.alert(
        e?.response?.data?.message ||
          t('userManagement.editRole.errorGeneral', 'Roller güncellenemedi. Lütfen tekrar deneyin.'),
      );
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
      Alert.alert(
        e?.response?.data?.message ||
          t('userManagement.status.error', 'Kullanıcı durumu güncellenemedi. Lütfen tekrar deneyin.'),
      );
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
          <Text style={styles.roleText}>{item.role || t('userManagement.table.noRole', 'Rol Yok')}</Text>
        </View>
        <Pressable
          onPress={() => {
            setEditUser(item);
            setEditRole(item.role || null);
          }}
          style={[styles.editBtn, { borderColor: theme.primary }]}
        >
          <Ionicons name="create-outline" size={14} color={theme.primary} />
          <Text style={{ color: theme.primary, fontWeight: '700', fontSize: 12 }}>
            {t('userManagement.editRole.button', 'Rol Düzenle')}
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
          placeholder={t('userManagement.table.searchPlaceholder', 'Ad veya e-posta ara…')}
          placeholderTextColor={theme.textTertiary}
          autoCapitalize="none"
          style={[
            styles.searchInput,
            { backgroundColor: theme.bgInput, borderColor: theme.border, color: theme.textPrimary },
          ]}
        />
        <Pressable
          onPress={() => {
            setForm(EMPTY_FORM);
            setShowPw(false);
            setCreateOpen(true);
          }}
          style={[styles.newBtn, { backgroundColor: theme.primary }]}
        >
          <Text style={{ color: theme.onPrimary, fontWeight: '700' }}>
            + {t('userManagement.new', 'Yeni')}
          </Text>
        </Pressable>
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
              {t('userManagement.table.noUsers', 'Kullanıcı bulunamadı.')}
            </Text>
          }
        />
      )}

      {/* Yeni kullanıcı modalı */}
      <Modal visible={createOpen} transparent animationType="slide" onRequestClose={() => setCreateOpen(false)}>
        <SheetBackdrop>
          <View style={[styles.sheet, { backgroundColor: theme.bgSurface }]}>
            <Text style={[styles.sheetTitle, { color: theme.textPrimary }]}>
              {t('userManagement.form.title', 'Yeni Kullanıcı Oluştur')}
            </Text>
            <ScrollView style={{ maxHeight: 420 }} keyboardShouldPersistTaps="handled">
              <FormInput
                label={t('userManagement.form.username', 'Kullanıcı Adı')}
                value={form.username}
                onChangeText={(v) => setForm((f) => ({ ...f, username: v }))}
                theme={theme}
                autoCapitalize="none"
              />
              <FormInput
                label={t('userManagement.form.email', 'E-posta')}
                value={form.email}
                onChangeText={(v) => setForm((f) => ({ ...f, email: v }))}
                theme={theme}
                autoCapitalize="none"
                keyboardType="email-address"
              />
              <FormInput
                label={t('userManagement.form.firstName', 'Ad')}
                value={form.firstName}
                onChangeText={(v) => setForm((f) => ({ ...f, firstName: v }))}
                theme={theme}
              />
              <FormInput
                label={t('userManagement.form.lastName', 'Soyad')}
                value={form.lastName}
                onChangeText={(v) => setForm((f) => ({ ...f, lastName: v }))}
                theme={theme}
              />

              <View style={styles.group}>
                <Text style={[styles.fieldLabel, { color: theme.textSecondary }]}>
                  {t('userManagement.form.password', 'Şifre')}
                </Text>
                <View style={styles.pwWrap}>
                  <TextInput
                    value={form.password}
                    onChangeText={(v) => setForm((f) => ({ ...f, password: v }))}
                    secureTextEntry={!showPw}
                    autoCapitalize="none"
                    placeholderTextColor={theme.textTertiary}
                    style={[
                      styles.input,
                      styles.pwInput,
                      { backgroundColor: theme.bgInput, borderColor: theme.border, color: theme.textPrimary },
                    ]}
                  />
                  <Pressable onPress={() => setShowPw((s) => !s)} hitSlop={8} style={styles.pwEye}>
                    <Ionicons
                      name={showPw ? 'eye-off-outline' : 'eye-outline'}
                      size={20}
                      color={theme.textTertiary}
                    />
                  </Pressable>
                </View>
                <Text style={[styles.hint, { color: theme.textTertiary }]}>
                  {t('userManagement.form.passwordHint', 'En az 8 karakter, 1 büyük harf ve 1 rakam içermelidir.')}
                </Text>
              </View>

              <View style={{ marginBottom: 10 }}>
                <PickerField
                  label={t('userManagement.form.roles', 'Roller')}
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
        </SheetBackdrop>
      </Modal>

      {/* Rol düzenleme modalı */}
      <Modal visible={!!editUser} transparent animationType="slide" onRequestClose={() => setEditUser(null)}>
        <SheetBackdrop>
          <View style={[styles.sheet, { backgroundColor: theme.bgSurface }]}>
            <Text style={[styles.sheetTitle, { color: theme.textPrimary }]}>
              {t('userManagement.editRole.title', 'Rol Düzenle')}
            </Text>
            <PickerField
              label={t('userManagement.form.roles', 'Roller')}
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
        </SheetBackdrop>
      </Modal>
    </View>
  );
}

function FormInput({ label, theme, ...props }) {
  return (
    <View style={styles.group}>
      <Text style={[styles.fieldLabel, { color: theme.textSecondary }]}>{label}</Text>
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
  toolbar: { flexDirection: 'row', gap: 8, padding: 12, paddingBottom: 8 },
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
  editBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  sheet: { borderTopLeftRadius: 20, borderTopRightRadius: 20, padding: 20, gap: 12 },
  sheetTitle: { fontSize: 17, fontWeight: '700' },
  group: { marginBottom: 10, gap: 5 },
  fieldLabel: { fontSize: 13, fontWeight: '600' },
  input: { borderWidth: 1, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 10, fontSize: 14 },
  pwWrap: { position: 'relative', justifyContent: 'center' },
  pwInput: { paddingRight: 44 },
  pwEye: { position: 'absolute', right: 10, padding: 2 },
  hint: { fontSize: 11 },
  sheetActions: { flexDirection: 'row', gap: 10 },
  sheetBtn: { flex: 1, height: 46, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
});
