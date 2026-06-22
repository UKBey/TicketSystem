import { formatDate, formatShortDate, formatMinutes } from './ticketFormatters';
import { STATUS_COLORS, PRIORITY_COLORS } from '../constants/ticketColors';
import { pickLocalized } from './localizedName';

// Tarayıcının native "Yazdır / PDF olarak kaydet" ekranı için tam bir HTML dökümanı
// üretir; PdfExportModal bunu gizli bir iframe'e yazıp yazdırır (uygulama içinde ekran yok).
//
// Tüm dinamik metin HTML-escape edilir — başlık, açıklama, yorum/worklog/audit içerikleri
// kullanıcı girdisidir, escape edilmezse HTML'i bozar veya XSS yaratır.

const esc = (s) =>
  String(s ?? '').replace(/[&<>"']/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
  ));

// Uygulamayla aynı status/priority renkleriyle (ticketColors) basılan rozet.
function badge(label, colorSet) {
  const bg = colorSet?.bg ?? '#e5e7eb';
  const fg = colorSet?.color ?? '#374151';
  return `<span class="badge" style="background:${bg};color:${fg}">${esc(label)}</span>`;
}
function statusBadge(status, theme, t) {
  const c = (STATUS_COLORS[status] ?? STATUS_COLORS.CLOSED)[theme === 'dark' ? 'dark' : 'light'];
  return badge(t(`ticket.status.${status}`, { defaultValue: status }), c);
}
function priorityBadge(priority, theme, t) {
  const c = (PRIORITY_COLORS[priority] ?? PRIORITY_COLORS.LOW)[theme === 'dark' ? 'dark' : 'light'];
  return badge(t(`ticket.priority.${priority}`, { defaultValue: priority }), c);
}

// CSAT puanı: 5 üzerinden dolu/boş yıldızlar.
function starsHtml(rating) {
  const r = Math.max(0, Math.min(5, Number(rating) || 0));
  let s = '<span class="stars">';
  for (let i = 1; i <= 5; i++) s += `<span class="star ${i <= r ? 'on' : 'off'}">${i <= r ? '★' : '☆'}</span>`;
  return s + '</span>';
}

// Audit "değişiklik" hücresi: CSAT -> yıldız, priority/status -> renkli badge, diğer -> metin.
function auditChange(e, theme, t) {
  if (e.actionType === 'CSAT_SUBMITTED' && e.newState != null) return starsHtml(e.newState);
  if (!e.previousState && !e.newState) return '';
  const isPriority = e.actionType === 'PRIORITY_CHANGE';
  const isStatus = KNOWN_STATUSES.includes(e.previousState) || KNOWN_STATUSES.includes(e.newState);
  const render = (v) => {
    if (v == null || v === '') return '';
    if (isPriority) return priorityBadge(v, theme, t);
    if (isStatus) return statusBadge(v, theme, t);
    return esc(localizeState(e.actionType, v, t));
  };
  const prev = render(e.previousState);
  const next = render(e.newState);
  return prev && next ? `${prev} <span class="arrow">→</span> ${next}` : (next || prev);
}

// audit.actionType -> AuditTimeline ile aynı i18n etiket anahtarı (ticketDetail.<key>).
const AUDIT_LABEL_KEYS = {
  CREATE: 'auditCreated', CLAIM: 'auditClaimed', UNCLAIM: 'auditReleased',
  ASSIGN: 'auditAssigned', RESOLVE: 'auditResolved', REOPEN: 'auditReopened',
  WAITING: 'auditWaiting', RESUME: 'auditResumed', CLOSE: 'auditClosed',
  STATUS_CHANGE: 'auditStatusChange', PRIORITY_CHANGE: 'auditPriorityChange',
  TOPIC_CHANGE: 'auditTopicChange', CSAT_SUBMITTED: 'auditCsat',
};

const KNOWN_STATUSES = ['NEW', 'IN_PROGRESS', 'WAITING_FOR_CUSTOMER', 'RESOLVED', 'CLOSED'];

/** Bir state değerini (status/priority kodu) okunaklı/yerelleştirilmiş metne çevirir. */
function localizeState(actionType, value, t) {
  if (value == null || value === '') return '';
  if (actionType === 'PRIORITY_CHANGE') return t(`ticket.priority.${value}`, { defaultValue: value });
  if (KNOWN_STATUSES.includes(value)) return t(`ticket.status.${value}`, { defaultValue: value });
  return String(value);
}

