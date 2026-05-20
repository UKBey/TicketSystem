import { useState, useEffect } from 'react';
import {
  Modal,
  View,
  Text,
  Pressable,
  TextInput,
  StyleSheet,
  ActivityIndicator,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import SheetBackdrop from './SheetBackdrop';

const CSAT_MAX = 500;

/** CSAT memnuniyet anketi modalı — 1-5 yıldız puan + opsiyonel yorum. */
export default function CsatSheet({ visible, busy, onCancel, onConfirm }) {
  const { theme } = useTheme();
  const { t } = useTranslation();
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState('');

  useEffect(() => {
    if (visible) {
      setRating(5);
      setComment('');
    }
  }, [visible]);

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onCancel}>
      <SheetBackdrop onClose={onCancel}>
        <View style={[styles.sheet, { backgroundColor: theme.bgSurface }]}>
          <Text style={[styles.title, { color: theme.textPrimary }]}>
            {t('ticketDetail.csatTitle', 'Memnuniyet Anketi')}
          </Text>
          <Text style={[styles.question, { color: theme.textSecondary }]}>
            {t('ticketDetail.csatQuestion', 'Hizmetimizden ne kadar memnun kaldınız?')}
          </Text>

          <View style={styles.stars}>
            {[1, 2, 3, 4, 5].map((star) => (
              <Pressable key={star} onPress={() => setRating(star)} hitSlop={6}>
                <Ionicons
                  name={rating >= star ? 'star' : 'star-outline'}
                  size={40}
                  color={rating >= star ? theme.warning : theme.textTertiary}
                />
              </Pressable>
            ))}
          </View>

          <TextInput
            value={comment}
            onChangeText={setComment}
            placeholder={t('ticketDetail.csatPlaceholder', 'Yorumunuz (opsiyonel)')}
            placeholderTextColor={theme.textTertiary}
            multiline
            maxLength={CSAT_MAX}
            style={[
              styles.note,
              { backgroundColor: theme.bgInput, borderColor: theme.border, color: theme.textPrimary },
            ]}
          />

          <View style={styles.actions}>
            <Pressable
              onPress={onCancel}
              disabled={busy}
              style={[styles.btn, { borderWidth: 1, borderColor: theme.border }]}
            >
              <Text style={{ color: theme.textSecondary, fontWeight: '600' }}>
                {t('common.cancel', 'İptal')}
              </Text>
            </Pressable>
            <Pressable
              onPress={() => onConfirm({ rating, comment: comment.trim() })}
              disabled={busy}
              style={[styles.btn, { backgroundColor: theme.primary, opacity: busy ? 0.5 : 1 }]}
            >
              {busy ? (
                <ActivityIndicator color={theme.onPrimary} size="small" />
              ) : (
                <Text style={{ color: theme.onPrimary, fontWeight: '700' }}>
                  {t('ticketDetail.csatSubmit', 'Gönder')}
                </Text>
              )}
            </Pressable>
          </View>
        </View>
      </SheetBackdrop>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, justifyContent: 'flex-end' },
  sheet: { borderTopLeftRadius: 20, borderTopRightRadius: 20, padding: 20, gap: 12 },
  title: { fontSize: 17, fontWeight: '700' },
  question: { fontSize: 14 },
  stars: { flexDirection: 'row', justifyContent: 'center', gap: 8, paddingVertical: 4 },
  note: { borderWidth: 1, borderRadius: 10, padding: 12, minHeight: 64, fontSize: 14 },
  actions: { flexDirection: 'row', gap: 10 },
  btn: { flex: 1, height: 46, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
});
