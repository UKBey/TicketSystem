/**
 * Liste gövdesini (tablo vb.) sarmalayan yükleme katmanı.
 *
 * - İlk yüklemede (`initial`) ortalı bir spinner gösterir.
 * - Sonraki refetch'lerde (filtre/sıralama/sayfa değişimi) önceki içeriği ekranda
 *   TUTAR; hafifçe soluklaştırıp üstte ince bir kayan ilerleme çubuğu gösterir.
 *   Böylece tablo DOM'dan sökülmediği için sayfa çökmez, scroll yukarı zıplamaz ve
 *   boş liste anlık olarak görünmez — refetch sırasındaki "çirkin" titreme biter.
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
      {/* Üstte ince kayan ilerleme çubuğu (refetch sürerken) */}
      {loading && (
        <div className="absolute inset-x-0 top-0 z-10 h-[3px] overflow-hidden pointer-events-none"
          style={{ backgroundColor: 'rgba(59,130,246,0.15)' }}>
          <div className="animate-indeterminate" style={{ backgroundColor: '#3b82f6' }} />
        </div>
      )}
      <div
        style={{
          opacity: loading ? 0.6 : 1,
          transition: 'opacity 200ms ease',
          pointerEvents: loading ? 'none' : 'auto',
        }}
      >
        {children}
      </div>
    </div>
  );
}
