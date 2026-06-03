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

/**
 * Alt sekmeler = kullanıcının rollerinin verdiği yetkilerin BİRLEŞİMİ (web Sidebar ile aynı).
 * caps: { isCustomer, isAgent, isManager, isLeadAgent, isAdmin, isStaff }.
 * - Operasyonel sekmeler (Çalışma/Havuz/Takım/Geçmiş): isAgent (agent + lead).
 * - Tümü: isStaff (personel + yönetici/admin).
 * - Panel: isManager || isLeadAgent || isAdmin.
 * - Biletlerim: isCustomer.
 * Lead + admin olan biri hem operasyonel hem yönetim sekmelerini görür.
 */
function tabsForCaps(caps, t) {
  const { isCustomer, isAgent, isManager, isLeadAgent, isAdmin, isStaff } = caps;
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

  const tabs = [];

  // Müşteri biletleri — "Biletlerim" CLOSED hariç tüm statüleri kapsar.
  if (isCustomer) {
    tabs.push({
      name: 'MyTickets',
      title: t('tabs.myTickets', 'Biletlerim'),
      icon: 'documents-outline',
      endpoint: '/tickets',
      canCreate: true,
      filters: ['status', 'priority', 'date'],
      statusOptions: ['NEW', 'IN_PROGRESS', 'WAITING_FOR_CUSTOMER', 'RESOLVED'],
    });
  }

  // Operasyonel — agent + lead agent.
  if (isAgent) {
    tabs.push(
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
    );
  }

  // Tüm biletler — personel (agent/lead) + yönetici + admin.
  if (isStaff) {
    tabs.push({
      name: 'AllTickets',
      title: t('tabs.all', 'Tümü'),
      icon: 'documents-outline',
      endpoint: '/tickets/all',
      filters: ['status', 'priority', 'sla', 'product', 'topic', 'date'],
    });
  }

  // Panel — yönetici / lead / admin.
  if (isManager || isLeadAgent || isAdmin) {
    tabs.push({
      name: 'Dashboard',
      title: t('tabs.dashboard', 'Panel'),
      icon: 'stats-chart-outline',
      dashboard: true,
    });
  }

  // Geçmiş — operasyonel kullanıcı kendi atanmış kapalı biletlerini, müşteri kendi
  // kapalı biletlerini görür. (isStaff ama agent olmayan yönetici/admin Tümü→CLOSED ile filtreler.)
  if (isAgent) {
    tabs.push({ ...history, endpoint: '/tickets/my-assigned', filters: poolFilters });
  } else if (isCustomer) {
    tabs.push({ ...history, endpoint: '/tickets', filters: ['priority', 'date'] });
  }

  tabs.push(menu);
  return tabs;
}

/** Rol bazlı alt sekme navigasyonu — her bilet sekmesi farklı endpoint kullanır. */
export default function MainTabs() {
  const { theme } = useTheme();
  const { t } = useTranslation();
  const { isCustomer, isAgent, isManager, isLeadAgent, isAdmin, isStaff } = useAuth();
  const tabs = tabsForCaps({ isCustomer, isAgent, isManager, isLeadAgent, isAdmin, isStaff }, t);

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
