import { useEffect, useRef, useState } from 'react';
import ResizeHandle from '../components/ResizeHandle';

// Bir sütun bu pikselin altına küçültülemez.
const MIN_COL_WIDTH = 60;

// localStorage'dan kayıtlı genişlikleri okur; bozuk/eski kayıtlara karşı yalnız
// pozitif sayısal değerleri alır (tarayıcı verisi her zaman güvenilmez).
function loadStoredWidths(storageKey) {
  if (!storageKey) return {};
  try {
    const parsed = JSON.parse(localStorage.getItem(storageKey));
    return Object.fromEntries(
      Object.entries(parsed ?? {}).filter(([, w]) => typeof w === 'number' && w >= MIN_COL_WIDTH),
    );
  } catch {
    return {};
  }
}

/**
 * Sürüklenerek ayarlanabilen tablo sütun genişlikleri — tüm liste tablolarında
 * paylaşılan tek kaynak. Genişlikler {@code storageKey} verilirse localStorage'da
 * kalıcı tutulur (tarayıcı profiline bağlı, backend'e gitmez); verilmezse yalnız
 * oturum boyunca state'te yaşar ve yenilemede {@code defaults}'a döner.
 *
 * Kullanım (tablo şablonu):
 *   const { tableWidth, handleFor, renderColgroup } = useColumnResize(DEFAULTS, columnOrder, 'colw:tickets');
 *   <table style={{ tableLayout: 'fixed', width: '100%', minWidth: `${tableWidth}px` }}>
 *     {renderColgroup()}
 *     ...<SortableTh resizeHandle={handleFor('id')} />...
 *
 * Son sütun sabit genişlik almaz (esner, kalan alanı doldurur) ve sürüklenemez.
 *
 * @param {Record<string, number>} defaults     sütun id → varsayılan px genişlik
 * @param {string[]} columnOrder                 sütun id'leri görünüm sırasıyla
 * @param {string} [storageKey]                  localStorage anahtarı (tablo başına benzersiz)
 * @returns {{ widthOf: (id:string)=>number, tableWidth: number, lastColId: string,
 *            handleFor: (id:string)=>React.ReactNode, renderColgroup: ()=>React.ReactNode }}
 */
export function useColumnResize(defaults, columnOrder, storageKey) {
  const [colWidths, setColWidths] = useState(() => loadStoredWidths(storageKey));
  // setColWidths closure'larda bayat kaldığı için güncel haritayı ref'te aynalıyoruz;
  // sürükleme bitişinde (onUp) localStorage'a bu ref yazılır.
  const colWidthsRef = useRef(colWidths);
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
      colWidthsRef.current = { ...colWidthsRef.current, [id]: next };
      setColWidths(colWidthsRef.current);
    };
    const onUp = () => {
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
      setResizing(false);
      // Her mousemove'da değil sürükleme bitince tek sefer yaz (gereksiz disk I/O olmasın).
      if (storageKey) {
        try {
          localStorage.setItem(storageKey, JSON.stringify(colWidthsRef.current));
        } catch { /* depolama dolu/kapalıysa sessizce vazgeç — genişlikler oturumda yine çalışır */ }
      }
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