// Ürün/konu adı PDF'in kendi dil seçimine (lang) göre çözülür — UI dilinden bağımsız.
function ticketDetailSection(ticket, t, theme, hideDescription, lang) {
  const productName = pickLocalized(ticket.productNameTr, ticket.productNameEn, lang);
  const topicName = pickLocalized(ticket.topicNameTr, ticket.topicNameEn, lang);
  const rows = [
    [t('ticket.table.title'), esc(ticket.title)],
    [t('ticket.table.status'), statusBadge(ticket.status, theme, t)],
    [t('ticket.table.priority'), priorityBadge(ticket.priority, theme, t)],
    [t('ticketDetail.customerLabel'), esc(ticket.customerName || ticket.customerId)],
    [t('ticketDetail.productLabel'), esc(productName || ticket.productId)],
  ];
  if (topicName || ticket.topicId) {
    rows.push([t('ticketDetail.topicLabel'), esc(topicName || `#${ticket.topicId}`)]);
  }
  rows.push([t('ticketDetail.pdfFieldCreated'), esc(formatDate(ticket.createdAt))]);

  const body = rows
    .map(([label, value]) => `<tr><th>${esc(label)}</th><td>${value}</td></tr>`)
    .join('');

  // conversation seçiliyse açıklama orada (ilk baloncuk) gösterilir; burada tekrar etmesin.
  const description = (ticket.description && !hideDescription)
    ? `<div class="desc-label">${esc(t('ticketDetail.pdfFieldDescription'))}</div>
       <div class="desc">${esc(ticket.description)}</div>`
    : '';

  return `
    <section>
      <h2>${esc(t('ticketDetail.pdfSectionTicketDetail'))}</h2>
      <table class="kv">${body}</table>
      ${description}
    </section>`;
}

// Public konuşma — uygulamadaki chat gibi sol/sağ baloncuklar. Hizalama indiren
// kişinin tarafına göre: müşteri indiriyorsa kendi (müşteri) mesajları sağda;
// personel indiriyorsa ajan/claimer mesajları sağda. (TicketTimeline ile aynı kural.)
function conversationChatSection(items, viewerIsCustomer, ticket, t) {
  // Açılış açıklaması (ticket.description) ilk EXTERNAL yorum olarak da kaydedilir;
  // burada tüm mesajlar gönderilme saatine göre tek akışta gösterilir. INTERNAL notlar
  // ticket detaildeki gibi sarı baloncuk olarak araya girer.
  const chatItems = [...items].sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
  if (!chatItems.length) {
    return `<section><h2>${esc(t('ticketDetail.pdfSectionConversation'))}</h2><div class="empty">${esc(t('ticketDetail.pdfEmpty'))}</div></section>`;
  }
  const customerId = ticket.customerId;
  const bubbles = chatItems.map((c) => {
    const name = c.authorName || c.authorId || '';
    const when = `<span class="when">${esc(formatDate(c.createdAt))}</span>`;
    if (c.type === 'INTERNAL') {
      // Dahili not: sarı, sağ tarafta (ajan tarafı), "INTERNAL" rozeti — TicketTimeline ile aynı.
      return `
        <div class="bubble internal">
          <div class="b-head"><strong>${esc(name)}</strong> <span class="ibadge">${esc(t('ticketDetail.internal'))}</span>${when}</div>
          <div class="b-msg">${esc(c.message)}</div>
        </div>`;
    }
    const isCustomerAuthor = c.authorRole ? c.authorRole === 'CUSTOMER' : c.authorId === customerId;
    const right = viewerIsCustomer ? isCustomerAuthor : !isCustomerAuthor;
    const roleLabel = isCustomerAuthor ? t('ticketDetail.roleCustomer') : t('ticketDetail.roleAgent');
    return `
      <div class="bubble ${right ? 'right' : 'left'}">
        <div class="b-head"><strong>${esc(name)}</strong>${right ? '' : ` &middot; ${esc(roleLabel)}`}${when}</div>
        <div class="b-msg">${esc(c.message)}</div>
      </div>`;
  }).join('');
  return `<section><h2>${esc(t('ticketDetail.pdfSectionConversation'))}</h2><div class="chat">${bubbles}</div></section>`;
}

