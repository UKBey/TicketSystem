import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { Pressable, Text } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import { useAuth } from '../auth/AuthContext';
import TicketListScreen from '../screens/TicketListScreen';
import MenuScreen from '../screens/MenuScreen';
import DashboardScreen from '../screens/DashboardScreen';

const Tab = createBottomTabNavigator();

/** Role göre alt sekme yapılandırması. */
function tabsForRole(role) {
  const menu = { name: 'Menu', title: 'Menü', icon: 'menu-outline', menu: true };

  if (role === 'AGENT' || role === 'AGENT_ADMIN') {
    return [
      { name: 'Workspace', title: 'Çalışma Alanım', icon: 'briefcase-outline', endpoint: '/tickets/my-assigned' },
      { name: 'Pool', title: 'Havuz', icon: 'albums-outline', endpoint: '/tickets/pool' },
      { name: 'Team', title: 'Takım', icon: 'people-outline', endpoint: '/tickets/team' },
      { name: 'AllTickets', title: 'Tüm Biletler', icon: 'documents-outline', endpoint: '/tickets/all' },
      menu,
    ];
  }
  if (role === 'MANAGER') {
    return [
      { name: 'Dashboard', title: 'Panel', icon: 'stats-chart-outline', dashboard: true },
      { name: 'AllTickets', title: 'Tüm Biletler', icon: 'documents-outline', endpoint: '/tickets/all' },
      menu,
    ];
  }
  // CUSTOMER (ve varsayılan)
  return [
    { name: 'MyTickets', title: 'Biletlerim', icon: 'documents-outline', endpoint: '/tickets' },
    menu,
  ];
}

/** Rol bazlı alt sekme navigasyonu — her bilet sekmesi farklı endpoint kullanır. */
export default function MainTabs() {
  const { theme } = useTheme();
  const { t } = useTranslation();
  const { getPrimaryRole } = useAuth();
  const tabs = tabsForRole(getPrimaryRole());

  return (
    <Tab.Navigator
      screenOptions={{
        headerStyle: { backgroundColor: theme.bgSurface },
        headerTintColor: theme.textPrimary,
        headerTitleStyle: { fontWeight: '700' },
        tabBarStyle: { backgroundColor: theme.bgSurface, borderTopColor: theme.border },
        tabBarActiveTintColor: theme.primary,
        tabBarInactiveTintColor: theme.textTertiary,
      }}
    >
      {tabs.map((tab) => (
        <Tab.Screen
          key={tab.name}
          name={tab.name}
          component={tab.menu ? MenuScreen : tab.dashboard ? DashboardScreen : TicketListScreen}
          initialParams={tab.endpoint ? { endpoint: tab.endpoint } : undefined}
          options={({ navigation }) => ({
            title: tab.title,
            tabBarLabel: tab.title,
            tabBarIcon: ({ color, size }) => (
              <Ionicons name={tab.icon} color={color} size={size} />
            ),
            headerRight: tab.endpoint
              ? () => (
                  <Pressable
                    onPress={() => navigation.navigate('CreateTicket')}
                    hitSlop={8}
                    style={{ paddingRight: 14 }}
                  >
                    <Text style={{ color: theme.primary, fontWeight: '700', fontSize: 15 }}>
                      + {t('createTicket.new', 'Yeni')}
                    </Text>
                  </Pressable>
                )
              : undefined,
          })}
        />
      ))}
    </Tab.Navigator>
  );
}
