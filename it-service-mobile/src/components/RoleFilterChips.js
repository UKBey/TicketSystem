import { ScrollView, Pressable, Text, StyleSheet } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';

// Yeni RBAC rolleri — AGENT_ADMIN kullanımdan kaldırıldı (filtrede gösterilmez).
const ROLES = ['CUSTOMER', 'AGENT', 'LEAD_AGENT', 'ADMIN', 'MANAGER'];

/** Yatay rol filtre çubuğu — tek seçim. value null ise "tümü". */
export default function RoleFilterChips({ value, onChange }) {
  const { theme } = useTheme();
  const { t } = useTranslation();
  const items = [
    { key: null, label: t('admin.panel.allRoles', 'Tüm Roller') },
    ...ROLES.map((r) => ({ key: r, label: r })),
  ];

  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      style={styles.bar}
      contentContainerStyle={styles.row}
      keyboardShouldPersistTaps="handled"
    >
      {items.map((it) => {
        const active = value === it.key;
        return (
          <Pressable
            key={it.key ?? 'all'}
            onPress={() => onChange(it.key)}
            style={[
              styles.chip,
              {
                borderColor: active ? theme.primary : theme.border,
                backgroundColor: active ? theme.primary : 'transparent',
              },
            ]}
          >
            <Text
              style={{
                color: active ? theme.onPrimary : theme.textSecondary,
                fontSize: 12,
                fontWeight: '600',
              }}
            >
              {it.label}
            </Text>
          </Pressable>
        );
      })}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  // Yatay ScrollView, dikey flex column içinde dikeyde büyümesin diye flexGrow:0.
  bar: { flexGrow: 0, flexShrink: 0 },
  row: { gap: 8, paddingHorizontal: 12, paddingBottom: 10, alignItems: 'center' },
  chip: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 999, borderWidth: 1 },
});
