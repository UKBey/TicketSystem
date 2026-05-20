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
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import { getPreferences, updatePreferences } from '../api/notifications';

/** camelCase anahtarı okunabilir etikete çevirir. */
function humanize(key) {
  return key
    .replace(/([A-Z])/g, ' $1')
    .replace(/^./, (c) => c.toUpperCase())
    .trim();
}

/**
 * Bildirim tercihleri — backend düz boolean nesnesi döner; her boolean alan
 * için bir toggle gösterilir (anahtar adları sabit kodlanmaz).
 */
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

  const save = async () => {
    setSaving(true);
    try {
      const res = await updatePreferences(prefs);
      setPrefs(res.data ?? prefs);
      Alert.alert(t('notificationPrefs.saved', 'Tercihler kaydedildi.'));
    } catch (e) {
      Alert.alert(t('notificationPrefs.saveFailed', 'Kaydedilemedi.'));
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

  const toggleKeys = Object.keys(prefs || {}).filter((k) => typeof prefs[k] === 'boolean');

  return (
    <View style={[styles.container, { backgroundColor: theme.bgBody }]}>
      <ScrollView contentContainerStyle={styles.content}>
        <View style={[styles.card, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
          {toggleKeys.length === 0 ? (
            <Text style={{ color: theme.textTertiary, textAlign: 'center' }}>
              {t('notificationPrefs.empty', 'Tercih bulunamadı.')}
            </Text>
          ) : (
            toggleKeys.map((key, i) => (
              <View
                key={key}
                style={[
                  styles.row,
                  i < toggleKeys.length - 1 && { borderBottomColor: theme.border, borderBottomWidth: StyleSheet.hairlineWidth },
                ]}
              >
                <Text style={[styles.rowLabel, { color: theme.textPrimary }]}>{humanize(key)}</Text>
                <Switch
                  value={prefs[key]}
                  onValueChange={(v) => setPrefs((p) => ({ ...p, [key]: v }))}
                  trackColor={{ true: theme.primary, false: theme.border }}
                />
              </View>
            ))
          )}
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
            <Text style={styles.btnText}>{t('common.save', 'Kaydet')}</Text>
          )}
        </Pressable>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  full: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  container: { flex: 1 },
  content: { padding: 16, gap: 16 },
  card: { borderRadius: 12, borderWidth: 1, paddingHorizontal: 16 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 14,
    gap: 12,
  },
  rowLabel: { fontSize: 14, flex: 1 },
  btn: { height: 48, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
  btnText: { color: '#fff', fontSize: 15, fontWeight: '700' },
});
