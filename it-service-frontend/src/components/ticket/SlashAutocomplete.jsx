import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { pickContent, fillPlaceholders } from '../../utils/cannedResponses';

/**
 * Inline `/`-triggered autocomplete dropdown rendered above the composer. Presentational:
 * the parent owns the matches, the active index and keyboard handling (so the same keystrokes
 * that drive the textarea drive this list). Shares the picker's data source.
 */
export default function SlashAutocomplete({
  matches = [],
  activeIndex = 0,
  ctx = {},
  previewLang = 'en',
  onSelect,
  onHover,
  anchorRef = null,
}) {
  const { t } = useTranslation();
  const listRef = useRef(null);

  useEffect(() => {
    const el = listRef.current?.querySelector(`[data-idx="${activeIndex}"]`);
    el?.scrollIntoView({ block: 'nearest' });
  }, [activeIndex]);

  return (
    <div
      ref={(node) => { listRef.current = node; if (anchorRef) anchorRef(node); }}
      className="z-50 max-h-60 w-[min(92vw,26rem)] overflow-y-auto rounded-xl border animate-fade-in"
      style={{ position: 'fixed', backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-xl)' }}
    >
      {matches.length === 0 ? (
        <p className="px-3 py-3 text-sm" style={{ color: 'var(--text-tertiary)' }}>
          {t('cannedResponses.slashNoResults')}
        </p>
      ) : (
        <ul role="listbox" aria-label={t('cannedResponses.title')}>
          {matches.map((tpl, idx) => {
            const { content } = pickContent(tpl, previewLang);
            const preview = fillPlaceholders(content, ctx);
            const active = idx === activeIndex;
            return (
              <li
                key={tpl.id}
                data-idx={idx}
                role="option"
                aria-selected={active}
                onMouseEnter={() => onHover?.(idx)}
                onMouseDown={(e) => { e.preventDefault(); onSelect?.(tpl); }}
                className="flex cursor-pointer items-start gap-2 px-3 py-2 transition-colors"
                style={{ backgroundColor: active ? 'var(--bg-surface-secondary)' : 'transparent' }}
              >
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-1.5 flex-wrap">
                    <span className="text-sm font-semibold break-words" style={{ color: 'var(--text-primary)' }}>
                      {tpl.title}
                    </span>
                    {tpl.shortcut && (
                      <span className="text-[11px] font-mono" style={{ color: 'var(--text-tertiary)' }}>/{tpl.shortcut}</span>
                    )}
                    <span
                      className="inline-flex items-center rounded-full px-1.5 py-0.5 text-[9px] font-bold uppercase"
                      style={tpl.scope === 'SHARED'
                        ? { backgroundColor: 'rgba(59,130,246,0.15)', color: '#3b82f6' }
                        : { backgroundColor: 'rgba(148,163,184,0.18)', color: 'var(--text-secondary)' }}
                    >
                      {tpl.scope === 'SHARED' ? t('cannedResponses.badgeShared') : t('cannedResponses.badgePersonal')}
                    </span>
                  </div>
                  <p className="mt-0.5 text-xs line-clamp-1 break-words" style={{ color: 'var(--text-secondary)' }}>
                    {preview}
                  </p>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
