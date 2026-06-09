import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { FileDown, X, Languages, Sun, Moon } from 'lucide-react';
import api, { getPdfPreferences, savePdfPreferences } from '../../services/api';
import i18n from '../../i18n';
import { useTheme } from '../../context/ThemeContext';
import { buildTicketPdfHtml } from '../../utils/buildTicketPdfHtml';

const modalStyles = `
  @keyframes modalFadeIn { from { opacity: 0; } to { opacity: 1; } }
  @keyframes modalSlideUp { from { transform: translateY(20px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }
  .pdf-modal-overlay { animation: modalFadeIn 0.3s ease-out; }
  .pdf-modal-content { animation: modalSlideUp 0.4s ease-out; }
`;

// Tüm olası bölümler. `staff: true` olanlar (worklog + internal notes) yalnızca onları
// görme yetkisi olan personele (isAgent) gösterilir — müşteride switch hiç çıkmaz.
const SECTIONS = [
  { key: 'ticketDetail',  staff: false },
  { key: 'conversation',  staff: false },
  { key: 'internalNotes', staff: true  },
  { key: 'worklog',       staff: true  },
  { key: 'audit',         staff: false },
];

const sectionDefaults = (isAgent) =>
  Object.fromEntries(SECTIONS.filter((s) => !s.staff || isAgent).map((s) => [s.key, true]));

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

export default function PdfExportModal({ isOpen, onClose, ticket, ticketCode, isAgent, isCustomer }) {
  const { t } = useTranslation();
  const { theme: appTheme } = useTheme();
  const available = SECTIONS.filter((s) => !s.staff || isAgent);

  const [sel, setSel] = useState(() => sectionDefaults(isAgent));
  const [language, setLanguage] = useState(uiLang);
  const [theme, setTheme] = useState(appTheme === 'dark' ? 'dark' : 'light');
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState(null);

  // Açılışta DB'deki son tercihleri yükle; yoksa varsayılanlar (hepsi seçili,
  // arayüz dili + arayüz teması). Sadece kullanıcının yetkisi olan bölümler uygulanır.
  useEffect(() => {
    if (!isOpen) return;
    setError(null);
    setSel(sectionDefaults(isAgent));
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
  }, [isOpen, isAgent, appTheme]);

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
      setError(t('ticketDetail.pdfNoSelection'));
      return;
    }
    setGenerating(true);
    setError(null);
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
      setError(t('ticketDetail.pdfError'));
    } finally {
      setGenerating(false);
    }
  };

  return (
    <>
      <style>{modalStyles}</style>
      <div className="pdf-modal-overlay fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
        <div
          className="pdf-modal-content w-full max-w-md sm:max-w-lg rounded-xl border max-h-[90vh] flex flex-col overflow-hidden"
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

          <div className="overflow-y-auto flex-1 p-4 sm:p-6 space-y-4">
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

            {error && (
              <div className="rounded-lg px-3 py-2 text-xs" style={{ backgroundColor: '#fee2e2', color: '#991b1b' }}>
                {error}
              </div>
            )}
          </div>

          <div className="border-t px-4 sm:px-6 py-4 flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
            <button
              className="w-full rounded-lg px-4 py-2.5 text-sm font-semibold transition-colors cursor-pointer flex items-center justify-center gap-2 text-white bg-primary-500 hover:bg-primary-600 disabled:opacity-50 disabled:cursor-not-allowed"
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
            </button>
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