function worklogSection(worklogs, t) {
  if (!worklogs.length) {
    return `<section><h2>${esc(t('ticketDetail.pdfSectionWorklog'))}</h2><div class="empty">${esc(t('ticketDetail.pdfEmpty'))}</div></section>`;
  }
  const rows = worklogs.map((w) => `
    <tr>
      <td>${esc(formatShortDate(w.createdAt))}</td>
      <td>${esc(w.agentName || w.agentId || '')}</td>
      <td class="num">${esc(formatMinutes(w.minutes))}</td>
      <td>${esc(w.description || '')}</td>
    </tr>`).join('');
  const total = worklogs.reduce((sum, w) => sum + (w.minutes || 0), 0);
  return `
    <section>
      <h2>${esc(t('ticketDetail.pdfSectionWorklog'))}</h2>
      <table class="grid">
        <thead><tr>
          <th>${esc(t('ticketDetail.pdfColDate'))}</th>
          <th>${esc(t('ticketDetail.pdfColAgent'))}</th>
          <th>${esc(t('ticketDetail.pdfColDuration'))}</th>
          <th>${esc(t('ticketDetail.pdfColDescription'))}</th>
        </tr></thead>
        <tbody>${rows}</tbody>
        <tfoot><tr>
          <td colspan="2" class="total-label">${esc(t('ticketDetail.pdfWorklogTotal'))}</td>
          <td class="num">${esc(formatMinutes(total))}</td>
          <td></td>
        </tr></tfoot>
      </table>
    </section>`;
}

function auditSection(auditLogs, t, theme) {
  if (!auditLogs.length) {
    return `<section><h2>${esc(t('ticketDetail.pdfSectionAudit'))}</h2><div class="empty">${esc(t('ticketDetail.pdfEmpty'))}</div></section>`;
  }
  const sorted = [...auditLogs].sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
  const rows = sorted.map((e) => {
    const labelKey = AUDIT_LABEL_KEYS[e.actionType] ?? 'auditUpdated';
    const action = t(`ticketDetail.${labelKey}`, { defaultValue: e.actionType });
    const change = auditChange(e, theme, t);
    const reason = e.reasonCode
      ? t(`reasonCode.${e.actionType}.${e.reasonCode}`, { defaultValue: e.reasonCode })
      : '';
    const detailBits = [reason && esc(reason), e.note && `&ldquo;${esc(e.note)}&rdquo;`]
      .filter(Boolean).join(' — ');
    // Atamada "kim" sütunu atanan ajanı gösterir; atayan "X tarafından" eklenir.
    const who = e.actionType === 'ASSIGN' && e.targetName
      ? `${esc(e.targetName)}${e.actorName ? ` ${esc(t('ticketDetail.auditAssignedBy', { name: e.actorName }))}` : ''}`
      : esc(e.actorName || '');
    return `
      <tr>
        <td>${esc(formatShortDate(e.createdAt))}</td>
        <td>${who}</td>
        <td>${esc(action)}</td>
        <td>${change}${detailBits ? `<div class="detail">${detailBits}</div>` : ''}</td>
      </tr>`;
  }).join('');
  return `
    <section>
      <h2>${esc(t('ticketDetail.pdfSectionAudit'))}</h2>
      <table class="grid">
        <thead><tr>
          <th>${esc(t('ticketDetail.pdfColDate'))}</th>
          <th>${esc(t('ticketDetail.pdfColActor'))}</th>
          <th>${esc(t('ticketDetail.pdfColAction'))}</th>
          <th>${esc(t('ticketDetail.pdfColChange'))}</th>
        </tr></thead>
        <tbody>${rows}</tbody>
      </table>
    </section>`;
}

