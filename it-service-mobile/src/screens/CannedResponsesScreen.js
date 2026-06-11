import { useState, useEffect, useCallback, useMemo } from 'react';
import {
  View, Text, ScrollView, Pressable, TextInput, Modal, StyleSheet, ActivityIndicator, Alert,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme/ThemeContext';
import { useAuth } from '../auth/AuthContext';
import { getProducts } from '../api/products';
import {
  getCannedResponses, createCannedResponse, updateCannedResponse, deleteCannedResponse,
} from '../api/cannedResponses';
import PickerField from '../components/PickerField';
import SheetBackdrop from '../components/SheetBackdrop';
import { PLACEHOLDER_TOKENS, fillPlaceholders, availableLangs } from '../utils/cannedResponses';
import { localizedName, sortByLocalizedName } from '../utils/localizedName';

const EMPTY = { title: '', shortcut: '', scope: 'PERSONAL', productId: null, visibility: 'BOTH', contentTr: '', contentEn: '' };

/** Hazır Yanıtlar yönetimi — kişisel (herkes) ve paylaşılan (admin/manager) şablon CRUD'u. */
export default function CannedResponsesScreen() {
  const { theme } = useTheme();
  const { t, i18n } = useTranslation();
  const { user, isLeadAgent, isAdmin } = useAuth();
  // Paylaşılan (SHARED) şablonları yönetme yetkisi — lead agent veya admin (web ile aynı).
  const canManageShared = isLeadAgent || isAdmin;

  const [items, setItems] = useState([]);
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [scopeFilter, setScopeFilter] = useState('ALL');
  const [productFilter, setProductFilter] = useState('ALL');
  const [langFilter, setLangFilter] = useState('ALL');
  const [visibilityFilter, setVisibilityFilter] = useState('ALL');

  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(EMPTY);
  const [activeLang, setActiveLang] = useState('tr');
  const [sel, setSel] = useState({ start: 0, end: 0 });
  const [saving, setSaving] = useState(false);

  const sampleCtx = useMemo(() => {
    const tr = i18n.language?.startsWith('tr');
    let date = '';
    try { date = new Date().toLocaleDateString(tr ? 'tr-TR' : 'en-US', { year: 'numeric', month: 'long', day: 'numeric' }); }
    catch { date = new Date().toLocaleDateString(); }
    return {
      'musteri.ad': tr ? 'Ahmet Yılmaz' : 'John Doe',
      'agent.ad': user?.name || 'Agent',
      'bilet.no': 'TCK-001',
      urun: 'VPN',
      konu: tr ? 'Bağlantı sorunu' : 'Connection issue',
      tarih: date,
    };
  }, [i18n.language, user]);

  useEffect(() => {
    getProducts().then((res) => setProducts((res.data ?? []).filter((p) => p.isActive))).catch(() => {});
  }, []);

  const load = useCallback(() => {
    setLoading(true);
    getCannedResponses()
      .then((res) => setItems(Array.isArray(res.data) ? res.data : []))
      .catch(() => setItems([]))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  const canManageItem = (it) =>
    (it.scope === 'PERSONAL' && it.ownerAgentId === user?.id) || (it.scope === 'SHARED' && canManageShared);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return items.filter((it) => {
      if (scopeFilter !== 'ALL' && it.scope !== scopeFilter) return false;
      if (productFilter === 'GLOBAL' && it.productId) return false;
      if (productFilter !== 'ALL' && productFilter !== 'GLOBAL' && it.productId !== productFilter) return false;
      if (langFilter === 'tr' && !(it.contentTr && it.contentTr.trim())) return false;
      if (langFilter === 'en' && !(it.contentEn && it.contentEn.trim())) return false;
      if (visibilityFilter !== 'ALL' && it.visibility !== visibilityFilter) return false;
      if (!q) return true;
      return [it.title, it.shortcut, it.contentTr, it.contentEn].some((f) => f && f.toLowerCase().includes(q));
    });
  }, [items, search, scopeFilter, productFilter, langFilter, visibilityFilter]);

  const openCreate = () => {
    setEditing(null);
    setForm(EMPTY);
    setActiveLang(i18n.language?.startsWith('tr') ? 'tr' : 'en');
    setOpen(true);
  };

  const openEdit = (it) => {
    setEditing(it);
    setForm({
      title: it.title ?? '', shortcut: it.shortcut ?? '', scope: it.scope ?? 'PERSONAL',
      productId: it.productId ?? null, visibility: it.visibility ?? 'BOTH',
      contentTr: it.contentTr ?? '', contentEn: it.contentEn ?? '',
    });
    setActiveLang(it.contentTr ? 'tr' : (it.contentEn ? 'en' : 'tr'));
    setOpen(true);
  };

  const contentField = activeLang === 'tr' ? 'contentTr' : 'contentEn';

  const insertPlaceholder = (token) => {
    const snippet = `{{${token}}}`;
    const cur = form[contentField] || '';
    const start = Math.min(sel.start, cur.length);
    const end = Math.min(sel.end, cur.length);
    const next = cur.slice(0, start) + snippet + cur.slice(end);
    setForm((f) => ({ ...f, [contentField]: next }));
    const caret = start + snippet.length;
    setSel({ start: caret, end: caret });
  };

  const submit = async () => {
    const title = form.title.trim();
    const tr = form.contentTr.trim();
    const en = form.contentEn.trim();
    if (!title || (!tr && !en)) {
      Alert.alert(t('cannedResponses.errorRequired', 'Title and at least one language content are required.'));
      return;
    }
    const scope = canManageShared ? form.scope : 'PERSONAL';
    const body = {
      title,
      shortcut: form.shortcut.trim() || null,
      scope,
      productId: form.productId || null,
      visibility: form.visibility,
      contentTr: tr || null,
      contentEn: en || null,
    };
    setSaving(true);
    try {
      if (editing) await updateCannedResponse(editing.id, body);
      else await createCannedResponse(body);
      setOpen(false);
      load();
    } catch (e) {
      Alert.alert(e?.response?.data?.message || t('cannedResponses.errorSave', 'Save failed.'));
    } finally {
      setSaving(false);
    }
  };

  const onDelete = (it) => {
    Alert.alert(t('cannedResponses.delete', 'Delete'), it.title, [
      { text: t('common.cancel', 'Cancel'), style: 'cancel' },
      {
        text: t('cannedResponses.delete', 'Delete'),
        style: 'destructive',
        onPress: () => {
          setItems((prev) => prev.filter((x) => x.id !== it.id));
          deleteCannedResponse(it.id).catch(() => load());
        },
      },
    ]);
  };

  const scopeOptions = canManageShared
    ? [
      { label: t('cannedResponses.scopePersonalOption', 'Personal (only you)'), value: 'PERSONAL' },
      { label: t('cannedResponses.scopeSharedOption', 'Team (shared)'), value: 'SHARED' },
    ]
    : [{ label: t('cannedResponses.scopePersonalOption', 'Personal (only you)'), value: 'PERSONAL' }];

  const preview = fillPlaceholders(form[contentField] || '', sampleCtx);

  return (
    <View style={[styles.container, { backgroundColor: theme.bgBody }]}>
      <ScrollView contentContainerStyle={styles.content}>
        <TextInput
          value={search}
          onChangeText={setSearch}
          placeholder={t('cannedResponses.searchPlaceholder', 'Search templates…')}
          placeholderTextColor={theme.textTertiary}
          style={[styles.input, { backgroundColor: theme.bgInput, borderColor: theme.border, color: theme.textPrimary }]}
        />

        <View style={styles.filterRow}>
          {[
            { key: 'ALL', label: t('cannedResponses.scopeAll', 'All') },
            { key: 'PERSONAL', label: t('cannedResponses.scopePersonal', 'Personal') },
            { key: 'SHARED', label: t('cannedResponses.scopeTeam', 'Team') },
          ].map((f) => (
            <Pressable
              key={f.key}
              onPress={() => setScopeFilter(f.key)}
              style={[styles.filterChip, { borderColor: theme.border }, scopeFilter === f.key && { backgroundColor: theme.primary, borderColor: theme.primary }]}
            >
              <Text style={{ color: scopeFilter === f.key ? theme.onPrimary : theme.textSecondary, fontSize: 12, fontWeight: '600' }}>{f.label}</Text>
            </Pressable>
          ))}
        </View>

        <PickerField
          label={t('cannedResponses.filterProduct', 'Product')}
          placeholder={t('cannedResponses.allProducts', 'All products')}
          value={productFilter}
          onChange={setProductFilter}
          options={[
            { label: t('cannedResponses.allProducts', 'All products'), value: 'ALL' },
            { label: t('cannedResponses.productGlobal', 'Global (all products)'), value: 'GLOBAL' },
            ...sortByLocalizedName(products).map((p) => ({ label: localizedName(p), value: p.id })),
          ]}
        />

        <View style={styles.filterRow}>
          {[
            { key: 'ALL', label: t('cannedResponses.filterAll', 'All') },
            { key: 'tr', label: 'TR' },
            { key: 'en', label: 'EN' },
          ].map((f) => (
            <Pressable
              key={`lang-${f.key}`}
              onPress={() => setLangFilter(f.key)}
              style={[styles.filterChip, { borderColor: theme.border }, langFilter === f.key && { backgroundColor: theme.primary, borderColor: theme.primary }]}
            >
              <Text style={{ color: langFilter === f.key ? theme.onPrimary : theme.textSecondary, fontSize: 12, fontWeight: '600' }}>{f.label}</Text>
            </Pressable>
          ))}
        </View>

        <View style={styles.filterRow}>
          {[
            { key: 'ALL', label: t('cannedResponses.filterAll', 'All') },
            { key: 'EXTERNAL', label: t('cannedResponses.visExternal', 'External') },
            { key: 'INTERNAL', label: t('cannedResponses.visInternal', 'Internal') },
            { key: 'BOTH', label: t('cannedResponses.visBoth', 'Both') },
          ].map((f) => (
            <Pressable
              key={`vis-${f.key}`}
              onPress={() => setVisibilityFilter(f.key)}
              style={[styles.filterChip, { borderColor: theme.border }, visibilityFilter === f.key && { backgroundColor: theme.primary, borderColor: theme.primary }]}
            >
              <Text style={{ color: visibilityFilter === f.key ? theme.onPrimary : theme.textSecondary, fontSize: 12, fontWeight: '600' }}>{f.label}</Text>
            </Pressable>
          ))}
        </View>

        <Pressable
          onPress={openCreate}
          style={({ pressed }) => [styles.addBtn, { borderColor: theme.primary, opacity: pressed ? 0.6 : 1 }]}
        >
          <Text style={{ color: theme.primary, fontWeight: '700' }}>+ {t('cannedResponses.add', 'New template')}</Text>
        </Pressable>

        {loading ? (
          <ActivityIndicator style={{ marginTop: 28 }} size="large" color={theme.primary} />
        ) : filtered.length === 0 ? (
          <Text style={[styles.hint, { color: theme.textTertiary }]}>
            {search.trim() ? t('cannedResponses.noResults', 'No matches.') : t('cannedResponses.emptyManage', 'No canned responses yet.')}
          </Text>
        ) : (
          filtered.map((it) => (
            <View key={it.id} style={[styles.item, { backgroundColor: theme.bgSurface, borderColor: theme.border }]}>
              <View style={styles.itemHead}>
                <View style={{ flex: 1 }}>
                  <View style={styles.badges}>
                    <Text style={[styles.itemTitle, { color: theme.textPrimary }]}>{it.title}</Text>
                    {it.shortcut ? <Text style={[styles.shortcut, { color: theme.textTertiary }]}>/{it.shortcut}</Text> : null}
                  </View>
                  <View style={styles.badges}>
                    <Badge theme={theme} tone={it.scope === 'SHARED' ? 'blue' : 'slate'}>
                      {it.scope === 'SHARED' ? t('cannedResponses.badgeShared', 'Team') : t('cannedResponses.badgePersonal', 'Personal')}
                    </Badge>
                    <Badge theme={theme} tone={it.visibility === 'INTERNAL' ? 'amber' : it.visibility === 'EXTERNAL' ? 'green' : 'slate'}>
                      {it.visibility === 'INTERNAL' ? t('cannedResponses.visInternal', 'Internal')
                        : it.visibility === 'EXTERNAL' ? t('cannedResponses.visExternal', 'External')
                          : t('cannedResponses.visBoth', 'Both')}
                    </Badge>
                    {availableLangs(it).map((l) => <Badge key={l} theme={theme} tone="slate">{l.toUpperCase()}</Badge>)}
                  </View>
                </View>
                {canManageItem(it) && (
                  <View style={styles.actions}>
                    <Pressable onPress={() => openEdit(it)} hitSlop={6} style={[styles.iconBtn, { borderColor: theme.border }]}>
                      <Ionicons name="create-outline" size={16} color={theme.textSecondary} />
                    </Pressable>
                    <Pressable onPress={() => onDelete(it)} hitSlop={6} style={[styles.iconBtn, { backgroundColor: theme.danger, borderColor: theme.danger }]}>
                      <Ionicons name="trash-outline" size={16} color="#fff" />
                    </Pressable>
                  </View>
                )}
              </View>
              <Text style={[styles.preview, { color: theme.textSecondary }]} numberOfLines={3}>
                {fillPlaceholders(it.contentTr || it.contentEn || '', sampleCtx)}
              </Text>
            </View>
          ))
        )}
      </ScrollView>

      <Modal visible={open} transparent animationType="slide" onRequestClose={() => setOpen(false)}>
        <SheetBackdrop onClose={() => setOpen(false)}>
          <View style={[styles.sheet, { backgroundColor: theme.bgSurface }]}>
            <ScrollView keyboardShouldPersistTaps="handled" style={{ maxHeight: 520 }} contentContainerStyle={{ gap: 12 }}>
              <Text style={[styles.sheetTitle, { color: theme.textPrimary }]}>
                {editing ? t('cannedResponses.modalEditTitle', 'Edit Canned Response') : t('cannedResponses.modalNewTitle', 'New Canned Response')}
              </Text>

              <TextInput
                value={form.title}
                onChangeText={(v) => setForm((f) => ({ ...f, title: v }))}
                placeholder={t('cannedResponses.placeholderTitle', 'e.g. VPN connection steps')}
                placeholderTextColor={theme.textTertiary}
                maxLength={150}
                style={[styles.input, { backgroundColor: theme.bgInput, borderColor: theme.border, color: theme.textPrimary }]}
              />
              <TextInput
                value={form.shortcut}
                onChangeText={(v) => setForm((f) => ({ ...f, shortcut: v }))}
                placeholder={t('cannedResponses.placeholderShortcut', 'e.g. vpn')}
                placeholderTextColor={theme.textTertiary}
                maxLength={50}
                autoCapitalize="none"
                style={[styles.input, { backgroundColor: theme.bgInput, borderColor: theme.border, color: theme.textPrimary }]}
              />

              <PickerField
                label={t('cannedResponses.labelScope', 'Scope')}
                placeholder={t('cannedResponses.labelScope', 'Scope')}
                value={form.scope}
                onChange={(v) => setForm((f) => ({ ...f, scope: v }))}
                options={scopeOptions}
                disabled={!canManageShared}
              />
              {/* Product binding applies to both scopes (personal or shared). */}
              <PickerField
                label={t('cannedResponses.labelProduct', 'Product')}
                placeholder={t('cannedResponses.productGlobal', 'Global (all products)')}
                value={form.productId}
                onChange={(v) => setForm((f) => ({ ...f, productId: v }))}
                options={[
                  { label: t('cannedResponses.productGlobal', 'Global (all products)'), value: null },
                  ...sortByLocalizedName(products).map((p) => ({ label: localizedName(p), value: p.id })),
                ]}
              />

              <View>
                <Text style={[styles.fieldLabel, { color: theme.textPrimary }]}>{t('cannedResponses.labelVisibility', 'Visibility')}</Text>
                <View style={styles.filterRow}>
                  {['EXTERNAL', 'INTERNAL', 'BOTH'].map((v) => (
                    <Pressable
                      key={v}
                      onPress={() => setForm((f) => ({ ...f, visibility: v }))}
                      style={[styles.filterChip, { borderColor: theme.border }, form.visibility === v && { backgroundColor: theme.primary, borderColor: theme.primary }]}
                    >
                      <Text style={{ color: form.visibility === v ? theme.onPrimary : theme.textSecondary, fontSize: 12, fontWeight: '600' }}>
                        {v === 'EXTERNAL' ? t('cannedResponses.visExternal', 'External') : v === 'INTERNAL' ? t('cannedResponses.visInternal', 'Internal') : t('cannedResponses.visBoth', 'Both')}
                      </Text>
                    </Pressable>
                  ))}
                </View>
              </View>

              {/* Content with TR/EN tabs */}
              <View>
                <View style={styles.contentHead}>
                  <Text style={[styles.fieldLabel, { color: theme.textPrimary }]}>{t('cannedResponses.labelContent', 'Content')}</Text>
                  <View style={[styles.langToggle, { borderColor: theme.border }]}>
                    {['tr', 'en'].map((l) => (
                      <Pressable key={l} onPress={() => setActiveLang(l)} style={[styles.langBtn, activeLang === l && { backgroundColor: theme.primary }]}>
                        <Text style={{ color: activeLang === l ? theme.onPrimary : theme.textTertiary, fontSize: 11, fontWeight: '700' }}>{l.toUpperCase()}</Text>
                      </Pressable>
                    ))}
                  </View>
                </View>
                <TextInput
                  value={form[contentField]}
                  onChangeText={(v) => setForm((f) => ({ ...f, [contentField]: v }))}
                  onSelectionChange={(e) => setSel(e.nativeEvent.selection)}
                  placeholder={t('cannedResponses.placeholderContent', 'Hello {{musteri.ad}}, …')}
                  placeholderTextColor={theme.textTertiary}
                  multiline
                  maxLength={2000}
                  style={[styles.input, { minHeight: 110, textAlignVertical: 'top', backgroundColor: theme.bgInput, borderColor: theme.border, color: theme.textPrimary }]}
                />
                <View style={styles.tokens}>
                  {PLACEHOLDER_TOKENS.map((token) => (
                    <Pressable key={token} onPress={() => insertPlaceholder(token)} style={[styles.token, { borderColor: theme.border }]}>
                      <Text style={{ color: theme.textSecondary, fontSize: 11, fontFamily: 'monospace' }}>{`{{${token}}}`}</Text>
                    </Pressable>
                  ))}
                </View>
              </View>

              {/* Preview */}
              <View>
                <Text style={[styles.fieldLabel, { color: theme.textTertiary }]}>{t('cannedResponses.preview', 'Preview')}</Text>
                <View style={[styles.previewBox, { backgroundColor: theme.bgSurfaceSecondary, borderColor: theme.border }]}>
                  <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
                    {preview || t('cannedResponses.noContentLang', 'No content for this language.')}
                  </Text>
                </View>
              </View>

              <View style={styles.sheetActions}>
                <Pressable onPress={() => setOpen(false)} style={[styles.sheetBtn, { borderWidth: 1, borderColor: theme.border }]}>
                  <Text style={{ color: theme.textSecondary, fontWeight: '600' }}>{t('cannedResponses.cancel', 'Cancel')}</Text>
                </Pressable>
                <Pressable
                  onPress={submit}
                  disabled={saving}
                  style={[styles.sheetBtn, { backgroundColor: theme.primary, opacity: saving ? 0.5 : 1 }]}
                >
                  {saving ? <ActivityIndicator color={theme.onPrimary} size="small" />
                    : <Text style={{ color: theme.onPrimary, fontWeight: '700' }}>{t('cannedResponses.save', 'Save')}</Text>}
                </Pressable>
              </View>
            </ScrollView>
          </View>
        </SheetBackdrop>
      </Modal>
    </View>
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
  container: { flex: 1 },
  content: { padding: 16, gap: 12 },
  input: { borderWidth: 1, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 11, fontSize: 14 },
  filterRow: { flexDirection: 'row', gap: 8, flexWrap: 'wrap' },
  filterChip: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 999, borderWidth: 1 },
  addBtn: { borderWidth: 1, borderRadius: 10, paddingVertical: 11, alignItems: 'center' },
  hint: { textAlign: 'center', marginTop: 28, fontSize: 14 },
  item: { borderWidth: 1, borderRadius: 10, padding: 14, gap: 8 },
  itemHead: { flexDirection: 'row', gap: 8 },
  badges: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', gap: 6 },
  itemTitle: { fontSize: 14, fontWeight: '700' },
  shortcut: { fontSize: 11, fontFamily: 'monospace' },
  actions: { flexDirection: 'row', gap: 6 },
  iconBtn: { width: 30, height: 30, borderWidth: 1, borderRadius: 8, alignItems: 'center', justifyContent: 'center' },
  preview: { fontSize: 12, lineHeight: 18 },
  badge: { borderRadius: 999, paddingHorizontal: 6, paddingVertical: 1 },
  sheet: { borderTopLeftRadius: 20, borderTopRightRadius: 20, padding: 20 },
  sheetTitle: { fontSize: 17, fontWeight: '700' },
  fieldLabel: { fontSize: 14, fontWeight: '600', marginBottom: 6 },
  contentHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 },
  langToggle: { flexDirection: 'row', borderWidth: 1, borderRadius: 8, overflow: 'hidden' },
  langBtn: { paddingHorizontal: 10, paddingVertical: 4 },
  tokens: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginTop: 8 },
  token: { borderWidth: 1, borderRadius: 8, paddingHorizontal: 8, paddingVertical: 3 },
  previewBox: { borderWidth: 1, borderRadius: 10, padding: 10, minHeight: 44 },
  sheetActions: { flexDirection: 'row', gap: 10, marginTop: 4 },
  sheetBtn: { flex: 1, height: 46, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
});
