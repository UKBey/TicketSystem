import { describe, it, expect } from 'vitest';
import { buildTourSteps } from '../../components/onboarding/tourSteps';

/** part-* (sayfa parçası) adımlarından gezilen route sırasını (tekrarsız) çıkarır. */
function routeOrder(steps) {
  const seen = [];
  for (const s of steps) {
    if (s.id.startsWith('part-') && !seen.includes(s.route)) seen.push(s.route);
  }
  return seen;
}

describe('buildTourSteps', () => {
  it('müşteri (tek rol) yalnızca müşteri sayfalarını gezer', () => {
    const steps = buildTourSteps(['CUSTOMER']);
    const routes = routeOrder(steps);
    expect(steps[0].id).toBe('welcome');
    // Müşteri: genel bakış + biletlerim + (herkese ortak) profil
    expect(routes).toEqual(['/overview', '/my-tickets', '/profile']);
    // Personel sayfaları olmamalı
    expect(routes).not.toContain('/dashboard');
    expect(routes).not.toContain('/workspace');
    expect(routes).not.toContain('/user-management');
    expect(steps[steps.length - 1].id).toBe('ready');
  });

  it('rolleri Yönetim → Admin → Lead → Agent sırasıyla gezer', () => {
    const steps = buildTourSteps(['AGENT', 'ADMIN', 'MANAGER', 'LEAD_AGENT']);
    const routes = routeOrder(steps);
    // dashboard (oversight) önce, admin paneli, sonra users/products, lead (team), agent...
    expect(routes).toContain('/admin');
    expect(routes.indexOf('/dashboard')).toBeLessThan(routes.indexOf('/admin'));
    expect(routes.indexOf('/admin')).toBeLessThan(routes.indexOf('/user-management'));
    expect(routes.indexOf('/user-management')).toBeLessThan(routes.indexOf('/team'));
    expect(routes.indexOf('/team')).toBeLessThan(routes.indexOf('/workspace'));
    expect(routes).toContain('/pool');
    expect(routes).toContain('/canned-responses');
  });

  it('paylaşılan sayfaları (dashboard) yalnızca bir kez gezer — tekrar yok', () => {
    const steps = buildTourSteps(['MANAGER', 'LEAD_AGENT', 'ADMIN']);
    const navDashboard = steps.filter((s) => s.id === 'nav-dashboard');
    // dashboard ilk alan olduğundan nav adımı yok; route bir kez gezilir
    const routes = routeOrder(steps);
    expect(navDashboard).toHaveLength(0);
    expect(routes.filter((r) => r === '/dashboard')).toHaveLength(1);
    // route'lar benzersiz olmalı (duplicate sayfa yok)
    expect(new Set(routes).size).toBe(routes.length);
  });

  it('parça adımları data-tour hedeflerini gösterir', () => {
    const steps = buildTourSteps(['CUSTOMER']);
    const kpis = steps.find((s) => s.id === 'part-overviewKpis');
    expect(kpis).toBeTruthy();
    expect(kpis.target).toBe('[data-tour="overview-kpis"]');
    expect(kpis.route).toBe('/overview');
  });

  it('ilk alanın nav adımı yoktur; sonraki alanlar "buraya tıkla" nav adımıyla gelir', () => {
    const steps = buildTourSteps(['ADMIN', 'MANAGER']); // dashboard, users, products
    // İlk parça adımı welcome'dan hemen sonra gelir (nav adımı olmadan)
    const firstPartIdx = steps.findIndex((s) => s.id.startsWith('part-'));
    expect(steps[firstPartIdx - 1].id).toBe('welcome');
    // İkinci alan (users) bir nav adımıyla başlar ve clickHint taşır
    const navUsers = steps.find((s) => s.id === 'nav-users');
    expect(navUsers).toBeTruthy();
    expect(navUsers.clickHint).toBe(true);
    expect(navUsers.target).toBe('a[href="/user-management"]');
  });

  it('usersCreate parçası yalnızca admin için eklenir (manager salt-okunur)', () => {
    const adminIds = buildTourSteps(['ADMIN']).map((s) => s.id);
    const managerIds = buildTourSteps(['MANAGER']).map((s) => s.id);
    expect(adminIds).toContain('part-usersCreate');
    expect(managerIds).not.toContain('part-usersCreate');
    // tablo parçası ikisinde de var
    expect(managerIds).toContain('part-usersTable');
  });

  it('welcome ile başlar, navbar ayar/ipucu + profil adımları ve ready ile biter', () => {
    const steps = buildTourSteps(['AGENT']);
    const ids = steps.map((s) => s.id);
    expect(ids[0]).toBe('welcome');
    expect(ids).toContain('set-lang');
    expect(ids).toContain('set-theme');
    expect(ids).toContain('tip-palette');
    expect(ids).toContain('tip-bell');
    // Profil: navbar profil butonundan açılır + gerçek sayfa parçaları
    const navProfile = steps.find((s) => s.id === 'nav-profile');
    expect(navProfile.clickHint).toBe(true);
    expect(navProfile.target).toBe('[data-tour="profile-menu"]');
    expect(ids).toContain('part-profileHero');
    expect(ids).toContain('part-profileDetails');
    expect(ids).toContain('part-profilePrefs');
    // Profil parçaları gerçek /profile rotasında
    expect(steps.find((s) => s.id === 'part-profileHero').route).toBe('/profile');
    expect(ids[ids.length - 1]).toBe('ready');
  });

  it('lead, ekip ve performans/çalışma alanı sayfalarını gezer', () => {
    const routes = routeOrder(buildTourSteps(['LEAD_AGENT']));
    expect(routes).toContain('/team');
    expect(routes).toContain('/my-performance');
    expect(routes).toContain('/workspace');
    expect(routes).toContain('/pool');
  });

  it('saf agent ekip sayfasını gezmez', () => {
    const routes = routeOrder(buildTourSteps(['AGENT']));
    expect(routes).not.toContain('/team');
    expect(routes).toContain('/workspace');
  });
});