// Light/dark palet. Dark seçilince arka plan koyu basılsın diye body'de
// print-color-adjust: exact var (tarayıcı arka planları çıktıdan atmasın).
function stylesFor(theme) {
  const p = theme === 'dark'
    ? { bg: '#0f172a', text: '#e2e8f0', muted: '#94a3b8', heading: '#f1f5f9',
        border: '#334155', borderLight: '#1e293b', th: '#1e293b', zebra: 'rgba(148,163,184,0.06)',
        intBg: 'rgba(245,158,11,0.08)', intBorder: 'rgba(245,158,11,0.2)', intBadgeBg: 'rgba(245,158,11,0.2)', intBadgeColor: '#fde68a' }
    : { bg: '#ffffff', text: '#1f2937', muted: '#6b7280', heading: '#111827',
        border: '#d1d5db', borderLight: '#e5e7eb', th: '#f3f4f6', zebra: '#f9fafb',
        intBg: '#fffbeb', intBorder: '#fde68a', intBadgeBg: '#fef3c7', intBadgeColor: '#92400e' };
  return `
  /* margin: 0 + body padding → koyu zemin sayfa kenarına kadar dolar (dark mode'da
     beyaz kenar kalmaz) ve tarayıcının URL/tarih (localhost) üstbilgi/altbilgisi basılmaz. */
  /* margin:0 → beyaz kenar yok + tarayıcı footer'ı (localhost) yok. Her sayfaya
     üst/alt boşluk, tekrarlanan tablo thead/tfoot ile verilir (aşağıda .frame) —
     bunlar İÇERİK olduğu için koyu basılır ve her sayfada tekrar eder. */
  @page { margin: 0; }
  .frame { width: 100%; border-collapse: collapse; }
  .frame > tbody > tr > td { padding: 0 1.4cm; }
  .frame .pad { height: 1.2cm; }
  * { box-sizing: border-box; }
  html { background: ${p.bg}; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  body { font-family: -apple-system, Segoe UI, Roboto, Helvetica, Arial, sans-serif;
         color: ${p.text}; background: ${p.bg}; font-size: 12px; line-height: 1.5;
         margin: 0; padding: 0;
         -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  .accent-bar { height: 4px; background: #3b82f6; border-radius: 2px; margin: 0 0 14px; }
  header.doc { border-bottom: 1.5px solid ${p.border}; padding-bottom: 12px; margin-bottom: 20px; }
  header.doc .top { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 7px; }
  header.doc .report-label { font-size: 10px; font-weight: 700; letter-spacing: .12em; text-transform: uppercase; color: #3b82f6; }
  header.doc .generated { font-size: 10px; color: ${p.muted}; }
  header.doc .code-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
  header.doc .code { font-size: 24px; font-weight: 800; color: ${p.heading}; letter-spacing: -.01em; }
  header.doc .title { font-size: 14px; font-weight: 600; color: ${p.text}; margin-top: 5px; }
  .badge { display: inline-block; padding: 2px 9px; border-radius: 999px; font-size: 10px; font-weight: 700;
           text-transform: uppercase; letter-spacing: .03em; vertical-align: middle; }
  section { margin-bottom: 14px; page-break-inside: auto; }
  h2 { font-size: 12px; font-weight: 700; color: ${p.heading}; text-transform: uppercase; letter-spacing: .06em;
       border-left: 3px solid #3b82f6; padding: 1px 0 1px 8px; margin: 0 0 11px; }
  table { width: 100%; border-collapse: collapse; }
  table.kv th { text-align: left; width: 130px; color: ${p.muted}; font-weight: 600;
                vertical-align: top; padding: 3px 8px 3px 0; }
  table.kv td { padding: 3px 0; }
  .desc-label { color: ${p.muted}; font-weight: 600; margin-top: 8px; }
  .desc { white-space: pre-wrap; word-break: break-word; margin-top: 2px; }
  table.grid th { text-align: left; background: ${p.th}; color: ${p.text}; font-weight: 600;
                  padding: 5px 7px; border: 1px solid ${p.border}; }
  table.grid td { padding: 5px 7px; border: 1px solid ${p.borderLight}; vertical-align: top; word-break: break-word; }
  table.grid tbody tr:nth-child(even) td { background: ${p.zebra}; }
  table.grid .num { text-align: right; white-space: nowrap; }
  table.grid tfoot .total-label { text-align: right; font-weight: 700; }
  .detail { color: ${p.muted}; font-size: 11px; margin-top: 2px; }
  .empty { color: ${p.muted}; font-style: italic; padding: 4px 0; }
  .chat { display: block; }
  .bubble { max-width: 75%; padding: 7px 11px; border-radius: 10px; margin-bottom: 8px; page-break-inside: avoid; }
  .bubble .b-head { font-size: 10px; margin-bottom: 3px; }
  .bubble .b-head .when { float: right; margin-left: 10px; font-weight: 400; }
  .bubble .b-msg { white-space: pre-wrap; word-break: break-word; }
  .bubble.right { margin-left: auto; background: #3b82f6; color: #fff; }
  .bubble.right .b-head { color: rgba(255,255,255,0.85); }
  .bubble.left { margin-right: auto; background: ${p.th}; color: ${p.text}; border: 1px solid ${p.border}; }
  .bubble.left .b-head { color: ${p.muted}; }
  .bubble.internal { margin-left: auto; background: ${p.intBg}; color: ${p.text}; border: 1px solid ${p.intBorder}; }
  .bubble.internal .b-head { color: ${p.muted}; }
  .bubble .ibadge { display: inline-block; padding: 0 6px; border-radius: 999px; font-size: 9px; font-weight: 700;
                    text-transform: uppercase; background: ${p.intBadgeBg}; color: ${p.intBadgeColor}; margin-left: 4px; }
  .stars { white-space: nowrap; letter-spacing: 1px; }
  .stars .star.on { color: #f59e0b; }
  .stars .star.off { color: ${p.muted}; }
  .arrow { color: ${p.muted}; }
  /* Sadece grid (worklog/audit) satırları ve baloncuklar bölünmesin. Çerçeve
     tablosunun (.frame) tr'ına UYGULANMAZ — yoksa içerik sayfalara bölünemezdi. */
  table.grid tr, .bubble { page-break-inside: avoid; }
`;
}

