import { useState, useEffect } from 'react';
import {
  Modal,
  View,
  Text,
  Pressable,
  TextInput,
  Switch,
  StyleSheet,
  ActivityIndicator,
  ScrollView,
} from 'react-native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import { getAgentLimits, setAgentLimit } from '../api/users';
import SheetBackdrop from './SheetBackdrop';

/**
 * Bir agent'ın ürün bazlı bilet limit override'larını yöneten modal.
 * Her yetkili ürün için: varsayılan limit + özel limit toggle + değer + kaydet.
 */
export default function AgentLimitsSheet({ visible, user, onClose }) {
  const { theme } = useTheme();
  const { t } = useTranslation();
  const [limits, setLimits] = useState(null);
  const [loadErr, setLoadErr] = useState('');

  const products = user?.authorizedProducts || [];

  useEffect(() => {
    if (!visible || !user) return undefined;
    let cancelled = false;
    setLimits(null);
    setLoadErr('');
    getAgentLimits(user.id)
      .then((res) => {
        if (cancelled) return;
        const map = {};
        (res.data || []).forEach((l) => {
          map[l.productId] = {
            useCustom: !!l.useCustomLimit,
            value: l.maxActiveTickets != null ? String(l.maxActiveTickets) : '',
            saving: false,
            saved: false,
            error: '',
          };
        });
        (user.authorizedProducts || []).forEach((p) => {
          if (!map[p.id]) {
            map[p.id] = { useCustom: false, value: '', saving: false, saved: false, error: '' };
          }
        });
        setLimits(map);
      })
      .catch(() => {
        if (!cancelled) setLoadErr(t('admin.panel.agentLimitsErrorLoad', 'Limitler yüklenemedi.'));
      });
    return () => {
      cancelled = true;
    };
  }, [visible, user, t]);

  const patch = (pid, changes) =>
    setLimits((prev) => (prev ? { ...prev, [pid]: { ...prev[pid], ...changes } } : prev));

  const save = async (pid) => {
    const entry = limits[pid];
    const hasValue = entry.useCustom && String(entry.value).trim() !== '';
    const numVal = hasValue ? parseInt(entry.value, 10) : null;
    // Empty value with custom enabled means "unlimited" (null). Only validate when a value is entered.
    if (hasValue && (Number.isNaN(numVal) || numVal < 1)) {
      patch(pid, { error: '≥ 1' });
      return;
    }
    patch(pid, { saving: true, error: '' });
    try {
      await setAgentLimit(user.id, pid, entry.useCustom, numVal);
      patch(pid, { saving: false, saved: true });
      setTimeout(() => patch(pid, { saved: false }), 2000);
    } catch {
      patch(pid, { saving: false, error: t('admin.panel.agentLimitsErrorSave', 'Kaydedilemedi.') });
    }
  };

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <SheetBackdrop onClose={onClose}>
        <View style={[styles.sheet, { backgroundColor: theme.bgSurface }]}>
          <Text style={[styles.title, { color: theme.textPrimary }]}>
            {t('admin.panel.agentLimitsTitle', '{{name}} — Bilet Limitleri', {
              name: user?.fullName || `${user?.firstName ?? ''} ${user?.lastName ?? ''}`.trim(),
            })}
          </Text>
          <Text style={[styles.subtitle, { color: theme.textSecondary }]}>
            {t(
              'admin.panel.agentLimitsSubtitle',
              'Her ürün için varsayılan limiti geçersiz kılın. Özel limit kapalıysa ürün varsayılanı kullanılır.',
            )}
          </Text>

          {loadErr ? (
            <Text style={{ color: theme.danger, paddingVertical: 12 }}>{loadErr}</Text>
          ) : !limits ? (
            <ActivityIndicator style={{ paddingVertical: 24 }} color={theme.primary} />
          ) : products.length === 0 ? (
            <Text style={{ color: theme.textTertiary, paddingVertical: 12 }}>
              {t('admin.panel.agentLimitsNoProducts', "Bu agent'ın yetkili ürünü yok.")}
            </Text>
          ) : (
            <ScrollView style={{ maxHeight: 380 }} keyboardShouldPersistTaps="handled">
              {products.map((p) => {
                const e = limits[p.id] || {
                  useCustom: false,
                  value: '',
                  saving: false,
                  saved: false,
                  error: '',
                };
                return (
                  <View
                    key={p.id}
                    style={[styles.row, { borderColor: theme.border }]}
                  >
                    <View style={styles.rowHead}>
                      <Text style={[styles.prodName, { color: theme.textPrimary }]} numberOfLines={1}>
                        {p.name}
                      </Text>
                      <Text style={[styles.default, { color: theme.textTertiary }]}>
                        {t('admin.panel.agentLimitsDefault', 'Varsayılan')}:{' '}
                        {p.maxActiveTickets ?? t('admin.panel.agentLimitsUnlimited', 'Limitsiz')}
                      </Text>
                    </View>
                    <View style={styles.rowControls}>
                      <View style={styles.toggleWrap}>
                        <Switch
                          value={e.useCustom}
                          onValueChange={(v) => patch(p.id, { useCustom: v, saved: false, error: '' })}
                          trackColor={{ true: theme.primary, false: theme.border }}
                        />
                        <Text style={{ color: theme.textSecondary, fontSize: 12 }}>
                          {t('admin.panel.agentLimitsCustom', 'Özel Limit')}
                        </Text>
                      </View>
                      <TextInput
                        value={e.value}
                        onChangeText={(v) => patch(p.id, { value: v, saved: false, error: '' })}
                        editable={e.useCustom}
                        keyboardType="number-pad"
                        placeholder={e.useCustom ? t('admin.panel.agentLimitsUnlimited', 'Sınırsız') : '—'}
                        placeholderTextColor={theme.textTertiary}
                        style={[
                          styles.input,
                          {
                            backgroundColor: theme.bgInput,
                            borderColor: e.error ? theme.danger : theme.border,
                            color: theme.textPrimary,
                            opacity: e.useCustom ? 1 : 0.4,
                          },
                        ]}
                      />
                      <Pressable
                        onPress={() => save(p.id)}
                        disabled={e.saving}
                        style={[
                          styles.saveBtn,
                          { backgroundColor: e.saved ? theme.success : theme.primary, opacity: e.saving ? 0.6 : 1 },
                        ]}
                      >
                        {e.saving ? (
                          <ActivityIndicator color="#fff" size="small" />
                        ) : (
                          <Text style={styles.saveText}>
                            {e.saved
                              ? t('admin.panel.agentLimitsSaved', 'Kaydedildi')
                              : t('admin.panel.agentLimitsSave', 'Kaydet')}
                          </Text>
                        )}
                      </Pressable>
                    </View>
                    {!!e.error && (
                      <Text style={{ color: theme.danger, fontSize: 11 }}>{e.error}</Text>
                    )}
                  </View>
                );
              })}
            </ScrollView>
          )}

          <Pressable
            onPress={onClose}
            style={[styles.closeBtn, { borderWidth: 1, borderColor: theme.border }]}
          >
            <Text style={{ color: theme.textSecondary, fontWeight: '600' }}>
              {t('admin.panel.agentLimitsClose', 'Kapat')}
            </Text>
          </Pressable>
        </View>
      </SheetBackdrop>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, justifyContent: 'flex-end' },
  sheet: { borderTopLeftRadius: 20, borderTopRightRadius: 20, padding: 20, gap: 10 },
  title: { fontSize: 17, fontWeight: '700' },
  subtitle: { fontSize: 13, lineHeight: 18 },
  row: { borderWidth: 1, borderRadius: 10, padding: 12, marginBottom: 8, gap: 8 },
  rowHead: { gap: 2 },
  prodName: { fontSize: 14, fontWeight: '700' },
  default: { fontSize: 12 },
  rowControls: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  toggleWrap: { alignItems: 'center', gap: 2 },
  input: {
    width: 64,
    height: 40,
    borderWidth: 1,
    borderRadius: 8,
    textAlign: 'center',
    fontSize: 14,
  },
  saveBtn: {
    flex: 1,
    height: 40,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 8,
  },
  saveText: { color: '#fff', fontSize: 13, fontWeight: '700' },
  closeBtn: { height: 46, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
});
