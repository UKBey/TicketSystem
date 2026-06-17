import { forwardRef } from 'react';

/**
 * Tek tip buton soyutlamasi — varyant/boyut + tiklamada ripple (dalga) efekti.
 *
 * Stiller projedeki mevcut dolgulu CTA kaliplarindan turetildi; ripple yalnizca
 * gorsel olarak anlamli oldugu icin varsayilan acik ama `ripple={false}` ile
 * kapatilabilir. `prefers-reduced-motion` saygi gosterilir (span hic eklenmez).
 *
 * Ornek:
 *   <Button variant="primary" onClick={save}>Kaydet</Button>
 *   <Button variant="secondary" onClick={onClose}>Iptal</Button>
 *   <Button variant="danger" size="sm" fullWidth>Sil</Button>
 */

const BASE =
  'relative overflow-hidden inline-flex items-center justify-center gap-2 rounded-lg font-semibold ' +
  'transition-colors cursor-pointer focus:outline-none disabled:opacity-50 disabled:cursor-not-allowed';

const VARIANTS = {
  primary: 'text-white bg-primary-500 hover:bg-primary-600 focus:ring-4 focus:ring-primary-500/30',
  danger: 'text-white bg-danger-500 hover:bg-danger-600 focus:ring-4 focus:ring-danger-500/30',
  success: 'text-white bg-emerald-600 hover:bg-emerald-700 focus:ring-4 focus:ring-emerald-500/30',
  secondary: 'border hover:bg-black/5 dark:hover:bg-white/5 focus:ring-4 focus:ring-primary-500/20',
};

const SIZES = {
  sm: 'px-3 py-1.5 text-xs',
  md: 'px-4 py-2 text-sm',
  lg: 'px-6 py-3 text-sm',
};

// secondary varyanti tema CSS degiskenleriyle stillenir (border/metin rengi).
const SECONDARY_STYLE = {
  borderColor: 'var(--border-color)',
  color: 'var(--text-secondary)',
  backgroundColor: 'transparent',
};

function spawnRipple(event) {
  const button = event.currentTarget;
  const rect = button.getBoundingClientRect();
  const diameter = Math.max(rect.width, rect.height);
  const span = document.createElement('span');
  span.className = 'btn-ripple';
  span.style.width = span.style.height = `${diameter}px`;
  span.style.left = `${event.clientX - rect.left - diameter / 2}px`;
  span.style.top = `${event.clientY - rect.top - diameter / 2}px`;
  span.addEventListener('animationend', () => span.remove());
  button.appendChild(span);
}

const Button = forwardRef(function Button(
  {
    variant = 'primary',
    size = 'md',
    fullWidth = false,
    ripple = true,
    type = 'button',
    className = '',
    style,
    onClick,
    disabled,
    children,
    ...props
  },
  ref,
) {
  const variantClass = VARIANTS[variant] ?? VARIANTS.primary;
  const sizeClass = SIZES[size] ?? SIZES.md;
  const widthClass = fullWidth ? 'w-full' : '';
  const mergedStyle = variant === 'secondary' ? { ...SECONDARY_STYLE, ...style } : style;

  const reducedMotion =
    typeof window !== 'undefined' &&
    window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

  // Ripple basma aninda (pointerdown) cikar — onClick degil; cunku onClick'teki
  // yan etki (modal acma / yonlendirme / modal kapama) cogu CTA'da dalgayi
  // gorunmeden ortuyor. Basinca tetiklemek dalganin hep gorunmesini saglar.
  const handlePointerDown = (event) => {
    if (ripple && !disabled && !reducedMotion && event.button === 0) {
      spawnRipple(event);
    }
  };

  return (
    <button
      ref={ref}
      type={type}
      disabled={disabled}
      onPointerDown={handlePointerDown}
      onClick={onClick}
      style={mergedStyle}
      className={`${BASE} ${variantClass} ${sizeClass} ${widthClass} ${className}`.trim()}
      {...props}
    >
      {children}
    </button>
  );
});

export default Button;
