import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../../context/ThemeContext';
import {
  FileText, UserCheck, UserMinus, UserPlus, CheckCircle2,
  RotateCcw, Clock, Play, XCircle, ArrowRight, ChevronDown, Flag, Hash, Star,
} from 'lucide-react';
import { formatDate } from '../../utils/ticketFormatters';
import { StatusBadge, PriorityBadge } from '../Badges';

// ---- per-action visual config ------------------------------------------------

const ACTION_CONFIG = {
  CREATE:        { icon: FileText,    color: '#3b82f6', bg: 'rgba(59,130,246,0.12)',  labelKey: 'auditCreated'     },
  CLAIM:         { icon: UserCheck,   color: '#8b5cf6', bg: 'rgba(139,92,246,0.12)', labelKey: 'auditClaimed'     },
  UNCLAIM:       { icon: UserMinus,   color: '#f59e0b', bg: 'rgba(245,158,11,0.12)', labelKey: 'auditReleased'    },
  ASSIGN:        { icon: UserPlus,    color: '#8b5cf6', bg: 'rgba(139,92,246,0.12)', labelKey: 'auditAssigned'    },
  RESOLVE:       { icon: CheckCircle2,color: '#10b981', bg: 'rgba(16,185,129,0.12)', labelKey: 'auditResolved'    },
  REOPEN:        { icon: RotateCcw,   color: '#f59e0b', bg: 'rgba(245,158,11,0.12)', labelKey: 'auditReopened'    },
  WAITING:       { icon: Clock,       color: '#eab308', bg: 'rgba(234,179,8,0.12)',  labelKey: 'auditWaiting'     },
  RESUME:        { icon: Play,        color: '#3b82f6', bg: 'rgba(59,130,246,0.12)', labelKey: 'auditResumed'     },
  CLOSE:         { icon: XCircle,     color: '#ef4444', bg: 'rgba(239,68,68,0.12)',  labelKey: 'auditClosed'      },
  STATUS_CHANGE:   { icon: ArrowRight,  color: '#6b7280', bg: 'rgba(107,114,128,0.12)', labelKey: 'auditStatusChange'   },
  PRIORITY_CHANGE: { icon: Flag,        color: '#ec4899', bg: 'rgba(236,72,153,0.12)',  labelKey: 'auditPriorityChange' },
  TOPIC_CHANGE:    { icon: Hash,        color: '#0ea5e9', bg: 'rgba(14,165,233,0.12)',  labelKey: 'auditTopicChange'    },
  CSAT_SUBMITTED:  { icon: Star,        color: '#f59e0b', bg: 'rgba(245,158,11,0.12)',  labelKey: 'auditCsat'           },
};

const DEFAULT_CONFIG = { icon: ArrowRight, color: '#6b7280', bg: 'rgba(107,114,128,0.12)', labelKey: 'auditUpdated' };

// Status/priority geçiş etiketleri için paylaşılan StatusBadge/PriorityBadge kullanılır
// (renkler her ekranda tek kaynaktan — constants/ticketColors).

// ---- AuditTimeline -----------------------------------------------------------

