import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Search, Star, X, Zap, AlertTriangle, Settings } from 'lucide-react';
import {
  availableLangs,
  pickContent,
  fillPlaceholders,
  suitsCommentType,
} from '../../utils/cannedResponses';

/**
 * "⚡ Hazır yanıtlar" picker popover. Discoverable, mobile-friendly access path to canned
 * responses; shares the same data source and selection behavior as the slash autocomplete.
 *
 * Features: live search, scope tabs (Personal/Team/Product), favorites section, tr/en preview
 * toggle, filled preview, EXTERNAL/INTERNAL awareness (suited templates float up; mismatched
 * ones are visually de-emphasized), keyboard navigation and ARIA listbox semantics.
 */
export default function CannedResponsePicker({
  open,
  onClose,
  templates = [],
  loading = false,
  error = false,
  ctx = {},
  previewLang = 'en',
  onPreviewLangChange,
  commentType = 'EXTERNAL',
  productId = null,
  recentIds = [],
  onInsert,
  onToggleFavorite,
  onManage,
  canManage = true,
  anchorRef = null,
  triggerRef = null,
}) {
  const { t } = useTranslation();
  const [query, setQuery] = useState('');
  const [scopeTab, setScopeTab] = useState('ALL');
  const [activeIndex, setActiveIndex] = useState(0);
  const containerRef = useRef(null);
  const searchRef = useRef(null);
  const listRef = useRef(null);

  // Focus search on open; reset transient state.
  useEffect(() => {
    if (open) {
      setQuery('');
      setScopeTab('ALL');
      setActiveIndex(0);
      // defer focus until the popover is in the DOM
      requestAnimationFrame(() => searchRef.current?.focus());
    }
  }, [open]);

  // Close on outside click / Escape.
  useEffect(() => {
    if (!open) return undefined;
    const onDocMouseDown = (e) => {
      const inPanel = containerRef.current?.contains(e.target);
      const inTrigger = triggerRef?.current?.contains(e.target);
      if (!inPanel && !inTrigger) onClose?.();
    };
    document.addEventListener('mousedown', onDocMouseDown);
    return () => document.removeEventListener('mousedown', onDocMouseDown);
  }, [open, onClose, triggerRef]);

  const scopePredicate = (tpl) => {
    if (scopeTab === 'PERSONAL') return tpl.scope === 'PERSONAL';
    if (scopeTab === 'TEAM') return tpl.scope === 'SHARED' && !tpl.productId;
    if (scopeTab === 'PRODUCT') return tpl.scope === 'SHARED' && tpl.productId === productId;
    return true;
  };

  const searchPredicate = (tpl) => {
    const q = query.trim().toLowerCase();
    if (!q) return true;
    return [tpl.title, tpl.shortcut, tpl.contentTr, tpl.contentEn]
      .some((f) => f && f.toLowerCase().includes(q));
  };

  // Ordered list: suited-for-current-mode first within each group; favorites grouped on top.
  const { ordered, favCount } = useMemo(() => {
    const filtered = templates.filter(scopePredicate).filter(searchPredicate);
    const recentRank = (id) => {
      const i = recentIds.indexOf(id);
      return i === -1 ? Number.MAX_SAFE_INTEGER : i;
    };
    const sorter = (a, b) => {
      const sa = suitsCommentType(a, commentType) ? 0 : 1;
      const sb = suitsCommentType(b, commentType) ? 0 : 1;
      if (sa !== sb) return sa - sb;
      const ra = recentRank(a.id);
      const rb = recentRank(b.id);
      if (ra !== rb) return ra - rb;
      return 0;
    };
    const favs = filtered.filter((t2) => t2.favorite).sort(sorter);
    const rest = filtered.filter((t2) => !t2.favorite).sort(sorter);
    return { ordered: [...favs, ...rest], favCount: favs.length };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [templates, query, scopeTab, commentType, productId, recentIds]);

  useEffect(() => {
    setActiveIndex((i) => Math.min(i, Math.max(0, ordered.length - 1)));
  }, [ordered.length]);

  const select = (tpl) => {
    if (tpl) onInsert?.(tpl);
  };

  const handleKeyDown = (e) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActiveIndex((i) => (ordered.length ? (i + 1) % ordered.length : 0));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActiveIndex((i) => (ordered.length ? (i - 1 + ordered.length) % ordered.length : 0));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      select(ordered[activeIndex]);
    } else if (e.key === 'Escape') {
      e.preventDefault();
      onClose?.();
    }
  };

  // Keep the active option scrolled into view.
  useEffect(() => {
    const el = listRef.current?.querySelector(`[data-idx="${activeIndex}"]`);
    el?.scrollIntoView({ block: 'nearest' });
  }, [activeIndex]);

  if (!open) return null;

  const tabs = [
    { key: 'ALL', label: t('cannedResponses.scopeAll') },
    { key: 'PERSONAL', label: t('cannedResponses.scopePersonal') },
    { key: 'TEAM', label: t('cannedResponses.scopeTeam') },
    { key: 'PRODUCT', label: t('cannedResponses.scopeProduct') },
  ];

  const renderOption = (tpl, idx) => {
    const langs = availableLangs(tpl);
    const { content } = pickContent(tpl, previewLang);
    const preview = fillPlaceholders(content, ctx);
    const suited = suitsCommentType(tpl, commentType);
    const active = idx === activeIndex;
    const mismatchTitle = tpl.visibility === 'EXTERNAL'
      ? t('cannedResponses.mismatchExternal')
      : t('cannedResponses.mismatchInternal');

    return (
      <li
        key={tpl.id}
        id={`canned-opt-${tpl.id}`}
        data-idx={idx}
        role="option"
        aria-selected={active}
        onMouseEnter={() => setActiveIndex(idx)}
        onClick={() => select(tpl)}
        className="flex cursor-pointer items-start gap-2 rounded-lg px-3 py-2 transition-colors"
        style={{
          backgroundColor: active ? 'var(--bg-surface-secondary)' : 'transparent',
          opacity: suited ? 1 : 0.55,
        }}
      >
        <button
          type="button"
          onClick={(e) => { e.stopPropagation(); onToggleFavorite?.(tpl); }}
          className="mt-0.5 flex-shrink-0 cursor-pointer"
          aria-label={t('cannedResponses.toggleFavorite')}
          aria-pressed={!!tpl.favorite}
          title={t('cannedResponses.toggleFavorite')}
        >
          <Star
            className="h-4 w-4"
            style={{ color: tpl.favorite ? '#f59e0b' : 'var(--text-tertiary)' }}
            fill={tpl.favorite ? '#f59e0b' : 'none'}
          />
        </button>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-1.5 flex-wrap">
            <span className="text-sm font-semibold break-words min-w-0" style={{ color: 'var(--text-primary)' }}>
              {tpl.title}
            </span>
            {tpl.shortcut && (
              <span className="text-[11px] font-mono" style={{ color: 'var(--text-tertiary)' }}>/{tpl.shortcut}</span>
            )}
            <ScopeBadge scope={tpl.scope} t={t} />
            <VisibilityBadge visibility={tpl.visibility} t={t} />
            {langs.map((l) => (
              <span key={l} className="inline-flex items-center rounded px-1 py-0.5 text-[9px] font-bold uppercase"
                style={{ backgroundColor: 'var(--bg-surface-secondary)', color: 'var(--text-tertiary)' }}>
                {l}
              </span>
            ))}
            {!suited && (
              <span title={mismatchTitle} className="inline-flex items-center">
                <AlertTriangle className="h-3 w-3" style={{ color: '#f59e0b' }} aria-label={mismatchTitle} />
              </span>
            )}
          </div>
          <p className="mt-0.5 text-xs line-clamp-2 break-words" style={{ color: 'var(--text-secondary)' }}>
            {preview}
          </p>
        </div>
      </li>
    );
  };

  return (
    <div
      ref={(node) => { containerRef.current = node; if (anchorRef) anchorRef(node); }}
      className="z-50 w-[min(92vw,28rem)] rounded-xl border animate-fade-in"
      style={{ position: 'fixed', backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-xl)' }}
      role="dialog"
      aria-label={t('cannedResponses.title')}
    >
      {/* Header */}
      <div className="flex items-center justify-between gap-2 border-b px-3 py-2.5" style={{ borderColor: 'var(--border-color)' }}>
        <div className="flex items-center gap-1.5 text-sm font-bold" style={{ color: 'var(--text-primary)' }}>
          <Zap className="h-4 w-4 text-primary-500" />
          {t('cannedResponses.title')}
        </div>
        <div className="flex items-center gap-1">
          {/* tr/en preview language toggle */}
          <div className="flex overflow-hidden rounded-md border" style={{ borderColor: 'var(--border-color)' }}>
            {['tr', 'en'].map((l) => (
              <button
                key={l}
                type="button"
                onClick={() => onPreviewLangChange?.(l)}
                className="px-1.5 py-0.5 text-[10px] font-bold uppercase transition-colors cursor-pointer"
                style={previewLang === l
                  ? { backgroundColor: 'var(--color-primary-500, #3b82f6)', color: '#fff' }
                  : { color: 'var(--text-tertiary)', backgroundColor: 'transparent' }}
                aria-pressed={previewLang === l}
              >
                {l}
              </button>
            ))}
          </div>
          <button
            type="button"
            onClick={onClose}
            className="flex h-7 w-7 items-center justify-center rounded-md transition-colors cursor-pointer"
            style={{ color: 'var(--text-tertiary)' }}
            aria-label={t('cannedResponses.close')}
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      </div>

      {/* Search */}
      <div className="px-3 pt-2.5">
        <div className="relative">
          <Search className="absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2" style={{ color: 'var(--text-tertiary)' }} />
          <input
            ref={searchRef}
            type="text"
            value={query}
            onChange={(e) => { setQuery(e.target.value); setActiveIndex(0); }}
            onKeyDown={handleKeyDown}
            placeholder={t('cannedResponses.searchPlaceholder')}
            className="w-full rounded-lg border py-1.5 pl-8 pr-2 text-sm outline-none focus:ring-2"
            style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
            aria-controls="canned-listbox"
            aria-activedescendant={ordered[activeIndex] ? `canned-opt-${ordered[activeIndex].id}` : undefined}
          />
        </div>
      </div>

      {/* Scope tabs */}
      <div className="flex gap-1 px-3 pt-2 overflow-x-auto">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => { setScopeTab(tab.key); setActiveIndex(0); }}
            className="whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-semibold transition-colors cursor-pointer border"
            style={scopeTab === tab.key
              ? { backgroundColor: 'var(--color-primary-500, #3b82f6)', color: '#fff', borderColor: 'transparent' }
              : { color: 'var(--text-secondary)', borderColor: 'var(--border-color)', backgroundColor: 'transparent' }}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* List */}
      <div ref={listRef} className="max-h-72 overflow-y-auto px-2 py-2">
        {loading ? (
          <div className="flex items-center justify-center py-8">
            <div className="h-6 w-6 rounded-full border-2 animate-spin" style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
          </div>
        ) : error ? (
          <p className="px-3 py-6 text-center text-sm" style={{ color: 'var(--text-tertiary)' }}>
            {t('cannedResponses.loadError')}
          </p>
        ) : ordered.length === 0 ? (
          <div className="px-3 py-6 text-center">
            <p className="text-sm" style={{ color: 'var(--text-tertiary)' }}>
              {query.trim() ? t('cannedResponses.noResults') : t('cannedResponses.empty')}
            </p>
            {canManage && !query.trim() && (
              <button
                type="button"
                onClick={onManage}
                className="mt-2 inline-flex items-center gap-1 text-xs font-semibold text-primary-500 hover:underline cursor-pointer"
              >
                <Settings className="h-3.5 w-3.5" />
                {t('cannedResponses.createFirst')}
              </button>
            )}
          </div>
        ) : (
          <ul id="canned-listbox" role="listbox" aria-label={t('cannedResponses.title')}>
            {favCount > 0 && (
              <li role="presentation" className="px-3 pb-1 pt-1 text-[10px] font-bold uppercase tracking-wider flex items-center gap-1" style={{ color: 'var(--text-tertiary)' }}>
                <Star className="h-3 w-3" fill="#f59e0b" style={{ color: '#f59e0b' }} />
                {t('cannedResponses.favorites')}
              </li>
            )}
            {ordered.map((tpl, idx) => (
              <FragmentWithDivider key={tpl.id} showDivider={favCount > 0 && idx === favCount}>
                {renderOption(tpl, idx)}
              </FragmentWithDivider>
            ))}
          </ul>
        )}
      </div>

      {/* Footer — manage link */}
      {canManage && (
        <div className="border-t px-3 py-2" style={{ borderColor: 'var(--border-color)' }}>
          <button
            type="button"
            onClick={onManage}
            className="inline-flex items-center gap-1 text-xs font-medium transition-colors cursor-pointer hover:text-primary-500"
            style={{ color: 'var(--text-secondary)' }}
          >
            <Settings className="h-3.5 w-3.5" />
            {t('cannedResponses.manageLink')}
          </button>
        </div>
      )}
    </div>
  );
}

