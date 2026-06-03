import { useState, useEffect } from 'react';
import {
  View,
  Text,
  TextInput,
  Pressable,
  ScrollView,
  StyleSheet,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import { useAuth } from '../auth/AuthContext';
import { updateProfile, changePassword } from '../api/users';

/** Profil — ad/soyad/e-posta düzenleme ve şifre değiştirme. */
export default function ProfileScreen() {
  const { theme } = useTheme();
  const { t } = useTranslation();
  const { user, getPrimaryRole, roles, refreshUser } = useAuth();

  // Kullanıcının sahip olduğu TÜM roller — gösterim önceliğine göre sıralı.
  // LEAD_AGENT varsa AGENT gizlenir (lead, agent'ı kapsar).
  const primaryRole = getPrimaryRole();
  const displayRoles = (() => {
    const order = ['ADMIN', 'MANAGER', 'LEAD_AGENT', 'AGENT', 'CUSTOMER'];
    const uniq = new Set(roles ?? []);
    if (uniq.has('LEAD_AGENT')) uniq.delete('AGENT');
    const ordered = order.filter((r) => uniq.has(r));
    return ordered.length ? ordered : primaryRole ? [primaryRole] : [];
  })();

  const [firstName, setFirstName] = useState(user?.firstName ?? '');
  const [lastName, setLastName] = useState(user?.lastName ?? '');
  const [email, setEmail] = useState(user?.email ?? '');
  const [savingProfile, setSavingProfile] = useState(false);

  const [curPw, setCurPw] = useState('');
  const [newPw, setNewPw] = useState('');
  const [confirmPw, setConfirmPw] = useState('');
  const [savingPw, setSavingPw] = useState(false);

  // Token claim'leri değişince (kayıt + refreshUser sonrası) formu tazele.
  useEffect(() => {
    setFirstName(user?.firstName ?? '');
    setLastName(user?.lastName ?? '');
    setEmail(user?.email ?? '');
  }, [user]);

  const saveProfile = async () => {
    if (!firstName.trim()) {
      Alert.alert(t('profile.firstNameRequired', 'Ad alanı boş olamaz.'));
      return;
    }
    if (!lastName.trim()) {
      Alert.alert(t('profile.lastNameRequired', 'Soyad alanı boş olamaz.'));
      return;
    }
    if (!email.trim()) {
      Alert.alert(t('profile.emailInvalid', 'Geçerli bir e-posta adresi girin.'));
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
      const status = e?.response?.status;
      const msg = e?.response?.data?.message;
      if (status === 409) {
        Alert.alert(msg || t('profile.emailConflict', 'Bu e-posta adresi başka bir kullanıcıda kayıtlı.'));
      } else {
        Alert.alert(msg || t('profile.saveError', 'Profil güncellenemedi. Lütfen tekrar deneyin.'));
      }
    } finally {
      setSavingProfile(false);
    }
  };

  const savePassword = async () => {
    if (!curPw) {
      Alert.alert(t('profile.passwordModal.currentRequired', 'Mevcut şifre zorunludur.'));
      return;
    }
    if (!newPw) {
      Alert.alert(t('profile.passwordModal.newRequired', 'Yeni şifre zorunludur.'));
      return;
    }
    if (newPw.length < 8 || !/[A-Z]/.test(newPw) || !/[0-9]/.test(newPw)) {
      Alert.alert(
        t('profile.passwordModal.weakPassword', 'En az 8 karakter, 1 büyük harf ve 1 rakam içermelidir.'),
      );
      return;
    }
    if (newPw !== confirmPw) {
      Alert.alert(t('profile.passwordModal.mismatch', 'Şifreler eşleşmiyor.'));
      return;
    }
    setSavingPw(true);
    try {
      await changePassword({ currentPassword: curPw, newPassword: newPw });
      setCurPw('');
      setNewPw('');
      setConfirmPw('');
      Alert.alert(t('profile.passwordModal.success', 'Şifre başarıyla değiştirildi.'));
    } catch (e) {
      const status = e?.response?.status;
      const msg = e?.response?.data?.message;
      if (status === 400 || status === 401) {
        Alert.alert(msg || t('profile.passwordModal.wrongCurrent', 'Mevcut şifre yanlış.'));
      } else {
        Alert.alert(msg || t('profile.passwordModal.genericError', 'Şifre değiştirilemedi. Lütfen tekrar deneyin.'));
      }
    } finally {
      setSavingPw(false);
    }
  };

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: theme.bgBody }}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
      keyboardDismissMode="on-drag"
      automaticallyAdjustKeyboardInsets
    >
      <View style={[styles.card, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
        <Text style={[styles.cardTitle, { color: theme.textPrimary }]}>
          {t('profile.accountDetails', 'Hesap Detayları')}
        </Text>
        <Text style={[styles.role, { color: theme.textTertiary }]}>
          {t('profile.role', 'Rol')}: {displayRoles.length ? displayRoles.join(', ') : '—'}
        </Text>

        <LabeledInput
          label={t('profile.fieldFirstName', 'Ad')}
          value={firstName}
          onChangeText={setFirstName}
          theme={theme}
        />
        <LabeledInput
          label={t('profile.fieldLastName', 'Soyad')}
          value={lastName}
          onChangeText={setLastName}
          theme={theme}
        />
        <LabeledInput
          label={t('profile.fieldEmail', 'E-posta')}
          value={email}
          onChangeText={setEmail}
          theme={theme}
          keyboardType="email-address"
          autoCapitalize="none"
        />

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
            <Text style={styles.btnText}>{t('profile.save', 'Kaydet')}</Text>
          )}
        </Pressable>
      </View>

      <View style={[styles.card, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
        <Text style={[styles.cardTitle, { color: theme.textPrimary }]}>
          {t('profile.changePassword', 'Şifre Değiştir')}
        </Text>

        <LabeledInput
          label={t('profile.passwordModal.currentPassword', 'Mevcut Şifre')}
          value={curPw}
          onChangeText={setCurPw}
          theme={theme}
          secureTextEntry
        />
        <LabeledInput
          label={t('profile.passwordModal.newPassword', 'Yeni Şifre')}
          value={newPw}
          onChangeText={setNewPw}
          theme={theme}
          secureTextEntry
        />
        <LabeledInput
          label={t('profile.passwordModal.confirmPassword', 'Yeni Şifre (Tekrar)')}
          value={confirmPw}
          onChangeText={setConfirmPw}
          theme={theme}
          secureTextEntry
        />

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
            <Text style={styles.btnText}>{t('profile.passwordModal.submit', 'Şifreyi Değiştir')}</Text>
          )}
        </Pressable>
      </View>
    </ScrollView>
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
  content: { padding: 16, gap: 16, paddingBottom: 40 },
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
