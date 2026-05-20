import { useState } from 'react';
import { View, Text, Pressable, StyleSheet, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { useTheme } from '../theme/ThemeContext';
import { updateLanguagePreference } from '../api/users';
import LanguageSheet from '../components/LanguageSheet';

/** Menü sekmesi — kullanıcı bilgisi, diğer ekranlara geçiş, tema ve çıkış. */
export default function MenuScreen({ navigation }) {
  const { user, roles, getPrimaryRole, logout } = useAuth();
  const { theme, mode, toggle } = useTheme();
  const { t, i18n } = useTranslation();
  const [langOpen, setLangOpen] = useState(false);
  const isAdmin = roles.includes('AGENT_ADMIN') || roles.includes('MANAGER');

  return (
    <SafeAreaView edges={['bottom']} style={[styles.container, { backgroundColor: theme.bgBody }]}>
      <ScrollView contentContainerStyle={styles.content}>
        <View style={[styles.card, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
          <View style={[styles.avatar, { backgroundColor: theme.primary }]}>
            <Text style={styles.avatarText}>
              {(user?.name || user?.username || '?').charAt(0).toUpperCase()}
            </Text>
          </View>
          <Text style={[styles.name, { color: theme.textPrimary }]}>
            {user?.name || user?.username}
          </Text>
          <Text style={[styles.sub, { color: theme.textSecondary }]}>{user?.email || '—'}</Text>
          <View style={[styles.roleBadge, { backgroundColor: theme.primary }]}>
            <Text style={styles.roleText}>{getPrimaryRole() || '—'}</Text>
          </View>
          {roles.length > 1 && (
            <Text style={[styles.sub, { color: theme.textTertiary }]}>{roles.join(', ')}</Text>
          )}
        </View>

        <View style={styles.group}>
          <MenuRow
            theme={theme}
            icon="notifications-outline"
            label={t('menu.notifications', 'Bildirimler')}
            onPress={() => navigation.navigate('Notifications')}
          />
          <MenuRow
            theme={theme}
            icon="bulb-outline"
            label={t('menu.knownIssues', 'Bilinen Sorunlar')}
            onPress={() => navigation.navigate('KnownIssues')}
          />
          <MenuRow
            theme={theme}
            icon="person-outline"
            label={t('menu.profile', 'Profil')}
            onPress={() => navigation.navigate('Profile')}
          />
          <MenuRow
            theme={theme}
            icon="cube-outline"
            label={t('menu.products', 'Ürünler')}
            onPress={() => navigation.navigate('Products')}
          />
          <MenuRow
            theme={theme}
            icon="options-outline"
            label={t('menu.notificationPrefs', 'Bildirim Tercihleri')}
            onPress={() => navigation.navigate('NotificationPreferences')}
          />
          {isAdmin && (
            <MenuRow
              theme={theme}
              icon="people-outline"
              label={t('menu.userManagement', 'Kullanıcı Yönetimi')}
              onPress={() => navigation.navigate('UserManagement')}
            />
          )}
          {isAdmin && (
            <MenuRow
              theme={theme}
              icon="shield-checkmark-outline"
              label={t('menu.adminPanel', 'Yönetim Paneli')}
              onPress={() => navigation.navigate('AdminPanel')}
            />
          )}
          <MenuRow
            theme={theme}
            icon="language-outline"
            label={`${t('nav.language.label', 'Dil')} — ${
              i18n.language?.startsWith('tr') ? 'Türkçe' : 'English'
            }`}
            onPress={() => setLangOpen(true)}
          />
          <MenuRow
            theme={theme}
            icon={mode === 'dark' ? 'sunny-outline' : 'moon-outline'}
            label={mode === 'dark' ? t('menu.lightMode', 'Açık tema') : t('menu.darkMode', 'Koyu tema')}
            onPress={toggle}
          />
        </View>

        <Pressable
          onPress={logout}
          style={({ pressed }) => [
            styles.logout,
            { backgroundColor: theme.danger, opacity: pressed ? 0.7 : 1 },
          ]}
        >
          <Ionicons name="log-out-outline" size={20} color="#fff" />
          <Text style={styles.logoutText}>{t('menu.logout', 'Çıkış Yap')}</Text>
        </Pressable>
      </ScrollView>

      <LanguageSheet
        visible={langOpen}
        onClose={() => setLangOpen(false)}
        onSelect={(code) => updateLanguagePreference(code).catch(() => {})}
      />
    </SafeAreaView>
  );
}

function MenuRow({ icon, label, onPress, theme }) {
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [
        styles.row,
        { backgroundColor: theme.bgSurface, borderColor: theme.border, opacity: pressed ? 0.7 : 1 },
      ]}
    >
      <Ionicons name={icon} size={20} color={theme.textSecondary} />
      <Text style={[styles.rowText, { color: theme.textPrimary }]}>{label}</Text>
      <Ionicons name="chevron-forward" size={18} color={theme.textTertiary} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 20, gap: 14 },
  card: { borderRadius: 14, borderWidth: 1, padding: 20, alignItems: 'center', gap: 6 },
  avatar: {
    width: 64,
    height: 64,
    borderRadius: 32,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 4,
  },
  avatarText: { color: '#fff', fontSize: 26, fontWeight: '700' },
  name: { fontSize: 18, fontWeight: '700' },
  sub: { fontSize: 13 },
  roleBadge: { paddingHorizontal: 10, paddingVertical: 3, borderRadius: 999, marginTop: 4 },
  roleText: { color: '#fff', fontSize: 11, fontWeight: '700' },
  group: { gap: 8 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    borderWidth: 1,
    borderRadius: 12,
    padding: 16,
  },
  rowText: { flex: 1, fontSize: 15, fontWeight: '500' },
  logout: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    height: 50,
    borderRadius: 12,
    marginTop: 6,
  },
  logoutText: { color: '#fff', fontSize: 15, fontWeight: '700' },
});
