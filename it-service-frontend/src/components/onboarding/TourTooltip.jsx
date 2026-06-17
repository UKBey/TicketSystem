import { useTranslation } from 'react-i18next';
import { ChevronLeft, ChevronRight, X, MousePointerClick } from 'lucide-react';

/**
 * Joyride özel tooltip bileşeni. Akış kontrolü (Skip/Back/Next/Finish) tamamen buradan
 * yürütülür — handler'lar `step.data` üzerinden gelir (OnboardingTour tarafından enjekte).
 *
 * Davranış:
 *  - Skip her adımda görünür.
 *  - Geri (Back) ilk adımda şeffaf (görünmez ama yer kaplar), diğerlerinde görünür.
 *  - İleri (Next) son adımda "Başlayalım" olur.
 *  - clickHint adımlarında "buraya tıkla" rozeti gösterilir (kullanıcı tıklayamaz; İleri'de
 *    hedefe tıklama animasyonu oynar).
 */
export default function TourTooltip({ step, tooltipProps }) {
  const { t } = useTranslation();
  const d = step.data || {};
  const { onSkip, onBack, onNext, isFirst, isLast, index, total, clickHint } = d;

  return (
    <div
      {...tooltipProps}
      className="w-[340px] max-w-[88vw] rounded-2xl overflow-hidden"
      style={{ backgroundColor: 'var(--bg-surface)', boxShadow: '0 20px 50px rgba(0,0,0,0.35)' }}
    >
      {/* Header: adım sayacı + skip */}
      <div className="flex items-center justify-between px-4 pt-3.5">
        <div className="flex items-center gap-1.5">
          {Array.from({ length: total }).map((_, i) => (
            <div
              key={i}
              className="rounded-full transition-all duration-300"
              style={{
                height: '5px',
                width: i === index ? '18px' : '5px',
                backgroundColor: i === index ? '#6366f1' : i < index ? '#a5b4fc' : 'var(--border-color)',
              }}
            />
          ))}
        </div>
        <button
          onClick={onSkip}
          className="flex items-center gap-1 rounded-lg px-2 py-1 text-[11px] font-medium transition-opacity hover:opacity-80"
          style={{ color: 'var(--text-tertiary)' }}
        >
          <X className="h-3 w-3" />
          {t('onboarding.controls.skip')}
        </button>
      </div>

      {/* Body */}
      <div className="px-4 py-3">
        {clickHint && (
          <div
            className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[10px] font-semibold mb-2"
            style={{ backgroundColor: '#e0e7ff', color: '#4338ca' }}
          >
            <MousePointerClick className="h-3 w-3" />
            {t('onboarding.controls.clickHint')}
          </div>
        )}
        {d.title && (
          <h3 className="text-base font-bold mb-1" style={{ color: 'var(--text-primary)' }}>{d.title}</h3>
        )}
        {d.body && (
          <p className="text-sm leading-relaxed" style={{ color: 'var(--text-secondary)' }}>{d.body}</p>
        )}
      </div>

      {/* Footer */}
      <div className="flex items-center justify-between px-4 py-3" style={{ borderTop: '1px solid var(--border-color)' }}>
        <button
          onClick={onBack}
          disabled={isFirst}
          className="flex items-center gap-1 rounded-lg px-3 py-2 text-sm font-medium transition-all"
          style={{
            color: isFirst ? 'transparent' : 'var(--text-secondary)',
            backgroundColor: isFirst ? 'transparent' : 'var(--bg-surface-secondary)',
            pointerEvents: isFirst ? 'none' : 'auto',
          }}
        >
          <ChevronLeft className="h-4 w-4" />
          {t('onboarding.controls.back')}
        </button>

        <button
          onClick={onNext}
          className="flex items-center gap-1.5 rounded-lg px-5 py-2 text-sm font-semibold text-white transition-opacity hover:opacity-90"
          style={{ background: 'linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%)' }}
        >
          {isLast ? t('onboarding.controls.finish') : t('onboarding.controls.next')}
          <ChevronRight className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}
