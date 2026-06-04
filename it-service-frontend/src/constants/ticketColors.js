/**
 * Bilet STATUS ve PRIORITY renkleri için TEK kaynak. Tüm ekranlar (badge'ler,
 * dashboard grafikleri, audit timeline, CSAT kartları, öncelik seçici) buradan
 * beslenir ki bir status/priority her yerde aynı renkte görünsün.
 *
 * Her giriş:
 *   light / dark  → badge arka planı + yazı rengi (tema duyarlı pastel rozet)
 *   solid         → grafik/nokta/uyarı için tek dolu hex (pie dilimi, legend noktası)
 *   chipText      → açık-zeminli kart/çip bağlamlarında okunaklı koyu yazı rengi
 *                   (CRITICAL badge'i dolu kırmızı + beyaz yazı olduğu için light.color
 *                    açık zeminde kullanılamaz; chipText bu durumu çözer)
 */

export const STATUS_COLORS = {
  NEW:                  { light: { bg: '#dbeafe', color: '#1e40af' }, dark: { bg: 'rgba(59,130,246,0.2)',  color: '#93c5fd' }, solid: '#3b82f6', chipText: '#1e40af' },
  IN_PROGRESS:          { light: { bg: '#fef3c7', color: '#92400e' }, dark: { bg: 'rgba(245,158,11,0.2)', color: '#fde68a' }, solid: '#f59e0b', chipText: '#92400e' },
  WAITING_FOR_CUSTOMER: { light: { bg: '#ede9fe', color: '#5b21b6' }, dark: { bg: 'rgba(139,92,246,0.2)', color: '#c4b5fd' }, solid: '#8b5cf6', chipText: '#5b21b6' },
  RESOLVED:             { light: { bg: '#dcfce7', color: '#166534' }, dark: { bg: 'rgba(34,197,94,0.2)',  color: '#86efac' }, solid: '#22c55e', chipText: '#166534' },
  CLOSED:               { light: { bg: '#f1f5f9', color: '#475569' }, dark: { bg: 'rgba(100,116,139,0.2)', color: '#cbd5e1' }, solid: '#64748b', chipText: '#475569' },
};

export const PRIORITY_COLORS = {
  // Yeşil → sarı → turuncu → kırmızı. HIGH turuncu, CRITICAL dolu kırmızı (beyaz yazı)
  // — ikisi belirgin biçimde ayrışır.
  LOW:      { light: { bg: '#dcfce7', color: '#166534' }, dark: { bg: 'rgba(34,197,94,0.2)',  color: '#86efac' }, solid: '#22c55e', chipText: '#15803d' },
  MEDIUM:   { light: { bg: '#fef3c7', color: '#92400e' }, dark: { bg: 'rgba(245,158,11,0.2)', color: '#fde68a' }, solid: '#f59e0b', chipText: '#b45309' },
  HIGH:     { light: { bg: '#ffedd5', color: '#9a3412' }, dark: { bg: 'rgba(249,115,22,0.2)', color: '#fdba74' }, solid: '#f97316', chipText: '#c2410c' },
  CRITICAL: { light: { bg: '#dc2626', color: '#ffffff' }, dark: { bg: '#dc2626',              color: '#ffffff' }, solid: '#ef4444', chipText: '#b91c1c' },
};
