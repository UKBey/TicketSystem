/**
 * Tablo başlığının (th) sağ kenarındaki sütun-genişlik sürükleme tutamacı. 10px'lik
 * tıklama alanı, üzerine gelince ince bir dikey çizgi gösterir. mousedown sıralamayı
 * tetiklemesin diye event durdurulur. {@link useColumnResize} hook'u ile birlikte
 * kullanılır; saran th `position: relative` olmalıdır (SortableTh zaten sağlar).
 */
import { useTranslation } from 'react-i18next';

export default function ResizeHandle({ onStart }) {
  const { t } = useTranslation();
  return (
    <span
      role="separator"
      aria-orientation="vertical"
      onMouseDown={onStart}
      onClick={(e) => e.stopPropagation()}
      className="group absolute top-0 right-0 h-full flex items-center justify-end select-none"
      style={{ width: '10px', cursor: 'col-resize', touchAction: 'none' }}
      title={t('common.resizeColumn')}
    >
      <span className="h-1/2 w-px bg-transparent group-hover:bg-primary-400 transition-colors" />
    </span>
  );
}
