import { Modal, View, Text, Pressable, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import { setLanguage } from '../i18n';

const LANGUAGES = [
  { code: 'tr', label: 'Türkçe', flag: '🇹🇷' },
  { code: 'en', label: 'English', flag: '🇺🇸' },
];

/**
 * Dil seçim modalı — i18n dilini değiştirir ve AsyncStorage'a kalıcı yazar.
 * onSelect callback'i, dili backend'e de kaydetmek isteyen ekranlar içindir.
 */
export default function LanguageSheet({ visible, onClose, onSelect }) {
  const { theme } = useTheme();
  const { t, i18n } = useTranslation();
  const current = i18n.language?.startsWith('tr') ? 'tr' : 'en';

  const choose = async (code) => {
    if (code !== current) {
      await setLanguage(code);
      onSelect?.(code);
    }
    onClose();
  };

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <Pressable style={[styles.backdrop, { backgroundColor: theme.overlay }]} onPress={onClose}>
        <Pressable
          style={[styles.sheet, { backgroundColor: theme.bgSurface }]}
          onPress={(e) => e.stopPropagation()}
        >
          <Text style={[styles.title, { color: theme.textPrimary }]}>
            {t('nav.language.label', 'Dil')}
          </Text>
          {LANGUAGES.map((lang) => {
            const active = lang.code === current;
            return (
              <Pressable
                key={lang.code}
                onPress={() => choose(lang.code)}
                style={({ pressed }) => [
                  styles.option,
                  { borderColor: theme.border, opacity: pressed ? 0.6 : 1 },
                ]}
              >
                <Text style={styles.flag}>{lang.flag}</Text>
                <Text
                  style={[
                    styles.label,
                    { color: active ? theme.primary : theme.textPrimary, fontWeight: active ? '700' : '500' },
                  ]}
                >
                  {lang.label}
                </Text>
                {active && <Ionicons name="checkmark" size={20} color={theme.primary} />}
              </Pressable>
            );
          })}
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, justifyContent: 'center', padding: 28 },
  sheet: { borderRadius: 14, padding: 16, gap: 6 },
  title: { fontSize: 16, fontWeight: '700', marginBottom: 4 },
  option: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    borderWidth: 1,
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 13,
  },
  flag: { fontSize: 20 },
  label: { flex: 1, fontSize: 15 },
});
