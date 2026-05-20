import { useState, useEffect } from 'react';
import {
  View,
  Text,
  TextInput,
  Pressable,
  ScrollView,
  StyleSheet,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Alert,
} from 'react-native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import { useAuth } from '../auth/AuthContext';
import { getUser, updateProfile, changePassword } from '../api/users';

/** Profil — ad/soyad/e-posta düzenleme ve şifre değiştirme. */
export default function ProfileScreen() {
  const { theme } = useTheme();
  const { t } = useTranslation();
  const { user, getPrimaryRole, refreshUser } = useAuth();

  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(true);
  const [savingProfile, setSavingProfile] = useState(false);

  const [curPw, setCurPw] = useState('');
  const [newPw, setNewPw] = useState('');
  const [confirmPw, setConfirmPw] = useState('');
  const [savingPw, setSavingPw] = useState(false);

  useEffect(() => {
    if (!user?.id) {
      setLoading(false);
      return;
    }
    getUser(user.id)
      .then((res) => {
        setFirstName(res.data?.firstName ?? '');
        setLastName(res.data?.lastName ?? '');
        setEmail(res.data?.email ?? '');
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [user?.id]);

  const saveProfile = async () => {
    if (!email.trim()) {
      Alert.alert(t('profile.emailRequired', 'E-posta zorunludur.'));
      return;
    }
    setSavingProfile(true);
    try {
      await updateProfile({
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        email: email.trim(),
      });
      await refreshUser();
      Alert.alert(t('profile.saved', 'Profil güncellendi.'));
    } catch (e) {
      Alert.alert(e?.response?.data?.message || t('profile.saveFailed', 'Güncellenemedi.'));
    } finally {
      setSavingProfile(false);
    }
  };

  const savePassword = async () => {
    if (!curPw || !newPw || newPw !== confirmPw) {
      Alert.alert(t('profile.pwMismatch', 'Yeni şifreler eşleşmiyor veya boş.'));
      return;
    }
    setSavingPw(true);
    try {
      await changePassword({ currentPassword: curPw, newPassword: newPw });
      setCurPw('');
      setNewPw('');
      setConfirmPw('');
      Alert.alert(t('profile.pwChanged', 'Şifre değiştirildi.'));
    } catch (e) {
      Alert.alert(e?.response?.data?.message || t('profile.pwFailed', 'Şifre değiştirilemedi.'));
    } finally {
      setSavingPw(false);
    }
  };

  if (loading) {
    return (
      <View style={[styles.full, { backgroundColor: theme.bgBody }]}>
        <ActivityIndicator size="large" color={theme.primary} />
      </View>
    );
  }

  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: theme.bgBody }}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView
        contentContainerStyle={styles.content}
        keyboardShouldPersistTaps="handled"
        keyboardDismissMode="on-drag"
      >
        <View style={[styles.card, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
          <Text style={[styles.cardTitle, { color: theme.textPrimary }]}>
            {t('profile.info', 'Profil Bilgileri')}
          </Text>
          <Text style={[styles.role, { color: theme.textTertiary }]}>
            {t('profile.role', 'Rol')}: {getPrimaryRole() || '—'}
          </Text>

          <LabeledInput label={t('profile.firstName', 'Ad')} value={firstName}
            onChangeText={setFirstName} theme={theme} />
          <LabeledInput label={t('profile.lastName', 'Soyad')} value={lastName}
            onChangeText={setLastName} theme={theme} />
          <LabeledInput label={t('profile.email', 'E-posta')} value={email}
            onChangeText={setEmail} theme={theme} keyboardType="email-address" autoCapitalize="none" />

          <Pressable
            onPress={saveProfile}
            disabled={savingProfile}
            style={({ pressed }) => [
              styles.btn,
              { backgroundColor: theme.primary, opacity: savingProfile || pressed ? 0.6 : 1 },
            ]}
          >
            {savingProfile ? (
              <ActivityIndicator color={theme.onPrimary} />
            ) : (
              <Text style={styles.btnText}>{t('common.save', 'Kaydet')}</Text>
            )}
          </Pressable>
        </View>

        <View style={[styles.card, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
          <Text style={[styles.cardTitle, { color: theme.textPrimary }]}>
            {t('profile.changePassword', 'Şifre Değiştir')}
          </Text>

          <LabeledInput label={t('profile.currentPassword', 'Mevcut şifre')} value={curPw}
            onChangeText={setCurPw} theme={theme} secureTextEntry />
          <LabeledInput label={t('profile.newPassword', 'Yeni şifre')} value={newPw}
            onChangeText={setNewPw} theme={theme} secureTextEntry />
          <LabeledInput label={t('profile.confirmPassword', 'Yeni şifre (tekrar)')} value={confirmPw}
            onChangeText={setConfirmPw} theme={theme} secureTextEntry />

          <Pressable
            onPress={savePassword}
            disabled={savingPw}
            style={({ pressed }) => [
              styles.btn,
              { backgroundColor: theme.primary, opacity: savingPw || pressed ? 0.6 : 1 },
            ]}
          >
            {savingPw ? (
              <ActivityIndicator color={theme.onPrimary} />
            ) : (
              <Text style={styles.btnText}>{t('profile.updatePassword', 'Şifreyi Güncelle')}</Text>
            )}
          </Pressable>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

function LabeledInput({ label, theme, ...props }) {
  return (
    <View style={styles.group}>
      <Text style={[styles.label, { color: theme.textSecondary }]}>{label}</Text>
      <TextInput
        placeholderTextColor={theme.textTertiary}
        style={[styles.input, { backgroundColor: theme.bgInput, borderColor: theme.border, color: theme.textPrimary }]}
        {...props}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  full: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  content: { padding: 16, gap: 16 },
  card: { borderRadius: 12, borderWidth: 1, padding: 16, gap: 12 },
  cardTitle: { fontSize: 16, fontWeight: '700' },
  role: { fontSize: 12, marginTop: -4 },
  group: { gap: 6 },
  label: { fontSize: 13, fontWeight: '600' },
  input: {
    borderWidth: 1,
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 11,
    fontSize: 14,
  },
  btn: { height: 48, borderRadius: 10, alignItems: 'center', justifyContent: 'center', marginTop: 4 },
  btnText: { color: '#fff', fontSize: 15, fontWeight: '700' },
});
