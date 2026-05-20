import { useState } from 'react';
import {
  View,
  Text,
  Pressable,
  StyleSheet,
  ActivityIndicator,
} from 'react-native';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { useTheme } from '../theme/ThemeContext';

/** Giriş ekranı — Keycloak OIDC akışını sistem tarayıcısında başlatır. */
export default function LoginScreen() {
  const { login } = useAuth();
  const { theme } = useTheme();
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

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
});