/**
 * @param {object}  args
 * @param {object}  args.ticket      ticket detail objesi
 * @param {string}  args.ticketCode  TCK-001 gibi gösterim kodu
 * @param {object}  args.sections    {ticketDetail, conversation, internalNotes, worklog, audit} bool
 * @param {Array}   args.comments    ham yorum listesi (type'a göre bölünür)
 * @param {Array}   args.worklogs    worklog listesi
 * @param {Function} args.t          i18next çeviri fonksiyonu (seçilen dile bağlı — getFixedT)
 * @param {string}  [args.theme]     'light' | 'dark' — PDF renk teması
 * @param {string}  [args.lang]      html lang attribute (örn. 'tr' | 'en')
 * @param {boolean} [args.viewerIsCustomer] indiren kişi biletin müşterisi mi (chat hizası için)
 * @returns {string} yazdırılmaya hazır tam HTML dökümanı (print, iframe opener tarafından tetiklenir)
 */
export function buildTicketPdfHtml({ ticket, ticketCode, sections, comments = [], worklogs = [], t, theme = 'light', lang = 'en', viewerIsCustomer = false }) {
  const external = comments.filter((c) => c.type !== 'INTERNAL');
  const internal = comments.filter((c) => c.type === 'INTERNAL');
  const audit = ticket.auditLogs ?? ticket.ticketAuditLogs ?? [];

  // Internal notlar AYRI bölüm değil — ticket detaildeki gibi sohbetin içinde, gönderilme
  // saatine göre araya girer (sarı baloncuk). conversation -> external, internalNotes -> internal.
  const chatComments = [
    ...(sections.conversation ? external : []),
    ...(sections.internalNotes ? internal : []),
  ];

  const parts = [];
  if (sections.ticketDetail)  parts.push(ticketDetailSection(ticket, t, theme, sections.conversation, lang));
  if (sections.conversation || sections.internalNotes)
    parts.push(conversationChatSection(chatComments, viewerIsCustomer, ticket, t));
  if (sections.worklog)       parts.push(worklogSection(worklogs, t));
  if (sections.audit)         parts.push(auditSection(audit, t, theme));

  // Dosya adı (tarayıcı varsayılan PDF adı): "TCK-001 Details" / "TCK-001 Detayları".
  const docTitle = `${ticketCode} ${t('ticketDetail.pdfFileSuffix')}`;
  const generated = `${t('ticketDetail.pdfGeneratedAt')}: ${formatDate(new Date().toISOString())}`;

  return `<!DOCTYPE html>
<html lang="${esc(lang)}">
<head>
  <meta charset="utf-8" />
  <title>${esc(docTitle)}</title>
  <style>${stylesFor(theme)}</style>
</head>
<body>
  <table class="frame">
    <thead><tr><td><div class="pad"></div></td></tr></thead>
    <tbody><tr><td>
      <div class="accent-bar"></div>
      <header class="doc">
        <div class="top">
          <span class="report-label">${esc(t('ticketDetail.pdfDocTitle'))}</span>
          <span class="generated">${esc(generated)}</span>
        </div>
        <div class="code-row">
          <span class="code">${esc(ticketCode)}</span>
          ${statusBadge(ticket.status, theme, t)}
          ${priorityBadge(ticket.priority, theme, t)}
        </div>
        <div class="title">${esc(ticket.title)}</div>
      </header>
      ${parts.join('\n')}
    </td></tr></tbody>
    <tfoot><tr><td><div class="pad"></div></td></tr></tfoot>
  </table>
</body>
</html>`;
}
