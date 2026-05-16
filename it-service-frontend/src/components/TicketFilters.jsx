import { useEffect, useRef, useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import api from '../services/api';
import MultiSelectFilter from './filters/MultiSelectFilter';
import FilterSearchInput from './filters/FilterSearchInput';
import FilterChip from './filters/FilterChip';
import ClearFiltersButton from './filters/ClearFiltersButton';

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
  topicIds = [], slaStatuses = [], dateFrom, dateTo,
  onStatus, onPriority, onSearch, onProductIds, onAgentIds, onTopicIds,
  onSlaStatuses, onDateFrom, onDateTo,
  onClear,
  hideStatus    = false,
  hideAgent     = false,
  hideProduct   = false,
  statusOptions = STATUSES,
  scopedProductId,
}) {
  const { t } = useTranslation();
  const [products, setProducts] = useState([]);
  const [agents,   setAgents]   = useState([]);
  const [topics,   setTopics]   = useState([]);

  const SLA_STATUSES = [
    { value: 'BREACHED', label: t('ticket.filters.slaBreached') },
    { value: 'ACTIVE',   label: t('ticket.filters.slaActive') },
    { value: 'PAUSED',   label: t('ticket.filters.slaPaused') },
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
    || agentIds?.length || topicIds?.length || slaStatuses?.length || dateFrom || dateTo;

  return (
    <div className="border-b" style={{ borderColor: 'var(--border-color)' }}>
      {/* Main filter row */}
      <div className="flex flex-col sm:flex-row sm:flex-wrap sm:items-center gap-2 px-4 py-3">

        <FilterSearchInput
          key={search || '__empty__'}
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

        {!hideProduct && products.length > 0 && (
          <MultiSelectFilter
            values={productIds}
            onChange={onProductIds}
            placeholder={t('ticket.filters.allProducts')}
            options={products.map((p) => ({ value: String(p.id), label: p.name }))}
          />
        )}

        {onTopicIds && topicFilterAvailable && (
          <MultiSelectFilter
            values={topicIds}
            onChange={onTopicIds}
            placeholder={t('ticket.filters.allTopics')}
            options={topics.map((tp) => ({ value: String(tp.id), label: tp.name }))}
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
              label={`${t('ticket.filters.from')}: ${dateFrom ? new Date(dateFrom).toLocaleDateString() : '…'} → ${dateTo ? new Date(dateTo).toLocaleDateString() : '…'}`}
              onRemove={() => { onDateFrom(''); onDateTo(''); }}
            />
          )}
        </div>
      )}
    </div>
  );
}

// ── Tarih aralığı yerel kalır; başka bir yerde kullanılmıyor ──────────────────

function DateRangePicker({ dateFrom, dateTo, onDateFrom, onDateTo, datePresets, t }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  const hasDate = dateFrom || dateTo;

  useEffect(() => {
    const handler = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const applyPreset = (days) => {
    if (days === 0) {
      const today = new Date(); today.setHours(0, 0, 0, 0);
      const end = new Date();   end.setHours(23, 59, 59, 999);
      onDateFrom(today.toISOString()); onDateTo(end.toISOString());
    } else {
      const end = new Date();
      const start = new Date();
      start.setDate(start.getDate() - days);
      start.setHours(0, 0, 0, 0);
      onDateFrom(start.toISOString()); onDateTo(end.toISOString());
    }
    setOpen(false);
  };

  const toInputValue = (iso) => (iso ? iso.slice(0, 10) : '');
  const fromInput = (dateStr, isEnd) => {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    if (isEnd) d.setHours(23, 59, 59, 999); else d.setHours(0, 0, 0, 0);
    return d.toISOString();
  };

  return (
    <div className="relative w-full sm:w-auto" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="w-full sm:w-auto inline-flex items-center justify-between sm:justify-start gap-1.5 rounded-lg border px-2.5 py-1.5 text-xs cursor-pointer transition-all"
        style={{
          backgroundColor: hasDate ? 'rgba(59,130,246,0.08)' : 'var(--bg-input)',
          borderColor:     hasDate ? '#3b82f6'               : 'var(--border-color)',
          color:           hasDate ? '#2563eb'               : 'var(--text-secondary)',
        }}
      >
        {hasDate
          ? `${dateFrom ? new Date(dateFrom).toLocaleDateString(undefined, { month: 'short', day: 'numeric' }) : '…'} → ${dateTo ? new Date(dateTo).toLocaleDateString(undefined, { month: 'short', day: 'numeric' }) : '…'}`
          : t('ticket.filters.dateRange')}
        <ChevronDown className="h-3 w-3" />
      </button>

      {open && (
        <div className="absolute left-0 top-full mt-1 z-50 rounded-xl border shadow-lg p-3 w-64"
          style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
          <p className="mb-2 text-[10px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-tertiary)' }}>{t('ticket.filters.quickSelect')}</p>
          <div className="grid grid-cols-2 gap-1 mb-3">
            {datePresets.map((p) => (
              <button key={p.label} type="button" onClick={() => applyPreset(p.days)}
                className="rounded-lg border px-2 py-1 text-xs cursor-pointer transition-colors hover:bg-primary-50 dark:hover:bg-primary-500/10 text-left"
                style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>
                {p.label}
              </button>
            ))}
          </div>

          <p className="mb-1.5 text-[10px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-tertiary)' }}>{t('ticket.filters.customRange')}</p>
          <div className="space-y-1.5">
            <div>
              <label className="text-[10px] mb-0.5 block" style={{ color: 'var(--text-tertiary)' }}>{t('ticket.filters.from')}</label>
              <input type="date" value={toInputValue(dateFrom)}
                onChange={(e) => onDateFrom(fromInput(e.target.value, false))}
                className="w-full rounded-lg border px-2 py-1 text-xs outline-none"
                style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }} />
            </div>
            <div>
              <label className="text-[10px] mb-0.5 block" style={{ color: 'var(--text-tertiary)' }}>{t('ticket.filters.to')}</label>
              <input type="date" value={toInputValue(dateTo)}
                onChange={(e) => onDateTo(fromInput(e.target.value, true))}
                className="w-full rounded-lg border px-2 py-1 text-xs outline-none"
                style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }} />
            </div>
          </div>

          {(dateFrom || dateTo) && (
            <button type="button" onClick={() => { onDateFrom(''); onDateTo(''); setOpen(false); }}
              className="mt-2 w-full rounded-lg border px-2 py-1 text-xs cursor-pointer transition-colors hover:bg-danger-50 dark:hover:bg-danger-500/10"
              style={{ borderColor: 'var(--border-color)', color: 'var(--text-tertiary)' }}>
              {t('ticket.filters.clearDates')}
            </button>
          )}
        </div>
      )}
    </div>
  );
}
