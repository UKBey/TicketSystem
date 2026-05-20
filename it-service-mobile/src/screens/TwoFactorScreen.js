import { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  ScrollView,
  Pressable,
  StyleSheet,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import { getTotpDevices, deleteTotpDevice, notifyTotpAdded } from '../api/users';
import { configureTotp } from '../auth/oidc';

/** Epoch milisaniyeyi okunabilir tarihe çevirir. */
function fmtEpoch(ms) {
  if (!ms) return '—';
  const d = new Date(Number(ms));
  if (Number.isNaN(d.getTime())) return '—';
  const p = (n) => String(n).padStart(2, '0');
  return `${p(d.getDate())}.${p(d.getMonth() + 1)}.${d.getFullYear()} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

/** İki adımlı doğrulama — authenticator (TOTP) cihazlarını listele, ekle, sil. */
export default function TwoFactorScreen() {
  const { theme } = useTheme();
  const { t } = useTranslation();

  const [devices, setDevices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [adding, setAdding] = useState(false);
  const [deletingId, setDeletingId] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getTotpDevices();
      setDevices(Array.isArray(res.data) ? res.data : []);
    } catch {
      setDevices([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const addDevice = async () => {
    setAdding(true);
    try {
      const beforeIds = devices.map((d) => d.id);
      const ok = await configureTotp();
      if (ok) {
        const res = await getTotpDevices();
        const list = Array.isArray(res.data) ? res.data : [];
        setDevices(list);
        if (list.some((d) => !beforeIds.includes(d.id))) {
          notifyTotpAdded().catch(() => {});
        }
      }
    } catch (e) {
      Alert.alert(t('profile.twoFactorModal.loadError', 'İşlem tamamlanamadı.'));
    } finally {
      setAdding(false);
    }
  };

  const remove = (device) => {
    Alert.alert(
      t('profile.twoFactorModal.confirmDelete', 'Sil?'),
      t('profile.twoFactorModal.confirmDeleteDesc', 'Bu cihaz authenticator olarak kullanılamayacak.'),
      [
        { text: t('profile.twoFactorModal.deleteCancel', 'Vazgeç'), style: 'cancel' },
        {
          text: t('profile.twoFactorModal.deleteConfirm', 'Evet, sil'),
          style: 'destructive',
          onPress: async () => {
            setDeletingId(device.id);
            try {
              await deleteTotpDevice(device.id);
              setDevices((d) => d.filter((x) => x.id !== device.id));
            } catch {
              Alert.alert(t('profile.twoFactorModal.deleteError', 'Cihaz silinemedi.'));
            } finally {
              setDeletingId(null);
            }
          },
        },
      ],
    );
  };

  return (
    <View style={[styles.container, { backgroundColor: theme.bgBody }]}>
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={[styles.subtitle, { color: theme.textSecondary }]}>
          {t('profile.twoFactorModal.subtitle', 'Authenticator cihazlarınızı yönetin.')}
        </Text>

        {loading ? (
          <ActivityIndicator size="large" color={theme.primary} style={{ marginTop: 32 }} />
        ) : devices.length === 0 ? (
          <View style={[styles.empty, { borderColor: theme.border }]}>
            <Ionicons name="phone-portrait-outline" size={36} color={theme.textTertiary} />
            <Text style={[styles.emptyTitle, { color: theme.textPrimary }]}>
              {t('profile.twoFactorModal.noDevices', 'Henüz authenticator cihazı eklenmemiş.')}
            </Text>
            <Text style={[styles.emptyHint, { color: theme.textTertiary }]}>
              {t(
                'profile.twoFactorModal.noDevicesHint',
                'Hesabınızı ekstra güvenlik katmanıyla korumak için bir cihaz ekleyin.',
              )}
            </Text>
          </View>
        ) : (
          devices.map((d) => (
            <View
              key={d.id}
              style={[styles.deviceRow, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}
            >
              <View style={[styles.deviceIcon, { backgroundColor: `${theme.success}22` }]}>
                <Ionicons name="phone-portrait-outline" size={18} color={theme.success} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={[styles.deviceLabel, { color: theme.textPrimary }]}>
                  {d.userLabel || t('profile.twoFactorModal.deviceWithoutLabel', 'İsimsiz cihaz')}
                </Text>
                <Text style={[styles.deviceDate, { color: theme.textTertiary }]}>
                  {t('profile.twoFactorModal.createdOn', 'Eklendi: {{date}}', {
                    date: fmtEpoch(d.createdDate),
                  })}
                </Text>
              </View>
              {deletingId === d.id ? (
                <ActivityIndicator size="small" color={theme.danger} />
              ) : (
                <Pressable onPress={() => remove(d)} hitSlop={6}>
                  <Ionicons name="trash-outline" size={20} color={theme.danger} />
                </Pressable>
              )}
            </View>
          ))
        )}
      </ScrollView>

      <View style={[styles.footer, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
        <Pressable
          onPress={addDevice}
          disabled={adding}
          style={({ pressed }) => [
            styles.addBtn,
            { backgroundColor: theme.success, opacity: adding || pressed ? 0.6 : 1 },
          ]}
        >
          {adding ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <>
              <Ionicons name="add" size={18} color="#fff" />
              <Text style={styles.addBtnText}>
                {t('profile.twoFactorModal.addDevice', 'Yeni Cihaz Ekle')}
              </Text>
            </>
          )}
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 16, gap: 12 },
  subtitle: { fontSize: 13, lineHeight: 18 },
  empty: {
    borderWidth: 1,
    borderStyle: 'dashed',
    borderRadius: 12,
    padding: 28,
    alignItems: 'center',
    gap: 8,
    marginTop: 8,
  },
  emptyTitle: { fontSize: 14, fontWeight: '700', textAlign: 'center' },
  emptyHint: { fontSize: 12, textAlign: 'center', lineHeight: 17 },
  deviceRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    borderWidth: 1,
    borderRadius: 12,
    padding: 12,
  },
  deviceIcon: { width: 36, height: 36, borderRadius: 9, alignItems: 'center', justifyContent: 'center' },
  deviceLabel: { fontSize: 14, fontWeight: '600' },
  deviceDate: { fontSize: 11, marginTop: 2 },
  footer: { borderTopWidth: 1, padding: 12 },
  addBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    height: 48,
    borderRadius: 12,
  },
  addBtnText: { color: '#fff', fontSize: 15, fontWeight: '700' },
});
