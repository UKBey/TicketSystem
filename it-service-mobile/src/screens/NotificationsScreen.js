import { useState, useEffect, useCallback, useLayoutEffect } from 'react';
import {
  View,
  Text,
  FlatList,
  Pressable,
  StyleSheet,
  ActivityIndicator,
  RefreshControl,
  Alert,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import {
  getNotifications,
  markAsRead,
  markAllAsRead,
  deleteNotification,
  deleteAllNotifications,
} from '../api/notifications';
import { formatDate } from '../utils/format';

/** Bildirim listesi — dokun: oku + bilete git, uzun bas: sil. */
export default function NotificationsScreen({ navigation }) {
  const { theme } = useTheme();
  const { t } = useTranslation();

  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async (isRefresh = false) => {
    if (isRefresh) setRefreshing(true);
    else setLoading(true);
    try {
      const res = await getNotifications(0, 50);
      setItems(res.data?.content ?? res.data ?? []);
    } catch (e) {
      // sessiz — boş liste gösterilir
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const markAll = useCallback(() => {
    setItems((prev) => prev.map((x) => ({ ...x, isRead: true })));
    markAllAsRead().catch(() => {});
  }, []);

  const deleteAll = useCallback(() => {
    Alert.alert(
      t('notifications.deleteAllTitle', 'Tüm bildirimleri sil'),
      t('notifications.deleteAllConfirm', 'Tüm bildirimler kalıcı olarak silinecek. Emin misiniz?'),
      [
        { text: t('common.cancel', 'İptal'), style: 'cancel' },
        {
          text: t('notifications.deleteAll', 'Tümünü Sil'),
          style: 'destructive',
          onPress: () => {
            setItems([]);
            deleteAllNotifications().catch(() => load(true));
          },
        },
      ],
    );
  }, [t, load]);

  useLayoutEffect(() => {
    navigation.setOptions({
      headerRight: () => (
        <View style={styles.headerActions}>
          <Pressable onPress={markAll} hitSlop={8}>
            <Text style={{ color: theme.primary, fontWeight: '600', fontSize: 13 }}>
              {t('notifications.markAll', 'Tümünü Oku')}
            </Text>
          </Pressable>
          <Pressable onPress={deleteAll} hitSlop={8}>
            <Ionicons name="trash-outline" size={20} color={theme.danger} />
          </Pressable>
        </View>
      ),
    });
  }, [navigation, markAll, deleteAll, theme.primary, theme.danger, t]);

  const onPress = (n) => {
    if (!n.isRead) {
      setItems((prev) => prev.map((x) => (x.id === n.id ? { ...x, isRead: true } : x)));
      markAsRead(n.id).catch(() => {});
    }
    if (n.ticketId) {
      navigation.navigate('TicketDetail', { id: n.ticketId });
    }
  };

  const onLongPress = (n) => {
    Alert.alert(
      t('notifications.deleteTitle', 'Bildirimi sil'),
      n.title || n.message || '',
      [
        { text: t('common.cancel', 'İptal'), style: 'cancel' },
        {
          text: t('common.delete', 'Sil'),
          style: 'destructive',
          onPress: () => {
            setItems((prev) => prev.filter((x) => x.id !== n.id));
            deleteNotification(n.id).catch(() => {});
          },
        },
      ],
    );
  };

  const renderItem = ({ item }) => (
    <Pressable
      onPress={() => onPress(item)}
      onLongPress={() => onLongPress(item)}
      style={({ pressed }) => [
        styles.item,
        {
          backgroundColor: item.isRead ? theme.bgSurface : theme.bgSurfaceSecondary,
          borderColor: theme.border,
          opacity: pressed ? 0.7 : 1,
        },
      ]}
    >
      {!item.isRead && <View style={[styles.dot, { backgroundColor: theme.primary }]} />}
      <View style={{ flex: 1, gap: 3 }}>
        {!!item.title && (
          <Text style={[styles.title, { color: theme.textPrimary }]}>{item.title}</Text>
        )}
        <Text style={[styles.message, { color: theme.textSecondary }]}>{item.message}</Text>
        <Text style={[styles.date, { color: theme.textTertiary }]}>
          {formatDate(item.createdAt)}
        </Text>
      </View>
    </Pressable>
  );

  return (
    <View style={[styles.container, { backgroundColor: theme.bgBody }]}>
      {loading ? (
        <ActivityIndicator style={styles.center} size="large" color={theme.primary} />
      ) : (
        <FlatList
          data={items}
          keyExtractor={(x) => String(x.id)}
          renderItem={renderItem}
          contentContainerStyle={styles.list}
          refreshControl={
            <RefreshControl refreshing={refreshing} onRefresh={() => load(true)} tintColor={theme.primary} />
          }
          ListEmptyComponent={
            <Text style={[styles.center, { color: theme.textTertiary }]}>
              {t('notifications.empty', 'Bildirim yok.')}
            </Text>
          }
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  headerActions: { flexDirection: 'row', alignItems: 'center', gap: 16, paddingRight: 12 },
  list: { padding: 12, gap: 8, flexGrow: 1 },
  item: {
    flexDirection: 'row',
    gap: 10,
    borderRadius: 10,
    borderWidth: 1,
    padding: 13,
    alignItems: 'flex-start',
  },
  dot: { width: 8, height: 8, borderRadius: 4, marginTop: 6 },
  title: { fontSize: 14, fontWeight: '700' },
  message: { fontSize: 13, lineHeight: 18 },
  date: { fontSize: 11, marginTop: 2 },
  center: { marginTop: 48, textAlign: 'center', alignSelf: 'center' },
});
