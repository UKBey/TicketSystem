import { useState } from 'react';
import {
  View,
  Text,
  Pressable,
  StyleSheet,
  ActivityIndicator,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { useTheme } from '../theme/ThemeContext';
import LanguageSheet from '../components/LanguageSheet';

/** Giriş ekranı — Keycloak OIDC akışını sistem tarayıcısında başlatır. */
export default function LoginScreen() {
  const { login } = useAuth();
  const { theme } = useTheme();
  const { t, i18n } = useTranslation();
  const insets = useSafeAreaInsets();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [langOpen, setLangOpen] = useState(false);

  const onLogin = async () => {
    setBusy(true);
    setError(null);
    try {
      const ok = await login();
      if (!ok) setError(t('login.cancelled', 'Giriş tamamlanmadı.'));
    } catch (e) {
      setError(t('login.error', 'Giriş sırasında bir hata oluştu.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <View style={[styles.container, { backgroundColor: theme.bgBody }]}>
      <Pressable
        onPress={() => setLangOpen(true)}
        hitSlop={8}
        style={[
          styles.langBtn,
          { top: insets.top + 8, backgroundColor: theme.bgSurface, borderColor: theme.border },
        ]}
      >
        <Ionicons name="language-outline" size={16} color={theme.textSecondary} />
        <Text style={[styles.langText, { color: theme.textSecondary }]}>
          {i18n.language?.startsWith('tr') ? 'Türkçe' : 'English'}
        </Text>
      </Pressable>

      <View style={styles.brand}>
        <Text style={[styles.title, { color: theme.textPrimary }]}>
          IT Service Desk
        </Text>
        <Text style={[styles.subtitle, { color: theme.textSecondary }]}>
          {t('login.subtitle', 'Destek talepleri yönetim sistemi')}
        </Text>
      </View>

      {error && (
        <Text style={[styles.error, { color: theme.danger }]}>{error}</Text>
      )}

      <Pressable
        onPress={onLogin}
        disabled={busy}
        style={({ pressed }) => [
          styles.button,
          { backgroundColor: theme.primary, opacity: busy || pressed ? 0.7 : 1 },
        ]}
      >
        {busy ? (
          <ActivityIndicator color={theme.onPrimary} />
        ) : (
          <Text style={[styles.buttonText, { color: theme.onPrimary }]}>
            {t('login.signIn', 'Giriş Yap')}
          </Text>
        )}
      </Pressable>

      <LanguageSheet visible={langOpen} onClose={() => setLangOpen(false)} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 32,
  },
  brand: {
    alignItems: 'center',
    marginBottom: 48,
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
  },
  subtitle: {
    fontSize: 14,
    marginTop: 8,
  },
  error: {
    textAlign: 'center',
    marginBottom: 16,
    fontSize: 14,
  },
  button: {
    height: 52,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonText: {
    fontSize: 16,
    fontWeight: '600',
  },
  langBtn: {
    position: 'absolute',
    right: 16,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    borderWidth: 1,
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 7,
  },
  langText: {
    fontSize: 13,
    fontWeight: '600',
  },
});
