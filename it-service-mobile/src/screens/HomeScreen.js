import { View, Text, Pressable, StyleSheet, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { useTheme } from '../theme/ThemeContext';

/**
 * Geçici ana ekran — auth round-trip'inin çalıştığını kanıtlar.
 * Sonraki fazda rol bazlı navigation + gerçek ekranlarla değiştirilecek.
 */
export default function HomeScreen() {
  const { user, roles, getPrimaryRole, logout } = useAuth();
  const { theme } = useTheme();
  const { t } = useTranslation();

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.bgBody }]}>
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={[styles.greeting, { color: theme.textPrimary }]}>
          {t('home.welcome', 'Hoş geldin')}, {user?.name || user?.username}
        </Text>

        <View style={[styles.card, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
          <Row label="E-posta" value={user?.email} theme={theme} />
          <Row label="Kullanıcı adı" value={user?.username} theme={theme} />
          <Row label="Roller" value={roles.join(', ') || '—'} theme={theme} />
          <Row label="Birincil rol" value={getPrimaryRole() || '—'} theme={theme} />
        </View>

        <Text style={[styles.note, { color: theme.textTertiary }]}>
          Rol bazlı ekranlar bir sonraki fazda eklenecek.
        </Text>

        <Pressable
          onPress={logout}
          style={({ pressed }) => [
            styles.button,
            { backgroundColor: theme.danger, opacity: pressed ? 0.7 : 1 },
          ]}
        >
          <Text style={styles.buttonText}>{t('home.logout', 'Çıkış Yap')}</Text>
        </Pressable>
      </ScrollView>
    </SafeAreaView>
  );
}

function Row({ label, value, theme }) {
  return (
    <View style={styles.row}>
      <Text style={[styles.rowLabel, { color: theme.textSecondary }]}>{label}</Text>
      <Text style={[styles.rowValue, { color: theme.textPrimary }]}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 20, gap: 20 },
  greeting: { fontSize: 22, fontWeight: '700' },
  card: { borderRadius: 12, borderWidth: 1, padding: 16, gap: 12 },
  row: { flexDirection: 'row', justifyContent: 'space-between', gap: 16 },
  rowLabel: { fontSize: 14 },
  rowValue: { fontSize: 14, fontWeight: '600', flexShrink: 1, textAlign: 'right' },
  note: { fontSize: 13, fontStyle: 'italic' },
  button: {
    height: 48,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonText: { color: '#ffffff', fontSize: 15, fontWeight: '600' },
});
