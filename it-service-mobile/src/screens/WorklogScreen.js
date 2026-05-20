import { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  TextInput,
  Pressable,
  ScrollView,
  StyleSheet,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Alert,
} from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import { getWorklogs, addWorklog, deleteWorklog } from '../api/tickets';
import { formatMinutes, formatDate } from '../utils/format';

/** Bilet süre kayıtları (worklog) — liste, ekleme, silme. Bilet detayından açılır. */
export default function WorklogScreen({ route }) {
  const { id } = route.params;
  const { theme } = useTheme();
  const { t } = useTranslation();

  const [worklogs, setWorklogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [minutes, setMinutes] = useState('');
  const [description, setDescription] = useState('');
  const [adding, setAdding] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getWorklogs(id);
      setWorklogs(res.data ?? []);
    } catch (e) {
      setWorklogs([]);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  // Ekran açıkken süre kayıtlarını 3 sn'de bir sessizce tazeler — başka bir
  // kullanıcı worklog ekler/silerse yenileme yapmadan ekrana yansır.
  useFocusEffect(
    useCallback(() => {
      const interval = setInterval(() => {
        getWorklogs(id)
          .then((res) => setWorklogs(res.data ?? []))
          .catch(() => {});
      }, 3000);
      return () => clearInterval(interval);
    }, [id]),
  );

  const add = async () => {
    const mins = parseInt(minutes, 10);
    if (!mins || mins <= 0) {
      Alert.alert(t('worklog.invalidMinutes', 'Geçerli bir dakika girin.'));
      return;
    }
    setAdding(true);
    try {
      const res = await addWorklog(id, {
        minutes: mins,
        description: description.trim() || null,
      });
      setWorklogs((prev) => [...prev, res.data]);
      setMinutes('');
      setDescription('');
    } catch (e) {
      Alert.alert(e?.response?.data?.message || t('worklog.addFailed', 'Eklenemedi.'));
    } finally {
      setAdding(false);
    }
  };

  const remove = (w) => {
    Alert.alert(t('worklog.deleteTitle', 'Kaydı sil'), formatMinutes(w.minutes), [
      { text: t('common.cancel', 'İptal'), style: 'cancel' },
      {
        text: t('common.delete', 'Sil'),
        style: 'destructive',
        onPress: () => {
          setWorklogs((prev) => prev.filter((x) => x.id !== w.id));
          deleteWorklog(id, w.id).catch(() => load());
        },
      },
    ]);
  };

  const total = worklogs.reduce((s, w) => s + (w.minutes || 0), 0);

  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: theme.bgBody }}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView
        contentContainerStyle={styles.content}
        keyboardShouldPersistTaps="handled"
        keyboardDismissMode="on-drag"
      >
        <View style={[styles.addCard, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
          <Text style={[styles.cardTitle, { color: theme.textPrimary }]}>
            {t('worklog.add', 'Süre Kaydı Ekle')}
          </Text>
          <TextInput
            value={minutes}
            onChangeText={setMinutes}
            placeholder={t('worklog.minutes', 'Dakika')}
            placeholderTextColor={theme.textTertiary}
            keyboardType="number-pad"
            style={[styles.input, { backgroundColor: theme.bgInput, borderColor: theme.border, color: theme.textPrimary }]}
          />
          <TextInput
            value={description}
            onChangeText={setDescription}
            placeholder={t('worklog.description', 'Açıklama (opsiyonel)')}
            placeholderTextColor={theme.textTertiary}
            multiline
            style={[
              styles.input,
              { minHeight: 64, textAlignVertical: 'top', backgroundColor: theme.bgInput, borderColor: theme.border, color: theme.textPrimary },
            ]}
          />
          <Pressable
            onPress={add}
            disabled={adding}
            style={({ pressed }) => [
              styles.btn,
              { backgroundColor: theme.primary, opacity: adding || pressed ? 0.6 : 1 },
            ]}
          >
            {adding ? (
              <ActivityIndicator color={theme.onPrimary} size="small" />
            ) : (
              <Text style={styles.btnText}>{t('common.add', 'Ekle')}</Text>
            )}
          </Pressable>
        </View>

        <Text style={[styles.total, { color: theme.textSecondary }]}>
          {t('worklog.total', 'Toplam')}: {formatMinutes(total)}
        </Text>

        {loading ? (
          <ActivityIndicator size="large" color={theme.primary} style={{ marginTop: 24 }} />
        ) : worklogs.length === 0 ? (
          <Text style={{ color: theme.textTertiary, textAlign: 'center', marginTop: 16 }}>
            {t('worklog.empty', 'Kayıt yok.')}
          </Text>
        ) : (
          worklogs.map((w) => (
            <Pressable
              key={w.id}
              onLongPress={() => remove(w)}
              style={[styles.item, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}
            >
              <View style={styles.itemTop}>
                <Text style={[styles.itemMins, { color: theme.primary }]}>
                  {formatMinutes(w.minutes)}
                </Text>
                <Text style={[styles.itemDate, { color: theme.textTertiary }]}>
                  {formatDate(w.createdAt)}
                </Text>
              </View>
              {!!w.description && (
                <Text style={[styles.itemDesc, { color: theme.textSecondary }]}>{w.description}</Text>
              )}
            </Pressable>
          ))
        )}
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  content: { padding: 16, gap: 12 },
  addCard: { borderRadius: 12, borderWidth: 1, padding: 16, gap: 10 },
  cardTitle: { fontSize: 15, fontWeight: '700' },
  input: { borderWidth: 1, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 11, fontSize: 14 },
  btn: { height: 44, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
  btnText: { color: '#fff', fontSize: 14, fontWeight: '700' },
  total: { fontSize: 13, fontWeight: '600', textAlign: 'right' },
  item: { borderRadius: 10, borderWidth: 1, padding: 13, gap: 5 },
  itemTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  itemMins: { fontSize: 15, fontWeight: '700' },
  itemDate: { fontSize: 11 },
  itemDesc: { fontSize: 13, lineHeight: 18 },
});