function FragmentWithDivider({ showDivider, children }) {
  if (!showDivider) return children;
  return (
    <>
      <li role="presentation" className="my-1 border-t" style={{ borderColor: 'var(--border-color)' }} aria-hidden="true" />
      {children}
    </>
  );
}

function ScopeBadge({ scope, t }) {
  const isShared = scope === 'SHARED';
  return (
    <span
      className="inline-flex items-center rounded-full px-1.5 py-0.5 text-[9px] font-bold uppercase"
      style={isShared
        ? { backgroundColor: 'rgba(59,130,246,0.15)', color: '#3b82f6' }
        : { backgroundColor: 'rgba(148,163,184,0.18)', color: 'var(--text-secondary)' }}
    >
      {isShared ? t('cannedResponses.badgeShared') : t('cannedResponses.badgePersonal')}
    </span>
  );
}

function VisibilityBadge({ visibility, t }) {
  if (!visibility || visibility === 'BOTH') {
    return (
      <span className="inline-flex items-center rounded-full px-1.5 py-0.5 text-[9px] font-bold uppercase"
        style={{ backgroundColor: 'rgba(148,163,184,0.18)', color: 'var(--text-secondary)' }}>
        {t('cannedResponses.visBoth')}
      </span>
    );
  }
  const internal = visibility === 'INTERNAL';
  return (
    <span className="inline-flex items-center rounded-full px-1.5 py-0.5 text-[9px] font-bold uppercase"
      style={internal
        ? { backgroundColor: 'rgba(245,158,11,0.18)', color: '#b45309' }
        : { backgroundColor: 'rgba(16,185,129,0.15)', color: '#059669' }}>
      {internal ? t('cannedResponses.visInternal') : t('cannedResponses.visExternal')}
    </span>
  );
}
