import { useEffect, useState } from 'react';
import ResizeHandle from '../components/ResizeHandle';

// Bir sütun bu pikselin altına küçültülemez.
const MIN_COL_WIDTH = 60;

/**
 * Sürüklenerek ayarlanabilen tablo sütun genişlikleri — tüm liste tablolarında
 * paylaşılan tek kaynak. Genişlikler yalnızca oturum boyunca state'te tutulur
 * (kalıcı değil — sayfa yenilenince {@code defaults}'a döner).
 *
 * Kullanım (tablo şablonu):
 *   const { tableWidth, handleFor, renderColgroup } = useColumnResize(DEFAULTS, columnOrder);
 *   <table style={{ tableLayout: 'fixed', width: '100%', minWidth: `${tableWidth}px` }}>
 *     {renderColgroup()}
 *     ...<SortableTh resizeHandle={handleFor('id')} />...
 *
 * Son sütun sabit genişlik almaz (esner, kalan alanı doldurur) ve sürüklenemez.
 *
 * @param {Record<string, number>} defaults     sütun id → varsayılan px genişlik
 * @param {string[]} columnOrder                 sütun id'leri görünüm sırasıyla
 * @returns {{ widthOf: (id:string)=>number, tableWidth: number, lastColId: string,
 *            handleFor: (id:string)=>React.ReactNode, renderColgroup: ()=>React.ReactNode }}
 */
export function useColumnResize(defaults, columnOrder) {
  const [colWidths, setColWidths] = useState({});
  const [resizing, setResizing] = useState(false);

  // Sürükleme sürerken metin seçimini ve imleci global olarak ayarla. DOM mutasyonu
  // efekt içinde olmalı (react-hooks/immutability), o yüzden body stilini burada yönetiyoruz.
  useEffect(() => {
    if (!resizing) return undefined;
    const prevSelect = document.body.style.userSelect;
    const prevCursor = document.body.style.cursor;
    document.body.style.userSelect = 'none';
    document.body.style.cursor = 'col-resize';
    return () => {
      document.body.style.userSelect = prevSelect;
      document.body.style.cursor = prevCursor;
    };
  }, [resizing]);

  const lastColId = columnOrder[columnOrder.length - 1];
  const widthOf = (id) => colWidths[id] ?? defaults[id];
  const tableWidth = columnOrder.reduce((sum, id) => sum + widthOf(id), 0);

  // Tutamaca basılınca, bırakana kadar fareyi izleyip yalnızca o sütunun genişliğini
  // günceller. Dinleyiciler aynı kapanış içinde eklenip kaldırıldığı için referanslar
  // eşleşir (useCallback/ref gerekmez).
  const startResize = (e, id) => {
    e.preventDefault();
    e.stopPropagation();
    const startX = e.clientX;
    const startW = widthOf(id);
    const onMove = (ev) => {
      const next = Math.max(MIN_COL_WIDTH, startW + (ev.clientX - startX));
      setColWidths((prev) => ({ ...prev, [id]: next }));
    };
    const onUp = () => {
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
      setResizing(false);
    };
    setResizing(true);
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  };

  // Son (esnek) sütun sürüklenemez — kalan alanı kendisi doldurur.
  const handleFor = (id) => (id === lastColId ? null : <ResizeHandle onStart={(e) => startResize(e, id)} />);

  // colgroup'u tek yerden üret: son sütun genişliksiz (esner), diğerleri sabit px.
  const renderColgroup = () => (
    <colgroup>
      {columnOrder.map((id) => (
        id === lastColId ? <col key={id} /> : <col key={id} style={{ width: `${widthOf(id)}px` }} />
      ))}
    </colgroup>
  );

  return { widthOf, tableWidth, lastColId, handleFor, renderColgroup };
}
