import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
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
  Dimensions,
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
import { getAttachments, uploadAttachment } from '../api/attachments';
import { downloadAttachment } from '../utils/download';
import { formatDate, statusColor, priorityColor, statusLabel, priorityLabel } from '../utils/format';
import { REASON_CODES } from '../constants/reasonCodes';
import { useTicketWebSocket } from '../hooks/useTicketWebSocket';
import ReasonSheet from '../components/ReasonSheet';
import ChangeWithReasonSheet from '../components/ChangeWithReasonSheet';
import AssignSheet from '../components/AssignSheet';
import CsatSheet from '../components/CsatSheet';
import SlaBadge from '../components/SlaBadge';

const COMMENT_MAX = 500;
const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
// Sabit boyutlu sohbet paneli — cihaz yüksekliğine göre bir kez hesaplanır.
const CHAT_HEIGHT = Math.round(Dimensions.get('window').height * 0.42);

const AUDIT_KEYS = {
  CLAIM: 'auditClaimed',
  UNCLAIM: 'auditReleased',
  CLOSE: 'auditClosed',
  RESOLVE: 'auditResolved',
  REOPEN: 'auditReopened',
  CREATE: 'auditCreated',
  ASSIGN: 'auditAssigned',
  STATUS_CHANGE: 'auditStatusChange',
  PRIORITY_CHANGE: 'auditPriorityChange',
  TOPIC_CHANGE: 'auditTopicChange',
};

const humanize = (s) => String(s || '—').replace(/_/g, ' ');

