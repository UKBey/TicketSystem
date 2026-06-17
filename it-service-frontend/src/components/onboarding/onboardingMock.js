import axios from 'axios';
import api from '../../services/api';

/**
 * Onboarding sırasında GERÇEK sayfaları örnek (sahte) veriyle göstermek için axios
 * adapter'ını geçici olarak değiştirir. Yalnızca tur sayfalarının okuduğu GET uç
 * noktalarını yakalar ve hardcoded (i18n) yanıt döner; eşleşmeyen tüm istekler (ör.
 * /users/sync, PUT /users/me/onboarding-complete, /notifications) gerçek backend'e geçer.
 *
 * Böylece onboarding ekranları kullanıcının GERÇEK sayfalarının birebir aynısıdır —
 * sadece içlerindeki veri sabittir. install → uninstall (return) ile geri alınır.
 *
 * @param {import('i18next').i18n} i18n  aktif i18next örneği (dil + çeviri)
 * @returns {() => void} adapter'ı eski haline döndüren fonksiyon
 */
export function installOnboardingMock(i18n) {
  const prev = api.defaults.adapter;
  const realAdapter = axios.getAdapter(prev || axios.defaults.adapter);
  const t = (k, o) => i18n.t(k, o);

  const ok = (data, config) => Promise.resolve({
    data, status: 200, statusText: 'OK', headers: {}, config, request: {},
  });

  // ── Ortak referans veriler ───────────────────────────────────────────────
  const now = Date.UTC(2026, 5, 17, 9, 0, 0); // sabit referans (Date.now kullanma)
  const iso = (hoursAgo) => new Date(now - hoursAgo * 3600 * 1000).toISOString();
  const day = (daysAgo) => new Date(now - daysAgo * 86400 * 1000).toISOString().slice(0, 10);

  const PRODUCTS = [
    { id: 1, nameTr: 'Mobil Uygulama',    nameEn: 'Mobile App' },
    { id: 2, nameTr: 'Web Platformu',     nameEn: 'Web Platform' },
    { id: 3, nameTr: 'API / Entegrasyon', nameEn: 'API / Integration' },
    { id: 4, nameTr: 'Fatura & Ödeme',    nameEn: 'Billing & Payment' },
  ];
  const AGENTS = [
    { id: 'a1', name: 'Ahmet Çelik' },
    { id: 'a2', name: 'Büşra Koç' },
    { id: 'a3', name: 'Can Öztürk' },
    { id: 'a4', name: 'Duygu Şahin' },
  ];

  // ── Ticket örnekleri (TicketTable / RecentTicketsList şekli) ─────────────
  const TICKETS = [
    { id: 2048, titleKey: 'tkAppCrash',   status: 'IN_PROGRESS',          priority: 'HIGH',     productId: 1, topicId: 11, slaBreached: false, csatRating: null, claimers: [{ agentId: 'a1', agentName: 'Ahmet Çelik' }], createdAt: iso(5) },
    { id: 2047, titleKey: 'tkLogin',      status: 'WAITING_FOR_CUSTOMER', priority: 'MEDIUM',   productId: 2, topicId: 21, slaBreached: false, csatRating: null, claimers: [{ agentId: 'a2', agentName: 'Büşra Koç' }], createdAt: iso(20) },
    { id: 2046, titleKey: 'tkInvoice',    status: 'NEW',                  priority: 'LOW',      productId: 4, topicId: 41, slaBreached: false, csatRating: null, claimers: [], createdAt: iso(2) },
    { id: 2045, titleKey: 'tkApiTimeout', status: 'IN_PROGRESS',          priority: 'CRITICAL', productId: 3, topicId: 31, slaBreached: true,  csatRating: null, claimers: [{ agentId: 'a1', agentName: 'Ahmet Çelik' }, { agentId: 'a3', agentName: 'Can Öztürk' }], createdAt: iso(30) },
    { id: 2044, titleKey: 'tkPassword',   status: 'RESOLVED',             priority: 'MEDIUM',   productId: 2, topicId: 22, slaBreached: false, csatRating: 5,    claimers: [{ agentId: 'a2', agentName: 'Büşra Koç' }], createdAt: iso(48) },
    { id: 2043, titleKey: 'tkSlowReport', status: 'RESOLVED',             priority: 'LOW',      productId: 1, topicId: 12, slaBreached: false, csatRating: 4,    claimers: [{ agentId: 'a4', agentName: 'Duygu Şahin' }], createdAt: iso(72) },
  ];
  const topicName = (id) => t(`onboarding.mock.topic${id}`, { defaultValue: '' });
  const buildTicket = (tk) => ({
    id: tk.id,
    title: t(`onboarding.mock.${tk.titleKey}`),
    status: tk.status,
    priority: tk.priority,
    productId: tk.productId,
    topicId: tk.topicId,
    topicNameTr: topicName(tk.topicId),
    topicNameEn: topicName(tk.topicId),
    createdAt: tk.createdAt,
    updatedAt: tk.createdAt,
    slaBreached: tk.slaBreached,
    claimers: tk.claimers,
    csatRating: tk.csatRating,
  });
  const page = (items) => ({
    content: items, totalElements: items.length, totalPages: 1, number: 0, size: 20,
  });
  const ticketPage = (filterFn) => page((filterFn ? TICKETS.filter(filterFn) : TICKETS).map(buildTicket));

  // ── Metrikler ─────────────────────────────────────────────────────────────
  const statusDistribution = {
    newCount: 14, inProgressCount: 23, waitingForCustomerCount: 9,
    resolvedCount: 31, closedCount: 65, totalCount: 142,
  };
  const timeline = {
    timeline: Array.from({ length: 10 }, (_, i) => ({
      date: day(9 - i),
      created: [6, 9, 5, 11, 7, 10, 6, 12, 8, 9][i],
      resolved: [4, 7, 6, 8, 5, 9, 7, 10, 6, 8][i],
      closed: [2, 3, 2, 4, 3, 3, 2, 5, 3, 4][i],
      slaBreach: [0, 1, 0, 1, 0, 0, 1, 0, 0, 1][i],
    })),
  };
  const agentName = (i) => AGENTS[i].name;
  const agentPerformance = {
    agents: [
      { agentId: 'a1', agentName: agentName(0), role: 'LEAD_AGENT', activeTickets: 8, resolvedLast24Hours: 6, avgResolutionHours: 3.4, csatAverage: 4.6, slaBreachedCount: 0, worklogMinutesLast7Days: 1860 },
      { agentId: 'a2', agentName: agentName(1), role: 'AGENT',      activeTickets: 5, resolvedLast24Hours: 4, avgResolutionHours: 4.1, csatAverage: 4.4, slaBreachedCount: 1, worklogMinutesLast7Days: 1520 },
      { agentId: 'a3', agentName: agentName(2), role: 'AGENT',      activeTickets: 10, resolvedLast24Hours: 7, avgResolutionHours: 2.9, csatAverage: 4.8, slaBreachedCount: 0, worklogMinutesLast7Days: 2040 },
      { agentId: 'a4', agentName: agentName(3), role: 'AGENT',      activeTickets: 3, resolvedLast24Hours: 3, avgResolutionHours: 5.2, csatAverage: 4.2, slaBreachedCount: 0, worklogMinutesLast7Days: 980 },
    ],
    totalAgents: 4, totalActiveTickets: 26, totalResolvedLast24Hours: 20, averageCsat: 4.5,
  };
  const prioritySla = {
    priorityMetrics: [
      { priority: 'CRITICAL', ticketCount: 6,  slaTargetHours: 4,  avgResolutionHours: 3.6, breachCount: 1, breachPercentage: 16.7, onTimePercentage: 83.3 },
      { priority: 'HIGH',     ticketCount: 18, slaTargetHours: 8,  avgResolutionHours: 6.4, breachCount: 2, breachPercentage: 11.1, onTimePercentage: 88.9 },
      { priority: 'MEDIUM',   ticketCount: 42, slaTargetHours: 24, avgResolutionHours: 14.2, breachCount: 3, breachPercentage: 7.1,  onTimePercentage: 92.9 },
      { priority: 'LOW',      ticketCount: 76, slaTargetHours: 48, avgResolutionHours: 22.8, breachCount: 1, breachPercentage: 1.3,  onTimePercentage: 98.7 },
    ],
  };
  const productMetrics = {
    productMetrics: PRODUCTS.map((p, i) => ({
      productId: p.id,
      productNameTr: p.nameTr,
      productNameEn: p.nameEn,
      totalTickets: [58, 44, 22, 18][i],
      openTickets: [12, 9, 4, 3][i],
      avgResolutionHours: [5.2, 6.1, 8.4, 4.3][i],
      csatAverage: [4.5, 4.3, 4.1, 4.6][i],
      slaBreachPercentage: [4.2, 6.5, 9.1, 2.0][i],
    })),
  };
  const csatMetrics = {
    averageRating: 4.5, totalResponses: 128,
    ratingDistribution: { 1: 3, 2: 4, 3: 12, 4: 41, 5: 68 },
    trend: { thisMonth: 4.6, lastMonth: 4.3, trend: 'UP' },
    byPriority: {
      CRITICAL: { avg: 4.2, responses: 14 },
      HIGH: { avg: 4.4, responses: 33 },
      MEDIUM: { avg: 4.6, responses: 52 },
      LOW: { avg: 4.7, responses: 29 },
    },
    topComments: [t('onboarding.mock.comment1'), t('onboarding.mock.comment2'), t('onboarding.mock.comment3')],
  };
  const worklogCompletion = {
    periodDays: 30,
    agentWorklogs: AGENTS.map((a, i) => ({
      agentId: a.id, agentUsername: a.name, totalMinutes: [1860, 1520, 2040, 980][i], totalEntries: [31, 26, 34, 17][i],
    })),
    completionRates: {
      completionRate: 88.5, slaComplianceRate: 93.2, totalResolved: 96, totalClosed: 65,
      totalCreated: 142, resolvedInPeriod: 96, avgResolutionHours: 11.4,
    },
  };
  const alertsBacklog = {
    breachedSLA: [
      { ticketId: 2045, title: t('onboarding.mock.tkApiTimeout'), priority: 'CRITICAL', customerName: t('onboarding.mock.custAcme'), customerId: 'c1', hoursUntilDeadline: -2.5 },
    ],
    upcomingBreach: [
      { ticketId: 2048, title: t('onboarding.mock.tkAppCrash'), priority: 'HIGH', customerName: t('onboarding.mock.custTechno'), customerId: 'c2', hoursUntilDeadline: 1.5 },
    ],
    waitingTooLong: [
      { ticketId: 2047, title: t('onboarding.mock.tkLogin'), priority: 'MEDIUM', customerName: t('onboarding.mock.custGlobex'), customerId: 'c3', status: 'WAITING_FOR_CUSTOMER', hoursWaiting: 36.0 },
    ],
    backlogMetrics: { unassignedCount: 5, newTicketsWaiting: 14, avgWaitingHours: 7.8 },
  };

  // Müşteri kişisel dashboard
  const meCustomer = {
    openTickets: 3, totalTickets: 11, resolvedTickets: 7, avgResolutionHours: 6.5,
    slaBreachedCount: 0, csatAverage: 4.6, csatCount: 5,
    statusDistribution, timeline,
    recentTickets: TICKETS.slice(0, 5).map(buildTicket),
  };
  // Ajan kişisel dashboard
  const meAgent = {
    activeTickets: 8, totalClaimed: 64, resolvedInRange: 41, worklogMinutesInRange: 1860,
    avgResolutionHours: 3.4, slaBreachRate: 2.1, csatAverage: 4.6, csatCount: 28,
    statusDistribution, timeline,
    worklogTimeline: Array.from({ length: 10 }, (_, i) => ({ date: day(9 - i), minutes: [180, 240, 150, 300, 210, 260, 190, 320, 220, 280][i] })),
    csat: {
      ratingDistribution: { 1: 0, 2: 1, 3: 3, 4: 9, 5: 15 }, totalResponses: 28, average: 4.6,
      trend: Array.from({ length: 6 }, (_, i) => ({ date: day((5 - i) * 5), avg: [4.2, 4.3, 4.4, 4.5, 4.6, 4.7][i] })),
    },
    recentTickets: TICKETS.slice(0, 5).map(buildTicket),
  };

  // ── Kullanıcılar (UserManagementPage + AdminPanel) ─────────────────────────
  // AdminPanel kullanıcının yetkili ürünlerini (authorizedProducts) ve rollerini gösterir.
  const pref = (p) => ({ id: p.id, nameTr: p.nameTr, nameEn: p.nameEn });
  const USER_LIST = [
    { id: 'u1', fullName: 'Ahmet Çelik',  email: 'ahmet@example.com',  username: 'ahmet.celik',  role: 'LEAD_AGENT', roles: ['LEAD_AGENT'],       isActive: true,  createdAt: iso(2400), authorizedProducts: [pref(PRODUCTS[0]), pref(PRODUCTS[1])] },
    { id: 'u2', fullName: 'Büşra Koç',    email: 'busra@example.com',  username: 'busra.koc',    role: 'AGENT',      roles: ['AGENT'],            isActive: true,  createdAt: iso(2100), authorizedProducts: [pref(PRODUCTS[0])] },
    { id: 'u3', fullName: 'Can Öztürk',   email: 'can@example.com',    username: 'can.ozturk',   role: 'ADMIN',      roles: ['ADMIN', 'MANAGER'], isActive: true,  createdAt: iso(5000), authorizedProducts: [] },
    { id: 'u4', fullName: 'Duygu Şahin',  email: 'duygu@example.com',  username: 'duygu.sahin',  role: 'MANAGER',    roles: ['MANAGER'],          isActive: true,  createdAt: iso(1800), authorizedProducts: [] },
    { id: 'u5', fullName: 'Emre Demir',   email: 'emre@example.com',   username: 'emre.demir',   role: 'CUSTOMER',   roles: ['CUSTOMER'],         isActive: true,  createdAt: iso(900),  authorizedProducts: [pref(PRODUCTS[2])] },
    { id: 'u6', fullName: 'Selin Arslan', email: 'selin@example.com',  username: 'selin.arslan', role: 'CUSTOMER',   roles: ['CUSTOMER'],         isActive: false, createdAt: iso(600),  authorizedProducts: [pref(PRODUCTS[3])] },
  ];
  // AdminPanel admin/manager kullanıcıları hariç tutar (excludeGlobalRoles=true).
  const usersPage = (url) => {
    const exclude = /excludeGlobalRoles=true/.test(url || '');
    const list = exclude
      ? USER_LIST.filter((u) => !u.roles.some((r) => r === 'ADMIN' || r === 'MANAGER'))
      : USER_LIST;
    return page(list);
  };

  // ── Ürünler (ProductPanel /products/paged) ─────────────────────────────────
  const PRODUCTS_PAGED = page(PRODUCTS.map((p, i) => ({
    id: p.id, nameTr: p.nameTr, nameEn: p.nameEn, isActive: true, maxActiveTickets: [50, 40, 25, 30][i],
  })));

  // ── /products (dizi — filtreler + canned) ───────────────────────────────────
  const PRODUCTS_ARRAY = PRODUCTS.map((p) => ({
    id: p.id, nameTr: p.nameTr, nameEn: p.nameEn, productNameTr: p.nameTr, productNameEn: p.nameEn, isActive: true,
  }));

  // ── /users/agents (filtre dropdown) ─────────────────────────────────────────
  const AGENTS_ARRAY = AGENTS.map((a) => ({ id: a.id, name: a.name, fullName: a.name }));

  // ── Hazır yanıtlar (CannedResponsesPage) ────────────────────────────────────
  const CANNED = page([
    { id: 1, title: t('onboarding.mock.canned1Title'), shortcut: 'merhaba',  scope: 'SHARED',   productId: null, visibility: 'EXTERNAL', contentTr: t('onboarding.mock.canned1Tr'), contentEn: t('onboarding.mock.canned1En'), ownerAgentId: 'a1', favorite: true },
    { id: 2, title: t('onboarding.mock.canned2Title'), shortcut: 'cozuldu',  scope: 'SHARED',   productId: 1,    visibility: 'EXTERNAL', contentTr: t('onboarding.mock.canned2Tr'), contentEn: t('onboarding.mock.canned2En'), ownerAgentId: 'a1', favorite: false },
    { id: 3, title: t('onboarding.mock.canned3Title'), shortcut: 'bilgi',    scope: 'PERSONAL', productId: null, visibility: 'BOTH',     contentTr: t('onboarding.mock.canned3Tr'), contentEn: t('onboarding.mock.canned3En'), ownerAgentId: 'a1', favorite: false },
    { id: 4, title: t('onboarding.mock.canned4Title'), shortcut: 'eskalasyon', scope: 'PERSONAL', productId: 3,  visibility: 'INTERNAL', contentTr: t('onboarding.mock.canned4Tr'), contentEn: t('onboarding.mock.canned4En'), ownerAgentId: 'a1', favorite: false },
  ]);

  // Not: /agents/{id}/limits MOCK'LANMAZ. Workspace'in kapasite kartları gerçek
  // backend'den beslensin ki kullanıcının GERÇEK ekranıyla birebir olsun (limit yoksa
  // kart da çıkmaz) — sahte limit enjekte etmek "nereden çıktı" sorununa yol açıyordu.

  // ── Eşleştirme tablosu (sıra önemli — daha özgül olan üstte) ────────────────
  const routes = [
    { m: /^\/tickets\/my-assigned/,        d: () => ticketPage((tk) => ['IN_PROGRESS', 'WAITING_FOR_CUSTOMER', 'RESOLVED'].includes(tk.status)) },
    { m: /^\/tickets\/pool/,               d: () => ticketPage((tk) => tk.status === 'NEW') },
    { m: /^\/tickets\/team/,               d: () => ticketPage((tk) => tk.claimers.length > 0) },
    { m: /^\/tickets(\?|$)/,               d: () => ticketPage() },
    { m: /^\/users\/agents/,               d: () => AGENTS_ARRAY },
    { m: /^\/users(\?|$)/,                 d: (url) => usersPage(url) },
    { m: /^\/products\/paged/,             d: () => PRODUCTS_PAGED },
    { m: /^\/products\/[^/]+\/topics/,     d: () => [] },
    { m: /^\/products(\?|$)/,              d: () => PRODUCTS_ARRAY },
    { m: /^\/canned-responses\/paged/,     d: () => CANNED },
    { m: /^\/metrics\/me\/customer/,       d: () => meCustomer },
    { m: /^\/metrics\/me\/agent/,          d: () => meAgent },
    { m: /^\/metrics\/dashboard-summary/,  d: () => ({ totalOpenTickets: 46, newTicketsLast24Hours: 14, slaBreachedCount: 3, slaBreachedPercentage: 6.5, avgResponseTimeHours: 2.4, csatAverage: 4.5, csatTotalResponses: 128, priorityDistribution: { critical: 6, high: 18, medium: 42, low: 76 } }) },
    { m: /^\/metrics\/status-distribution/, d: () => statusDistribution },
    { m: /^\/metrics\/agent-performance/,  d: () => agentPerformance },
    { m: /^\/metrics\/ticket-timeline/,    d: () => timeline },
    { m: /^\/metrics\/priority-sla-metrics/, d: () => prioritySla },
    { m: /^\/metrics\/product-metrics/,    d: () => productMetrics },
    { m: /^\/metrics\/csat-metrics/,       d: () => csatMetrics },
    { m: /^\/metrics\/worklog-completion/, d: () => worklogCompletion },
    { m: /^\/metrics\/alerts-backlog/,     d: () => alertsBacklog },
  ];

  api.defaults.adapter = (config) => {
    const method = (config.method || 'get').toLowerCase();
    const url = config.url || '';
    if (method === 'get') {
      const hit = routes.find((r) => r.m.test(url));
      if (hit) return ok(hit.d(url), config);
    }
    // Eşleşmeyen her şey gerçek backend'e gider (sync, onboarding-complete, bildirimler...).
    return realAdapter(config);
  };

  return () => { api.defaults.adapter = prev; };
}
