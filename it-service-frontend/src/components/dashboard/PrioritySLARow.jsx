function formatHours(value) {
  if (value === null || value === undefined) {
    return '0.0h';
  }

  return `${Number(value).toFixed(1)}h`;
}

function formatNumber(value) {
  return new Intl.NumberFormat('tr-TR').format(value ?? 0);
}

function getStatusTone(onTimePercentage) {
  if (onTimePercentage >= 90) {
    return 'good';
  }

  if (onTimePercentage >= 80) {
    return 'warning';
  }

  return 'danger';
}

export default function PrioritySLARow({ item, maxScaleHours, hovered, onHover, onLeave }) {
  const progressWidth = Math.min((Number(item.avgResolutionHours ?? 0) / Math.max(maxScaleHours, 1)) * 100, 100);
  const targetPosition = Math.min((Number(item.slaTargetHours ?? 0) / Math.max(maxScaleHours, 1)) * 100, 100);
  const tone = getStatusTone(Number(item.onTimePercentage ?? 0));
  const targetLabel = `${formatHours(item.avgResolutionHours)} / ${formatHours(item.slaTargetHours)} ${Number(item.avgResolutionHours ?? 0) <= Number(item.slaTargetHours ?? 0) ? '✅' : '⚠️'}`;

  return (
    <article
      className={`priority-sla-row ${hovered ? 'priority-sla-row--hovered' : ''}`}
      onMouseEnter={onHover}
      onMouseLeave={onLeave}
      onFocus={onHover}
      onBlur={onLeave}
      tabIndex={0}
      role="button"
      aria-label={`${item.priority} priority SLA metric row`}
      title={`${item.priority}: ${targetLabel}, breach ${formatNumber(item.breachCount)}, on-time ${Number(item.onTimePercentage ?? 0).toFixed(0)}%`}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <span className={`priority-sla-badge priority-sla-badge--${tone}`} />
            <h3 className="truncate text-sm font-bold tracking-wide" style={{ color: 'var(--text-primary)' }}>
              {item.priority}
            </h3>
          </div>
          <p className="mt-1 text-xs" style={{ color: 'var(--text-secondary)' }}>
            {formatNumber(item.ticketCount)} bilet · SLA hedefi {formatHours(item.slaTargetHours)}
          </p>
        </div>

        <div className="text-right">
          <div className="text-sm font-black" style={{ color: 'var(--text-primary)' }}>
            {formatHours(item.avgResolutionHours)}
          </div>
          <div className="text-[11px]" style={{ color: 'var(--text-tertiary)' }}>
            / {formatHours(item.slaTargetHours)}
          </div>
        </div>
      </div>

      <div className="priority-sla-track mt-4">
        <div className={`priority-sla-fill priority-sla-fill--${tone}`} style={{ width: `${progressWidth}%` }} />
        <span className="priority-sla-target" style={{ left: `${targetPosition}%` }} />
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-3 text-xs">
        <span className="priority-sla-chip" style={{ backgroundColor: 'var(--bg-surface-secondary)', color: 'var(--text-secondary)' }}>
          Breach: {formatNumber(item.breachCount)}
        </span>
        <span className="priority-sla-chip" style={{ backgroundColor: 'var(--bg-surface-secondary)', color: 'var(--text-secondary)' }}>
          On-time: {Number(item.onTimePercentage ?? 0).toFixed(0)}%
        </span>
        <span className="priority-sla-chip" style={{ backgroundColor: 'var(--bg-surface-secondary)', color: 'var(--text-secondary)' }}>
          Hedef: {formatHours(item.slaTargetHours)}
        </span>
      </div>

      <div className={`priority-sla-legend ${hovered ? 'priority-sla-legend--visible' : ''}`}>
        <div className="text-[11px] uppercase tracking-[0.18em]" style={{ color: 'var(--text-tertiary)' }}>
          Hover detay
        </div>
        <div className="mt-1 text-xs" style={{ color: 'var(--text-primary)' }}>
          {item.priority} · {formatHours(item.avgResolutionHours)} ortalama çözüm
        </div>
        <div className="mt-1 text-xs" style={{ color: 'var(--text-secondary)' }}>
          Breach {formatNumber(item.breachCount)} · On-time {Number(item.onTimePercentage ?? 0).toFixed(0)}%
        </div>
      </div>
    </article>
  );
}
