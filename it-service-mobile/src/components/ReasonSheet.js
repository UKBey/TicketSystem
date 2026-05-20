import { useState, useEffect } from 'react';
import {
  Modal,
  View,
  Text,
  Pressable,
  TextInput,
  ScrollView,
  StyleSheet,
  ActivityIndicator,
} from 'react-native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import SheetBackdrop from './SheetBackdrop';

/**
 * Sebep kodu + not seçtiren modal — çöz / kapat / unclaim gibi aksiyonlarda kullanılır.
 * onConfirm({ reasonCode, note }) çağrılır.
 */
export default function ReasonSheet({
  visible,
  title,
  actionKey,
  codes,
  onCancel,
  onConfirm,
  busy,
}) {
  const { theme } = useTheme();
  const { t } = useTranslation();
  const [code, setCode] = useState(null);
  const [note, setNote] = useState('');

  useEffect(() => {
    if (visible) {
      setCode(null);
      setNote('');
    }
  }, [visible]);

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onCancel}>
      <SheetBackdrop onClose={onCancel}>
        <View style={[styles.sheet, { backgroundColor: theme.bgSurface }]}>
          <Text style={[styles.title, { color: theme.textPrimary }]}>{title}</Text>

          <ScrollView style={styles.list} keyboardShouldPersistTaps="handled">
            {codes.map((c) => {
              const active = code === c;
              return (
                <Pressable
                  key={c}
                  onPress={() => setCode(c)}
                  style={[
                    styles.option,
                    { borderColor: active ? theme.primary : theme.border },
                  ]}
                >
                  <View style={[styles.radio, { borderColor: active ? theme.primary : theme.textTertiary }]}>
                    {active && <View style={[styles.radioDot, { backgroundColor: theme.primary }]} />}
                  </View>
                  <Text style={[styles.optionText, { color: theme.textPrimary }]}>
                    {t(`reasonCode.${actionKey}.${c}`, c)}
                  </Text>
                </Pressable>
              );
            })}
          </ScrollView>

          <TextInput
            value={note}
            onChangeText={setNote}
            placeholder={t('reason.note', 'Not (opsiyonel)')}
            placeholderTextColor={theme.textTertiary}
            multiline
            style={[styles.note, { backgroundColor: theme.bgInput, borderColor: theme.border, color: theme.textPrimary }]}
          />

          <View style={styles.actions}>
            <Pressable
              onPress={onCancel}
              style={[styles.btn, { borderWidth: 1, borderColor: theme.border }]}
            >
              <Text style={{ color: theme.textSecondary, fontWeight: '600' }}>
                {t('common.cancel', 'İptal')}
              </Text>
            </Pressable>
            <Pressable
              onPress={() => code && onConfirm({ reasonCode: code, note: note.trim() })}
              disabled={!code || busy}
              style={[styles.btn, { backgroundColor: theme.primary, opacity: !code || busy ? 0.5 : 1 }]}
            >
              {busy ? (
                <ActivityIndicator color={theme.onPrimary} size="small" />
              ) : (
                <Text style={{ color: theme.onPrimary, fontWeight: '700' }}>
                  {t('common.confirm', 'Onayla')}
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
  sheet: {
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    padding: 20,
    gap: 14,
  },
  title: { fontSize: 17, fontWeight: '700' },
  list: { maxHeight: 260 },
  option: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    borderWidth: 1,
    borderRadius: 10,
    padding: 12,
    marginBottom: 8,
  },
  radio: {
    width: 20,
    height: 20,
    borderRadius: 10,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  radioDot: { width: 10, height: 10, borderRadius: 5 },
  optionText: { fontSize: 14, flexShrink: 1 },
  note: {
    borderWidth: 1,
    borderRadius: 10,
    padding: 12,
    minHeight: 60,
    fontSize: 14,
  },
  actions: { flexDirection: 'row', gap: 10 },
  btn: {
    flex: 1,
    height: 46,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
