import { ArrowUp, ArrowDown, ArrowUpDown } from 'lucide-react';

/**
 * Tıklanınca sıralayan tablo başlığı (th). Tüm tablo sayfalarında (bilet listeleri,
 * kullanıcı yönetimi) paylaşılan tek kaynak — daha önce TicketTable ve ProductPage
 * içinde ayrı ayrı kopyalanmıştı.
 *
 * @param {string} field        bu sütunun sıralama alanı (backend sortBy değeri)
 * @param {string} label        başlık metni
 * @param {string} sortBy       o an aktif sıralama alanı
 * @param {string} sortDir      'asc' | 'desc'
 * @param {Function} [onSort]   (field) => void — verilmezse başlık sıralanamaz (düz th)
 * @param {boolean} [invertArrow] priority gibi alanlarda görsel ok yönünü tersine çevirir
 *                                ("asc" backend'de LOW→CRITICAL demek ama kullanıcı
 *                                "yukarı ok = en yüksek üstte" bekler)
 * @param {'left'|'right'} [align] başlık hizası — sağa yaslı işlem sütunları için 'right'
 * @param {React.ReactNode} [resizeHandle] sağ kenara yerleşen sütun genişlik tutamacı
 *                                         (TicketTable verir; verilmezse th sabit genişlikli)
 */
export default function SortableTh({ field, label, sortBy, sortDir, onSort, invertArrow = false, align = 'left', resizeHandle = null }) {
  const active = sortBy === field;

  const displayDir = invertArrow
    ? (sortDir === 'asc' ? 'desc' : 'asc')
    : sortDir;

  const Icon = active
    ? (displayDir === 'asc' ? ArrowUp : ArrowDown)
    : ArrowUpDown;

  const alignClass = align === 'right' ? 'text-right' : 'text-left';
  const baseClass = `${alignClass} px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b`;
  // position: relative — resizeHandle (absolute) bu th'e göre konumlanır.
  const baseStyle = { color: 'var(--text-tertiary)', borderColor: 'var(--border-color)', position: 'relative' };

  if (typeof onSort !== 'function') {
    return <th className={baseClass} style={baseStyle}>{label}{resizeHandle}</th>;
  }

  return (
    <th className={baseClass} style={baseStyle}>
      <button
        type="button"
        onClick={() => onSort(field)}
        className="inline-flex items-center gap-1 cursor-pointer hover:opacity-80 transition-opacity uppercase tracking-wider"
        style={{ color: active ? '#3b82f6' : 'var(--text-tertiary)' }}
      >
        {label}
        <Icon className="h-3 w-3" />
      </button>
      {resizeHandle}
    </th>
  );
}
