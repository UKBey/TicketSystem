import { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  ScrollView,
  TextInput,
  Pressable,
  StyleSheet,
  ActivityIndicator,
  RefreshControl,
  KeyboardAvoidingView,
  Platform,
  Alert,
} from 'react-native';
import { useHeaderHeight } from '@react-navigation/elements';
import { Ionicons } from '@expo/vector-icons';
import * as DocumentPicker from 'expo-document-picker';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import { useAuth } from '../auth/AuthContext';
import {
  getTicket,
  getComments,
  postComment,
  claimTicket,
  changeStatus,
  closeTicket,
  unclaimTicket,
  changePriority,
  changeTopic,
  assignTicket,
  submitCsat,
} from '../api/tickets';
import { getProductTopics } from '../api/products';
import { getAgentsWithCapacity } from '../api/users';
import {
  formatDate,
  statusColor,
  priorityColor,
  statusLabel,
  priorityLabel,
} from '../utils/format';
import { REASON_CODES } from '../constants/reasonCodes';
import { getAttachments, uploadAttachment } from '../api/attachments';
import { downloadAttachment } from '../utils/download';
import ReasonSheet from '../components/ReasonSheet';
import ChangeWithReasonSheet from '../components/ChangeWithReasonSheet';
import AssignSheet from '../components/AssignSheet';
import CsatSheet from '../components/CsatSheet';

const COMMENT_MAX = 500;
const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

