import { useState, useEffect, useMemo, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  Search, Gauge, TicketCheck, Briefcase, Inbox, History, Users, Layers,
  LayoutDashboard, Settings, UserPlus, Package, Zap, LifeBuoy, User,
  Clock, CornerDownLeft, ArrowUp, ArrowDown, X,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { usePanelPrefs } from '../context/PanelPrefsContext';
import { useCommandPalette } from '../context/CommandPaletteContext';
import { useEscapeToClose } from '../hooks/useEscapeToClose';
import { StatusBadge } from './Badges';
import { localizedName } from '../utils/localizedName';
import api from '../services/api';

const SEARCH_MIN_CHARS = 2;
const SEARCH_DEBOUNCE_MS = 250;
const SEARCH_LIMIT = 6;

function ticketCode(id) {
  return `TCK-${String(id).padStart(3, '0')}`;
}

// Aramayı tetikle: en az 2 karakter VEYA tamamen rakam (kısa bilet ID'leri için, örn. "7").
function shouldSearch(q) {
  return q.length >= SEARCH_MIN_CHARS || /^\d+$/.test(q);
}

/**
 * Ctrl/Cmd+K ile açılan komut paleti. Açıldığında {@link PaletteInner} taze mount edilir —
 * böylece state her açılışta sıfırlanır (reset için effect'e gerek kalmaz).
 */
export default function CommandPalette() {
  const { open } = useCommandPalette();
  if (!open) return null;
  return <PaletteInner />;
}

/**
 * İki iş yapar:
 *  1) Rol + panel tercihlerine göre süzülmüş hızlı navigasyon
 *  2) Yazıldıkça canlı bilet arama (personel → /tickets/all, müşteri → /tickets)
 * Boş sorguda son kullanılanları gösterir. Tamamen klavyeyle kullanılabilir.
 */
function PaletteInner() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { close, recent, addRecent } = useCommandPalette();
  const { isPanelVisible } = usePanelPrefs();
  const {
    isCustomer, isAgent, isLeadAgent, isAdmin, isManager, isStaff,
  } = useAuth();

  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [searching, setSearching] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);

  const inputRef = useRef(null);
  const itemRefs = useRef([]);

  // ── Rol + panel tercihlerine göre komut listesi (Sidebar mantığının aynısı) ──
  const commands = useMemo(() => {
    const showDashboard = isManager || isLeadAgent || isAdmin;
    return [
      { id: 'overview',         to: '/overview',          icon: Gauge,           label: t('sidebar.overview'),        show: isCustomer },
      { id: 'my-tickets',       to: '/my-tickets',        icon: TicketCheck,     label: t('sidebar.myTickets'),       show: isCustomer },
      { id: 'my-performance',   to: '/my-performance',    icon: Gauge,           label: t('sidebar.myPerformance'),   show: isAgent },
      { id: 'workspace',        to: '/workspace',         icon: Briefcase,       label: t('sidebar.workspace'),       show: isAgent && isPanelVisible('workspace') },
      { id: 'pool',             to: '/pool',              icon: Inbox,           label: t('sidebar.pool'),            show: isAgent && isPanelVisible('pool') },
      { id: 'history',          to: '/history',           icon: History,         label: t('sidebar.history'),         show: isAgent && isPanelVisible('history') },
      { id: 'team',             to: '/team',              icon: Users,           label: t('sidebar.teamTickets'),     show: isAgent && isPanelVisible('team') },
      { id: 'all-tickets',      to: '/all-tickets',       icon: Layers,          label: t('sidebar.allTickets'),      show: isStaff && isPanelVisible('allTickets') },
      { id: 'dashboard',        to: '/dashboard',         icon: LayoutDashboard, label: t('sidebar.dashboard'),       show: showDashboard },
      { id: 'admin',            to: '/admin',             icon: Settings,        label: t('sidebar.admin'),           show: isAdmin },
      { id: 'user-management',  to: '/user-management',   icon: UserPlus,        label: t('sidebar.userManagement'),  show: isAdmin || isManager },
      { id: 'products',         to: '/products',          icon: Package,         label: t('sidebar.products'),        show: isAdmin || isManager },
      { id: 'canned-responses', to: '/canned-responses',  icon: Zap,             label: t('sidebar.cannedResponses'), show: isAgent || isAdmin },
      { id: 'known-issues',     to: '/known-issues',      icon: LifeBuoy,        label: t('sidebar.knownIssues'),     show: true },
      { id: 'profile',          to: '/profile',           icon: User,            label: t('profile.title'),           show: true },
    ].filter((c) => c.show);
  }, [t, isCustomer, isAgent, isLeadAgent, isAdmin, isManager, isStaff, isPanelVisible]);

  // ── Açılışta input'a odaklan (DOM etkileşimi — setState değil) ──
  useEffect(() => {
    const id = setTimeout(() => inputRef.current?.focus(), 0);
    return () => clearTimeout(id);
  }, []);

  // ── Bilet arama — setState'ler bir fonksiyon içinde (effect gövdesinde değil) ──
  const runSearch = useCallback((raw) => {
    const q = raw.trim();
    if (!shouldSearch(q)) {
      setResults([]);
      setSearching(false);
      return;
    }
    setSearching(true);
    const endpoint = isStaff ? '/tickets/all' : '/tickets';
    api.get(endpoint, {
      params: { search: q, size: SEARCH_LIMIT, page: 0, sortBy: 'createdAt', sortDir: 'desc' },
    })
      .then((res) => { setResults(res.data?.content ?? []); setActiveIndex(0); })
      .catch(() => setResults([]))
      .finally(() => setSearching(false));
  }, [isStaff]);

  useEffect(() => {
    const id = setTimeout(() => runSearch(query), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(id);
  }, [query, runSearch]);

  const handleSelect = useCallback((entry) => {
    addRecent({ key: entry.key, label: entry.label, sub: entry.sub, to: entry.to });
    close();
    navigate(entry.to);
  }, [addRecent, close, navigate]);

  // ── Görüntülenecek gruplar + düz (klavye-gezilebilir) öğe listesi ──
  const { groups, flatItems } = useMemo(() => {
    const q = query.trim().toLowerCase();
    const g = [];

    if (q === '') {
      if (recent.length > 0) {
        g.push({
          titleKey: 'commandPalette.recent',
          items: recent.map((r) => ({
            key: r.key, label: r.label, sub: r.sub, icon: Clock, to: r.to,
          })),
        });
      }
      g.push({
        titleKey: 'commandPalette.navigate',
        items: commands.map((c) => ({
          key: c.id, label: c.label, icon: c.icon, to: c.to,
        })),
      });
    } else {
      const matched = commands.filter((c) => c.label.toLowerCase().includes(q));
      if (matched.length > 0) {
        g.push({
          titleKey: 'commandPalette.navigate',
          items: matched.map((c) => ({
            key: c.id, label: c.label, icon: c.icon, to: c.to,
          })),
        });
      }
      if (results.length > 0) {
        g.push({
          titleKey: 'commandPalette.tickets',
          items: results.map((tk) => ({
            key: `ticket-${tk.id}`,
            label: tk.title,
            sub: `${ticketCode(tk.id)}${localizedName(tk, 'topicName') ? ` · ${localizedName(tk, 'topicName')}` : ''}`,
            status: tk.status,
            icon: TicketCheck,
            to: `/tickets/${tk.id}`,
          })),
        });
      }
    }

    const flat = g.flatMap((grp) => grp.items);
    return { groups: g, flatItems: flat };
  }, [query, recent, commands, results]);

  // Render/seçimde kullanılan güvenli index (liste küçülürse taşmayı engeller).
  const clampedActive = flatItems.length ? Math.min(activeIndex, flatItems.length - 1) : 0;

  // Aktif öğeyi görünür alana kaydır (DOM etkileşimi — setState değil).
  useEffect(() => {
    itemRefs.current[clampedActive]?.scrollIntoView({ block: 'nearest' });
  }, [clampedActive]);

  useEscapeToClose(true, close);

  const handleKeyDown = (e) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActiveIndex((i) => (flatItems.length ? (i + 1) % flatItems.length : 0));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActiveIndex((i) => (flatItems.length ? (i - 1 + flatItems.length) % flatItems.length : 0));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      const item = flatItems[clampedActive];
      if (item) handleSelect(item);
    }
  };

  const q = query.trim();
  const showEmpty = q !== '' && !searching && flatItems.length === 0;
  let runningIndex = -1;

  return (
    <div
      className="fixed inset-0 z-[60] flex items-start justify-center p-4 pt-[12vh]"
      style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}
      onMouseDown={(e) => { if (e.target === e.currentTarget) close(); }}
    >
      <div
        className="w-full max-w-xl flex flex-col rounded-2xl border shadow-2xl animate-fade-in overflow-hidden"
        style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', maxHeight: '70vh' }}
        role="dialog"
        aria-modal="true"
        onKeyDown={handleKeyDown}
      >
        {/* Search input */}
        <div className="flex items-center gap-3 px-4 py-3 border-b flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
          <Search className="h-5 w-5 flex-shrink-0" style={{ color: 'var(--text-tertiary)' }} />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => { setQuery(e.target.value); setActiveIndex(0); }}
            placeholder={t('commandPalette.placeholder')}
            className="flex-1 min-w-0 bg-transparent text-sm outline-none"
            style={{ color: 'var(--text-primary)' }}
            aria-label={t('commandPalette.placeholder')}
          />
          {searching && (
            <div className="h-4 w-4 flex-shrink-0 rounded-full border-2 animate-spin" style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
          )}
          <button
            type="button"
            onClick={close}
            className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-md transition-colors cursor-pointer hover:bg-[var(--bg-surface-hover)]"
            style={{ color: 'var(--text-tertiary)' }}
            aria-label={t('common.close')}
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Results */}
        <div className="overflow-y-auto py-2">
          {showEmpty ? (
            <div className="px-4 py-10 text-center text-sm" style={{ color: 'var(--text-tertiary)' }}>
              {t('commandPalette.noResults')}
            </div>
          ) : (
            groups.map((grp) => (
              <div key={grp.titleKey} className="px-2 pb-1.5">
                <div className="px-2 pt-2 pb-1 text-[11px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-tertiary)' }}>
                  {t(grp.titleKey)}
                </div>
                {grp.items.map((item) => {
                  runningIndex += 1;
                  const idx = runningIndex;
                  const active = idx === clampedActive;
                  const Icon = item.icon;
                  return (
                    <button
                      key={item.key}
                      ref={(el) => { itemRefs.current[idx] = el; }}
                      type="button"
                      onClick={() => handleSelect(item)}
                      onMouseMove={() => setActiveIndex(idx)}
                      className="w-full flex items-center gap-3 rounded-lg px-2.5 py-2 text-left transition-colors"
                      style={{ backgroundColor: active ? 'var(--bg-surface-hover)' : 'transparent' }}
                    >
                      <div
                        className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg"
                        style={{ backgroundColor: 'var(--bg-surface-secondary)' }}
                      >
                        <Icon className="h-4 w-4" style={{ color: active ? '#3b82f6' : 'var(--text-secondary)' }} />
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="text-sm font-medium truncate" style={{ color: 'var(--text-primary)' }}>
                          {item.label}
                        </div>
                        {item.sub && (
                          <div className="text-xs truncate" style={{ color: 'var(--text-tertiary)' }}>{item.sub}</div>
                        )}
                      </div>
                      {item.status && <StatusBadge status={item.status} />}
                      {active && (
                        <CornerDownLeft className="h-3.5 w-3.5 flex-shrink-0" style={{ color: 'var(--text-tertiary)' }} />
                      )}
                    </button>
                  );
                })}
              </div>
            ))
          )}
        </div>

        {/* Footer — klavye ipuçları */}
        <div
          className="hidden sm:flex items-center gap-4 px-4 py-2 border-t flex-shrink-0 text-[11px]"
          style={{ borderColor: 'var(--border-color)', color: 'var(--text-tertiary)' }}
        >
          <span className="flex items-center gap-1">
            <Kbd><ArrowUp className="h-3 w-3" /></Kbd>
            <Kbd><ArrowDown className="h-3 w-3" /></Kbd>
            {t('commandPalette.hintNavigate')}
          </span>
          <span className="flex items-center gap-1">
            <Kbd><CornerDownLeft className="h-3 w-3" /></Kbd>
            {t('commandPalette.hintSelect')}
          </span>
          <span className="flex items-center gap-1">
            <Kbd>Esc</Kbd>
            {t('commandPalette.hintClose')}
          </span>
        </div>
      </div>
    </div>
  );
}

function Kbd({ children }) {
  return (
    <kbd
      className="inline-flex min-w-[20px] h-5 items-center justify-center rounded border px-1 font-sans text-[10px] font-semibold"
      style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
    >
      {children}
    </kbd>
  );
}
