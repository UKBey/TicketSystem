/**
 * Liste gövdesini (tablo vb.) sarmalayan yükleme katmanı.
 *
 * - İlk yüklemede (`initial`) ortalı bir spinner gösterir.
 * - Sonraki refetch'lerde (filtre/sıralama/sayfa değişimi) önceki içeriği ekranda
 *   TUTAR; hafifçe soluklaştırıp üstte küçük bir spinner gösterir. Böylece tablo
 *   DOM'dan sökülmediği için sayfa çökmez, scroll yukarı zıplamaz ve boş liste
 *   anlık olarak görünmez — refetch sırasındaki "çirkin" titreme ortadan kalkar.
 *
 * @param {boolean} initial  henüz hiç veri yokken (ilk yükleme) true
 * @param {boolean} loading  herhangi bir istek uçuşurken true
 * @param {React.ReactNode} children  gösterilecek liste/tablo
 */
export default function ListLoadingOverlay({ initial, loading, children }) {
  if (initial) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="h-8 w-8 rounded-full border-[3px] animate-spin"
          style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
      </div>
    );
  }

  return (
    <div className="relative">
      <div
        style={{
          opacity: loading ? 0.45 : 1,
          transition: 'opacity 150ms ease',
          pointerEvents: loading ? 'none' : 'auto',
        }}
      >
        {children}
      </div>
      {loading && (
        <div className="absolute inset-x-0 top-0 flex justify-center pt-6 pointer-events-none">
          <div className="h-7 w-7 rounded-full border-[3px] animate-spin"
            style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
        </div>
      )}
    </div>
  );
}
