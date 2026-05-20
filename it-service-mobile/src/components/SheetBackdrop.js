import { KeyboardAvoidingView, Pressable, Keyboard, Platform, StyleSheet } from 'react-native';
import { useTheme } from '../theme/ThemeContext';

/**
 * Alt-sayfa (bottom sheet) modalları için ortak arka plan.
 * - Klavye açıldığında içeriği yukarı iter (KeyboardAvoidingView), böylece
 *   giriş alanları ve butonlar klavyenin altında kalmaz.
 * - Karartılmış alana dokununca klavyeyi kapatır.
 */
export default function SheetBackdrop({ children }) {
  const { theme } = useTheme();
  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      style={[styles.backdrop, { backgroundColor: theme.overlay }]}
    >
      <Pressable style={StyleSheet.absoluteFill} onPress={Keyboard.dismiss} />
      {children}
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, justifyContent: 'flex-end' },
});
