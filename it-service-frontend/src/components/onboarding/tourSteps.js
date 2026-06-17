/**
 * Onboarding turunun rol-duyarlı adımlarını üretir. Tur, kullanıcının GERÇEK sayfalarını
 * (mock veriyle) gezer ve her sayfanın PARÇALARINI spotlight ile gösterir (az yazı, çok görsel).
 *
 * Sıralama: Yönetim/Gözetim (dashboard) → Admin (kullanıcı/ürün) → Lead (takım) →
 * Agent (performans/çalışma alanı/havuz) → ortak içerik (hazır yanıtlar) → Customer
 * (genel bakış/biletlerim) → ortak Ayarlar/İpuçları (navbar) → Bitiş.
 *
 * Her alan koşula göre BİR kez eklenir (duplicate yok). Alan başına:
 *  - nav adımı  : önceki sayfada kalırken sol menüdeki ilgili öğeyi gösterir (clickHint).
 *  - parça adımları: route'a gider, gerçek sayfadaki belirli bölümleri (KPI, grafik, tablo,
 *                    buton…) spotlight'lar; kısa bir başlık + tek cümle açıklama.
 * İlk alanın nav adımı yoktur (welcome doğrudan ilk sayfaya açılır).
 *
 * Adım alanları: { id, route, target, placement, clickHint, titleKey, bodyKey }
 */
