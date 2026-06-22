import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { FileDown, X, Languages, Sun, Moon } from 'lucide-react';
import api, { getPdfPreferences, savePdfPreferences } from '../../services/api';
import i18n from '../../i18n';
import { useTheme } from '../../context/ThemeContext';
import { useToast } from '../../context/ToastContext';
import { buildTicketPdfHtml } from '../../utils/buildTicketPdfHtml';
import Button from '../Button';

const modalStyles = `
  @keyframes modalFadeIn { from { opacity: 0; } to { opacity: 1; } }
  @keyframes modalSlideUp { from { transform: translateY(20px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }
  @keyframes pdfSkelPulse { 0%, 100% { opacity: 0.55; } 50% { opacity: 1; } }
  .pdf-modal-overlay { animation: modalFadeIn 0.3s ease-out; }
  .pdf-modal-content { animation: modalSlideUp 0.4s ease-out; }
  .pdf-skel { animation: pdfSkelPulse 1.6s ease-in-out infinite; }
`;

// Tüm olası bölümler. `staff: true` olanlar (worklog + internal notes) yalnızca onları
// görme yetkisi olan personele (agent/lead + manager/admin) gösterilir — müşteride çıkmaz.
const SECTIONS = [
  { key: 'ticketDetail',  staff: false },
  { key: 'conversation',  staff: false },
  { key: 'internalNotes', staff: true  },
  { key: 'worklog',       staff: true  },
  { key: 'audit',         staff: false },
];

const sectionDefaults = (canSeeStaffSections) =>
  Object.fromEntries(SECTIONS.filter((s) => !s.staff || canSeeStaffSections).map((s) => [s.key, true]));

const uiLang = () => (i18n.language?.startsWith('tr') ? 'tr' : 'en');

// Gizli (ekran dışı) bir iframe'e yazıp doğrudan tarayıcının yazdır/PDF ekranını açar —
// yeni sekme veya site içi bir ekran yok. Yazdırma bitince iframe temizlenir.
function printViaIframe(html, fileName) {
  const iframe = document.createElement('iframe');
  iframe.setAttribute('aria-hidden', 'true');
  iframe.style.cssText = 'position:fixed;left:-10000px;top:0;width:800px;height:600px;border:0;';
  document.body.appendChild(iframe);
  // Tarayıcı varsayılan PDF adını document.title'dan alır; bazıları iframe yerine üst
  // dökümanın title'ını kullanır — ikisini de ayarla, yazdırma bitince geri al.
  const prevTitle = document.title;
  const cleanup = () => {
    document.title = prevTitle;
    if (iframe.parentNode) iframe.parentNode.removeChild(iframe);
  };
  const cw = iframe.contentWindow;
  cw.document.open();
  cw.document.write(html);
  cw.document.close();
  if (fileName) document.title = fileName;
  const run = () => {
    try {
      cw.focus();
      cw.onafterprint = () => setTimeout(cleanup, 200);
      cw.print();
    } catch {
      cleanup();
      return;
    }
    setTimeout(cleanup, 60000); // onafterprint tetiklenmezse temizle
  };
  setTimeout(run, 150); // içerik yerleşsin
}

