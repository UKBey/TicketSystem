import { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  Pressable,
  StyleSheet,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import { requestPasswordReset } from '../api/auth';

const SUPPORTED_LANGS = ['en', 'tr'];

/** Şifremi unuttum — e-posta ile sıfırlama bağlantısı talebi (anonim ekran). */
export default function ForgotPasswordScreen({ navigation }) {
  const { theme, mode } = useTheme();
  const { t, i18n } = useTranslation();

  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState('');

  const submit = async () => {
    if (!email.trim() || submitting) return;
    setError('');
    setSubmitting(true);
    try {
      const raw = (i18n.language || 'en').split('-')[0].toLowerCase();
      const language = SUPPORTED_LANGS.includes(raw) ? raw : 'en';
      await requestPasswordReset(email.trim(), language, mode);
      setSubmitted(true);
    } catch (e) {
      const status = e?.response?.status;
      if (status === 429) {
        setError(t('forgotPassword.rateLimit', 'Çok fazla deneme yapıldı. Lütfen biraz sonra tekrar deneyin.'));
      } else if (status === 400) {
        setError(t('forgotPassword.invalidEmail', 'Geçerli bir e-posta adresi girin.'));
      } else {
        setError(t('forgotPassword.unknownError', 'Bir hata oluştu. Lütfen tekrar deneyin.'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={[styles.container, { backgroundColor: theme.bgBody }]}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <View style={styles.inner}>
        <View style={styles.brand}>
          <Text style={[styles.title, { color: theme.textPrimary }]}>
            {t('forgotPassword.title', 'Şifrenizi sıfırlayın')}
          </Text>
          <Text style={[styles.subtitle, { color: theme.textSecondary }]}>
            {t(
              'forgotPassword.subtitle',
              'E-posta adresinizi girin, şifre sıfırlama bağlantısını size gönderelim.',
            )}
          </Text>
        </View>

        {submitted ? (
          <View style={[styles.successBox, { backgroundColor: theme.bgSurface, borderColor: theme.success }]}>
            <Text style={[styles.successText, { color: theme.success }]}>
              {t(
                'forgotPassword.success',
                'Bu e-posta ile bir hesap varsa, şifre sıfırlama bağlantısı gönderildi. Gelen kutunuzu (ve spam klasörünüzü) kontrol edin.',
              )}
            </Text>
          </View>
        ) : (
          <>
            <Text style={[styles.label, { color: theme.textSecondary }]}>
              {t('forgotPassword.emailLabel', 'E-posta')}
            </Text>
            <TextInput
              value={email}
              onChangeText={setEmail}
              placeholder={t('forgotPassword.emailPlaceholder', 'ornek@kurum.com')}
              placeholderTextColor={theme.textTertiary}
              autoCapitalize="none"
              autoCorrect={false}
              keyboardType="email-address"
              returnKeyType="send"
              onSubmitEditing={submit}
              style={[
                styles.input,
                { backgroundColor: theme.bgInput, borderColor: theme.border, color: theme.textPrimary },
              ]}
            />

            {!!error && (
              <Text style={[styles.error, { color: theme.danger }]}>{error}</Text>
            )}

            <Pressable
              onPress={submit}
              disabled={submitting || !email.trim()}
              style={({ pressed }) => [
                styles.button,
                {
                  backgroundColor: theme.primary,
                  opacity: submitting || !email.trim() || pressed ? 0.6 : 1,
                },
              ]}
            >
              {submitting ? (
                <ActivityIndicator color={theme.onPrimary} />
              ) : (
                <Text style={[styles.buttonText, { color: theme.onPrimary }]}>
                  {t('forgotPassword.submit', 'Sıfırlama bağlantısı gönder')}
                </Text>
              )}
            </Pressable>
          </>
        )}

        <Pressable
          onPress={() => navigation.goBack()}
          hitSlop={8}
          style={styles.backLink}
        >
          <Ionicons name="arrow-back" size={15} color={theme.textSecondary} />
          <Text style={[styles.backText, { color: theme.textSecondary }]}>
            {t('forgotPassword.backToLogin', 'Girişe dön')}
          </Text>
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center' },
  inner: { paddingHorizontal: 32, gap: 12 },
  brand: { alignItems: 'center', marginBottom: 12 },
  title: { fontSize: 24, fontWeight: '700', textAlign: 'center' },
  subtitle: { fontSize: 14, marginTop: 8, textAlign: 'center', lineHeight: 20 },
  label: { fontSize: 13, fontWeight: '600' },
  input: {
    borderWidth: 1,
    borderRadius: 12,
    paddingHorizontal: 14,
    height: 50,
    fontSize: 15,
  },
  error: { fontSize: 13 },
  button: {
    height: 52,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 4,
  },
  buttonText: { fontSize: 16, fontWeight: '600' },
  successBox: { borderWidth: 1, borderRadius: 12, padding: 16 },
  successText: { fontSize: 14, lineHeight: 20 },
  backLink: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    marginTop: 12,
  },
  backText: { fontSize: 13, fontWeight: '600' },
});
