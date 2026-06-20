/**
 * Tablo başlığının (th) sağ kenarındaki sütun-genişlik sürükleme tutamacı. 12px'lik
 * tıklama alanı. Keşfedilebilir olması için varsayılan durumda sönük bir dikey çizgi +
 * tutuş noktaları (grip) gösterir; üzerine gelince primary renge belirginleşir.
 * mousedown sıralamayı tetiklemesin diye event durdurulur. {@link useColumnResize}
 * hook'u ile kullanılır; saran th `position: relative` olmalıdır (SortableTh sağlar).
 */
import { GripVertical } from 'lucide-react';
import { useTranslation } from 'react-i18next';

export default function ResizeHandle({ onStart }) {
  const { t } = useTranslation();
  return (
    <span
      role="separator"
      aria-orientation="vertical"
      onMouseDown={onStart}
      onClick={(e) => e.stopPropagation()}
      className="group absolute top-0 right-0 h-full flex items-center justify-center select-none"
      style={{ width: '12px', cursor: 'col-resize', touchAction: 'none' }}
      title={t('common.resizeColumn')}
    >
      {/* Her zaman görünen sönük dikey çizgi — sütun sınırını işaret eder. */}
      <span
        className="absolute right-0 h-1/2 w-px transition-colors group-hover:bg-primary-500"
        style={{ backgroundColor: 'var(--border-color)' }}
      />
      {/* Hover'da beliren tutuş ikonu — sürüklenebildiğini netleştirir. */}
      <GripVertical
        className="relative h-3.5 w-3.5 opacity-0 transition-opacity group-hover:opacity-100"
        style={{ color: 'var(--text-tertiary)' }}
      />
    </span>
  );
}
