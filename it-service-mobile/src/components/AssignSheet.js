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
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import PickerField from './PickerField';
import SheetBackdrop from './SheetBackdrop';

/** Bileti bir agent'a atamak için modal — agent seçimi + not. */
export default function AssignSheet({ visible, agents, busy, onCancel, onConfirm }) {
  const { theme } = useTheme();
  const { t } = useTranslation();
  const [agentId, setAgentId] = useState(null);
  const [note, setNote] = useState('');

  useEffect(() => {
    if (visible) {
      setAgentId(null);
      setNote('');
    }
  }, [visible]);

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onCancel}>
      <SheetBackdrop onClose={onCancel}>
        <View style={[styles.sheet, { backgroundColor: theme.bgSurface }]}>
          <Text style={[styles.title, { color: theme.textPrimary }]}>
            {t('ticketDetail.assignTitle', 'Bileti Ata')}
          </Text>

          <PickerField
            label={t('ticketDetail.agent', 'Agent')}
            placeholder={t('ticketDetail.selectAgent', 'Agent seç')}
            value={agentId}
            onChange={setAgentId}
            options={agents}
          />

          <TextInput
            value={note}
            onChangeText={setNote}
            placeholder={t('reason.note', 'Not (opsiyonel)')}
            placeholderTextColor={theme.textTertiary}
            multiline
            style={[styles.note, { backgroundColor: theme.bgInput, borderColor: theme.border, color: theme.textPrimary }]}
          />

          <View style={styles.actions}>
            <Pressable onPress={onCancel} style={[styles.btn, { borderWidth: 1, borderColor: theme.border }]}>
              <Text style={{ color: theme.textSecondary, fontWeight: '600' }}>
                {t('common.cancel', 'İptal')}
              </Text>
            </Pressable>
            <Pressable
              onPress={() => agentId && onConfirm({ agentId, note: note.trim() })}
              disabled={!agentId || busy}
              style={[styles.btn, { backgroundColor: theme.primary, opacity: !agentId || busy ? 0.5 : 1 }]}
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
  sheet: { borderTopLeftRadius: 20, borderTopRightRadius: 20, padding: 20, gap: 12 },
  title: { fontSize: 17, fontWeight: '700' },
  note: { borderWidth: 1, borderRadius: 10, padding: 12, minHeight: 56, fontSize: 14 },
  actions: { flexDirection: 'row', gap: 10 },
  btn: { flex: 1, height: 46, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
});
