import { useState, useEffect } from 'react';
import {
  View,
  Text,
  Switch,
  Pressable,
  ScrollView,
  StyleSheet,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import { getPreferences, updatePreferences } from '../api/notifications';

/** Bildirim olayları — her biri emailOn<Key> ve notifyOn<Key> tercihi taşır. */
const EVENTS = [
  { key: 'TicketCreated', icon: 'document-text-outline', color: '#3b82f6', label: 'Bilet oluşturuldu' },
  { key: 'TicketAssigned', icon: 'person-add-outline', color: '#8b5cf6', label: 'Bilet bana atandı' },
  { key: 'StatusChanged', icon: 'sync-outline', color: '#0ea5e9', label: 'Bilet durumu değişti' },
  { key: 'CommentAdded', icon: 'chatbubble-outline', color: '#22c55e', label: 'Yorum eklendi' },
  { key: 'SlaWarning', icon: 'warning-outline', color: '#f59e0b', label: 'SLA uyarısı' },
  { key: 'SlaBreached', icon: 'alert-circle-outline', color: '#ef4444', label: 'SLA ihlali' },
  { key: 'TicketResolved', icon: 'checkmark-circle-outline', color: '#10b981', label: 'Bilet çözüldü' },
];

/** Bildirim tercihleri — olay bazlı e-posta / uygulama içi bildirim ayarları. */
export default function NotificationPreferencesScreen() {
  const { theme } = useTheme();
  const { t } = useTranslation();

  const [prefs, setPrefs] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    getPreferences()
      .then((res) => setPrefs(res.data ?? {}))
      .catch(() => setPrefs({}))
      .finally(() => setLoading(false));
  }, []);

  const toggle = (key, value) => setPrefs((p) => ({ ...p, [key]: value }));

  const save = async () => {
    setSaving(true);
    try {
      const res = await updatePreferences(prefs);
      setPrefs(res.data ?? prefs);
      Alert.alert(t('notificationPrefs.saved', 'Tercihler kaydedildi.'));
    } catch (e) {
      Alert.alert(
        e?.response?.data?.message ||
          t('notificationPrefs.errorSave', 'Kaydedilemedi. Lütfen tekrar deneyin.'),
      );
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <View style={[styles.full, { backgroundColor: theme.bgBody }]}>
        <ActivityIndicator size="large" color={theme.primary} />
      </View>
    );
  }

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: theme.bgBody }}
      contentContainerStyle={styles.content}
    >
      <Text style={[styles.subtitle, { color: theme.textSecondary }]}>
        {t(
          'notificationPrefs.subtitle',
          'Hangi olaylar için e-posta ve uygulama içi bildirim almak istediğinizi seçin.',
        )}
      </Text>

      <View style={[styles.card, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
        <View style={[styles.headerRow, { borderBottomColor: theme.border }]}>
          <Text style={[styles.colEvent, { color: theme.textTertiary }]}>
            {t('notificationPrefs.colEvent', 'Olay')}
          </Text>
          <Text style={[styles.colHead, { color: theme.textTertiary }]}>
            {t('notificationPrefs.colEmail', 'E-posta')}
          </Text>
          <Text style={[styles.colHead, { color: theme.textTertiary }]}>
            {t('notificationPrefs.colInApp', 'Uygulama')}
          </Text>
        </View>

        {EVENTS.map((ev, i) => {
          const emailKey = `emailOn${ev.key}`;
          const notifyKey = `notifyOn${ev.key}`;
          return (
            <View
              key={ev.key}
              style={[
                styles.eventRow,
                i > 0 && { borderTopColor: theme.border, borderTopWidth: StyleSheet.hairlineWidth },
              ]}
            >
              <View style={styles.eventLeft}>
                <View style={[styles.iconWrap, { backgroundColor: `${ev.color}22` }]}>
                  <Ionicons name={ev.icon} size={16} color={ev.color} />
                </View>
                <Text style={[styles.eventLabel, { color: theme.textPrimary }]}>
                  {t(`notificationPrefs.event.${ev.key}`, ev.label)}
                </Text>
              </View>
              <View style={styles.colSwitch}>
                <Switch
                  value={prefs?.[emailKey] ?? true}
                  onValueChange={(v) => toggle(emailKey, v)}
                  trackColor={{ true: theme.primary, false: theme.border }}
                />
              </View>
              <View style={styles.colSwitch}>
                <Switch
                  value={prefs?.[notifyKey] ?? true}
                  onValueChange={(v) => toggle(notifyKey, v)}
                  trackColor={{ true: theme.primary, false: theme.border }}
                />
              </View>
            </View>
          );
        })}
      </View>

      <Pressable
        onPress={save}
        disabled={saving}
        style={({ pressed }) => [
          styles.btn,
          { backgroundColor: theme.primary, opacity: saving || pressed ? 0.6 : 1 },
        ]}
      >
        {saving ? (
          <ActivityIndicator color={theme.onPrimary} />
        ) : (
          <Text style={styles.btnText}>{t('notificationPrefs.save', 'Kaydet')}</Text>
        )}
      </Pressable>

      <Text style={[styles.note, { color: theme.textTertiary }]}>
        {t(
          'notificationPrefs.retentionNote',
          'Okunmuş bildirimler 48 saat, okunmamışlar 10 gün sonra otomatik olarak silinir.',
        )}
      </Text>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  full: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  content: { padding: 16, gap: 14 },
  subtitle: { fontSize: 13, lineHeight: 18 },
  card: { borderRadius: 12, borderWidth: 1, paddingHorizontal: 14 },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  colEvent: { flex: 1, fontSize: 11, fontWeight: '700', textTransform: 'uppercase' },
  colHead: { width: 62, textAlign: 'center', fontSize: 10, fontWeight: '700', textTransform: 'uppercase' },
  eventRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 12 },
  eventLeft: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: 10 },
  iconWrap: { width: 32, height: 32, borderRadius: 8, alignItems: 'center', justifyContent: 'center' },
  eventLabel: { fontSize: 13, flexShrink: 1 },
  colSwitch: { width: 62, alignItems: 'center' },
  btn: { height: 48, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
  btnText: { color: '#fff', fontSize: 15, fontWeight: '700' },
  note: { fontSize: 11, lineHeight: 16 },
});
