import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import MainTabs from './MainTabs';
import TicketDetailScreen from '../screens/TicketDetailScreen';
import CreateTicketScreen from '../screens/CreateTicketScreen';
import NotificationsScreen from '../screens/NotificationsScreen';
import KnownIssuesScreen from '../screens/KnownIssuesScreen';
import ProfileScreen from '../screens/ProfileScreen';
import ProductsScreen from '../screens/ProductsScreen';
import NotificationPreferencesScreen from '../screens/NotificationPreferencesScreen';
import WorklogScreen from '../screens/WorklogScreen';
import UserManagementScreen from '../screens/UserManagementScreen';
import AdminPanelScreen from '../screens/AdminPanelScreen';
import TicketListScreen from '../screens/TicketListScreen';

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
      <Stack.Screen
        name="KnownIssues"
        component={KnownIssuesScreen}
        options={{ title: t('knownIssues.title', 'Bilinen Sorunlar') }}
      />
      <Stack.Screen
        name="Profile"
        component={ProfileScreen}
        options={{ title: t('profile.title', 'Profil') }}
      />
      <Stack.Screen
        name="Products"
        component={ProductsScreen}
        options={{ title: t('products.title', 'Ürünler') }}
      />
      <Stack.Screen
        name="NotificationPreferences"
        component={NotificationPreferencesScreen}
        options={{ title: t('notificationPrefs.title', 'Bildirim Tercihleri') }}
      />
      <Stack.Screen
        name="Worklog"
        component={WorklogScreen}
        options={{ title: t('worklog.title', 'Süre Kayıtları') }}
      />
      <Stack.Screen
        name="UserManagement"
        component={UserManagementScreen}
        options={{ title: t('userManagement.title', 'Kullanıcı Yönetimi') }}
      />
      <Stack.Screen
        name="AdminPanel"
        component={AdminPanelScreen}
        options={{ title: t('admin.panel.title', 'Yönetim Paneli') }}
      />
      <Stack.Screen
        name="ProductTickets"
        component={TicketListScreen}
        options={({ route }) => ({
          title: route.params?.title || t('products.tickets', 'Ürün Biletleri'),
        })}
      />
    </Stack.Navigator>
  );
}
