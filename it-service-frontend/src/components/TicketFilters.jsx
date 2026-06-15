import { useEffect, useRef, useState } from 'react';
import { CalendarDays, Check, ChevronDown, ChevronLeft, ChevronRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import i18n from '../i18n';
import api from '../services/api';
import MultiSelectFilter from './filters/MultiSelectFilter';
import FilterSearchInput from './filters/FilterSearchInput';
import FilterChip from './filters/FilterChip';
import FloatingPanel from './filters/FloatingPanel';
import { formatDate } from '../utils/dateFormat';
import { useDateFormat } from '../context/DateFormatContext';
import ClearFiltersButton from './filters/ClearFiltersButton';
import { localizedName, sortByLocalizedName } from '../utils/localizedName';

const STATUSES   = ['NEW', 'IN_PROGRESS', 'WAITING_FOR_CUSTOMER', 'RESOLVED', 'CLOSED'];
const PRIORITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];

/**
 * Bilet listeleme sayfaları için tam yetenekli filtre barı.
 *
 * Önemli davranış:
 *   - Topic filtresi yalnızca productIds seçildikten sonra aktif olur.
 *   - Tek productId taşıyan sayfalarda (ProductPage) `scopedProductId` ile topic otomatik gelir.
 */
export default function TicketFilters({
  status = [], priority = [], search, productIds = [], agentIds = [],
  topicIds = [], slaStatuses = [], csatRatings = [], dateFrom, dateTo,
  onStatus, onPriority, onSearch, onProductIds, onAgentIds, onTopicIds,
  onSlaStatuses, onCsatRatings, onDateFrom, onDateTo, onDateRange,
  onClear,
  hideStatus    = false,
  hideAgent     = false,
  hideProduct   = false,
  showCsat      = false,   // CSAT filtresi yalnızca ADMIN/MANAGER görünümünde açılır
  statusOptions = STATUSES,
  scopedProductId,
}) {
  const { t } = useTranslation();
  useDateFormat(); // re-render when user's date format preference changes
  const [products, setProducts] = useState([]);
  const [agents,   setAgents]   = useState([]);
  const [topics,   setTopics]   = useState([]);

  const SLA_STATUSES = [
    { value: 'BREACHED', label: t('ticket.filters.slaBreached') },
    { value: 'ACTIVE',   label: t('ticket.filters.slaActive') },
    { value: 'PAUSED',   label: t('ticket.filters.slaPaused') },
  ];

  const CSAT_OPTIONS = [
    { value: '5', label: '5 ★' },
    { value: '4', label: '4 ★' },
    { value: '3', label: '3 ★' },
    { value: '2', label: '2 ★' },
    { value: '1', label: '1 ★' },
    { value: 'NONE', label: t('ticket.filters.csatNone') },
  ];

  const DATE_PRESETS = [
    { label: t('ticket.filters.presetToday'),  days: 0 },
    { label: t('ticket.filters.presetLast7'),  days: 7 },
    { label: t('ticket.filters.presetLast30'), days: 30 },
    { label: t('ticket.filters.presetLast90'), days: 90 },
  ];

  useEffect(() => {
    if (!hideProduct) api.get('/products').then((r) => setProducts(r.data)).catch(() => {});
    if (!hideAgent)   api.get('/users/agents').then((r) => setAgents(r.data)).catch(() => {});
  }, []); // eslint-disable-line

  // Seçili productId(ler) değişince ilgili topic listesini topla.
  const productsForTopics = scopedProductId
    ? [scopedProductId]
    : (productIds || []).map((id) => Number(id));

  useEffect(() => {
    const load = async () => {
      if (productsForTopics.length === 0) {
        setTopics([]);
        return;
      }
      const results = await Promise.all(
        productsForTopics.map((pid) =>
          api.get(`/products/${pid}/topics`).then((r) => r.data).catch(() => []),
        ),
      );
      // Aynı ID birden fazla ürünün topic listesinde gelirse de tek satır görsün.
      const merged = new Map();
      results.flat().forEach((t) => merged.set(t.id, t));
      setTopics(Array.from(merged.values()));
    };
    load();
  }, [productsForTopics.join(',')]); // eslint-disable-line

  // Seçili topic, artık geçerli ürünler arasında değilse otomatik temizle.
  useEffect(() => {
    if (!topicIds || topicIds.length === 0) return;
    const validIds = new Set(topics.map((t) => String(t.id)));
    const filtered = topicIds.filter((id) => validIds.has(String(id)));
    if (filtered.length !== topicIds.length && onTopicIds) {
      onTopicIds(filtered);
    }
  }, [topics]); // eslint-disable-line

  const topicFilterAvailable = productsForTopics.length > 0;

  const hasFilters = status?.length || priority?.length || search || productIds?.length
    || agentIds?.length || topicIds?.length || slaStatuses?.length || csatRatings?.length || dateFrom || dateTo;

  return (
    <div className="border-b" style={{ borderColor: 'var(--border-color)' }}>
      {/* Main filter row */}
      <div className="flex flex-col sm:flex-row sm:flex-wrap sm:items-center gap-2 px-4 py-3">

        <FilterSearchInput
          value={search}
          onChange={onSearch}
          placeholder={t('ticket.filters.searchPlaceholder')}
        />

        {!hideStatus && (
          <MultiSelectFilter
            values={status}
            onChange={onStatus}
            placeholder={t('ticket.filters.allStatuses')}
            options={statusOptions.map((s) => ({ value: s, label: s.replace(/_/g, ' ') }))}
          />
        )}

        <MultiSelectFilter
          values={priority}
          onChange={onPriority}
          placeholder={t('ticket.filters.allPriorities')}
          options={PRIORITIES.map((p) => ({ value: p, label: p }))}
        />

        <MultiSelectFilter
          values={slaStatuses}
          onChange={onSlaStatuses}
          placeholder={t('ticket.filters.allSla')}
          options={SLA_STATUSES}
        />

        {showCsat && onCsatRatings && (
          <MultiSelectFilter
            values={csatRatings}
            onChange={onCsatRatings}
            placeholder={t('ticket.filters.allCsat')}
            options={CSAT_OPTIONS}
          />
        )}

        {!hideProduct && products.length > 0 && (
          <MultiSelectFilter
            values={productIds}
            onChange={onProductIds}
            placeholder={t('ticket.filters.allProducts')}
            options={sortByLocalizedName(products).map((p) => ({ value: String(p.id), label: localizedName(p) }))}
          />
        )}

        {onTopicIds && topicFilterAvailable && (
          <MultiSelectFilter
            values={topicIds}
            onChange={onTopicIds}
            placeholder={t('ticket.filters.allTopics')}
            options={sortByLocalizedName(topics).map((tp) => ({ value: String(tp.id), label: localizedName(tp) }))}
          />
        )}

        {!hideAgent && agents.length > 0 && (
          <MultiSelectFilter
            values={agentIds}
            onChange={onAgentIds}
            placeholder={t('ticket.filters.allAgents')}
            options={agents.map((a) => ({ value: a.id, label: a.fullName }))}
          />
        )}

        <DateRangePicker
          dateFrom={dateFrom} dateTo={dateTo}
          onDateFrom={onDateFrom} onDateTo={onDateTo}
          onDateRange={onDateRange}
          datePresets={DATE_PRESETS}
          t={t}
        />

        {hasFilters && <ClearFiltersButton onClick={onClear} label={t('ticket.filters.clearAll')} />}
      </div>

      {/* Active filter chips */}
      {hasFilters && (
        <div className="flex flex-wrap gap-1.5 px-4 pb-2.5">
          {status?.map((s) => (
            <FilterChip key={s} label={t('ticket.filters.chipStatus', { value: s.replace(/_/g, ' ') })}
              onRemove={() => onStatus(status.filter((v) => v !== s))} />
          ))}
          {priority?.map((p) => (
            <FilterChip key={p} label={t('ticket.filters.chipPriority', { value: p })}
              onRemove={() => onPriority(priority.filter((v) => v !== p))} />
          ))}
          {search    && <FilterChip label={t('ticket.filters.chipSearch', { value: search })} onRemove={() => onSearch('')} />}
          {slaStatuses?.map((s) => (
            <FilterChip key={s} label={t('ticket.filters.chipSla', { value: SLA_STATUSES.find((sl) => sl.value === s)?.label ?? s })}
              onRemove={() => onSlaStatuses(slaStatuses.filter((v) => v !== s))} />
          ))}
          {csatRatings?.map((c) => (
            <FilterChip key={c} label={t('ticket.filters.chipCsat', { value: c === 'NONE' ? t('ticket.filters.csatNone') : `${c} ★` })}
              onRemove={() => onCsatRatings(csatRatings.filter((v) => v !== c))} />
          ))}
          {productIds?.map((pid) => (
            <FilterChip key={pid} label={t('ticket.filters.chipProduct', { value: products.find((p) => String(p.id) === pid)?.name ?? pid })}
              onRemove={() => onProductIds(productIds.filter((v) => v !== pid))} />
          ))}
          {topicIds?.map((tid) => (
            <FilterChip key={tid} label={t('ticket.filters.chipTopic', { value: topics.find((tp) => String(tp.id) === String(tid))?.name ?? tid })}
              onRemove={() => onTopicIds(topicIds.filter((v) => v !== tid))} />
          ))}
          {agentIds?.map((aid) => (
            <FilterChip key={aid} label={t('ticket.filters.chipAgent', { value: agents.find((a) => a.id === aid)?.fullName ?? aid })}
              onRemove={() => onAgentIds(agentIds.filter((v) => v !== aid))} />
          ))}
          {(dateFrom || dateTo) && (
            <FilterChip
              label={`${t('ticket.filters.from')}: ${dateFrom ? formatDate(dateFrom) : '…'} → ${dateTo ? formatDate(dateTo) : '…'}`}
              onRemove={() => { if (onDateRange) onDateRange('', ''); else { onDateFrom(''); onDateTo(''); } }}
            />
          )}
        </div>
      )}
    </div>
  );
}

