import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';

/**
 * Bir tetikleyici elemana (anchor) tutturulan, body'ye portal'lanan açılır panel.
 *
 * Neden portal + position:fixed: filtre dropdown'ları daha önce tetikleyicinin
 * `relative` sarmalayıcısı içinde `absolute` render ediliyordu; bilet listesi kartı
 * `overflow-hidden` olduğundan kısa tablolarda panel dikeyde kırpılıyordu. Body'ye
 * portal'layıp fixed konumlandırınca tüm overflow-hidden atalardan kaçar.
 *
 * Konumlandırma: varsayılan olarak anchor'ın altına açılır; alta sığmıyorsa üste
 * çevirir. Yatayda anchor'ın sol kenarına (align='left') ya da sağ kenarına
 * (align='right') hizalar; ekran kenarını taşarsa içeri çeker.
 *
 * @param {React.RefObject} anchorRef  konum referansı (tetikleyici sarmalayıcı)
 * @param {boolean} open
 * @param {Function} onClose           dışarı tıklama / Esc ile kapatma
 * @param {'left'|'right'} [align]
 * @param {string} [className]         panel kutusu sınıfları
 * @param {object} [style]
 */
export default function FloatingPanel({ anchorRef, open, onClose, align = 'left', className = '', style = {}, children }) {
  const panelRef = useRef(null);
  const [pos, setPos] = useState(null);

  // Anchor'a göre konumu hesapla; panel render olduktan sonra gerçek boyutuyla yeniden ölçer.
  useLayoutEffect(() => {
    if (!open) return undefined;

    const compute = () => {
      const anchor = anchorRef.current;
      const panel = panelRef.current;
      if (!anchor) return;
      const r = anchor.getBoundingClientRect();
      const pw = panel?.offsetWidth ?? r.width;
      const ph = panel?.offsetHeight ?? 0;
      const gap = 4;
      const margin = 8;
      const vw = window.innerWidth;
      const vh = window.innerHeight;

      // Yatay
      let left = align === 'right' ? r.right - pw : r.left;
      if (left + pw > vw - margin) left = vw - margin - pw;
      if (left < margin) left = margin;

      // Dikey: altı tercih et, sığmazsa ve üstte yer varsa üste çevir
      let top = r.bottom + gap;
      if (top + ph > vh - margin && r.top - gap - ph > margin) {
        top = r.top - gap - ph;
      }

      setPos((prev) => (prev && prev.left === left && prev.top === top ? prev : { left, top }));
    };

    compute();
    window.addEventListener('scroll', compute, true);
    window.addEventListener('resize', compute);
    return () => {
      window.removeEventListener('scroll', compute, true);
      window.removeEventListener('resize', compute);
    };
  }, [open, align, anchorRef]);

  // Dışarı tıklama + Esc — panel portal'da olduğundan hem anchor hem panel kontrol edilir
  useEffect(() => {
    if (!open) return undefined;
    const onDown = (e) => {
      if (panelRef.current?.contains(e.target)) return;
      if (anchorRef.current?.contains(e.target)) return;
      onClose();
    };
    const onKey = (e) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open, onClose, anchorRef]);

  if (!open) return null;

  return createPortal(
    <div
      ref={panelRef}
      className={className}
      style={{
        position: 'fixed',
        left: pos?.left ?? -9999,
        top: pos?.top ?? -9999,
        visibility: pos ? 'visible' : 'hidden',
        zIndex: 50,
        ...style,
      }}
    >
      {children}
    </div>,
    document.body,
  );
}