export default function AuditTimeline({ auditLogs }) {
  const { t }        = useTranslation();
  const { theme }    = useTheme();
  const isDark       = theme === 'dark';
  const [expanded, setExpanded] = useState(true);

  if (!auditLogs || auditLogs.length === 0) return null;

  // Chronological order (oldest first)
  const sorted = [...auditLogs].sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));

  return (
    <div
      className="rounded-xl border overflow-hidden"
      style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}
    >
      {/* Header */}
      <button
        className="w-full flex items-center justify-between px-5 py-3.5 border-b cursor-pointer transition-colors hover:bg-[var(--bg-surface-secondary)]"
        style={{ borderColor: expanded ? 'var(--border-color)' : 'transparent' }}
        onClick={() => setExpanded((v) => !v)}
      >
        <div className="flex items-center gap-2.5">
          <div
            className="flex h-6 w-6 items-center justify-center rounded-lg"
            style={{ backgroundColor: 'rgba(59,130,246,0.12)' }}
          >
            <Clock className="h-3.5 w-3.5" style={{ color: '#3b82f6' }} />
          </div>
          <span className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
            {t('ticketDetail.auditHistory')}
          </span>
          <span
            className="inline-flex items-center justify-center rounded-full px-2 py-0.5 text-[11px] font-bold"
            style={{ backgroundColor: 'var(--bg-surface-secondary)', color: 'var(--text-tertiary)' }}
          >
            {sorted.length}
          </span>
        </div>
        <ChevronDown
          className="h-4 w-4 shrink-0 transition-transform"
          style={{ color: 'var(--text-tertiary)', transform: expanded ? 'rotate(0deg)' : 'rotate(-90deg)' }}
        />
      </button>

      {/* Timeline body with smooth open/close animation */}
      <div
        style={{
          maxHeight: expanded ? '540px' : '0',
          overflow: 'hidden',
          transition: 'max-height 0.3s ease-out, opacity 0.3s ease-out',
          opacity: expanded ? 1 : 0,
        }}
      >
        <div className="px-4 sm:px-5 py-4 max-h-[500px] overflow-y-auto">
          <div className="relative">
            {/* Vertical connector line */}
            <div
              className="absolute left-[15px] top-[22px] bottom-[22px] w-px"
              style={{ backgroundColor: 'var(--border-color)' }}
            />

            <div className="space-y-1">
              {sorted.map((entry, idx) => {
                const cfg    = ACTION_CONFIG[entry.actionType] ?? DEFAULT_CONFIG;
                const Icon   = cfg.icon;
                const isLast = idx === sorted.length - 1;

                return (
                  <div key={entry.id} className="relative flex gap-3.5 group">
                    {/* Icon dot */}
                    <div
                      className="relative z-10 flex h-[30px] w-[30px] shrink-0 items-center justify-center rounded-full shadow-sm mt-1"
                      style={{ backgroundColor: cfg.bg, border: `2px solid ${cfg.color}22` }}
                    >
                      <Icon className="h-3.5 w-3.5" style={{ color: cfg.color }} />
                    </div>

                    {/* Content card */}
                    <div
                      className={`flex-1 min-w-0 rounded-xl px-3 sm:px-4 py-3 mb-3 transition-colors ${isLast ? '' : ''}`}
                      style={{ backgroundColor: isDark ? 'rgba(255,255,255,0.03)' : 'var(--bg-surface-secondary)', borderLeft: `3px solid ${cfg.color}55` }}
                    >
                      {/* Top row: badge + actor + timestamp */}
                      <div className="flex items-center justify-between gap-2 flex-wrap mb-1.5">
                        <div className="flex items-center gap-2 flex-wrap min-w-0">
                          <span
                            className="inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-[11px] font-bold"
                            style={{ backgroundColor: cfg.bg, color: cfg.color }}
                          >
                            <Icon className="h-2.5 w-2.5" />
                            {t(`ticketDetail.${cfg.labelKey}`)}
                          </span>
                          {entry.actorName && (
                            <span className="text-xs font-semibold break-words" style={{ color: 'var(--text-primary)' }}>
                              {entry.actorName}
                            </span>
                          )}
                        </div>
                        <span className="text-[11px]" style={{ color: 'var(--text-tertiary)' }}>
                          {formatDate(entry.createdAt)}
                        </span>
                      </div>

                      {/* State transition */}
                      {entry.actionType === 'CSAT_SUBMITTED' ? (
                        <div className="flex items-center gap-1.5 mb-1.5">
                          {[1, 2, 3, 4, 5].map((n) => (
                            <Star
                              key={n}
                              className="h-3.5 w-3.5"
                              style={{ color: '#f59e0b', fill: n <= Number(entry.newState) ? '#f59e0b' : 'transparent' }}
                            />
                          ))}
                          <span className="text-xs font-semibold ml-0.5" style={{ color: 'var(--text-secondary)' }}>
                            {entry.newState}/5
                          </span>
                        </div>
                      ) : entry.actionType === 'PRIORITY_CHANGE' ? (
                        entry.previousState && entry.newState && (
                          <div className="flex items-center flex-wrap gap-1.5 mb-1.5">
                            <PriorityBadge priority={entry.previousState} />
                            <ArrowRight className="h-3 w-3 shrink-0" style={{ color: 'var(--text-tertiary)' }} />
                            <PriorityBadge priority={entry.newState} />
                          </div>
                        )
                      ) : entry.actionType === 'TOPIC_CHANGE' ? (
                        entry.newState && (
                          <div className="flex items-center flex-wrap gap-1.5 mb-1.5 text-xs" style={{ color: 'var(--text-secondary)' }}>
                            {entry.previousState && (
                              <>
                                <span className="inline-flex items-center rounded-md px-2 py-0.5 text-[11px] font-medium break-words" style={{ backgroundColor: 'var(--bg-surface-secondary)', color: 'var(--text-secondary)' }}>
                                  {entry.previousState}
                                </span>
                                <ArrowRight className="h-3 w-3 shrink-0" style={{ color: 'var(--text-tertiary)' }} />
                              </>
                            )}
                            <span className="inline-flex items-center rounded-md px-2 py-0.5 text-[11px] font-semibold break-words" style={{ backgroundColor: 'rgba(14,165,233,0.15)', color: '#0ea5e9' }}>
                              {entry.newState}
                            </span>
                          </div>
                        )
                      ) : (
                        <>
                          {entry.previousState && entry.newState && (
                            <div className="flex items-center flex-wrap gap-1.5 mb-1.5">
                              <StatusBadge status={entry.previousState} />
                              <ArrowRight className="h-3 w-3 shrink-0" style={{ color: 'var(--text-tertiary)' }} />
                              <StatusBadge status={entry.newState} />
                            </div>
                          )}
                          {!entry.previousState && entry.newState && (
                            <div className="flex items-center flex-wrap gap-1.5 mb-1.5">
                              <StatusBadge status={entry.newState} />
                            </div>
                          )}
                        </>
                      )}

                      {/* Reason chip */}
                      {entry.reasonCode && (
                        <span
                          className="inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-semibold mt-1"
                          style={{ backgroundColor: cfg.bg, color: cfg.color }}
                        >
                          {t(`reasonCode.${entry.actionType}.${entry.reasonCode}`, {
                            defaultValue: entry.reasonCode,
                          })}
                        </span>
                      )}

                      {/* Note */}
                      {entry.note && (
                        <p
                          className="text-xs leading-relaxed mt-1.5 italic"
                          style={{ color: 'var(--text-secondary)' }}
                        >
                          &ldquo;{entry.note}&rdquo;
                        </p>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
