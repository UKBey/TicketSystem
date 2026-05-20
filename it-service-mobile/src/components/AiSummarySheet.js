import { useState, useEffect, useCallback } from 'react';
import {
  Modal,
  View,
  Text,
  Pressable,
  ScrollView,
  StyleSheet,
  ActivityIndicator,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import i18n from '../i18n';
import { useTheme } from '../theme/ThemeContext';
import { generateAiSummary, getLatestAiSummary } from '../api/ai';
import { formatDate } from '../utils/format';
import SheetBackdrop from './SheetBackdrop';

const VIOLET = '#7c3aed';

/**
 * AI özeti modalı — bilet konuşmasının llm-service tarafından üretilmiş özetini
 * gösterir; "Oluştur / Yenile" ile yeni özet ister. Açılınca en son özeti
 * getirir. Web'deki AiSummaryModal ile işlevsel eşdeğer; yalnızca agent'lara
 * görünen butondan açılır.
 */
export default function AiSummarySheet({ visible, ticketId, onClose }) {
  const { theme } = useTheme();
  const { t } = useTranslation();

  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchLatest = useCallback(async () => {
    try {
      const res = await getLatestAiSummary(ticketId);
      setSummary(res.data);
    } catch {
      // Henüz özet üretilmemişse 404 döner — boş durum gösterilir.
      setSummary(null);
    }
  }, [ticketId]);

  useEffect(() => {
    if (visible) {
      setError(null);
      fetchLatest();
    }
  }, [visible, fetchLatest]);

  const handleGenerate = async () => {
    setLoading(true);
    setError(null);
    try {
      const lang = i18n.language?.startsWith('tr') ? 'tr' : 'en';
      const res = await generateAiSummary(ticketId, lang);
      setSummary(res.data);
    } catch (err) {
      const status = err?.response?.status;
      const data = err?.response?.data;
      if (status === 429) {
        const seconds = Math.ceil(data?.retryAfterSeconds ?? 10);
        // Per-IP rate limit body'de error:"RATE_LIMIT_EXCEEDED" doner; Groq token
        // kotasi ise ProblemDetail (detail) gonderir — ayri mesaj gosterilir.
        const key =
          data?.error === 'RATE_LIMIT_EXCEEDED'
            ? 'ticketDetail.aiSummaryThrottle'
            : 'ticketDetail.aiSummaryRateLimit';
        setError(t(key, { seconds }));
      } else {
        setError(
          data?.detail ||
            t('ticketDetail.aiSummaryError', 'Özet oluşturulamadı. Lütfen tekrar deneyin.'),
        );
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <SheetBackdrop onClose={onClose}>
        <View style={[styles.sheet, { backgroundColor: theme.bgSurface }]}>
          <View style={styles.header}>
            <View style={styles.titleRow}>
              <Ionicons name="sparkles" size={18} color={VIOLET} />
              <Text style={[styles.title, { color: theme.textPrimary }]}>
                {t('ticketDetail.aiSummaryTitle', 'AI Özeti')}
              </Text>
            </View>
            <Pressable onPress={onClose} hitSlop={8}>
              <Ionicons name="close" size={22} color={theme.textTertiary} />
            </Pressable>
          </View>

          <ScrollView style={styles.body} contentContainerStyle={styles.bodyContent}>
            {summary && (
              <>
                <View
                  style={[
                    styles.summaryBox,
                    { backgroundColor: theme.dark ? 'rgba(124,58,237,0.12)' : '#f5f3ff' },
                  ]}
                >
                  <Text selectable style={[styles.summaryText, { color: theme.textPrimary }]}>
                    {summary.summary}
                  </Text>
                </View>
                <View style={styles.meta}>
                  <Text style={[styles.metaText, { color: theme.textTertiary }]} numberOfLines={1}>
                    {summary.model}
                  </Text>
                  <Text style={[styles.metaText, { color: theme.textTertiary }]}>
                    {formatDate(summary.createdAt)}
                  </Text>
                </View>
              </>
            )}
            {error && (
              <View
                style={[
                  styles.errorBox,
                  { backgroundColor: theme.dark ? 'rgba(239,68,68,0.12)' : '#fee2e2' },
                ]}
              >
                <Text style={{ color: theme.danger, fontSize: 13, lineHeight: 18 }}>{error}</Text>
              </View>
            )}
            {!summary && !loading && !error && (
              <Text style={[styles.empty, { color: theme.textTertiary }]}>
                {t('ticketDetail.aiSummaryEmpty', 'Henüz özet yok. Oluşturmak için butona tıklayın.')}
              </Text>
            )}
          </ScrollView>

          <Pressable
            onPress={handleGenerate}
            disabled={loading}
            style={({ pressed }) => [
              styles.genBtn,
              { backgroundColor: VIOLET, opacity: loading || pressed ? 0.7 : 1 },
            ]}
          >
            {loading ? (
              <>
                <ActivityIndicator color="#fff" size="small" />
                <Text style={styles.genBtnText}>
                  {t('ticketDetail.aiSummaryGenerating', 'Oluşturuluyor...')}
                </Text>
              </>
            ) : (
              <>
                <Ionicons name="sparkles" size={16} color="#fff" />
                <Text style={styles.genBtnText}>
                  {summary
                    ? t('ticketDetail.aiSummaryRegenerate', 'Özeti Yenile')
                    : t('ticketDetail.aiSummaryGenerate', 'Özet Oluştur')}
                </Text>
              </>
            )}
          </Pressable>
        </View>
      </SheetBackdrop>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, justifyContent: 'flex-end' },
  sheet: { borderTopLeftRadius: 20, borderTopRightRadius: 20, padding: 20, gap: 14 },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: 8, flexShrink: 1 },
  title: { fontSize: 17, fontWeight: '700' },
  body: { maxHeight: 360 },
  bodyContent: { gap: 10 },
  summaryBox: { borderRadius: 12, padding: 14, borderLeftWidth: 3, borderLeftColor: VIOLET },
  summaryText: { fontSize: 14, lineHeight: 21 },
  meta: { flexDirection: 'row', justifyContent: 'space-between', gap: 8 },
  metaText: { fontSize: 11, flexShrink: 1 },
  errorBox: { borderRadius: 10, paddingHorizontal: 12, paddingVertical: 10 },
  empty: { fontSize: 13, textAlign: 'center', paddingVertical: 24 },
  genBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    height: 48,
    borderRadius: 12,
  },
  genBtnText: { color: '#fff', fontSize: 14, fontWeight: '700' },
});
