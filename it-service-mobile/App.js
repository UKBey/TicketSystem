import { StatusBar } from 'expo-status-bar';
import { LogBox } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import './src/i18n';
import { ThemeProvider } from './src/theme/ThemeContext';
import { AuthProvider } from './src/auth/AuthContext';
import RootNavigator from './src/navigation/RootNavigator';

// Bilet detayındaki sohbet, sabit yükseklikli bir kutuda FlatList kullanır;
// yükseklik sınırlı olduğu için sanallaştırma doğru çalışır — uyarı yanlış pozitif.
LogBox.ignoreLogs(['VirtualizedLists should never be nested']);

export default function App() {
  return (
    <SafeAreaProvider>
      <ThemeProvider>
        <AuthProvider>
          <StatusBar style="auto" />
          <RootNavigator />
        </AuthProvider>
      </ThemeProvider>
    </SafeAreaProvider>
  );
}
