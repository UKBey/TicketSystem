import { describe, it, expect, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../../context/ToastContext';
import { installOnboardingMock } from '../../components/onboarding/onboardingMock';
import Dashboard from '../../pages/manager/Dashboard';

// installOnboardingMock yalnızca i18n.t/language kullanır; ajan adları mock'ta sabittir.
const i18nStub = { t: (k) => k, language: 'en' };

// Gerçek sayfanın (Manager Dashboard) onboarding mock adapter'ıyla GERÇEK metricService
// üzerinden örnek veriyle dolarak render olduğunu doğrular — "birebir + hatasız" güvencesi.
let restore;
afterEach(() => { restore?.(); restore = undefined; });

describe('onboarding renders the real page with mock data', () => {
  it('Manager Dashboard mock veriyle dolu render olur', async () => {
    restore = installOnboardingMock(i18nStub);
    render(
      <ToastProvider>
        <MemoryRouter><Dashboard /></MemoryRouter>
      </ToastProvider>
    );
    // Ajan performans tablosu mock ajan adıyla dolmalı (adapter → metricService → sayfa).
    // jsdom Tailwind'i uygulamadığından mobil + masaüstü düzen aynı anda DOM'da olur → birden çok eşleşme.
    const matches = await screen.findAllByText('Ahmet Çelik', {}, { timeout: 4000 });
    expect(matches.length).toBeGreaterThan(0);
  });
});