export default function PdfExportModal({ isOpen, onClose, ticket, ticketCode, canSeeStaffSections, isCustomer }) {
  const { t } = useTranslation();
  const { theme: appTheme } = useTheme();
  const toast = useToast();
  const available = SECTIONS.filter((s) => !s.staff || canSeeStaffSections);

  const [sel, setSel] = useState(() => sectionDefaults(canSeeStaffSections));
  const [language, setLanguage] = useState(uiLang);
  const [theme, setTheme] = useState(appTheme === 'dark' ? 'dark' : 'light');
  const [generating, setGenerating] = useState(false);

  // Açılışta DB'deki son tercihleri yükle; yoksa varsayılanlar (hepsi seçili,
  // arayüz dili + arayüz teması). Sadece kullanıcının yetkisi olan bölümler uygulanır.
  useEffect(() => {
    if (!isOpen) return;
    setSel(sectionDefaults(canSeeStaffSections));
    setLanguage(uiLang());
    setTheme(appTheme === 'dark' ? 'dark' : 'light');

    let cancelled = false;
    getPdfPreferences()
      .then((res) => {
        if (cancelled) return;
        const raw = res.data?.preferences;
        if (!raw) return;
        let saved;
        try { saved = JSON.parse(raw); } catch { return; }
        if (saved.sections && typeof saved.sections === 'object') {
          setSel((prev) => {
            const next = { ...prev };
            for (const key of Object.keys(prev)) {
              if (typeof saved.sections[key] === 'boolean') next[key] = saved.sections[key];
            }
            return next;
          });
        }
        if (saved.language === 'tr' || saved.language === 'en') setLanguage(saved.language);
        if (saved.theme === 'light' || saved.theme === 'dark') setTheme(saved.theme);
      })
      .catch(() => { /* tercih yoksa/erişilemezse varsayılanlarla devam */ });
    return () => { cancelled = true; };
  }, [isOpen, canSeeStaffSections, appTheme]);

  if (!isOpen) return null;

  // Kullanıcı bir seçimi her değiştirdiğinde DB'ye ANINDA kaydet (generate beklemeden).
  // Yükleme (load) bu yolu çağırmaz; yalnızca kullanıcı etkileşimleri kaydeder.
  const persist = (nextSel, nextLang, nextTheme) =>
    savePdfPreferences(JSON.stringify({ sections: nextSel, language: nextLang, theme: nextTheme })).catch(() => {});
  const toggle = (key) => {
    const next = { ...sel, [key]: !sel[key] };
    setSel(next);
    persist(next, language, theme);
  };
  const onLanguage = (v) => { setLanguage(v); persist(sel, v, theme); };
  const onTheme = (v) => { setTheme(v); persist(sel, language, v); };
  const anySelected = available.some((s) => sel[s.key]);

  const handleGenerate = async () => {
    if (!anySelected) {
      toast.error(t('ticketDetail.pdfNoSelection'));
      return;
    }
    setGenerating(true);
    try {
      let comments = [];
      let worklogs = [];
      const reqs = [];
      if (sel.conversation || sel.internalNotes) {
        reqs.push(api.get(`/tickets/${ticket.id}/comments`).then((r) => { comments = r.data || []; }));
      }
      if (sel.worklog) {
        reqs.push(api.get(`/tickets/${ticket.id}/worklogs`).then((r) => { worklogs = r.data || []; }));
      }
      await Promise.all(reqs);

      // Seçilen dile bağlı çeviri fonksiyonu — PDF içeriği arayüz dilinden bağımsız.
      const pdfT = i18n.getFixedT(language);
      const html = buildTicketPdfHtml({
        ticket, ticketCode, sections: sel, comments, worklogs,
        t: pdfT, theme, lang: language, viewerIsCustomer: isCustomer,
      });

      const fileName = `${ticketCode} ${pdfT('ticketDetail.pdfFileSuffix')}`;
      printViaIframe(html, fileName);
      onClose(); // seçimler zaten her değişimde DB'ye kaydedildi
    } catch {
      toast.error(t('ticketDetail.pdfError'));
    } finally {
      setGenerating(false);
    }
  };

  return (
    <>
      <style>{modalStyles}</style>
      <div className="pdf-modal-overlay fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
        <div
          className="pdf-modal-content w-full max-w-md sm:max-w-3xl rounded-xl border max-h-[90vh] flex flex-col overflow-hidden"
          style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-lg)' }}
        >
          <div className="flex items-center justify-between px-4 sm:px-6 py-4 border-b flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
            <span className="text-lg font-semibold flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
              <FileDown className="h-5 w-5 text-primary-500" />
              {t('ticketDetail.pdfTitle')}
            </span>
            <button
              className="flex h-8 w-8 items-center justify-center rounded transition-colors cursor-pointer hover:opacity-70"
              style={{ color: 'var(--text-tertiary)' }}
              onClick={onClose}
              title={t('form.close')}
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          <div className="overflow-y-auto flex-1 p-4 sm:p-6">
            <div className="flex flex-col sm:flex-row gap-5 sm:gap-6">
              {/* Sol: seçenekler */}
              <div className="flex-1 min-w-0 space-y-4">
                <p className="text-xs" style={{ color: 'var(--text-secondary)' }}>
                  {t('ticketDetail.pdfDescription')}
                </p>

                {/* Bölüm seçimi — sağda switch */}
                <div className="space-y-1.5">
                  {available.map((s) => (
                    <Switch
                      key={s.key}
                      checked={!!sel[s.key]}
                      onChange={() => toggle(s.key)}
                      label={t(`ticketDetail.pdfSection${cap(s.key)}`)}
                    />
                  ))}
                </div>

                {/* Dil + Tema seçimi */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <Segmented
                    icon={<Languages className="h-3.5 w-3.5" />}
                    label={t('ticketDetail.pdfLanguage')}
                    value={language}
                    onChange={onLanguage}
                    options={[{ value: 'tr', label: 'TR' }, { value: 'en', label: 'EN' }]}
                  />
                  <Segmented
                    icon={theme === 'dark' ? <Moon className="h-3.5 w-3.5" /> : <Sun className="h-3.5 w-3.5" />}
                    label={t('ticketDetail.pdfTheme')}
                    value={theme}
                    onChange={onTheme}
                    options={[
                      { value: 'light', label: t('ticketDetail.pdfThemeLight') },
                      { value: 'dark', label: t('ticketDetail.pdfThemeDark') },
                    ]}
                  />
                </div>
              </div>

              {/* Sağ: canlı iskelet önizleme (gerçek veri yok) */}
              <div className="w-full sm:w-64 flex-shrink-0">
                <div className="flex items-center justify-between mb-1.5">
                  <span className="text-xs font-medium" style={{ color: 'var(--text-tertiary)' }}>
                    {t('ticketDetail.pdfPreviewLabel')}
                  </span>
                </div>
                {/* Önizleme içeriği arayüz diline değil, PDF dil tercihine bağlı. */}
                <PdfPreview sel={sel} theme={theme} t={i18n.getFixedT(language)} available={available} />
              </div>
            </div>
          </div>

          <div className="border-t px-4 sm:px-6 py-4 flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
            <Button
              fullWidth
              onClick={handleGenerate}
              disabled={generating || !anySelected}
            >
              {generating ? (
                <>
                  <div className="h-4 w-4 rounded-full border-2 animate-spin" style={{ borderColor: 'rgba(255,255,255,0.3)', borderTopColor: '#fff' }} />
                  {t('ticketDetail.pdfGenerating')}
                </>
              ) : (
                <>
                  <FileDown className="h-4 w-4" />
                  {t('ticketDetail.pdfGenerate')}
                </>
              )}
            </Button>
          </div>
        </div>
      </div>
    </>
  );
}

