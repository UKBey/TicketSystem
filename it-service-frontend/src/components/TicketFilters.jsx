import { X } from 'lucide-react';

const STATUSES  = ['NEW', 'IN_PROGRESS', 'WAITING_FOR_CUSTOMER', 'RESOLVED', 'CLOSED'];
const PRIORITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];

/**
 * Compact filter bar for ticket list pages.
 *
 * Props:
 *   status        {string}
 *   priority      {string}
 *   onStatus      (v: string) => void
 *   onPriority    (v: string) => void
 *   hideStatus?   {boolean}   — hide status filter (e.g. Pool always NEW)
 *   hidePriority? {boolean}
 */
export default function TicketFilters({
  status, priority,
  onStatus, onPriority,
  hideStatus = false,
  hidePriority = false,
}) {
  const hasFilters = status || priority;

  return (
    <div className="flex flex-wrap items-center gap-2 px-4 py-3 border-b"
      style={{ borderColor: 'var(--border-color)' }}>

      {!hideStatus && (
        <FilterSelect
          value={status}
          onChange={onStatus}
          placeholder="All statuses"
          options={STATUSES}
        />
      )}

      {!hidePriority && (
        <FilterSelect
          value={priority}
          onChange={onPriority}
          placeholder="All priorities"
          options={PRIORITIES}
        />
      )}

      {hasFilters && (
        <button
          type="button"
          onClick={() => { onStatus(''); onPriority(''); }}
          className="inline-flex items-center gap-1 rounded-lg border px-2.5 py-1.5 text-xs font-medium transition-colors cursor-pointer hover:bg-danger-50 dark:hover:bg-danger-500/10"
          style={{ borderColor: 'var(--border-color)', color: 'var(--text-tertiary)' }}
        >
          <X className="h-3 w-3" />
          Clear
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
        borderColor:     value ? '#3b82f6'                : 'var(--border-color)',
        color:           value ? '#2563eb'                : 'var(--text-secondary)',
      }}
    >
      <option value="">{placeholder}</option>
      {options.map((o) => (
        <option key={o} value={o}>{o.replace(/_/g, ' ')}</option>
      ))}
    </select>
  );
}