/** Bilet detayı — bilgi/aksiyonlar + sabit boyutlu sohbet paneli + denetim geçmişi. */
export default function TicketDetailScreen({ route, navigation }) {
  const { id } = route.params;
  const { theme } = useTheme();
  const { t } = useTranslation();
  const { hasRole } = useAuth();
  const headerHeight = useHeaderHeight();
  const isAgent = hasRole('AGENT') || hasRole('AGENT_ADMIN');
  const isCustomer = hasRole('CUSTOMER');
  const canUseAttachments = isAgent || isCustomer;

  const [ticket, setTicket] = useState(null);
  const [comments, setComments] = useState([]);
  const [attachments, setAttachments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(null);

  const [message, setMessage] = useState('');
  const [commentType, setCommentType] = useState('EXTERNAL');
  const [sending, setSending] = useState(false);
  const [cooldown, setCooldown] = useState(0);

  const [actionBusy, setActionBusy] = useState(false);
  const [reasonMode, setReasonMode] = useState(null);
  const [changeMode, setChangeMode] = useState(null);
  const [topicOptions, setTopicOptions] = useState([]);
  const [assignOpen, setAssignOpen] = useState(false);
  const [agentOptions, setAgentOptions] = useState([]);
  const [csatOpen, setCsatOpen] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [downloadingId, setDownloadingId] = useState(null);
  const [auditOpen, setAuditOpen] = useState(false);

  const chatRef = useRef(null);
  const shouldScroll = useRef(true);

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
            // Ekler kritik değil.
          }
        }
        shouldScroll.current = true;
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

  // Gerçek zamanlı: yorum/ek/güncelleme WebSocket üzerinden anlık gelir.
  useTicketWebSocket(id, {
    includeInternal: isAgent,
    onComment: (c) => {
      setComments((prev) => (prev.some((x) => x.id === c.id) ? prev : [...prev, c]));
      shouldScroll.current = true;
    },
    onAttachment: (a) => {
      setAttachments((prev) => (prev.some((x) => x.id === a.id) ? prev : [...prev, a]));
      shouldScroll.current = true;
    },
    onTicketUpdated: () => {
      getTicket(id)
        .then((r) => setTicket(r.data))
        .catch(() => {});
    },
  });

  // Yorum + ekler tek bir kronolojik sohbet akışında birleşir.
  const timeline = useMemo(() => {
    const items = [
      ...comments.map((c) => ({ ...c, _kind: 'comment' })),
      ...attachments.map((a) => ({ ...a, _kind: 'attachment' })),
    ];
    items.sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
    return items;
  }, [comments, attachments]);

  const sendComment = async () => {
    if (!message.trim() || cooldown > 0 || sending) return;
    setSending(true);
    try {
      const res = await postComment(id, {
        message: message.trim(),
        type: isAgent ? commentType : 'EXTERNAL',
      });
      setComments((prev) => (prev.some((c) => c.id === res.data.id) ? prev : [...prev, res.data]));
      setMessage('');
      shouldScroll.current = true;
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
      fn = () => unclaimTicket(id, { reasonCode, note });
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
      const result = await DocumentPicker.getDocumentAsync({ type: '*/*', copyToCacheDirectory: true });
      if (result.canceled) return;
      const file = result.assets?.[0];
      if (!file) return;
      setUploading(true);
      const res = await uploadAttachment(id, file);
      setAttachments((prev) => (prev.some((a) => a.id === res.data.id) ? prev : [...prev, res.data]));
      shouldScroll.current = true;
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
  const auditLogs = ticket.auditLogs ?? [];

  /** Bir sohbet baloncuğu (yorum veya ek) — bakan kullanıcının tarafına göre hizalanır. */
  const renderBubble = (item) => {
    const isAttachment = item._kind === 'attachment';
    const authorId = isAttachment ? item.uploaderId : item.authorId;
    const authorRole = isAttachment ? null : item.authorRole;
    const isInternal = item.type === 'INTERNAL';
    const isCustomerAuthor = authorRole
      ? authorRole === 'CUSTOMER'
      : authorId === ticket.customerId;
    const isRight = isInternal ? true : isCustomer ? isCustomerAuthor : !isCustomerAuthor;
    const displayName =
      (!isAttachment && item.authorName) ||
      (isCustomerAuthor
        ? ticket.customerName || t('ticketDetail.roleCustomer', 'Müşteri')
        : t('ticketDetail.roleAgent', 'Temsilci'));

    let bg;
    let fg;
    let borderColor;
    if (isInternal) {
      bg = theme.dark ? 'rgba(245,158,11,0.14)' : '#fffbeb';
      fg = theme.textPrimary;
      borderColor = theme.warning;
    } else if (isRight) {
      bg = theme.primary;
      fg = '#ffffff';
      borderColor = theme.primary;
    } else {
      bg = theme.bgSurfaceSecondary;
      fg = theme.textPrimary;
      borderColor = theme.border;
    }
    const subColor = isRight && !isInternal ? 'rgba(255,255,255,0.7)' : theme.textTertiary;

    return (
      <View
        key={`${item._kind}-${item.id}`}
        style={[styles.bubbleWrap, { alignItems: isRight ? 'flex-end' : 'flex-start' }]}
      >
        <View style={[styles.bubble, { backgroundColor: bg, borderColor }]}>
          <View style={styles.bubbleHead}>
            <Text
              style={[
                styles.bubbleName,
                { color: isRight && !isInternal ? 'rgba(255,255,255,0.85)' : theme.textSecondary },
              ]}
              numberOfLines={1}
            >
              {displayName}
            </Text>
            {!isRight && !isInternal && (
              <View style={[styles.roleTag, { backgroundColor: theme.bgSurface }]}>
                <Text style={{ fontSize: 9, fontWeight: '700', color: theme.textSecondary }}>
                  {isCustomerAuthor
                    ? t('ticketDetail.roleCustomer', 'Müşteri')
                    : t('ticketDetail.roleAgent', 'Temsilci')}
                </Text>
              </View>
            )}
            {isInternal && (
              <View style={[styles.roleTag, { backgroundColor: `${theme.warning}33` }]}>
                <Text style={{ fontSize: 9, fontWeight: '700', color: theme.warning }}>
                  {t('ticketDetail.internal', 'Dahili')}
                </Text>
              </View>
            )}
          </View>

          {isAttachment ? (
            <Pressable
              onPress={() => doDownload(item)}
              disabled={downloadingId === item.id}
              style={styles.attachInBubble}
            >
              <Ionicons name="document-attach" size={20} color={fg} />
              <Text style={[styles.attachInName, { color: fg }]} numberOfLines={1}>
                {item.fileName}
              </Text>
              {downloadingId === item.id ? (
                <ActivityIndicator size="small" color={fg} />
              ) : (
                <Ionicons name="download-outline" size={18} color={fg} />
              )}
            </Pressable>
          ) : (
            <Text style={[styles.bubbleMsg, { color: fg }]}>{item.message}</Text>
          )}

          <Text style={[styles.bubbleDate, { color: subColor }]}>{formatDate(item.createdAt)}</Text>
        </View>
      </View>
    );
  };

  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: theme.bgBody }}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      keyboardVerticalOffset={headerHeight}
    >
      <ScrollView
        contentContainerStyle={styles.content}
        keyboardShouldPersistTaps="handled"
        keyboardDismissMode="on-drag"
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={() => load(true)} tintColor={theme.primary} />
        }
      >
        <View style={[styles.card, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
          <View style={styles.row}>
            <Text style={[styles.id, { color: theme.textTertiary }]}>#{ticket.id}</Text>
            <View style={styles.headerBadges}>
              <SlaBadge slaInfo={ticket.slaInfo} />
              <View style={[styles.badge, { backgroundColor: statusColor(status) }]}>
                <Text style={styles.badgeText}>{statusLabel(status, t)}</Text>
              </View>
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
          <Field label={t('ticketDetail.created', 'Oluşturulma')} theme={theme} value={formatDate(ticket.createdAt)} />
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
              <ActionBtn theme={theme} busy={actionBusy} onPress={() => setCsatOpen(true)}
                label={t('ticketDetail.yesResolved', 'Evet, çözüldü')} color={theme.success} />
              <ActionBtn theme={theme} busy={actionBusy} onPress={() => doStatus('IN_PROGRESS')}
                label={t('ticketDetail.noResolved', 'Hayır, yeniden aç')} color={theme.danger} />
            </View>
          </View>
        )}

        {/* Sabit boyutlu, kendi içinde kaydırılabilen sohbet paneli. */}
        <Text style={[styles.section, { color: theme.textPrimary }]}>
          {t('ticketDetail.comments', 'Sohbet')}
        </Text>
        <View
          style={[
            styles.chatBox,
            { height: CHAT_HEIGHT, backgroundColor: theme.bgSurface, borderColor: theme.border },
          ]}
        >
          <ScrollView
            ref={chatRef}
            contentContainerStyle={styles.chatContent}
            keyboardShouldPersistTaps="handled"
            nestedScrollEnabled
            onContentSizeChange={() => {
              if (shouldScroll.current) {
                chatRef.current?.scrollToEnd({ animated: false });
                shouldScroll.current = false;
              }
            }}
          >
            {timeline.length === 0 ? (
              <Text style={{ color: theme.textTertiary, textAlign: 'center', marginTop: 16 }}>
                {t('ticketDetail.noComments', 'Henüz mesaj yok.')}
              </Text>
            ) : (
              timeline.map(renderBubble)
            )}
          </ScrollView>
        </View>

        {/* Denetim geçmişi — sohbetin altında, aç/kapa. */}
        {auditLogs.length > 0 && (
          <View style={[styles.card, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
            <Pressable
              onPress={() => setAuditOpen((o) => !o)}
              style={styles.auditHeader}
            >
              <Text style={[styles.cardTitle, { color: theme.textPrimary }]}>
                {t('ticketDetail.auditHistory', 'Denetim Geçmişi')} ({auditLogs.length})
              </Text>
              <Ionicons
                name={auditOpen ? 'chevron-up' : 'chevron-down'}
                size={20}
                color={theme.textSecondary}
              />
            </Pressable>
            {auditOpen &&
              auditLogs.map((a, i) => (
                <View
                  key={a.id ?? i}
                  style={[
                    styles.auditRow,
                    { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: theme.border },
                  ]}
                >
                  <View style={styles.auditTop}>
                    <Text style={[styles.auditAction, { color: theme.textPrimary }]}>
                      {AUDIT_KEYS[a.actionType]
                        ? t(`ticketDetail.${AUDIT_KEYS[a.actionType]}`, humanize(a.actionType))
                        : humanize(a.actionType)}
                    </Text>
                    <Text style={[styles.auditDate, { color: theme.textTertiary }]}>
                      {formatDate(a.createdAt)}
                    </Text>
                  </View>
                  <Text style={[styles.auditMeta, { color: theme.textSecondary }]}>
                    {a.actorName || '—'}
                    {a.previousState && a.newState
                      ? ` · ${statusLabel(a.previousState, t)} → ${statusLabel(a.newState, t)}`
                      : ''}
                  </Text>
                  {!!a.reasonCode && (
                    <Text style={[styles.auditMeta, { color: theme.textTertiary }]}>
                      {humanize(a.reasonCode)}
                    </Text>
                  )}
                  {!!a.note && (
                    <Text style={[styles.auditNote, { color: theme.textSecondary }]}>{a.note}</Text>
                  )}
                </View>
              ))}
          </View>
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
            {canUseAttachments && (
              <Pressable
                onPress={pickAndUpload}
                disabled={uploading}
                style={[styles.iconBtn, { borderColor: theme.border }]}
              >
                {uploading ? (
                  <ActivityIndicator size="small" color={theme.primary} />
                ) : (
                  <Ionicons name="attach" size={22} color={theme.textSecondary} />
                )}
              </Pressable>
            )}
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
  content: { padding: 14, gap: 14, paddingBottom: 20 },
  card: { borderRadius: 12, borderWidth: 1, padding: 16, gap: 10 },
  worklogBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  cardTitle: { fontSize: 14, fontWeight: '700' },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  id: { fontSize: 12, fontWeight: '600' },
  title: { fontSize: 18, fontWeight: '700' },
  desc: { fontSize: 14, lineHeight: 20 },
  badge: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 999 },
  badgeText: { color: '#fff', fontSize: 11, fontWeight: '700' },
  headerBadges: { flexDirection: 'row', alignItems: 'center', gap: 6, flexShrink: 1 },
  field: { flexDirection: 'row', justifyContent: 'space-between', gap: 16 },
  fieldLabel: { fontSize: 13 },
  fieldValue: { fontSize: 13, fontWeight: '600', flexShrink: 1, textAlign: 'right' },
  actionRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  actionBtn: { paddingHorizontal: 14, paddingVertical: 9, borderRadius: 9 },
  actionBtnText: { color: '#fff', fontSize: 13, fontWeight: '700' },
  section: { fontSize: 16, fontWeight: '700', marginTop: 2, marginBottom: -4 },
  chatBox: { borderRadius: 12, borderWidth: 1, overflow: 'hidden' },
  chatContent: { padding: 12, gap: 8 },
  bubbleWrap: { width: '100%' },
  bubble: { maxWidth: '86%', borderRadius: 14, borderWidth: 1, paddingHorizontal: 12, paddingVertical: 9, gap: 3 },
  bubbleHead: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  bubbleName: { fontSize: 11, fontWeight: '700', flexShrink: 1 },
  roleTag: { paddingHorizontal: 6, paddingVertical: 1, borderRadius: 6 },
  bubbleMsg: { fontSize: 14, lineHeight: 19 },
  bubbleDate: { fontSize: 10, marginTop: 1 },
  attachInBubble: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingVertical: 2 },
  attachInName: { flex: 1, fontSize: 13, fontWeight: '600' },
  auditHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  auditRow: { paddingTop: 8, marginTop: 2, gap: 2 },
  auditTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8 },
  auditAction: { fontSize: 13, fontWeight: '700', textTransform: 'capitalize', flexShrink: 1 },
  auditDate: { fontSize: 11 },
  auditMeta: { fontSize: 12 },
  auditNote: { fontSize: 12, fontStyle: 'italic' },
  composer: { borderTopWidth: 1, paddingHorizontal: 12, paddingTop: 10, paddingBottom: 12, gap: 8 },
  typeRow: { flexDirection: 'row', gap: 8 },
  typeChip: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 999, borderWidth: 1 },
  inputRow: { flexDirection: 'row', gap: 8, alignItems: 'flex-end' },
  iconBtn: {
    width: 44,
    height: 44,
    borderWidth: 1,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
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
    minWidth: 64,
    height: 44,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 10,
  },
  counter: { fontSize: 11, textAlign: 'right' },
});
