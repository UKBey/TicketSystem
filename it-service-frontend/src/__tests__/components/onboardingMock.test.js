import { describe, it, expect } from 'vitest';
import { installOnboardingMock } from '../../components/onboarding/onboardingMock';
import api from '../../services/api';

// installOnboardingMock yalnızca i18n.t ve i18n.language kullanır — stub yeterli.
const i18nStub = { t: (k) => k, language: 'en' };

describe('onboardingMock adapter', () => {
  it('sayfa GET uç noktalarını mock veriyle yakalar ve uninstall ile geri alır', async () => {
    const original = api.defaults.adapter;
    const restore = installOnboardingMock(i18nStub);
    try {
      // Metrikler — gerçek Dashboard'ın beklediği şekil
      const summary = await api.get('/metrics/dashboard-summary');
      expect(summary.data.totalOpenTickets).toBeGreaterThan(0);
      expect(summary.data.priorityDistribution).toBeTruthy();

      const status = await api.get('/metrics/status-distribution');
      expect(status.data.totalCount).toBeGreaterThan(0);

      const csat = await api.get('/metrics/csat-metrics');
      expect(csat.data.ratingDistribution['5']).toBeGreaterThan(0);
      expect(csat.data.trend.trend).toBe('UP');

      // Sayfalı liste zarfı (content/totalElements/totalPages)
      const users = await api.get('/users?page=0&size=20');
      expect(Array.isArray(users.data.content)).toBe(true);
      expect(users.data.content.length).toBeGreaterThan(0);
      expect(typeof users.data.totalElements).toBe('number');
      expect(users.data.totalPages).toBe(1);

      // Ticket endpoint'leri durum filtresine göre farklı veri döner
      const pool = await api.get('/tickets/pool?page=0');
      expect(pool.data.content.length).toBeGreaterThan(0);
      expect(pool.data.content.every((t) => t.status === 'NEW')).toBe(true);

      const assigned = await api.get('/tickets/my-assigned?page=0');
      expect(assigned.data.content.every((t) => t.status !== 'NEW')).toBe(true);

      // /products hem nameTr/En hem productNameTr/En taşımalı (iki tüketici)
      const products = await api.get('/products');
      expect(products.data[0].nameTr).toBeTruthy();
      expect(products.data[0].productNameEn).toBeTruthy();

      const canned = await api.get('/canned-responses/paged?page=0');
      expect(canned.data.content.length).toBeGreaterThan(0);
      expect(canned.data.content[0]).toHaveProperty('contentTr');
    } finally {
      restore();
    }

    // Adapter geri alınmış olmalı
    expect(api.defaults.adapter).toBe(original);
  });

  it('eşleşmeyen GET için gerçek adapter\'a düşer (mock dönmez)', async () => {
    const restore = installOnboardingMock(i18nStub);
    try {
      // /notifications mock listesinde yok → gerçek adapter'a düşer; ağ yok → hata fırlatır.
      await expect(api.get('/notifications')).rejects.toBeTruthy();
    } finally {
      restore();
    }
  });
});
