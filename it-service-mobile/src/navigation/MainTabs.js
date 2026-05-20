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

/** Role göre alt sekme yapılandırması. Geçmiş sekmesi kapalı biletleri gösterir. */
function tabsForRole(role, t) {
  const menu = { name: 'Menu', title: t('tabs.menu', 'Menü'), icon: 'menu-outline', menu: true };
  // Team/Workspace status filtresi NEW ve CLOSED hariç.
  const agentStatus = ['IN_PROGRESS', 'WAITING_FOR_CUSTOMER', 'RESOLVED'];
  const poolFilters = ['priority', 'sla', 'product', 'topic', 'date'];
  const history = {
    name: 'History',
    title: t('tabs.history', 'Geçmiş'),
    icon: 'time-outline',
    status: 'CLOSED',
  };

  if (role === 'AGENT' || role === 'AGENT_ADMIN') {
    return [
      {
        name: 'Workspace',
        title: t('tabs.workspace', 'Çalışma'),
        icon: 'briefcase-outline',
        endpoint: '/tickets/my-assigned',
        filters: ['status', 'priority', 'sla', 'product', 'topic', 'date'],
        statusOptions: agentStatus,
      },
      {
        name: 'Pool',
        title: t('tabs.pool', 'Havuz'),
        icon: 'albums-outline',
        endpoint: '/tickets/pool',
        filters: poolFilters,
      },
      {
        name: 'Team',
        title: t('tabs.team', 'Takım'),
        icon: 'people-outline',
        endpoint: '/tickets/team',
        filters: ['status', 'priority', 'sla', 'product', 'topic', 'agent', 'date'],
        statusOptions: agentStatus,
      },
      {
        name: 'AllTickets',
        title: t('tabs.all', 'Tümü'),
        icon: 'documents-outline',
        endpoint: '/tickets/all',
        filters: ['status', 'priority', 'sla', 'product', 'topic', 'date'],
      },
      { ...history, endpoint: '/tickets/my-assigned', filters: poolFilters },
      menu,
    ];
  }
  if (role === 'MANAGER') {
    return [
      { name: 'Dashboard', title: t('tabs.dashboard', 'Panel'), icon: 'stats-chart-outline', dashboard: true },
      menu,
    ];
  }
  // CUSTOMER (ve varsayılan)
  return [
    {
      name: 'MyTickets',
      title: t('tabs.myTickets', 'Biletlerim'),
      icon: 'documents-outline',
      endpoint: '/tickets',
      canCreate: true,
      filters: ['status', 'priority', 'date'],
    },
    { ...history, endpoint: '/tickets', filters: ['priority', 'date'] },
    menu,
  ];
}

/** Rol bazlı alt sekme navigasyonu — her bilet sekmesi farklı endpoint kullanır. */
export default function MainTabs() {
  const { theme } = useTheme();
  const { t } = useTranslation();
  const { getPrimaryRole } = useAuth();
  const tabs = tabsForRole(getPrimaryRole(), t);

  return (
    <Tab.Navigator
      screenOptions={{
        headerStyle: { backgroundColor: theme.bgSurface },
        headerTintColor: theme.textPrimary,
        headerTitleStyle: { fontWeight: '700' },
        tabBarStyle: { backgroundColor: theme.bgSurface, borderTopColor: theme.border },
        tabBarActiveTintColor: theme.primary,
        tabBarInactiveTintColor: theme.textTertiary,
        tabBarLabelStyle: { fontSize: 10 },
      }}
    >
      {tabs.map((tab) => (
        <Tab.Screen
          key={tab.name}
          name={tab.name}
          component={tab.menu ? MenuScreen : tab.dashboard ? DashboardScreen : TicketListScreen}
          initialParams={
            tab.endpoint
              ? {
                  endpoint: tab.endpoint,
                  status: tab.status,
                  filters: tab.filters,
                  statusOptions: tab.statusOptions,
                }
              : undefined
          }
          options={({ navigation }) => ({
            title: tab.title,
            tabBarLabel: tab.title,
            tabBarIcon: ({ color, size }) => (
              <Ionicons name={tab.icon} color={color} size={size} />
            ),
            headerRight: tab.canCreate
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