export function buildTourSteps(roles) {
  const has = (r) => roles.includes(r);
  const isManager = has('MANAGER');
  const isAdmin = has('ADMIN');
  const isLead = has('LEAD_AGENT');
  const isAgent = has('AGENT') || isLead; // lead, AGENT composite'ini kapsar
  const isCustomer = has('CUSTOMER');
  const onlyCustomer = isCustomer && roles.length === 1;

  // route → sidebar nav anahtarı (a[href]) + i18n nav anahtarı
  const NAV_KEY = {
    '/dashboard': 'dashboard',
    '/admin': 'admin',
    '/user-management': 'users',
    '/products': 'products',
    '/team': 'team',
    '/my-performance': 'performance',
    '/workspace': 'workspace',
    '/pool': 'pool',
    '/canned-responses': 'canned',
    '/overview': 'overview',
    '/my-tickets': 'mytickets',
  };

  // Sıraya göre alanlar + her alanın spotlight'lanacak parçaları.
  const AREAS = [
    { route: '/dashboard', when: isManager || isLead || isAdmin, parts: [
      { target: 'dash-kpis',   key: 'dashKpis',   placement: 'bottom' },
      { target: 'dash-charts', key: 'dashCharts', placement: 'top' },
      { target: 'dash-agents', key: 'dashAgents', placement: 'top' },
    ] },
    { route: '/admin', when: isAdmin, parts: [
      { target: 'admin-access', key: 'adminAccess', placement: 'top' },
    ] },
    { route: '/user-management', when: isAdmin || isManager, parts: [
      { target: 'users-create', key: 'usersCreate', placement: 'bottom', when: isAdmin },
      { target: 'users-table',  key: 'usersTable',  placement: 'top' },
    ] },
    { route: '/products', when: isAdmin || isManager, parts: [
      { target: 'products-create', key: 'productsCreate', placement: 'bottom', when: isAdmin },
      { target: 'products-table',  key: 'productsTable',  placement: 'top' },
    ] },
    { route: '/team', when: isLead, parts: [
      { target: 'team-list', key: 'teamList', placement: 'top' },
    ] },
    { route: '/my-performance', when: isAgent, parts: [
      { target: 'perf-kpis',   key: 'perfKpis',   placement: 'bottom' },
      { target: 'perf-charts', key: 'perfCharts', placement: 'top' },
    ] },
    { route: '/workspace', when: isAgent, parts: [
      { target: 'ws-list', key: 'wsList', placement: 'top' },
    ] },
    { route: '/pool', when: isAgent, parts: [
      { target: 'pool-list', key: 'poolList', placement: 'top' },
    ] },
    { route: '/canned-responses', when: isAgent || isAdmin, parts: [
      { target: 'canned-toolbar', key: 'cannedToolbar', placement: 'bottom' },
      { target: 'canned-grid',    key: 'cannedGrid',    placement: 'top' },
    ] },
    { route: '/overview', when: isCustomer, parts: [
      { target: 'overview-kpis',   key: 'overviewKpis',   placement: 'bottom' },
      { target: 'overview-charts', key: 'overviewCharts', placement: 'top' },
    ] },
    { route: '/my-tickets', when: isCustomer, parts: [
      { target: 'mytickets-new',  key: 'myticketsNew',  placement: 'bottom' },
      { target: 'mytickets-tabs', key: 'myticketsTabs', placement: 'bottom' },
      { target: 'mytickets-list', key: 'myticketsList', placement: 'top' },
    ] },
  ]
    .filter((a) => a.when)
    .map((a) => ({ ...a, parts: a.parts.filter((p) => p.when !== false) }));

  const steps = [];
  const firstRoute = AREAS.length ? AREAS[0].route : '/profile';

  // ── Karşılama (ilk gerçek sayfa arkada; tooltip ortada) ──
  steps.push({
    id: 'welcome',
    route: firstRoute,
    target: 'center',
    prefs: true, // karşılama adımında dil + tema seçici göster
    titleKey: 'onboarding.welcome.title',
    bodyKey: onlyCustomer ? 'onboarding.welcome.bodyCustomer' : 'onboarding.welcome.bodyStaff',
  });

  AREAS.forEach((area, i) => {
    const navKey = NAV_KEY[area.route];
    // İlk alan hariç: önceki sayfada kalıp sol menüdeki öğeyi göster ("buraya tıkla").
    if (i > 0) {
      steps.push({
        id: `nav-${navKey}`,
        route: AREAS[i - 1].route,
        target: `a[href="${area.route}"]`,
        placement: 'right',
        clickHint: true,
        titleKey: 'onboarding.nav.title',
        bodyKey: `onboarding.nav.${navKey}`,
      });
    }
    // Parça spotlight adımları (gerçek sayfada).
    area.parts.forEach((part) => {
      steps.push({
        id: `part-${part.key}`,
        route: area.route,
        target: `[data-tour="${part.target}"]`,
        placement: part.placement || 'auto',
        titleKey: `onboarding.parts.${part.key}.title`,
        bodyKey: `onboarding.parts.${part.key}.body`,
      });
    });
  });

  // ── Ortak: Ayarlar + İpuçları (navbar öğeleri; son sayfada kalırken) ──
  const lastRoute = AREAS.length ? AREAS[AREAS.length - 1].route : firstRoute;
  const NAVBAR_STEPS = [
    { id: 'set-lang',    target: '[data-tour="lang-switch"]',  key: 'settingsLang' },
    { id: 'set-theme',   target: '[data-tour="theme-toggle"]', key: 'settingsTheme' },
    { id: 'tip-palette', target: '[data-tour="cmd-palette"]',  key: 'tipsPalette' },
    { id: 'tip-bell',    target: '[data-tour="notif-bell"]',   key: 'tipsBell' },
  ];
  NAVBAR_STEPS.forEach((s) => {
    steps.push({
      id: s.id,
      route: lastRoute,
      target: s.target,
      placement: 'bottom',
      titleKey: `onboarding.steps.${s.key}.title`,
      bodyKey: `onboarding.steps.${s.key}.body`,
    });
  });

  // ── Ortak: Profil (navbar profil butonundan açılır; gerçek sayfa, mock yok) ──
  steps.push({
    id: 'nav-profile',
    route: lastRoute,
    target: '[data-tour="profile-menu"]',
    placement: 'bottom',
    clickHint: true,
    titleKey: 'onboarding.nav.title',
    bodyKey: 'onboarding.nav.profile',
  });
  const PROFILE_PARTS = [
    { target: 'profile-hero',    key: 'profileHero',    placement: 'bottom' },
    { target: 'profile-details', key: 'profileDetails', placement: 'right' },
    { target: 'profile-prefs',   key: 'profilePrefs',   placement: 'top' },
  ];
  PROFILE_PARTS.forEach((part) => {
    steps.push({
      id: `part-${part.key}`,
      route: '/profile',
      target: `[data-tour="${part.target}"]`,
      placement: part.placement,
      titleKey: `onboarding.parts.${part.key}.title`,
      bodyKey: `onboarding.parts.${part.key}.body`,
    });
  });

  // ── Bitiş (ortada — profil sayfasının üstünde) ──
  steps.push({
    id: 'ready',
    route: '/profile',
    target: 'center',
    titleKey: 'onboarding.ready.title',
    bodyKey: onlyCustomer ? 'onboarding.ready.bodyCustomer' : 'onboarding.ready.bodyStaff',
  });

  return steps;
}
