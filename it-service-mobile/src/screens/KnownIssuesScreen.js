import { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  ScrollView,
  Pressable,
  TextInput,
  Modal,
  StyleSheet,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import { useAuth } from '../auth/AuthContext';
import {
  getProducts,
  getProductTopics,
  getKnownIssues,
  createKnownIssue,
  deleteKnownIssue,
} from '../api/products';
import PickerField from '../components/PickerField';
import SheetBackdrop from '../components/SheetBackdrop';

/** Bilinen sorunlar bilgi tabanı — ürün seç, akordeon liste; admin ekler/siler. */
export default function KnownIssuesScreen() {
  const { theme } = useTheme();
  const { t } = useTranslation();
  const { isLeadAgent, isAdmin } = useAuth();
  // Bilinen sorunları yönetme (ürün içeriği) — lead agent veya admin (web ile aynı).
  const canManage = isLeadAgent || isAdmin;

  const [products, setProducts] = useState([]);
  const [productId, setProductId] = useState(null);
  const [issues, setIssues] = useState([]);
  const [loading, setLoading] = useState(false);
  const [expanded, setExpanded] = useState(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [topics, setTopics] = useState([]);
  const [form, setForm] = useState({ title: '', content: '', topicId: null });
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    getProducts()
      .then((res) => setProducts((res.data ?? []).filter((p) => p.isActive)))
      .catch(() => {});
  }, []);

  const loadIssues = useCallback(() => {
    if (!productId) {
      setIssues([]);
      return;
    }
    setLoading(true);
    getKnownIssues(productId)
      .then((res) => setIssues(res.data ?? []))
      .catch(() => setIssues([]))
      .finally(() => setLoading(false));
  }, [productId]);

  useEffect(() => {
    loadIssues();
    setExpanded(null);
  }, [loadIssues]);

  const openCreate = () => {
    setForm({ title: '', content: '', topicId: null });
    setTopics([]);
    getProductTopics(productId)
      .then((res) => setTopics(res.data ?? []))
      .catch(() => setTopics([]));
    setCreateOpen(true);
  };

  const submitCreate = async () => {
    if (!form.title.trim() || !form.content.trim()) return;
    setSaving(true);
    try {
      await createKnownIssue(productId, {
        title: form.title.trim(),
        content: form.content.trim(),
        topicId: form.topicId,
        isActive: true,
      });
      setCreateOpen(false);
      loadIssues();
    } catch (e) {
      Alert.alert(e?.response?.data?.message || t('knownIssues.createFailed', 'Oluşturulamadı.'));
    } finally {
      setSaving(false);
    }
  };

  const onDelete = (ki) => {
    Alert.alert(t('knownIssues.deleteTitle', 'Kaydı sil'), ki.title, [
      { text: t('common.cancel', 'İptal'), style: 'cancel' },
      {
        text: t('common.delete', 'Sil'),
        style: 'destructive',
        onPress: () => {
          setIssues((prev) => prev.filter((x) => x.id !== ki.id));
          deleteKnownIssue(ki.id).catch(() => loadIssues());
        },
      },
    ]);
  };

  return (
    <View style={[styles.container, { backgroundColor: theme.bgBody }]}>
      <ScrollView contentContainerStyle={styles.content}>
        <PickerField
          label={t('knownIssues.product', 'Ürün')}
          placeholder={t('knownIssues.selectProduct', 'Ürün seç')}
          value={productId}
          onChange={setProductId}
          options={products.map((p) => ({ label: p.name, value: p.id }))}
        />

        {canManage && productId && (
          <Pressable
            onPress={openCreate}
            style={({ pressed }) => [
              styles.addBtn,
              { borderColor: theme.primary, opacity: pressed ? 0.6 : 1 },
            ]}
          >
            <Text style={{ color: theme.primary, fontWeight: '700' }}>
              + {t('knownIssues.add', 'Yeni Sorun Ekle')}
            </Text>
          </Pressable>
        )}

        {loading ? (
          <ActivityIndicator style={{ marginTop: 32 }} size="large" color={theme.primary} />
        ) : !productId ? (
          <Text style={[styles.hint, { color: theme.textTertiary }]}>
            {t('knownIssues.pickFirst', 'Görüntülemek için bir ürün seçin.')}
          </Text>
        ) : issues.length === 0 ? (
          <Text style={[styles.hint, { color: theme.textTertiary }]}>
            {t('knownIssues.empty', 'Bu ürün için kayıt yok.')}
          </Text>
        ) : (
          issues.map((ki) => {
            const open = expanded === ki.id;
            return (
              <Pressable
                key={ki.id}
                onPress={() => setExpanded(open ? null : ki.id)}
                onLongPress={canManage ? () => onDelete(ki) : undefined}
                style={[styles.item, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}
              >
                <Text style={[styles.itemTitle, { color: theme.textPrimary }]}>
                  {open ? '▾ ' : '▸ '}
                  {ki.title}
                </Text>
                {open && !!ki.content && (
                  <Text style={[styles.itemContent, { color: theme.textSecondary }]}>{ki.content}</Text>
                )}
              </Pressable>
            );
          })
        )}
      </ScrollView>

      <Modal visible={createOpen} transparent animationType="slide" onRequestClose={() => setCreateOpen(false)}>
        <SheetBackdrop onClose={() => setCreateOpen(false)}>
          <View style={[styles.sheet, { backgroundColor: theme.bgSurface }]}>
            <Text style={[styles.sheetTitle, { color: theme.textPrimary }]}>
              {t('knownIssues.add', 'Yeni Sorun Ekle')}
            </Text>
            <TextInput
              value={form.title}
              onChangeText={(v) => setForm((f) => ({ ...f, title: v }))}
              placeholder={t('knownIssues.titlePlaceholder', 'Başlık')}
              placeholderTextColor={theme.textTertiary}
              style={[styles.input, { backgroundColor: theme.bgInput, borderColor: theme.border, color: theme.textPrimary }]}
            />
            <TextInput
              value={form.content}
              onChangeText={(v) => setForm((f) => ({ ...f, content: v }))}
              placeholder={t('knownIssues.contentPlaceholder', 'Çözüm / açıklama')}
              placeholderTextColor={theme.textTertiary}
              multiline
              style={[
                styles.input,
                { minHeight: 100, textAlignVertical: 'top', backgroundColor: theme.bgInput, borderColor: theme.border, color: theme.textPrimary },
              ]}
            />
            <PickerField
              label={t('knownIssues.topic', 'Konu (opsiyonel)')}
              placeholder={t('knownIssues.noTopic', 'Konu yok')}
              value={form.topicId}
              onChange={(v) => setForm((f) => ({ ...f, topicId: v }))}
              options={[
                { label: t('knownIssues.noTopic', 'Konu yok'), value: null },
                ...topics.map((tp) => ({ label: tp.name, value: tp.id })),
              ]}
            />
            <View style={styles.sheetActions}>
              <Pressable
                onPress={() => setCreateOpen(false)}
                style={[styles.sheetBtn, { borderWidth: 1, borderColor: theme.border }]}
              >
                <Text style={{ color: theme.textSecondary, fontWeight: '600' }}>
                  {t('common.cancel', 'İptal')}
                </Text>
              </Pressable>
              <Pressable
                onPress={submitCreate}
                disabled={!form.title.trim() || !form.content.trim() || saving}
                style={[
                  styles.sheetBtn,
                  {
                    backgroundColor: theme.primary,
                    opacity: !form.title.trim() || !form.content.trim() || saving ? 0.5 : 1,
                  },
                ]}
              >
                {saving ? (
                  <ActivityIndicator color={theme.onPrimary} size="small" />
                ) : (
                  <Text style={{ color: theme.onPrimary, fontWeight: '700' }}>
                    {t('common.save', 'Kaydet')}
                  </Text>
                )}
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
  content: { padding: 16, gap: 12 },
  addBtn: { borderWidth: 1, borderRadius: 10, paddingVertical: 11, alignItems: 'center' },
  hint: { textAlign: 'center', marginTop: 32, fontSize: 14 },
  item: { borderWidth: 1, borderRadius: 10, padding: 14, gap: 8 },
  itemTitle: { fontSize: 14, fontWeight: '600' },
  itemContent: { fontSize: 13, lineHeight: 19 },
  backdrop: { flex: 1, justifyContent: 'flex-end' },
  sheet: { borderTopLeftRadius: 20, borderTopRightRadius: 20, padding: 20, gap: 12 },
  sheetTitle: { fontSize: 17, fontWeight: '700' },
  input: { borderWidth: 1, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 11, fontSize: 14 },
  sheetActions: { flexDirection: 'row', gap: 10, marginTop: 4 },
  sheetBtn: { flex: 1, height: 46, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
});