function DateRangePicker({ dateFrom, dateTo, onDateFrom, onDateTo, onDateRange, datePresets, t }) {
  const [open, setOpen] = useState(false);
  const [activeCalendar, setActiveCalendar] = useState(null); // 'from' | 'to' | null
  const ref = useRef(null);
  const hasDate = dateFrom || dateTo;

  const closePanel = () => { setOpen(false); setActiveCalendar(null); };

  const applyPreset = (days) => {
    let from, to;
    if (days === 0) {
      const today = new Date(); today.setHours(0, 0, 0, 0);
      const end = new Date();   end.setHours(23, 59, 59, 999);
      from = today.toISOString(); to = end.toISOString();
    } else {
      const end = new Date();
      const start = new Date();
      start.setDate(start.getDate() - days);
      start.setHours(0, 0, 0, 0);
      from = start.toISOString(); to = end.toISOString();
    }
    if (onDateRange) onDateRange(from, to);
    else { onDateFrom(from); onDateTo(to); }
    closePanel();
  };

  const clearDates = () => {
    if (onDateRange) onDateRange('', '');
    else { onDateFrom(''); onDateTo(''); }
    closePanel();
  };

  const isPresetActive = (days) => {
    if (!dateFrom || !dateTo) return false;
    const from = new Date(dateFrom);
    const today = new Date();
    if (days === 0) {
      return from.toDateString() === today.toDateString() && new Date(dateTo).toDateString() === today.toDateString();
    }
    const expectedStart = new Date();
    expectedStart.setDate(expectedStart.getDate() - days);
    return from.toDateString() === expectedStart.toDateString();
  };

  const handleFromSelect = (isoStr) => {
    onDateFrom(isoStr);
    if (!dateTo) setActiveCalendar('to');
    else setActiveCalendar(null);
  };

  const handleToSelect = (isoStr) => {
    onDateTo(isoStr);
    setActiveCalendar(null);
  };

  return (
    <div className="relative w-full sm:w-auto" ref={ref}>
      <button
        type="button"
        onClick={() => { if (open) closePanel(); else setOpen(true); }}
        className="w-full sm:w-auto inline-flex items-center justify-between sm:justify-start gap-1.5 rounded-lg border px-2.5 py-1.5 text-xs cursor-pointer transition-all"
        style={{
          backgroundColor: hasDate ? 'rgba(59,130,246,0.08)' : 'var(--bg-input)',
          borderColor:     hasDate ? '#3b82f6'               : 'var(--border-color)',
          color:           hasDate ? '#2563eb'               : 'var(--text-secondary)',
        }}
      >
        <CalendarDays className="h-3.5 w-3.5 shrink-0" />
        {hasDate
          ? `${dateFrom ? formatDate(dateFrom) : '…'} → ${dateTo ? formatDate(dateTo) : '…'}`
          : t('ticket.filters.dateRange')}
        <ChevronDown className="h-3 w-3 shrink-0" />
      </button>

      <FloatingPanel
        anchorRef={ref}
        open={open}
        onClose={closePanel}
        className="rounded-xl border shadow-lg w-72"
        style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}
      >
          {/* Quick presets */}
          <div className="p-3 border-b" style={{ borderColor: 'var(--border-color)' }}>
            <p className="mb-2 text-[10px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-tertiary)' }}>
              {t('ticket.filters.quickSelect')}
            </p>
            <div className="grid grid-cols-2 gap-1">
              {datePresets.map((p) => {
                const active = isPresetActive(p.days);
                return (
                  <button key={p.label} type="button" onClick={() => applyPreset(p.days)}
                    className="rounded-lg px-2 py-1.5 text-xs cursor-pointer transition-all text-left font-medium inline-flex items-center gap-1"
                    style={{
                      backgroundColor: active ? 'rgba(59,130,246,0.1)' : 'transparent',
                      color:           active ? '#2563eb'               : 'var(--text-secondary)',
                      border:          `1px solid ${active ? '#3b82f6' : 'var(--border-color)'}`,
                    }}>
                    {active && <Check className="h-3 w-3 shrink-0" />}
                    {p.label}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Custom range */}
          <div className="p-3">
            <p className="mb-2 text-[10px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-tertiary)' }}>
              {t('ticket.filters.customRange')}
            </p>
            <div className="flex items-end gap-2">
              <div className="flex-1">
                <label className="text-[10px] mb-0.5 block" style={{ color: 'var(--text-tertiary)' }}>
                  {t('ticket.filters.from')}
                </label>
                <div
                  className="w-full rounded-lg border px-2 py-1.5 text-xs cursor-pointer select-none transition-colors"
                  onClick={() => setActiveCalendar(ac => ac === 'from' ? null : 'from')}
                  style={{
                    backgroundColor: 'var(--bg-input)',
                    borderColor: activeCalendar === 'from' ? '#3b82f6' : 'var(--border-color)',
                    color: dateFrom ? 'var(--text-primary)' : 'var(--text-tertiary)',
                  }}>
                  {dateFrom ? formatDate(dateFrom) : '–'}
                </div>
              </div>
              <span className="pb-2 text-xs shrink-0" style={{ color: 'var(--text-tertiary)' }}>→</span>
              <div className="flex-1">
                <label className="text-[10px] mb-0.5 block" style={{ color: 'var(--text-tertiary)' }}>
                  {t('ticket.filters.to')}
                </label>
                <div
                  className="w-full rounded-lg border px-2 py-1.5 text-xs cursor-pointer select-none transition-colors"
                  onClick={() => setActiveCalendar(ac => ac === 'to' ? null : 'to')}
                  style={{
                    backgroundColor: 'var(--bg-input)',
                    borderColor: activeCalendar === 'to' ? '#3b82f6' : 'var(--border-color)',
                    color: dateTo ? 'var(--text-primary)' : 'var(--text-tertiary)',
                  }}>
                  {dateTo ? formatDate(dateTo) : '–'}
                </div>
              </div>
            </div>

            {activeCalendar && (
              <MiniCalendar
                value={activeCalendar === 'from' ? dateFrom : dateTo}
                dateFrom={dateFrom}
                dateTo={dateTo}
                pickingFrom={activeCalendar === 'from'}
                onChange={activeCalendar === 'from' ? handleFromSelect : handleToSelect}
              />
            )}

            {hasDate && (
              <button type="button" onClick={clearDates}
                className="mt-2 w-full rounded-lg px-2 py-1.5 text-xs cursor-pointer transition-colors hover:bg-danger-50 dark:hover:bg-danger-500/10"
                style={{ border: '1px solid var(--border-color)', color: 'var(--text-tertiary)' }}>
                {t('ticket.filters.clearDates')}
              </button>
            )}
          </div>
      </FloatingPanel>
    </div>
  );
}

function MiniCalendar({ value, dateFrom, dateTo, pickingFrom, onChange }) {
  const locale = i18n.language?.startsWith('tr') ? 'tr-TR' : 'en-US';

  const initial = value ? new Date(value) : (dateFrom ? new Date(dateFrom) : new Date());
  const [viewYear, setViewYear] = useState(initial.getFullYear());
  const [viewMonth, setViewMonth] = useState(initial.getMonth());
  const [view, setView] = useState('day'); // 'day' | 'month' | 'year'
  const [yearRangeStart, setYearRangeStart] = useState(() => {
    const y = initial.getFullYear();
    return y - (y % 12);
  });

  const prevMonth = () => {
    if (viewMonth === 0) { setViewMonth(11); setViewYear(y => y - 1); }
    else setViewMonth(m => m - 1);
  };
  const nextMonth = () => {
    if (viewMonth === 11) { setViewMonth(0); setViewYear(y => y + 1); }
    else setViewMonth(m => m + 1);
  };

  const monthLabel = new Date(viewYear, viewMonth, 1)
    .toLocaleDateString(locale, { month: 'long', year: 'numeric' });

  const dayHeaders = Array.from({ length: 7 }, (_, i) =>
    new Date(2024, 0, i + 1).toLocaleDateString(locale, { weekday: 'narrow' })
  );

  const firstDow = new Date(viewYear, viewMonth, 1).getDay();
  const startOffset = (firstDow + 6) % 7;
  const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate();
  const cells = [...Array(startOffset).fill(null), ...Array.from({ length: daysInMonth }, (_, i) => i + 1)];

  const today = new Date();
  const dayMs = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const todayMs = dayMs(today);
  const fromMs = dateFrom ? dayMs(new Date(dateFrom)) : null;
  const toMs   = dateTo   ? dayMs(new Date(dateTo))   : null;
  const sameFromTo = fromMs !== null && fromMs === toMs;

  const monthNames = Array.from({ length: 12 }, (_, i) =>
    new Date(2024, i, 1).toLocaleDateString(locale, { month: 'short' })
  );
  const years = Array.from({ length: 12 }, (_, i) => yearRangeStart + i);

  const navBtn = 'flex h-6 w-6 items-center justify-center rounded-md cursor-pointer transition-colors';
  const hoverBg = {
    onMouseEnter: e => { e.currentTarget.style.backgroundColor = 'var(--bg-surface-hover)'; },
    onMouseLeave: e => { e.currentTarget.style.backgroundColor = 'transparent'; },
  };
  const gridItemBase = 'rounded-lg py-1.5 text-xs font-medium cursor-pointer transition-colors';

  return (
    <div className="mt-2 pt-2 border-t" style={{ borderColor: 'var(--border-color)' }}>

      {/* ── YEAR VIEW ── */}
      {view === 'year' && (
        <>
          <div className="flex items-center justify-between mb-2">
            <button type="button" onClick={() => setYearRangeStart(s => s - 12)}
              className={navBtn} style={{ color: 'var(--text-secondary)' }} {...hoverBg}>
              <ChevronLeft className="h-3.5 w-3.5" />
            </button>
            <span className="text-xs font-semibold" style={{ color: 'var(--text-primary)' }}>
              {yearRangeStart} – {yearRangeStart + 11}
            </span>
            <button type="button" onClick={() => setYearRangeStart(s => s + 12)}
              className={navBtn} style={{ color: 'var(--text-secondary)' }} {...hoverBg}>
              <ChevronRight className="h-3.5 w-3.5" />
            </button>
          </div>
          <div className="grid grid-cols-3 gap-1">
            {years.map(y => {
              const sel = y === viewYear;
              return (
                <button key={y} type="button"
                  onClick={() => { setViewYear(y); setView('month'); }}
                  className={gridItemBase}
                  style={{
                    backgroundColor: sel ? '#3b82f6' : 'transparent',
                    color: sel ? '#fff' : 'var(--text-primary)',
                    border: `1px solid ${sel ? '#3b82f6' : 'transparent'}`,
                  }}
                  onMouseEnter={e => { if (!sel) e.currentTarget.style.backgroundColor = 'var(--bg-surface-hover)'; }}
                  onMouseLeave={e => { if (!sel) e.currentTarget.style.backgroundColor = 'transparent'; }}>
                  {y}
                </button>
              );
            })}
          </div>
        </>
      )}

      {/* ── MONTH VIEW ── */}
      {view === 'month' && (
        <>
          <div className="flex items-center justify-between mb-2">
            <button type="button" onClick={() => setViewYear(y => y - 1)}
              className={navBtn} style={{ color: 'var(--text-secondary)' }} {...hoverBg}>
              <ChevronLeft className="h-3.5 w-3.5" />
            </button>
            <button type="button"
              onClick={() => { setYearRangeStart(viewYear - (viewYear % 12)); setView('year'); }}
              className="text-xs font-semibold rounded-md px-2 py-0.5 cursor-pointer transition-colors"
              style={{ color: 'var(--text-primary)' }} {...hoverBg}>
              {viewYear}
            </button>
            <button type="button" onClick={() => setViewYear(y => y + 1)}
              className={navBtn} style={{ color: 'var(--text-secondary)' }} {...hoverBg}>
              <ChevronRight className="h-3.5 w-3.5" />
            </button>
          </div>
          <div className="grid grid-cols-3 gap-1">
            {monthNames.map((name, idx) => {
              const sel = idx === viewMonth;
              return (
                <button key={idx} type="button"
                  onClick={() => { setViewMonth(idx); setView('day'); }}
                  className={`${gridItemBase} capitalize`}
                  style={{
                    backgroundColor: sel ? '#3b82f6' : 'transparent',
                    color: sel ? '#fff' : 'var(--text-primary)',
                    border: `1px solid ${sel ? '#3b82f6' : 'transparent'}`,
                  }}
                  onMouseEnter={e => { if (!sel) e.currentTarget.style.backgroundColor = 'var(--bg-surface-hover)'; }}
                  onMouseLeave={e => { if (!sel) e.currentTarget.style.backgroundColor = 'transparent'; }}>
                  {name}
                </button>
              );
            })}
          </div>
        </>
      )}

      {/* ── DAY VIEW ── */}
      {view === 'day' && (
        <>
          <div className="flex items-center justify-between mb-2">
            <button type="button" onClick={prevMonth}
              className={navBtn} style={{ color: 'var(--text-secondary)' }} {...hoverBg}>
              <ChevronLeft className="h-3.5 w-3.5" />
            </button>
            <button type="button" onClick={() => setView('month')}
              className="text-xs font-semibold rounded-md px-2 py-0.5 cursor-pointer capitalize transition-colors"
              style={{ color: 'var(--text-primary)' }} {...hoverBg}>
              {monthLabel}
            </button>
            <button type="button" onClick={nextMonth}
              className={navBtn} style={{ color: 'var(--text-secondary)' }} {...hoverBg}>
              <ChevronRight className="h-3.5 w-3.5" />
            </button>
          </div>

          <div className="grid grid-cols-7 mb-0.5">
            {dayHeaders.map((d, i) => (
              <div key={i} className="flex items-center justify-center h-6 text-[10px] font-semibold"
                style={{ color: 'var(--text-tertiary)' }}>{d}</div>
            ))}
          </div>

          <div className="grid grid-cols-7">
            {cells.map((day, i) => {
              if (!day) return <div key={i} className="h-8" />;
              const cellMs  = new Date(viewYear, viewMonth, day).getTime();
              const isToday = todayMs === cellMs;
              const isFrom  = fromMs !== null && fromMs === cellMs;
              const isTo    = toMs   !== null && toMs   === cellMs;
              const inRange = fromMs !== null && toMs !== null && cellMs > fromMs && cellMs < toMs;
              const highlighted = isFrom || isTo;
              const leftBg  = (inRange || (isTo   && fromMs !== null && !sameFromTo)) ? 'rgba(59,130,246,0.12)' : 'transparent';
              const rightBg = (inRange || (isFrom && toMs   !== null && !sameFromTo)) ? 'rgba(59,130,246,0.12)' : 'transparent';
              return (
                <div key={i} className="relative flex h-8 items-center justify-center">
                  <div className="absolute inset-y-0 left-0 right-1/2" style={{ backgroundColor: leftBg }} />
                  <div className="absolute inset-y-0 left-1/2 right-0" style={{ backgroundColor: rightBg }} />
                  <button
                    type="button"
                    onClick={() => {
                      const d = new Date(viewYear, viewMonth, day);
                      d.setHours(pickingFrom ? 0 : 23, pickingFrom ? 0 : 59, pickingFrom ? 0 : 59, pickingFrom ? 0 : 999);
                      onChange(d.toISOString());
                    }}
                    className="relative z-10 flex h-7 w-7 items-center justify-center rounded-full text-xs cursor-pointer transition-colors"
                    style={{
                      backgroundColor: highlighted ? '#3b82f6' : 'transparent',
                      color: highlighted ? '#fff' : isToday ? '#3b82f6' : 'var(--text-primary)',
                      fontWeight: highlighted || isToday ? '600' : undefined,
                      outline: isToday && !highlighted ? '2px solid rgba(59,130,246,0.35)' : undefined,
                      outlineOffset: '1px',
                    }}
                    onMouseEnter={e => { if (!highlighted) e.currentTarget.style.backgroundColor = 'var(--bg-surface-hover)'; }}
                    onMouseLeave={e => { if (!highlighted) e.currentTarget.style.backgroundColor = 'transparent'; }}>
                    {day}
                  </button>
                </div>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}