/** Bölüm seçimi için switch: solda etiket, sağda toggle. */
function Switch({ checked, onChange, label }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      onClick={onChange}
      className="flex w-full items-center justify-between rounded-lg px-3 py-2.5 cursor-pointer text-left"
      style={{ backgroundColor: 'var(--bg-surface-secondary)', color: 'var(--text-primary)' }}
    >
      <span className="text-sm font-medium">{label}</span>
      <span
        className="relative inline-flex h-5 w-9 flex-shrink-0 items-center rounded-full transition-colors"
        style={{ backgroundColor: checked ? '#3b82f6' : 'var(--border-color)' }}
      >
        <span
          className="inline-block h-4 w-4 rounded-full bg-white transition-transform"
          style={{ transform: checked ? 'translateX(18px)' : 'translateX(2px)' }}
        />
      </span>
    </button>
  );
}

/** Küçük segmented control (dil/tema seçimi). */
function Segmented({ icon, label, value, onChange, options }) {
  return (
    <div>
      <div className="flex items-center gap-1.5 text-xs font-medium mb-1.5" style={{ color: 'var(--text-tertiary)' }}>
        {icon}{label}
      </div>
      <div className="flex rounded-lg border overflow-hidden" style={{ borderColor: 'var(--border-color)' }}>
        {options.map((o) => {
          const active = value === o.value;
          return (
            <button
              key={o.value}
              type="button"
              onClick={() => onChange(o.value)}
              className="flex-1 px-3 py-1.5 text-xs font-semibold transition-colors cursor-pointer"
              style={active
                ? { backgroundColor: '#3b82f6', color: '#fff' }
                : { backgroundColor: 'var(--bg-surface)', color: 'var(--text-secondary)' }}
            >
              {o.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}

// 'ticketDetail' -> 'TicketDetail' (i18n anahtarı: pdfSectionTicketDetail)
function cap(s) {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

// PDF temasıyla aynı palet (stylesFor ile uyumlu) — kullanıcı tema etkisini de görür.
function pdfPreviewPalette(theme) {
  const dark = theme === 'dark';
  return {
    dark,
    paper: dark ? '#0f172a' : '#ffffff',
    skel: dark ? 'rgba(148,163,184,0.22)' : '#e5e7eb',
    skelStrong: dark ? 'rgba(148,163,184,0.32)' : '#d1d5db',
    border: dark ? '#334155' : '#e5e7eb',
    heading: dark ? '#cbd5e1' : '#6b7280',
    th: dark ? '#1e293b' : '#f3f4f6',
  };
}

/** Tek bir iskelet (skeleton) çubuğu. */
function SkelBar({ pal, w = '100%', h = 6, strong = false }) {
  return (
    <div className="pdf-skel" style={{ width: w, height: h, borderRadius: 3, background: strong ? pal.skelStrong : pal.skel }} />
  );
}

/** Mavi sol-çizgili bölüm başlığı (PDF'deki h2 ile aynı görsel dil) + gerçek etiket. */
function SkelHeading({ pal, label }) {
  return (
    <div
      className="text-[8px] font-bold uppercase tracking-wide"
      style={{ color: pal.heading, borderLeft: '2px solid #3b82f6', paddingLeft: 5, margin: '10px 0 5px' }}
    >
      {label}
    </div>
  );
}

/** ticketDetail: anahtar/değer satırları (sol etiket iskeleti, sağ değer iskeleti). */
function SkelKvRows({ pal }) {
  return (
    <div className="space-y-1.5">
      {[60, 48, 52, 70, 64].map((vw, i) => (
        <div key={i} className="flex items-center gap-2">
          <div style={{ width: 38, flexShrink: 0 }}><SkelBar pal={pal} h={5} /></div>
          <div style={{ flex: 1 }}><SkelBar pal={pal} w={`${vw}%`} h={5} strong /></div>
        </div>
      ))}
    </div>
  );
}

/** Sohbet baloncuğu — sol/sağ ve (internalNotes) sarı dahili not. */
function SkelBubble({ pal, side, lines, internal }) {
  const align = side === 'right' ? 'flex-end' : 'flex-start';
  const bg = internal
    ? (pal.dark ? 'rgba(245,158,11,0.12)' : '#fffbeb')
    : side === 'right' ? '#3b82f6' : pal.th;
  const barColor = side === 'right' && !internal ? 'rgba(255,255,255,0.5)' : pal.skel;
  return (
    <div style={{ display: 'flex', justifyContent: align }}>
      <div
        style={{
          maxWidth: '78%', minWidth: '45%', padding: '5px 7px', borderRadius: 7, background: bg,
          border: internal
            ? `1px solid ${pal.dark ? 'rgba(245,158,11,0.25)' : '#fde68a'}`
            : side === 'left' ? `1px solid ${pal.border}` : 'none',
        }}
      >
        {Array.from({ length: lines }).map((_, i) => (
          <div key={i} className="pdf-skel" style={{ width: i === lines - 1 ? '60%' : '100%', height: 4, borderRadius: 2, marginTop: i ? 3 : 0, background: barColor }} />
        ))}
      </div>
    </div>
  );
}

/** Worklog/audit: başlık satırı + birkaç gövde satırı olan tablo iskeleti. */
function SkelTable({ pal, cols = 4, rows = 3 }) {
  return (
    <div style={{ border: `1px solid ${pal.border}`, borderRadius: 3, overflow: 'hidden' }}>
      <div style={{ display: 'flex', background: pal.th, padding: '3px 4px', gap: 4 }}>
        {Array.from({ length: cols }).map((_, i) => <div key={i} style={{ flex: 1 }}><SkelBar pal={pal} h={4} strong /></div>)}
      </div>
      {Array.from({ length: rows }).map((_, r) => (
        <div key={r} style={{ display: 'flex', padding: '3px 4px', gap: 4, borderTop: `1px solid ${pal.border}` }}>
          {Array.from({ length: cols }).map((_, i) => <div key={i} style={{ flex: 1 }}><SkelBar pal={pal} h={4} w={`${70 + ((i + r) % 3) * 10}%`} /></div>)}
        </div>
      ))}
    </div>
  );
}

/**
 * Seçimleri canlı yansıtan, ölçeklenmiş bir "kâğıt" önizlemesi. Yalnızca iskelet
 * bloklar gösterir — gerçek bilet verisi YOK. Amaç, kullanıcının her switch'in
 * PDF'de hangi bölümü/yerleşimi eklediğini görsel olarak anlaması. Yerleşim ve renk
 * teması buildTicketPdfHtml çıktısını taklit eder.
 */
function PdfPreview({ sel, theme, t, available }) {
  const pal = pdfPreviewPalette(theme);
  const has = (k) => available.some((s) => s.key === k) && !!sel[k];
  const showChat = has('conversation') || has('internalNotes');

  return (
    <div
      className="rounded-md"
      style={{ background: pal.paper, border: `1px solid ${pal.border}`, boxShadow: 'var(--shadow-md)', padding: '12px 11px', maxHeight: 340, overflow: 'hidden' }}
    >
      {/* Üst aksan çizgisi (PDF accent-bar) */}
      <div style={{ height: 3, borderRadius: 2, background: '#3b82f6', marginBottom: 8 }} />

      {/* Başlık alanı: etiket + tarih, kod, durum/öncelik rozetleri, başlık */}
      <div style={{ borderBottom: `1px solid ${pal.border}`, paddingBottom: 8, marginBottom: 4 }}>
        <div className="flex items-center justify-between">
          <div style={{ width: 50 }}><SkelBar pal={pal} h={5} /></div>
          <div style={{ width: 34 }}><SkelBar pal={pal} h={4} /></div>
        </div>
        <div className="flex items-center gap-2" style={{ marginTop: 6 }}>
          <div style={{ width: 46 }}><SkelBar pal={pal} h={9} strong /></div>
          <div className="pdf-skel" style={{ width: 26, height: 8, borderRadius: 999, background: pal.dark ? 'rgba(59,130,246,0.4)' : '#bfdbfe' }} />
          <div className="pdf-skel" style={{ width: 26, height: 8, borderRadius: 999, background: pal.dark ? 'rgba(239,68,68,0.4)' : '#fecaca' }} />
        </div>
        <div style={{ marginTop: 5 }}><SkelBar pal={pal} w="70%" h={5} /></div>
      </div>

      {has('ticketDetail') && (
        <>
          <SkelHeading pal={pal} label={t('ticketDetail.pdfSectionTicketDetail')} />
          <SkelKvRows pal={pal} />
        </>
      )}

      {showChat && (
        <>
          <SkelHeading pal={pal} label={t('ticketDetail.pdfSectionConversation')} />
          <div className="space-y-1.5">
            {has('conversation') && <SkelBubble pal={pal} side="left" lines={2} />}
            {has('conversation') && <SkelBubble pal={pal} side="right" lines={1} />}
            {has('internalNotes') && <SkelBubble pal={pal} side="right" lines={2} internal />}
          </div>
        </>
      )}

      {has('worklog') && (
        <>
          <SkelHeading pal={pal} label={t('ticketDetail.pdfSectionWorklog')} />
          <SkelTable pal={pal} cols={4} rows={2} />
        </>
      )}

      {has('audit') && (
        <>
          <SkelHeading pal={pal} label={t('ticketDetail.pdfSectionAudit')} />
          <SkelTable pal={pal} cols={4} rows={3} />
        </>
      )}
    </div>
  );
}
