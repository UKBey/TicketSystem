import { describe, it, expect } from 'vitest';
import { buildTicketPdfHtml } from '../../utils/buildTicketPdfHtml';

// t stub: anahtarı aynen döndürür → çıktıda hangi i18n anahtarının kullanıldığını
// doğrudan assert edebiliriz. İkinci argümanı (defaultValue) yok sayar.
const t = (k) => k;

const baseTicket = {
  id: 7,
  status: 'IN_PROGRESS',
  priority: 'HIGH',
  title: 'VPN sorunu',
  customerId: 'c1',
  customerName: 'Müşteri',
  productNameEn: 'CRM',
  createdAt: '2026-06-01T10:00:00Z',
  description: 'Açılış açıklaması',
  auditLogs: [],
};

const comments = [
  { id: 1, type: 'EXTERNAL', authorName: 'Müşteri', authorRole: 'CUSTOMER', authorId: 'c1', message: 'Merhaba', createdAt: '2026-06-01T10:00:00Z' },
  { id: 2, type: 'INTERNAL', authorName: 'Ajan', authorId: 'a1', message: 'Dahili not', createdAt: '2026-06-01T11:00:00Z' },
  { id: 3, type: 'EXTERNAL', authorName: 'Ajan', authorRole: 'AGENT', authorId: 'a1', message: 'Yanıt', createdAt: '2026-06-01T12:00:00Z' },
];

const build = (sections, extra = {}) =>
  buildTicketPdfHtml({ ticket: baseTicket, ticketCode: 'TCK-007', sections, comments, worklogs: [], t, theme: 'light', lang: 'tr', ...extra });

describe('buildTicketPdfHtml', () => {
  it('yalnızca seçili bölümleri içerir', () => {
    const html = build({ ticketDetail: true });
    expect(html).toContain('ticketDetail.pdfSectionTicketDetail');
    expect(html).not.toContain('ticketDetail.pdfSectionConversation');
    expect(html).not.toContain('ticketDetail.pdfSectionAudit');
  });

  it('kullanıcı içeriğini HTML-escape eder (XSS)', () => {
    const html = buildTicketPdfHtml({
      ticket: { ...baseTicket, title: '<script>alert(1)</script>' },
      ticketCode: 'TCK-007', sections: { ticketDetail: true }, comments: [], worklogs: [], t, theme: 'light', lang: 'tr',
    });
    expect(html).not.toContain('<script>alert(1)</script>');
    expect(html).toContain('&lt;script&gt;');
  });

  it('internalNotes açıkken dahili notu chat içinde sarı baloncuk olarak gösterir', () => {
    const html = build({ conversation: true, internalNotes: true });
    expect(html).toContain('bubble internal');
    expect(html).toContain('ibadge');
    expect(html).toContain('ticketDetail.internal');
    expect(html).toContain('Dahili not');
  });

  it('internalNotes kapalıyken dahili not görünmez', () => {
    const html = build({ conversation: true });
    expect(html).toContain('Merhaba');
    expect(html).not.toContain('Dahili not');
  });

  it('conversation seçiliyken açıklamayı Ticket Detail içinde tekrar etmez', () => {
    // Açıklama metni yorumlarda yok; yalnızca Ticket Detail'de görünebilir.
    const withConv = build({ ticketDetail: true, conversation: true });
    expect(withConv).not.toContain('Açılış açıklaması');

    const detailOnly = build({ ticketDetail: true });
    expect(detailOnly).toContain('Açılış açıklaması');
  });

  it('audit CSAT girişini yıldız olarak gösterir', () => {
    const html = buildTicketPdfHtml({
      ticket: { ...baseTicket, auditLogs: [{ actionType: 'CSAT_SUBMITTED', actorName: 'Müşteri', createdAt: '2026-06-03T10:00:00Z', newState: '4' }] },
      ticketCode: 'TCK-007', sections: { audit: true }, comments: [], worklogs: [], t, theme: 'light', lang: 'tr',
    });
    expect(html).toContain('★');
    expect(html).toContain('☆');
  });

  it('döküman başlığı (dosya adı) "<kod> <suffix>" biçiminde', () => {
    const html = build({ ticketDetail: true });
    expect(html).toContain('<title>TCK-007 ticketDetail.pdfFileSuffix</title>');
  });

  it('seçili bölümde kayıt yoksa boş durum gösterir', () => {
    const html = buildTicketPdfHtml({
      ticket: baseTicket, ticketCode: 'TCK-007', sections: { conversation: true }, comments: [], worklogs: [], t, theme: 'light', lang: 'tr',
    });
    expect(html).toContain('ticketDetail.pdfEmpty');
  });

  it('lang attribute ve dark tema sınıfını uygular', () => {
    const html = build({ ticketDetail: true }, { theme: 'dark', lang: 'en' });
    expect(html).toContain('<html lang="en">');
    // dark palette: koyu zemin
    expect(html).toContain('#0f172a');
  });
});
