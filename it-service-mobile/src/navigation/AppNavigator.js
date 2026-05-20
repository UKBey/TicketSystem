import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import MainTabs from './MainTabs';
import TicketDetailScreen from '../screens/TicketDetailScreen';
import CreateTicketScreen from '../screens/CreateTicketScreen';
import NotificationsScreen from '../screens/NotificationsScreen';

const Stack = createNativeStackNavigator();

/**
 * Oturum açık kullanıcının kök yığını — alt sekmeler (MainTabs) + üzerine açılan
 * detay ve oluşturma ekranları. Sekme içindeki listeler bu ekranlara navigate eder.
 */
export default function AppNavigator() {
  const { theme } = useTheme();
  const { t } = useTranslation();

  return (
    <Stack.Navigator
      screenOptions={{
        headerStyle: { backgroundColor: theme.bgSurface },
        headerTintColor: theme.textPrimary,
        headerTitleStyle: { fontWeight: '700' },
        contentStyle: { backgroundColor: theme.bgBody },
      }}
    >
      <Stack.Screen name="Main" component={MainTabs} options={{ headerShown: false }} />
      <Stack.Screen
        name="TicketDetail"
        component={TicketDetailScreen}
        options={({ route }) => ({
          title: route.params?.title || t('ticketDetail.title', 'Bilet'),
        })}
      />
      <Stack.Screen
        name="CreateTicket"
        component={CreateTicketScreen}
        options={{ title: t('createTicket.screenTitle', 'Yeni Bilet'), presentation: 'modal' }}
      />
      <Stack.Screen
        name="Notifications"
        component={NotificationsScreen}
        options={{ title: t('notifications.title', 'Bildirimler') }}
      />
    </Stack.Navigator>
  );
}