/** Bilet detayı — alanlar, aksiyonlar, yorum listesi ve yorum gönderme. */
export default function TicketDetailScreen({ route, navigation }) {
  const { id } = route.params;
  const { theme } = useTheme();
  const { t } = useTranslation();
  const { hasRole } = useAuth();
  const headerHeight = useHeaderHeight();
  const isAgent = hasRole('AGENT') || hasRole('AGENT_ADMIN');
  const isCustomer = hasRole('CUSTOMER');
  // Ek dosya uçları yalnızca CUSTOMER/AGENT/AGENT_ADMIN'e açık — MANAGER hariç.
  const canUseAttachments = isAgent || isCustomer;

  const [ticket, setTicket] = useState(null);
  const [comments, setComments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(null);

  const [message, setMessage] = useState('');
  const [commentType, setCommentType] = useState('EXTERNAL');
  const [sending, setSending] = useState(false);
  const [cooldown, setCooldown] = useState(0);

  const [actionBusy, setActionBusy] = useState(false);
  const [reasonMode, setReasonMode] = useState(null); // 'RESOLVE' | 'CLOSE' | 'UNCLAIM' | null
  const [changeMode, setChangeMode] = useState(null); // 'PRIORITY' | 'TOPIC' | null
  const [topicOptions, setTopicOptions] = useState([]);
  const [assignOpen, setAssignOpen] = useState(false);
  const [agentOptions, setAgentOptions] = useState([]);
  const [csatOpen, setCsatOpen] = useState(false);
  const [attachments, setAttachments] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [downloadingId, setDownloadingId] = useState(null);

  const load = useCallback(
    async (isRefresh = false) => {
      if (isRefresh) setRefreshing(true);
      else setLoading(true);
      setError(null);
      try {
        const [tRes, cRes] = await Promise.all([getTicket(id), getComments(id)]);
        setTicket(tRes.data);
        setComments(cRes.data ?? []);
        if (canUseAttachments) {
          try {
            const aRes = await getAttachments(id);
            setAttachments(aRes.data ?? []);
          } catch {
            // Ekler kritik değil — bilet yine de gösterilir.
          }
        }
      } catch (e) {
        setError(t('ticketDetail.error', 'Bilet yüklenemedi.'));
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [id, t, canUseAttachments],
  );

  useEffect(() => {
    load();
  }, [load]);

  const sendComment = async () => {
    if (!message.trim() || cooldown > 0 || sending) return;
    setSending(true);
    try {
      const res = await postComment(id, {
        message: message.trim(),
        type: isAgent ? commentType : 'EXTERNAL',
      });
      setComments((prev) =>
        prev.some((c) => c.id === res.data.id) ? prev : [...prev, res.data],
      );
      setMessage('');
      setCooldown(5);
      const timer = setInterval(() => {
        setCooldown((p) => {
          if (p <= 1) {
            clearInterval(timer);
            return 0;
          }
          return p - 1;
        });
      }, 1000);
    } catch (e) {
      Alert.alert(t('ticketDetail.sendCommentFailed', 'Yorum gönderilemedi.'));
    } finally {
      setSending(false);
    }
  };

  const runAction = async (fn, failMsg) => {
    setActionBusy(true);
    try {
      const res = await fn();
      if (res?.data) setTicket(res.data);
    } catch (e) {
      Alert.alert(e?.response?.data?.message || failMsg);
    } finally {
      setActionBusy(false);
    }
  };

  const doClaim = () =>
    runAction(() => claimTicket(id), t('ticketDetail.claimFailed', 'Üstlenilemedi.'));
  const doStatus = (status) =>
    runAction(() => changeStatus(id, { status }), t('ticketDetail.statusFailed', 'Durum güncellenemedi.'));

  const onReasonConfirm = async ({ reasonCode, note }) => {
    let fn;
    if (reasonMode === 'RESOLVE') {
      fn = () => changeStatus(id, { status: 'RESOLVED', reasonCode, note });
    } else if (reasonMode === 'CLOSE') {
      fn = () => closeTicket(id, { reasonCode, note });
    } else {
      fn = () => unclaimTicket(id, { reasonCode, note }); // UNCLAIM
    }
    await runAction(fn, t('ticketDetail.actionFailed', 'İşlem başarısız.'));
    setReasonMode(null);
  };

  const openChange = async (mode) => {
    if (mode === 'TOPIC' && ticket?.productId) {
      try {
        const res = await getProductTopics(ticket.productId);
        setTopicOptions((res.data ?? []).map((tp) => ({ label: tp.name, value: tp.id })));
      } catch {
        setTopicOptions([]);
      }
    }
    setChangeMode(mode);
  };

  const onChangeConfirm = async ({ value, reasonCode, note }) => {
    const fn =
      changeMode === 'PRIORITY'
        ? () => changePriority(id, { priority: value, reasonCode, note })
        : () => changeTopic(id, { topicId: value, reasonCode, note });
    await runAction(fn, t('ticketDetail.actionFailed', 'İşlem başarısız.'));
    setChangeMode(null);
  };

  const openAssign = async () => {
    if (ticket?.productId) {
      try {
        const res = await getAgentsWithCapacity(ticket.productId);
        setAgentOptions(
          (res.data ?? []).map((a) => ({
            label: a.agentName ?? a.fullName ?? a.name ?? String(a.agentId ?? a.id),
            value: a.agentId ?? a.id,
          })),
        );
      } catch {
        setAgentOptions([]);
      }
    }
    setAssignOpen(true);
  };

  const onAssignConfirm = async ({ agentId, note }) => {
    setActionBusy(true);
    try {
      await assignTicket(id, { targetAgentId: agentId, note });
      setAssignOpen(false);
      await load();
    } catch (e) {
      Alert.alert(e?.response?.data?.message || t('ticketDetail.actionFailed', 'İşlem başarısız.'));
    } finally {
      setActionBusy(false);
    }
  };

  const onCsatConfirm = async ({ rating, comment }) => {
    setActionBusy(true);
    try {
      await submitCsat(id, { rating, comment });
      setCsatOpen(false);
      await load();
    } catch (e) {
      Alert.alert(e?.response?.data?.message || t('ticketDetail.submitCsatFailed', 'Anket gönderilemedi.'));
    } finally {
      setActionBusy(false);
    }
  };

  const pickAndUpload = async () => {
    try {
      const result = await DocumentPicker.getDocumentAsync({
        type: '*/*',
        copyToCacheDirectory: true,
      });
      if (result.canceled) return;
      const file = result.assets?.[0];
      if (!file) return;
      setUploading(true);
      const res = await uploadAttachment(id, file);
      setAttachments((prev) =>
        prev.some((a) => a.id === res.data.id) ? prev : [...prev, res.data],
      );
    } catch (e) {
      Alert.alert(e?.response?.data?.message || t('ticketDetail.uploadFileFailed', 'Dosya yüklenemedi.'));
    } finally {
      setUploading(false);
    }
  };

  const doDownload = async (att) => {
    setDownloadingId(att.id);
    try {
      await downloadAttachment(att);
    } catch (e) {
      Alert.alert(t('ticketDetail.downloadFileFailed', 'Dosya indirilemedi.'));
    } finally {
      setDownloadingId(null);
    }
  };

  if (loading) {
    return (
      <View style={[styles.full, { backgroundColor: theme.bgBody }]}>
        <ActivityIndicator size="large" color={theme.primary} />
      </View>
    );
  }
  if (error || !ticket) {
    return (
      <View style={[styles.full, { backgroundColor: theme.bgBody }]}>
        <Text style={{ color: theme.danger }}>{error || '—'}</Text>
      </View>
    );
  }

  const status = ticket.status;
  const canComment = status !== 'CLOSED';
  const claimed = (ticket.claimers?.length ?? 0) > 0;
  const showActions = isAgent && status !== 'CLOSED';

  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: theme.bgBody }}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      keyboardVerticalOffset={headerHeight}
    >
      <ScrollView
        contentContainerStyle={styles.content}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={() => load(true)} tintColor={theme.primary} />
        }
      >
        <View style={[styles.card, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
          <View style={styles.row}>
            <Text style={[styles.id, { color: theme.textTertiary }]}>#{ticket.id}</Text>
            <View style={[styles.badge, { backgroundColor: statusColor(status) }]}>
              <Text style={styles.badgeText}>{statusLabel(status, t)}</Text>
            </View>
          </View>
          <Text style={[styles.title, { color: theme.textPrimary }]}>{ticket.title}</Text>
          {!!ticket.description && (
            <Text style={[styles.desc, { color: theme.textSecondary }]}>{ticket.description}</Text>
          )}
        </View>

        <View style={[styles.card, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
          <Field label={t('ticketDetail.priority', 'Öncelik')} theme={theme}
            value={priorityLabel(ticket.priority, t)} valueColor={priorityColor(ticket.priority)} />
          <Field label={t('ticketDetail.product', 'Ürün')} theme={theme} value={ticket.productName} />
          <Field label={t('ticketDetail.topic', 'Konu')} theme={theme} value={ticket.topicName} />
          <Field label={t('ticketDetail.customer', 'Müşteri')} theme={theme} value={ticket.customerName} />
          <Field label={t('ticketDetail.created', 'Oluşturulma')} theme={theme}
            value={formatDate(ticket.createdAt)} />
        </View>

        {showActions && (
          <View style={[styles.card, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
            <Text style={[styles.cardTitle, { color: theme.textPrimary }]}>
              {t('ticketDetail.actions', 'İşlemler')}
            </Text>
            <View style={styles.actionRow}>
              {!claimed && (
                <ActionBtn theme={theme} busy={actionBusy} onPress={doClaim}
                  label={t('ticketDetail.claim', 'Üstlen')} />
              )}
              {claimed && (
                <ActionBtn theme={theme} busy={actionBusy} onPress={() => setReasonMode('UNCLAIM')}
                  label={t('ticketDetail.unclaim', 'Bırak')} color={theme.textSecondary} />
              )}
              {(status === 'NEW' || status === 'WAITING_FOR_CUSTOMER') && (
                <ActionBtn theme={theme} busy={actionBusy} onPress={() => doStatus('IN_PROGRESS')}
                  label={t('ticketDetail.takeInProgress', 'İşleme Al')} />
              )}
              {status === 'IN_PROGRESS' && (
                <ActionBtn theme={theme} busy={actionBusy} onPress={() => doStatus('WAITING_FOR_CUSTOMER')}
                  label={t('ticketDetail.waitCustomer', 'Müşteri Bekleniyor')} />
              )}
              {status !== 'RESOLVED' && (
                <ActionBtn theme={theme} busy={actionBusy} onPress={() => setReasonMode('RESOLVE')}
                  label={t('ticketDetail.resolve', 'Çöz')} color={theme.success} />
              )}
              {status === 'RESOLVED' && (
                <ActionBtn theme={theme} busy={actionBusy} onPress={() => doStatus('IN_PROGRESS')}
                  label={t('ticketDetail.reopen', 'Yeniden Aç')} />
              )}
              {status === 'RESOLVED' && (
                <ActionBtn theme={theme} busy={actionBusy} onPress={() => setReasonMode('CLOSE')}
                  label={t('ticketDetail.close', 'Kapat')} color={theme.textSecondary} />
              )}
              <ActionBtn theme={theme} busy={actionBusy} onPress={() => openChange('PRIORITY')}
                label={t('ticketDetail.changePriority', 'Öncelik')} color={theme.textSecondary} />
              <ActionBtn theme={theme} busy={actionBusy} onPress={() => openChange('TOPIC')}
                label={t('ticketDetail.changeTopic', 'Konu')} color={theme.textSecondary} />
              {hasRole('AGENT_ADMIN') && (
                <ActionBtn theme={theme} busy={actionBusy} onPress={openAssign}
                  label={t('ticketDetail.assign', 'Ata')} color={theme.textSecondary} />
              )}
            </View>
          </View>
        )}

        {isAgent && (
          <Pressable
            onPress={() => navigation.navigate('Worklog', { id })}
            style={({ pressed }) => [
              styles.card,
              styles.worklogBtn,
              { backgroundColor: theme.bgSurface, borderColor: theme.border, opacity: pressed ? 0.7 : 1 },
            ]}
          >
            <Text style={[styles.cardTitle, { color: theme.textPrimary }]}>
              {t('ticketDetail.worklog', 'Süre Kayıtları')}
            </Text>
            <Text style={{ color: theme.primary, fontSize: 20, fontWeight: '700' }}>›</Text>
          </Pressable>
        )}

        {canUseAttachments && (
          <View style={[styles.card, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
            <View style={styles.row}>
              <Text style={[styles.cardTitle, { color: theme.textPrimary }]}>
                {t('ticketDetail.attachments', 'Ekler')} ({attachments.length})
              </Text>
              {canComment && (
                <Pressable onPress={pickAndUpload} disabled={uploading} hitSlop={6}>
                  {uploading ? (
                    <ActivityIndicator size="small" color={theme.primary} />
                  ) : (
                    <Text style={{ color: theme.primary, fontWeight: '700', fontSize: 13 }}>
                      + {t('ticketDetail.addFile', 'Dosya Ekle')}
                    </Text>
                  )}
                </Pressable>
              )}
            </View>
            {attachments.length === 0 ? (
              <Text style={{ color: theme.textTertiary, fontSize: 13 }}>
                {t('ticketDetail.noAttachments', 'Henüz ek yok.')}
              </Text>
            ) : (
              attachments.map((a) => (
                <Pressable
                  key={a.id}
                  onPress={() => doDownload(a)}
                  disabled={downloadingId === a.id}
                  style={({ pressed }) => [
                    styles.attachRow,
                    { borderColor: theme.border, opacity: pressed ? 0.7 : 1 },
                  ]}
                >
                  <Ionicons name="document-attach-outline" size={22} color={theme.textSecondary} />
                  <View style={{ flex: 1 }}>
                    <Text style={[styles.attachName, { color: theme.textPrimary }]} numberOfLines={1}>
                      {a.fileName}
                    </Text>
                    <Text style={[styles.attachDate, { color: theme.textTertiary }]}>
                      {formatDate(a.createdAt)}
                    </Text>
                  </View>
                  {downloadingId === a.id ? (
                    <ActivityIndicator size="small" color={theme.primary} />
                  ) : (
                    <Ionicons name="download-outline" size={20} color={theme.primary} />
                  )}
                </Pressable>
              ))
            )}
          </View>
        )}

        {isCustomer && status === 'RESOLVED' && (
          <View style={[styles.card, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
            <Text style={[styles.cardTitle, { color: theme.textPrimary }]}>
              {t('ticketDetail.resolvedQuestion', 'Sorununuz çözüldü mü?')}
            </Text>
            <Text style={[styles.desc, { color: theme.textSecondary }]}>
              {t(
                'ticketDetail.resolvedDesc',
                'Bu bilet çözüldü olarak işaretlendi. Sorununuz giderildiyse onaylayıp kısa bir anket doldurun, aksi halde bileti yeniden açın.',
              )}
            </Text>
            <View style={styles.actionRow}>
              <ActionBtn
                theme={theme}
                busy={actionBusy}
                onPress={() => setCsatOpen(true)}
                label={t('ticketDetail.yesResolved', 'Evet, çözüldü')}
                color={theme.success}
              />
              <ActionBtn
                theme={theme}
                busy={actionBusy}
                onPress={() => doStatus('IN_PROGRESS')}
                label={t('ticketDetail.noResolved', 'Hayır, yeniden aç')}
                color={theme.danger}
              />
            </View>
          </View>
        )}

        <Text style={[styles.section, { color: theme.textPrimary }]}>
          {t('ticketDetail.comments', 'Yorumlar')} ({comments.length})
        </Text>

        {comments.length === 0 ? (
          <Text style={{ color: theme.textTertiary, textAlign: 'center', marginTop: 8 }}>
            {t('ticketDetail.noComments', 'Henüz yorum yok.')}
          </Text>
        ) : (
          comments.map((c) => (
            <View
              key={c.id}
              style={[styles.comment, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}
            >
              <View style={styles.row}>
                <Text style={[styles.cAuthor, { color: theme.textPrimary }]}>{c.authorName || '—'}</Text>
                <Text style={[styles.cDate, { color: theme.textTertiary }]}>{formatDate(c.createdAt)}</Text>
              </View>
              {c.type === 'INTERNAL' && (
                <Text style={[styles.internal, { color: theme.warning }]}>
                  {t('ticketDetail.internal', 'Dahili')}
                </Text>
              )}
              <Text style={[styles.cMsg, { color: theme.textSecondary }]}>{c.message}</Text>
            </View>
          ))
        )}
      </ScrollView>

      {canComment && (
        <View style={[styles.composer, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
          {isAgent && (
            <View style={styles.typeRow}>
              {[
                { key: 'EXTERNAL', label: t('ticketDetail.reply', 'Müşteriye yanıt') },
                { key: 'INTERNAL', label: t('ticketDetail.internalNote', 'Dahili not') },
              ].map((opt) => {
                const active = commentType === opt.key;
                return (
                  <Pressable
                    key={opt.key}
                    onPress={() => setCommentType(opt.key)}
                    style={[
                      styles.typeChip,
                      { borderColor: theme.border },
                      active && { backgroundColor: theme.primary, borderColor: theme.primary },
                    ]}
                  >
                    <Text style={{ color: active ? theme.onPrimary : theme.textSecondary, fontSize: 12, fontWeight: '600' }}>
                      {opt.label}
                    </Text>
                  </Pressable>
                );
              })}
            </View>
          )}
          <View style={styles.inputRow}>
            <TextInput
              value={message}
              onChangeText={setMessage}
              placeholder={t('ticketDetail.messagePlaceholder', 'Mesaj yaz...')}
              placeholderTextColor={theme.textTertiary}
              multiline
              maxLength={COMMENT_MAX}
              style={[styles.input, { backgroundColor: theme.bgInput, borderColor: theme.border, color: theme.textPrimary }]}
            />
            <Pressable
              onPress={sendComment}
              disabled={!message.trim() || cooldown > 0 || sending}
              style={({ pressed }) => [
                styles.sendBtn,
                {
                  backgroundColor: theme.primary,
                  opacity: !message.trim() || cooldown > 0 || sending || pressed ? 0.5 : 1,
                },
              ]}
            >
              {sending ? (
                <ActivityIndicator color={theme.onPrimary} size="small" />
              ) : (
                <Text style={{ color: theme.onPrimary, fontWeight: '700' }}>
                  {cooldown > 0 ? `${cooldown}s` : t('ticketDetail.send', 'Gönder')}
                </Text>
              )}
            </Pressable>
          </View>
          <Text style={[styles.counter, { color: message.length >= COMMENT_MAX ? theme.danger : theme.textTertiary }]}>
            {message.length}/{COMMENT_MAX}
          </Text>
        </View>
      )}

      <ReasonSheet
        visible={reasonMode !== null}
        title={
          reasonMode === 'RESOLVE'
            ? t('ticketDetail.resolveTitle', 'Bileti Çöz')
            : reasonMode === 'CLOSE'
              ? t('ticketDetail.closeTitle', 'Bileti Kapat')
              : t('ticketDetail.unclaimTitle', 'Bileti Bırak')
        }
        actionKey={reasonMode || 'RESOLVE'}
        codes={REASON_CODES[reasonMode] || []}
        busy={actionBusy}
        onCancel={() => setReasonMode(null)}
        onConfirm={onReasonConfirm}
      />

      <ChangeWithReasonSheet
        visible={changeMode !== null}
        title={
          changeMode === 'PRIORITY'
            ? t('ticketDetail.changePriorityTitle', 'Önceliği Değiştir')
            : t('ticketDetail.changeTopicTitle', 'Konuyu Değiştir')
        }
        valueLabel={
          changeMode === 'PRIORITY'
            ? t('ticketDetail.newPriority', 'Yeni öncelik')
            : t('ticketDetail.newTopic', 'Yeni konu')
        }
        valuePlaceholder={t('common.select', 'Seç')}
        valueOptions={
          changeMode === 'PRIORITY'
            ? PRIORITIES.map((p) => ({ label: priorityLabel(p, t), value: p }))
            : topicOptions
        }
        actionKey={changeMode === 'PRIORITY' ? 'PRIORITY_CHANGE' : 'TOPIC_CHANGE'}
        codes={changeMode === 'PRIORITY' ? REASON_CODES.PRIORITY_CHANGE : REASON_CODES.TOPIC_CHANGE}
        busy={actionBusy}
        onCancel={() => setChangeMode(null)}
        onConfirm={onChangeConfirm}
      />

      <AssignSheet
        visible={assignOpen}
        agents={agentOptions}
        busy={actionBusy}
        onCancel={() => setAssignOpen(false)}
        onConfirm={onAssignConfirm}
      />

      <CsatSheet
        visible={csatOpen}
        busy={actionBusy}
        onCancel={() => setCsatOpen(false)}
        onConfirm={onCsatConfirm}
      />
    </KeyboardAvoidingView>
  );
}

function Field({ label, value, theme, valueColor }) {
  return (
    <View style={styles.field}>
      <Text style={[styles.fieldLabel, { color: theme.textSecondary }]}>{label}</Text>
      <Text style={[styles.fieldValue, { color: valueColor || theme.textPrimary }]}>{value || '—'}</Text>
    </View>
  );
}

function ActionBtn({ label, onPress, busy, theme, color }) {
  return (
    <Pressable
      onPress={onPress}
      disabled={busy}
      style={({ pressed }) => [
        styles.actionBtn,
        { backgroundColor: color || theme.primary, opacity: busy || pressed ? 0.6 : 1 },
      ]}
    >
      <Text style={styles.actionBtnText}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  full: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  content: { padding: 14, gap: 14 },
  card: { borderRadius: 12, borderWidth: 1, padding: 16, gap: 10 },
  worklogBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  cardTitle: { fontSize: 14, fontWeight: '700' },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  id: { fontSize: 12, fontWeight: '600' },
  title: { fontSize: 18, fontWeight: '700' },
  desc: { fontSize: 14, lineHeight: 20 },
  badge: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 999 },
  badgeText: { color: '#fff', fontSize: 11, fontWeight: '700' },
  field: { flexDirection: 'row', justifyContent: 'space-between', gap: 16 },
  fieldLabel: { fontSize: 13 },
  fieldValue: { fontSize: 13, fontWeight: '600', flexShrink: 1, textAlign: 'right' },
  actionRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  actionBtn: { paddingHorizontal: 14, paddingVertical: 9, borderRadius: 9 },
  actionBtnText: { color: '#fff', fontSize: 13, fontWeight: '700' },
  section: { fontSize: 16, fontWeight: '700', marginTop: 4 },
  comment: { borderRadius: 10, borderWidth: 1, padding: 12, gap: 4 },
  cAuthor: { fontSize: 13, fontWeight: '700' },
  cDate: { fontSize: 11 },
  cMsg: { fontSize: 14, lineHeight: 19 },
  internal: { fontSize: 10, fontWeight: '700' },
  composer: { borderTopWidth: 1, paddingHorizontal: 12, paddingTop: 10, paddingBottom: 12, gap: 8 },
  typeRow: { flexDirection: 'row', gap: 8 },
  typeChip: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 999, borderWidth: 1 },
  inputRow: { flexDirection: 'row', gap: 8, alignItems: 'flex-end' },
  input: {
    flex: 1,
    minHeight: 44,
    maxHeight: 120,
    borderWidth: 1,
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingTop: 10,
    paddingBottom: 10,
    fontSize: 14,
  },
  sendBtn: {
    minWidth: 72,
    height: 44,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 12,
  },
  counter: { fontSize: 11, textAlign: 'right' },
  attachRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    borderWidth: 1,
    borderRadius: 10,
    padding: 10,
  },
  attachName: { fontSize: 14, fontWeight: '600' },
  attachDate: { fontSize: 11, marginTop: 2 },
});
