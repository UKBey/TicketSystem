/**
 * Çift dilli (TR/EN) form alanı — hazır yanıtlar (canned responses) düzenleme ekranındaki
 * desenin tekrar kullanılabilir hâli: etiketin yanında küçük bir TR/EN sekme anahtarı, altında
 * yalnızca aktif dilin input/textarea'sı görünür.
 *
 * Aynı formdaki birden çok alan ortak bir `lang`/`onLang` state'i paylaşarak sekmeleri senkron
 * tutar (örn. Known Issues'ta başlık + açıklama tek anahtarla birlikte dil değiştirir).
 *
 * @param {string} label
 * @param {boolean} [required]            etikete ' *' ekler
 * @param {string} [hint]                 alanın altında küçük yardımcı metin
 * @param {'tr'|'en'} lang                aktif dil (kontrollü)
 * @param {(l:'tr'|'en')=>void} onLang    dil değiştirme
 * @param {string} valueTr
 * @param {string} valueEn
 * @param {(v:string)=>void} onChangeTr
 * @param {(v:string)=>void} onChangeEn
 * @param {'input'|'textarea'} [as]
 * @param {number} [rows]                 textarea için
 * @param {number} [maxLength]
 * @param {string} [placeholderTr]
 * @param {string} [placeholderEn]
 * @param {string} [placeholder]          her iki dil için ortak placeholder (tr/en verilmezse)
 * @param {boolean} [showToggle]          TR/EN sekmesini göster (varsayılan true). Aynı formdaki
 *                                        ikinci/sonraki alanlar için false verilip tek anahtar paylaşılır.
 */
export default function BilingualField({
  label, required = false, hint,
  lang, onLang,
  valueTr, valueEn, onChangeTr, onChangeEn,
  as = 'input', rows = 4, maxLength,
  placeholderTr, placeholderEn, placeholder,
  showToggle = true,
}) {
  const isTr = lang === 'tr';
  const value = (isTr ? valueTr : valueEn) ?? '';
  const onChange = isTr ? onChangeTr : onChangeEn;
  const ph = (isTr ? placeholderTr : placeholderEn) ?? placeholder;

  const fieldStyle = {
    backgroundColor: 'var(--bg-input)',
    borderColor: 'var(--border-color)',
    color: 'var(--text-primary)',
    '--tw-ring-color': 'var(--ring-color)',
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-1.5 gap-2">
        <label className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
          {label}{required ? ' *' : ''}
        </label>
        {showToggle && (
          <div className="flex overflow-hidden rounded-md border shrink-0" style={{ borderColor: 'var(--border-color)' }}>
            {['tr', 'en'].map((l) => (
              <button
                key={l}
                type="button"
                onClick={() => onLang(l)}
                className="px-2.5 py-1 text-xs font-bold uppercase transition-colors cursor-pointer"
                style={lang === l
                  ? { backgroundColor: 'var(--color-primary-500, #3b82f6)', color: '#fff' }
                  : { color: 'var(--text-tertiary)', backgroundColor: 'transparent' }}
              >
                {l}
              </button>
            ))}
          </div>
        )}
      </div>

      {as === 'textarea' ? (
        <textarea
          value={value}
          onChange={(e) => onChange(e.target.value)}
          rows={rows}
          maxLength={maxLength}
          placeholder={ph}
          className="w-full resize-y rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2"
          style={fieldStyle}
        />
      ) : (
        <input
          type="text"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          maxLength={maxLength}
          placeholder={ph}
          className="w-full rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2"
          style={fieldStyle}
        />
      )}

      {hint && <p className="mt-1 text-xs" style={{ color: 'var(--text-tertiary)' }}>{hint}</p>}
    </div>
  );
}
