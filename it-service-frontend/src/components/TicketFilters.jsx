import { useEffect, useRef, useState } from 'react';
import { Search, X, ChevronDown } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import api from '../services/api';

const STATUSES   = ['NEW', 'IN_PROGRESS', 'WAITING_FOR_CUSTOMER', 'RESOLVED', 'CLOSED'];
const PRIORITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];

/**
 * Full-featured filter bar for ticket list pages.
 *
 * Props (all optional):
 *   status, priority, search, productId, agentId, slaStatus, dateFrom, dateTo
 *   onStatus, onPriority, onSearch, onProductId, onAgentId, onSlaStatus, onDateFrom, onDateTo
 *   onClear        — clears all filters
 *   hideStatus     — hide status filter (e.g. Pool always NEW)
 *   hideAgent      — hide agent filter (e.g. customer pages)
 *   hideProduct    — hide product filter (e.g. ProductPage already scoped)
 */
export default function TicketFilters({
  status, priority, search, productId, agentId, slaStatus, dateFrom, dateTo,
  onStatus, onPriority, onSearch, onProductId, onAgentId, onSlaStatus, onDateFrom, onDateTo,
  onClear,
  hideStatus  = false,
  hideAgent   = false,
  hideProduct = false,
}) {
  const { t } = useTranslation();
  const [products, setProducts] = useState([]);
  const [agents,   setAgents]   = useState([]);

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

  // Fetch products and agents for dropdowns (only once)
  useEffect(() => {
    if (!hideProduct) {
      api.get('/products').then(r => setProducts(r.data)).catch(() => {});
    }
    if (!hideAgent) {
      api.get('/users/agents').then(r => setAgents(r.data)).catch(() => {});
    }
  }, []); // eslint-disable-line

  const hasFilters = status || priority || search || productId || agentId || slaStatus || dateFrom || dateTo;

  return (
    <div className="border-b" style={{ borderColor: 'var(--border-color)' }}>
      {/* Main filter row */}
      <div className="flex flex-wrap items-center gap-2 px-4 py-3">

        {/* Search */}
        <SearchInput key={search || '__empty__'} value={search} onChange={onSearch} placeholder={t('ticket.filters.searchPlaceholder')} />

        {/* Status */}
        {!hideStatus && (
          <FilterSelect
            value={status} onChange={onStatus}
            placeholder={t('ticket.filters.allStatuses')}
            options={STATUSES.map(s => ({ value: s, label: s.replace(/_/g, ' ') }))}
          />
        )}

        {/* Priority */}
        <FilterSelect
          value={priority} onChange={onPriority}
          placeholder={t('ticket.filters.allPriorities')}
          options={PRIORITIES.map(p => ({ value: p, label: p }))}
        />

        {/* SLA Status */}
        <FilterSelect
          value={slaStatus} onChange={onSlaStatus}
          placeholder={t('ticket.filters.allSla')}
          options={SLA_STATUSES}
        />

        {/* Product */}
        {!hideProduct && products.length > 0 && (
          <FilterSelect
            value={productId} onChange={onProductId}
            placeholder={t('ticket.filters.allProducts')}
            options={products.map(p => ({ value: String(p.id), label: p.name }))}
          />
        )}

        {/* Agent */}
        {!hideAgent && agents.length > 0 && (
          <FilterSelect
            value={agentId} onChange={onAgentId}
            placeholder={t('ticket.filters.allAgents')}
            options={agents.map(a => ({ value: a.id, label: a.fullName }))}
          />
        )}

        {/* Date range */}
        <DateRangePicker
          dateFrom={dateFrom} dateTo={dateTo}
          onDateFrom={onDateFrom} onDateTo={onDateTo}
          datePresets={DATE_PRESETS}
          t={t}
        />

        {/* Clear */}
        {hasFilters && (
          <button
            type="button"
            onClick={onClear}
            className="inline-flex items-center gap-1 rounded-lg border px-2.5 py-1.5 text-xs font-medium transition-colors cursor-pointer hover:bg-danger-50 dark:hover:bg-danger-500/10"
            style={{ borderColor: 'var(--border-color)', color: 'var(--text-tertiary)' }}
          >
            <X className="h-3 w-3" />
            {t('ticket.filters.clearAll')}
          </button>
        )}
      </div>

      {/* Active filter chips */}
      {hasFilters && (
        <div className="flex flex-wrap gap-1.5 px-4 pb-2.5">
          {status    && <Chip label={t('ticket.filters.chipStatus',   { value: status.replace(/_/g, ' ') })} onRemove={() => onStatus('')} />}
          {priority  && <Chip label={t('ticket.filters.chipPriority', { value: priority })}                  onRemove={() => onPriority('')} />}
          {search    && <Chip label={t('ticket.filters.chipSearch',   { value: search })}                    onRemove={() => onSearch('')} />}
          {slaStatus && <Chip label={t('ticket.filters.chipSla',      { value: SLA_STATUSES.find(s => s.value === slaStatus)?.label ?? slaStatus })} onRemove={() => onSlaStatus('')} />}
          {productId && <Chip label={t('ticket.filters.chipProduct',  { value: products.find(p => String(p.id) === productId)?.name ?? productId })} onRemove={() => onProductId('')} />}
          {agentId   && <Chip label={t('ticket.filters.chipAgent',    { value: agents.find(a => a.id === agentId)?.fullName ?? agentId })}           onRemove={() => onAgentId('')} />}
          {(dateFrom || dateTo) && (
            <Chip
              label={`${t('ticket.filters.from')}: ${dateFrom ? new Date(dateFrom).toLocaleDateString() : '…'} → ${dateTo ? new Date(dateTo).toLocaleDateString() : '…'}`}
              onRemove={() => { onDateFrom(''); onDateTo(''); }}
            />
          )}
        </div>
      )}
    </div>
  );
}

