import { useMemo, useState } from 'react';
import {
  View, Text, Pressable, Modal, TextInput, ScrollView, ActivityIndicator, StyleSheet,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import SheetBackdrop from './SheetBackdrop';
import {
  availableLangs, pickContent, fillPlaceholders, suitsCommentType,
} from '../utils/cannedResponses';

/**
 * "⚡ Hazır yanıtlar" bottom-sheet picker'ı — mobildeki birincil erişim yolu.
 * Arama, kapsam filtresi, favoriler, tr/en önizleme, EXTERNAL/INTERNAL farkındalığı
 * ve doldurulmuş önizleme. Seçince placeholder'lar dolu metin onInsert ile döner.
 */
export default function CannedResponseSheet({
  visible,
  onClose,
  templates = [],
  loading = false,
  commentType = 'EXTERNAL',
  ctx = {},
  previewLang = 'en',
  onPreviewLang,
  productId = null,
  onInsert,
  onToggleFavorite,
  onManage,
}) {
  const { theme } = useTheme();
  const { t } = useTranslation();
  const [query, setQuery] = useState('');
  const [scopeTab, setScopeTab] = useState('ALL');

  const tabs = [
    { key: 'ALL', label: t('cannedResponses.scopeAll', 'All') },
    { key: 'PERSONAL', label: t('cannedResponses.scopePersonal', 'Personal') },
    { key: 'TEAM', label: t('cannedResponses.scopeTeam', 'Team') },
    { key: 'PRODUCT', label: t('cannedResponses.scopeProduct', 'Product') },
  ];

  const ordered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const scopePred = (tpl) => {
      if (scopeTab === 'PERSONAL') return tpl.scope === 'PERSONAL';
      if (scopeTab === 'TEAM') return tpl.scope === 'SHARED' && !tpl.productId;
      if (scopeTab === 'PRODUCT') return tpl.scope === 'SHARED' && tpl.productId === productId;
      return true;
    };
    const searchPred = (tpl) => !q || [tpl.title, tpl.shortcut, tpl.contentTr, tpl.contentEn]
      .some((f) => f && f.toLowerCase().includes(q));
    const filtered = templates.filter(scopePred).filter(searchPred);
    const sorter = (a, b) => (suitsCommentType(a, commentType) ? 0 : 1) - (suitsCommentType(b, commentType) ? 0 : 1);
    const favs = filtered.filter((x) => x.favorite).sort(sorter);
    const rest = filtered.filter((x) => !x.favorite).sort(sorter);
    return { favs, rest };
  }, [templates, query, scopeTab, commentType, productId]);

  const renderItem = (tpl, isFav) => {
    const langs = availableLangs(tpl);
    const { content } = pickContent(tpl, previewLang);
    const preview = fillPlaceholders(content, ctx);
    const suited = suitsCommentType(tpl, commentType);
    return (
      <Pressable
        key={tpl.id}
        onPress={() => onInsert?.(tpl)}
        style={[styles.item, { borderColor: theme.border, opacity: suited ? 1 : 0.55 }]}
      >
        <Pressable onPress={() => onToggleFavorite?.(tpl)} hitSlop={8} style={styles.star}>
          <Ionicons
            name={tpl.favorite ? 'star' : 'star-outline'}
            size={18}
            color={tpl.favorite ? theme.warning : theme.textTertiary}
          />
        </Pressable>
        <View style={{ flex: 1 }}>
          <View style={styles.itemTop}>
            <Text style={[styles.itemTitle, { color: theme.textPrimary }]} numberOfLines={1}>{tpl.title}</Text>
            {tpl.shortcut ? <Text style={[styles.shortcut, { color: theme.textTertiary }]}>/{tpl.shortcut}</Text> : null}
            <Badge theme={theme} tone={tpl.scope === 'SHARED' ? 'blue' : 'slate'}>
              {tpl.scope === 'SHARED' ? t('cannedResponses.badgeShared', 'Team') : t('cannedResponses.badgePersonal', 'Personal')}
            </Badge>
            <Badge theme={theme} tone={tpl.visibility === 'INTERNAL' ? 'amber' : tpl.visibility === 'EXTERNAL' ? 'green' : 'slate'}>
              {tpl.visibility === 'INTERNAL' ? t('cannedResponses.visInternal', 'Internal')
                : tpl.visibility === 'EXTERNAL' ? t('cannedResponses.visExternal', 'External')
                  : t('cannedResponses.visBoth', 'Both')}
            </Badge>
            {langs.map((l) => <Badge key={l} theme={theme} tone="slate">{l.toUpperCase()}</Badge>)}
          </View>
          <Text style={[styles.preview, { color: theme.textSecondary }]} numberOfLines={2}>{preview}</Text>
        </View>
      </Pressable>
    );
  };

  const isEmpty = ordered.favs.length === 0 && ordered.rest.length === 0;

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <SheetBackdrop onClose={onClose}>
        <View style={[styles.sheet, { backgroundColor: theme.bgSurface }]}>
          {/* Header */}
          <View style={styles.header}>
            <View style={styles.headerLeft}>
              <Ionicons name="flash" size={18} color={theme.primary} />
              <Text style={[styles.title, { color: theme.textPrimary }]}>{t('cannedResponses.title', 'Canned Responses')}</Text>
            </View>
            <View style={styles.headerRight}>
              <View style={[styles.langToggle, { borderColor: theme.border }]}>
                {['tr', 'en'].map((l) => (
                  <Pressable
                    key={l}
                    onPress={() => onPreviewLang?.(l)}
                    style={[styles.langBtn, previewLang === l && { backgroundColor: theme.primary }]}
                  >
                    <Text style={{ color: previewLang === l ? theme.onPrimary : theme.textTertiary, fontSize: 11, fontWeight: '700' }}>
                      {l.toUpperCase()}
                    </Text>
                  </Pressable>
                ))}
              </View>
              <Pressable onPress={onClose} hitSlop={8}>
                <Ionicons name="close" size={22} color={theme.textTertiary} />
              </Pressable>
            </View>
          </View>

          {/* Search */}
          <TextInput
            value={query}
            onChangeText={setQuery}
            placeholder={t('cannedResponses.searchPlaceholder', 'Search templates…')}
            placeholderTextColor={theme.textTertiary}
            style={[styles.search, { backgroundColor: theme.bgInput, borderColor: theme.border, color: theme.textPrimary }]}
          />

          {/* Scope tabs */}
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.tabs}>
            {tabs.map((tab) => (
              <Pressable
                key={tab.key}
                onPress={() => setScopeTab(tab.key)}
                style={[
                  styles.tab,
                  { borderColor: theme.border },
                  scopeTab === tab.key && { backgroundColor: theme.primary, borderColor: theme.primary },
                ]}
              >
                <Text style={{ color: scopeTab === tab.key ? theme.onPrimary : theme.textSecondary, fontSize: 12, fontWeight: '600' }}>
                  {tab.label}
                </Text>
              </Pressable>
            ))}
          </ScrollView>

          {/* List */}
          <ScrollView style={{ maxHeight: 340 }} keyboardShouldPersistTaps="handled">
            {loading ? (
              <ActivityIndicator style={{ marginVertical: 28 }} color={theme.primary} />
            ) : isEmpty ? (
              <View style={{ paddingVertical: 24, alignItems: 'center', gap: 8 }}>
                <Text style={{ color: theme.textTertiary }}>
                  {query.trim() ? t('cannedResponses.noResults', 'No matches.') : t('cannedResponses.empty', 'No canned responses yet.')}
                </Text>
                {!query.trim() && (
                  <Pressable onPress={onManage}>
                    <Text style={{ color: theme.primary, fontWeight: '700' }}>{t('cannedResponses.createFirst', 'Create your first one')}</Text>
                  </Pressable>
                )}
              </View>
            ) : (
              <>
                {ordered.favs.length > 0 && (
                  <Text style={[styles.sectionLabel, { color: theme.textTertiary }]}>{t('cannedResponses.favorites', 'Favorites')}</Text>
                )}
                {ordered.favs.map((tpl) => renderItem(tpl, true))}
                {ordered.rest.map((tpl) => renderItem(tpl, false))}
              </>
            )}
          </ScrollView>

          {/* Footer */}
          <Pressable onPress={onManage} style={styles.manage}>
            <Ionicons name="settings-outline" size={15} color={theme.textSecondary} />
            <Text style={{ color: theme.textSecondary, fontSize: 13, fontWeight: '600' }}>
              {t('cannedResponses.manageLink', 'Manage canned responses')}
            </Text>
          </Pressable>
        </View>
      </SheetBackdrop>
    </Modal>
  );
}

