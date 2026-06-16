import { useState, useEffect } from 'react';
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
} from 'react-native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import { getProducts, getProductTopics, getKnownIssues } from '../api/products';
import { createTicket } from '../api/tickets';
import { priorityLabel } from '../utils/format';
import { localizedName, sortByLocalizedName, pickLocalized } from '../utils/localizedName';
import PickerField from '../components/PickerField';

const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

// "No Topic" sentinel — only offered when the selected product has no active
// topics. Submitted to the API as topicId: null.
const NO_TOPIC = 'NONE';

// Mirror the backend @Size constraints on TicketRequestDTO.
const TITLE_MAX = 100;
const DESCRIPTION_MAX = 500;

/** Yeni bilet oluşturma formu — ürün/konu seçimi + bilinen sorunlar paneli. */
export default function CreateTicketScreen({ navigation }) {
  const { theme } = useTheme();
  const { t } = useTranslation();

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState('MEDIUM');
  const [productId, setProductId] = useState(null);
  const [topicId, setTopicId] = useState(null);

  const [products, setProducts] = useState([]);
  const [topics, setTopics] = useState([]);
  const [knownIssues, setKnownIssues] = useState([]);
  const [expandedIssue, setExpandedIssue] = useState(null);

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    getProducts()
      .then((res) => setProducts((res.data ?? []).filter((p) => p.isActive)))
      .catch(() => {});
  }, []);

  useEffect(() => {
    setTopicId(null);
    setTopics([]);
    if (!productId) return;
    getProductTopics(productId)
      .then((res) => setTopics(res.data ?? []))
      .catch(() => setTopics([]));
  }, [productId]);

  useEffect(() => {
    setKnownIssues([]);
    setExpandedIssue(null);
    if (!productId || !topicId) return;
    getKnownIssues(productId, topicId === NO_TOPIC ? undefined : topicId)
      .then((res) => setKnownIssues((res.data ?? []).filter((k) => k.isActive)))
      .catch(() => setKnownIssues([]));
  }, [productId, topicId]);

  const submit = async () => {
    if (!title.trim() || !description.trim() || !productId || !topicId) {
      setError(t('createTicket.required', 'Başlık, açıklama, ürün ve konu zorunludur.'));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await createTicket({
        title: title.trim(),
        description: description.trim(),
        priority,
        productId,
        topicId: topicId === NO_TOPIC ? null : topicId,
      });
      navigation.goBack();
    } catch (e) {
      setError(e?.response?.data?.message || t('createTicket.failed', 'Bilet oluşturulamadı.'));
    } finally {
      setSubmitting(false);
    }
  };

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
        {error && <Text style={[styles.error, { color: theme.danger }]}>{error}</Text>}

        <View style={styles.group}>
          <Text style={[styles.label, { color: theme.textPrimary }]}>
            {t('createTicket.title', 'Başlık')} *
          </Text>
          <TextInput
            value={title}
            onChangeText={setTitle}
            maxLength={TITLE_MAX}
            placeholder={t('createTicket.titlePlaceholder', 'Kısa başlık')}
            placeholderTextColor={theme.textTertiary}
            style={[styles.input, { backgroundColor: theme.bgInput, borderColor: theme.border, color: theme.textPrimary }]}
          />
          <Text style={[styles.counter, { color: title.length >= TITLE_MAX ? theme.danger : theme.textTertiary }]}>
            {title.length}/{TITLE_MAX}
          </Text>
        </View>

        <View style={styles.group}>
          <Text style={[styles.label, { color: theme.textPrimary }]}>
            {t('createTicket.description', 'Açıklama')} *
          </Text>
          <TextInput
            value={description}
            onChangeText={setDescription}
            maxLength={DESCRIPTION_MAX}
            placeholder={t('createTicket.descriptionPlaceholder', 'Sorunu açıkla')}
            placeholderTextColor={theme.textTertiary}
            multiline
            style={[
              styles.input,
              styles.textarea,
              { backgroundColor: theme.bgInput, borderColor: theme.border, color: theme.textPrimary },
            ]}
          />
          <Text style={[styles.counter, { color: description.length >= DESCRIPTION_MAX ? theme.danger : theme.textTertiary }]}>
            {description.length}/{DESCRIPTION_MAX}
          </Text>
        </View>

        <PickerField
          label={t('createTicket.priority', 'Öncelik')}
          value={priority}
          onChange={setPriority}
          options={PRIORITIES.map((p) => ({ label: priorityLabel(p, t), value: p }))}
        />

        <PickerField
          label={`${t('createTicket.product', 'Ürün')} *`}
          placeholder={t('createTicket.selectProduct', 'Ürün seç')}
          value={productId}
          onChange={setProductId}
          options={sortByLocalizedName(products).map((p) => ({ label: localizedName(p), value: p.id }))}
        />

        <PickerField
          label={`${t('createTicket.topic', 'Konu')} *`}
          placeholder={
            !productId
              ? t('createTicket.selectProductFirst', 'Önce ürün seç')
              : t('createTicket.selectTopic', 'Konu seç')
          }
          value={topicId}
          onChange={setTopicId}
          disabled={!productId}
          options={
            productId && topics.length === 0
              ? [{ label: t('createTicket.noTopicOption', 'Konusuz'), value: NO_TOPIC }]
              : sortByLocalizedName(topics).map((tp) => ({ label: localizedName(tp), value: tp.id }))
          }
        />

        {knownIssues.length > 0 && (
          <View style={[styles.kiBox, { borderColor: theme.warning }]}>
            <Text style={[styles.kiHeading, { color: theme.textPrimary }]}>
              {t('createTicket.knownIssues', 'Bilinen sorunlar')} ({knownIssues.length})
            </Text>
            <ScrollView
              style={styles.kiScroll}
              nestedScrollEnabled
              keyboardShouldPersistTaps="handled"
            >
              {knownIssues.map((ki) => {
                const open = expandedIssue === ki.id;
                return (
                  <Pressable
                    key={ki.id}
                    onPress={() => setExpandedIssue(open ? null : ki.id)}
                    style={[styles.kiItem, { borderColor: theme.border, backgroundColor: theme.bgSurface }]}
                  >
                    <Text style={[styles.kiTitle, { color: theme.textPrimary }]}>
                      {open ? '▾ ' : '▸ '}
                      {localizedName(ki, 'title')}
                    </Text>
                    {open && !!pickLocalized(ki.contentTr, ki.contentEn) && (
                      <Text style={[styles.kiContent, { color: theme.textSecondary }]}>
                        {pickLocalized(ki.contentTr, ki.contentEn)}
                      </Text>
                    )}
                  </Pressable>
                );
              })}
            </ScrollView>
          </View>
        )}
      </ScrollView>

      <View style={[styles.footer, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
        <Pressable
          onPress={submit}
          disabled={submitting}
          style={({ pressed }) => [
            styles.submit,
            { backgroundColor: theme.primary, opacity: submitting || pressed ? 0.6 : 1 },
          ]}
        >
          {submitting ? (
            <ActivityIndicator color={theme.onPrimary} />
          ) : (
            <Text style={styles.submitText}>{t('createTicket.create', 'Bilet Oluştur')}</Text>
          )}
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  content: { padding: 16, gap: 16 },
  group: { gap: 6 },
  label: { fontSize: 14, fontWeight: '600' },
  input: {
    borderWidth: 1,
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 11,
    fontSize: 14,
  },
  textarea: { minHeight: 90, textAlignVertical: 'top' },
  counter: { fontSize: 11, textAlign: 'right' },
  error: { fontSize: 14, fontWeight: '500' },
  kiBox: { borderWidth: 1, borderRadius: 10, padding: 12, gap: 8 },
  kiHeading: { fontSize: 14, fontWeight: '700' },
  kiScroll: { maxHeight: 240 },
  kiItem: { borderWidth: 1, borderRadius: 8, padding: 10, gap: 6, marginBottom: 8 },
  kiTitle: { fontSize: 13, fontWeight: '600' },
  kiContent: { fontSize: 13, lineHeight: 18 },
  footer: { borderTopWidth: 1, padding: 12 },
  submit: {
    height: 50,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  submitText: { color: '#fff', fontSize: 16, fontWeight: '700' },
});