// ── Sub-components ────────────────────────────────────────────────────────────

function SearchInput({ value, onChange, placeholder }) {
  const [local, setLocal] = useState(value ?? '');
  const timer = useRef(null);

  const handleChange = (e) => {
    const v = e.target.value;
    setLocal(v);
    clearTimeout(timer.current);
    timer.current = setTimeout(() => onChange(v), 350);
  };

  return (
    <div className="relative">
      <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2"
        style={{ color: 'var(--text-tertiary)' }} />
      <input
        type="text"
        value={local}
        onChange={handleChange}
        placeholder={placeholder}
        className="rounded-lg border pl-8 pr-3 py-1.5 text-xs outline-none transition-all focus:ring-2 w-44"
        style={{
          backgroundColor: local ? 'rgba(59,130,246,0.06)' : 'var(--bg-input)',
          borderColor:     local ? '#3b82f6'               : 'var(--border-color)',
          color:           'var(--text-primary)',
        }}
      />
      {local && (
        <button type="button" onClick={() => { setLocal(''); onChange(''); }}
          className="absolute right-2 top-1/2 -translate-y-1/2 cursor-pointer"
          style={{ color: 'var(--text-tertiary)' }}>
          <X className="h-3 w-3" />
        </button>
      )}
    </div>
  );
}

function FilterSelect({ value, onChange, placeholder, options }) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="rounded-lg border px-2.5 py-1.5 text-xs outline-none cursor-pointer transition-all focus:ring-2"
      style={{
        backgroundColor: value ? 'rgba(59,130,246,0.08)' : 'var(--bg-input)',
        borderColor:     value ? '#3b82f6'               : 'var(--border-color)',
        color:           value ? '#2563eb'               : 'var(--text-secondary)',
      }}
    >
      <option value="">{placeholder}</option>
      {options.map(o => (
        <option key={o.value} value={o.value}>{o.label}</option>
      ))}
    </select>
  );
}

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
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      const end = new Date();
      end.setHours(23, 59, 59, 999);
      onDateFrom(today.toISOString());
      onDateTo(end.toISOString());
    } else {
      const end = new Date();
      const start = new Date();
      start.setDate(start.getDate() - days);
      start.setHours(0, 0, 0, 0);
      onDateFrom(start.toISOString());
      onDateTo(end.toISOString());
    }
    setOpen(false);
  };

  const toInputValue = (iso) => {
    if (!iso) return '';
    return iso.slice(0, 10); // yyyy-mm-dd
  };

  const fromInput = (dateStr, isEnd) => {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    if (isEnd) d.setHours(23, 59, 59, 999);
    else d.setHours(0, 0, 0, 0);
    return d.toISOString();
  };

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen(v => !v)}
        className="inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1.5 text-xs cursor-pointer transition-all"
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

          {/* Presets */}
          <p className="mb-2 text-[10px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-tertiary)' }}>{t('ticket.filters.quickSelect')}</p>
          <div className="grid grid-cols-2 gap-1 mb-3">
            {datePresets.map(p => (
              <button key={p.label} type="button" onClick={() => applyPreset(p.days)}
                className="rounded-lg border px-2 py-1 text-xs cursor-pointer transition-colors hover:bg-primary-50 dark:hover:bg-primary-500/10 text-left"
                style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>
                {p.label}
              </button>
            ))}
          </div>

          {/* Custom range */}
          <p className="mb-1.5 text-[10px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-tertiary)' }}>{t('ticket.filters.customRange')}</p>
          <div className="space-y-1.5">
            <div>
              <label className="text-[10px] mb-0.5 block" style={{ color: 'var(--text-tertiary)' }}>{t('ticket.filters.from')}</label>
              <input type="date" value={toInputValue(dateFrom)}
                onChange={e => onDateFrom(fromInput(e.target.value, false))}
                className="w-full rounded-lg border px-2 py-1 text-xs outline-none"
                style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }} />
            </div>
            <div>
              <label className="text-[10px] mb-0.5 block" style={{ color: 'var(--text-tertiary)' }}>{t('ticket.filters.to')}</label>
              <input type="date" value={toInputValue(dateTo)}
                onChange={e => onDateTo(fromInput(e.target.value, true))}
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

function Chip({ label, onRemove }) {
  return (
    <span className="inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[11px] font-medium"
      style={{ backgroundColor: 'rgba(59,130,246,0.08)', borderColor: 'rgba(59,130,246,0.2)', color: '#2563eb' }}>
      {label}
      <button type="button" onClick={onRemove} className="cursor-pointer hover:opacity-70">
        <X className="h-2.5 w-2.5" />
      </button>
    </span>
  );
}