function Badge({ tone = 'slate', theme, children }) {
  const tones = {
    slate: { bg: 'rgba(148,163,184,0.18)', fg: theme.textSecondary },
    blue: { bg: 'rgba(59,130,246,0.15)', fg: '#3b82f6' },
    amber: { bg: 'rgba(245,158,11,0.18)', fg: theme.warning },
    green: { bg: 'rgba(16,185,129,0.15)', fg: theme.success },
  };
  const c = tones[tone];
  return (
    <View style={[styles.badge, { backgroundColor: c.bg }]}>
      <Text style={{ color: c.fg, fontSize: 9, fontWeight: '700' }}>{children}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  sheet: { borderTopLeftRadius: 20, borderTopRightRadius: 20, padding: 16, gap: 10 },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  headerLeft: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  headerRight: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  title: { fontSize: 16, fontWeight: '700' },
  langToggle: { flexDirection: 'row', borderWidth: 1, borderRadius: 8, overflow: 'hidden' },
  langBtn: { paddingHorizontal: 8, paddingVertical: 3 },
  search: { borderWidth: 1, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 9, fontSize: 14 },
  tabs: { flexDirection: 'row', gap: 8, paddingVertical: 2 },
  tab: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 999, borderWidth: 1 },
  sectionLabel: { fontSize: 10, fontWeight: '700', textTransform: 'uppercase', letterSpacing: 0.5, paddingVertical: 6 },
  item: { flexDirection: 'row', gap: 8, borderWidth: 1, borderRadius: 10, padding: 10, marginBottom: 8 },
  star: { paddingTop: 1 },
  itemTop: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', gap: 6 },
  itemTitle: { fontSize: 14, fontWeight: '600', flexShrink: 1 },
  shortcut: { fontSize: 11, fontFamily: 'monospace' },
  preview: { fontSize: 12, marginTop: 2 },
  badge: { borderRadius: 999, paddingHorizontal: 6, paddingVertical: 1 },
  manage: { flexDirection: 'row', alignItems: 'center', gap: 6, paddingTop: 4 },
});
