import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { Pressable, Text } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import TicketListScreen from '../screens/TicketListScreen';
import TicketDetailScreen from '../screens/TicketDetailScreen';
import HomeScreen from '../screens/HomeScreen';

const Stack = createNativeStackNavigator();

/** Oturum açık kullanıcının ekran yığını. Rol bazlı sekmeler sonraki dilimlerde genişler. */
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
      <Stack.Screen
        name="TicketList"
        component={TicketListScreen}
        options={({ navigation }) => ({
          title: t('ticketList.title', 'Biletler'),
          headerRight: () => (
            <Pressable onPress={() => navigation.navigate('Account')} hitSlop={8}>
              <Text style={{ color: theme.primary, fontWeight: '600', fontSize: 15 }}>
                {t('account.title', 'Hesap')}
              </Text>
            </Pressable>
          ),
        })}
      />
      <Stack.Screen
        name="TicketDetail"
        component={TicketDetailScreen}
        options={({ route }) => ({
          title: route.params?.title || t('ticketDetail.title', 'Bilet'),
        })}
      />
      <Stack.Screen
        name="Account"
        component={HomeScreen}
        options={{ title: t('account.title', 'Hesap') }}
      />
    </Stack.Navigator>
  );
}
