import { useState, useEffect, useMemo, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Joyride, EVENTS } from 'react-joyride';
import { useAuth } from '../../context/AuthContext';
import Sidebar from '../Sidebar';
import Navbar from '../Navbar';
import ErrorBoundary from '../ErrorBoundary';
import { buildTourSteps } from './tourSteps';
import { installOnboardingMock } from './onboardingMock';
import TourTooltip from './TourTooltip';

// Gerçek sayfalar — onboarding bunları mock veriyle render eder (birebir aynı sayfalar).
import Dashboard from '../../pages/manager/Dashboard';
import AdminPanel from '../../pages/manager/AdminPanel';
import UserManagementPage from '../../pages/manager/UserManagementPage';
import ProductPanel from '../../pages/manager/ProductPanel';
import TeamTickets from '../../pages/agent/TeamTickets';
import AgentDashboard from '../../pages/agent/AgentDashboard';
import Workspace from '../../pages/agent/Workspace';
import Pool from '../../pages/agent/Pool';
import CannedResponsesPage from '../../pages/CannedResponsesPage';
import CustomerDashboard from '../../pages/customer/CustomerDashboard';
import MyTickets from '../../pages/customer/MyTickets';
import ProfilePage from '../../pages/ProfilePage';

const PAGE_BY_ROUTE = {
  '/dashboard': Dashboard,
  '/admin': AdminPanel,
  '/user-management': UserManagementPage,
  '/products': ProductPanel,
  '/team': TeamTickets,
  '/my-performance': AgentDashboard,
  '/workspace': Workspace,
  '/pool': Pool,
  '/canned-responses': CannedResponsesPage,
  '/overview': CustomerDashboard,
  '/my-tickets': MyTickets,
  '/profile': ProfilePage,
};

/** Bir elemanın DOM'a gelmesini bekler (sayfa geçişlerinde hedef hazır olana dek). */
function waitForElement(selector, timeout = 3000) {
  return new Promise((resolve) => {
    if (!selector || selector === 'body') return resolve(document.body);
    const started = performance.now();
    const tick = () => {
      const el = document.querySelector(selector);
      if (el) return resolve(el);
      if (performance.now() - started > timeout) return resolve(null);
      requestAnimationFrame(tick);
    };
    tick();
  });
}

/** Hedefe "tıklanıyormuş" animasyonu oynatır (kullanıcı gerçekte tıklayamaz). */
function playClickAnim(selector) {
  return new Promise((resolve) => {
    const el = selector && selector !== 'center' ? document.querySelector(selector) : null;
    if (!el) return resolve();
    el.classList.add('tour-click-anim');
    setTimeout(() => { el.classList.remove('tour-click-anim'); resolve(); }, 480);
  });
}

const noop = () => {};

export default function OnboardingTour() {
  const navigate = useNavigate();
  // `t` kimliği dil değişiminde yenilenir; tooltip metinleri yeniden çözümlenir.
  const { t, i18n } = useTranslation();
  const { roles, markOnboardingDone } = useAuth();

  // Mock adapter'ı İLK render sırasında (çocuk sayfalar fetch etmeden önce) kur.
  const restoreRef = useRef(null);
  if (restoreRef.current === null) {
    restoreRef.current = installOnboardingMock(i18n);
  }
  useEffect(() => () => { restoreRef.current?.(); restoreRef.current = null; }, []);

  const rawSteps = useMemo(() => buildTourSteps(roles), [roles]);

  const [index, setIndex] = useState(0);
  const [route, setRoute] = useState(rawSteps[0]?.route || '/dashboard');
  const [run, setRun] = useState(false);
  const transitioning = useRef(false);

  // İlk açılış — welcome adımı (hedef body, daima mevcut).
  useEffect(() => {
    const first = rawSteps[0];
    setRoute(first?.route || '/dashboard');
    setIndex(0);
    const id = requestAnimationFrame(() => setRun(true));
    return () => cancelAnimationFrame(id);
  }, [rawSteps]);

  // Aktif route'u URL'e yansıt — sol menüdeki NavLink "active" durumu doğru görünsün.
  useEffect(() => {
    navigate(route, { replace: true });
  }, [route, navigate]);

  const finish = useCallback(() => {
    setRun(false);
    markOnboardingDone();
    navigate('/', { replace: true });
  }, [markOnboardingDone, navigate]);

  // from → to geçişi. ÖNEMLİ: overlay'i (run) hiç kapatmıyoruz — kapatıp açmak
  // ekranı parlatıp tekrar griye çevirerek "flash" yaratıyordu. Bunun yerine adımın
  // sırasını, aktif hedef her an DOM'da olacak şekilde düzenliyoruz:
  //  - nav/navbar/center hedefleri KALICI'dır (sidebar/navbar/body hep ekranda) → route
  //    değişse bile bu hedefler kaybolmaz, overlay yerinde kalır.
  //  - part-* hedefleri sayfa içeriğine bağlıdır → önce route mount olmalı, sonra adım.
  const go = useCallback(async (from, to) => {
    if (transitioning.current) return;
    if (to < 0 || to >= rawSteps.length) return;
    const cur = rawSteps[from];
    const next = rawSteps[to];

    transitioning.current = true;
    try {
      if (to > from && cur?.clickHint) {
        await playClickAnim(cur.target);
      }
      if (next.route === cur.route) {
        setIndex(to);
      } else if (next.id.startsWith('part-')) {
        // Hedef yeni sayfanın içinde — önce sayfayı mount et, hedef gelince adımı geçir.
        // Bu sırada aktif adım (from) kalıcı bir hedeftedir (nav/sidebar), overlay sabit kalır.
        setRoute(next.route);
        const sel = next.target === 'center' ? 'body' : next.target;
        await waitForElement(sel);
        await new Promise((r) => requestAnimationFrame(r));
        setIndex(to);
      } else {
        // Hedef kalıcı (sidebar/navbar/center) — önce adımı geçir (hedef zaten var), sonra route.
        setIndex(to);
        setRoute(next.route);
      }
    } finally {
      transitioning.current = false;
    }
  }, [rawSteps]);

  // Joyride adımları — i18n çözümlenmiş metin + adıma bağlı handler'lar.
  const joyrideSteps = useMemo(() => {
    const total = rawSteps.length;
    return rawSteps.map((s, i) => ({
      target: s.target === 'center' ? 'body' : s.target,
      placement: s.target === 'center' ? 'center' : (s.placement || 'auto'),
      content: '',
      skipBeacon: true, // controlled modda beacon yerine tooltip doğrudan açılsın
      data: {
        title: t(s.titleKey),
        body: t(s.bodyKey),
        clickHint: !!s.clickHint,
        prefs: !!s.prefs,
        index: i,
        total,
        isFirst: i === 0,
        isLast: i === total - 1,
        onSkip: finish,
        onBack: () => go(i, i - 1),
        onNext: () => (i === total - 1 ? finish() : go(i, i + 1)),
      },
    }));
  }, [rawSteps, t, go, finish]);

  // Klavye: ← geri, →/Enter ileri, Esc atla.
  useEffect(() => {
    const handler = (e) => {
      if (e.key === 'Escape') finish();
      else if (e.key === 'ArrowLeft') go(index, index - 1);
      else if (e.key === 'ArrowRight' || e.key === 'Enter') {
        if (index === rawSteps.length - 1) finish();
        else go(index, index + 1);
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [index, rawSteps.length, go, finish]);

  // Joyride event — hedef bulunamazsa adımı atla (akış kontrolü tooltip butonlarında).
  const handleEvent = useCallback((data) => {
    if (data.type === EVENTS.TARGET_NOT_FOUND) {
      if (index < rawSteps.length - 1) go(index, index + 1);
      else finish();
    }
  }, [index, rawSteps.length, go, finish]);

  const PageComponent = PAGE_BY_ROUTE[route] || Dashboard;

  return (
    <div className="flex min-h-screen" style={{ backgroundColor: 'var(--bg-body)' }}>
      <Joyride
        steps={joyrideSteps}
        stepIndex={index}
        run={run}
        continuous
        scrollToFirstStep
        tooltipComponent={TourTooltip}
        onEvent={handleEvent}
        options={{
          zIndex: 10000,
          overlayColor: 'rgba(0,0,0,0.55)',
          primaryColor: '#6366f1',
          overlayClickAction: false,   // overlay tıklamada kapanmasın
          blockTargetInteraction: true, // hedef spotlight'ından tıklama geçmesin
          spotlightPadding: 6,
          // Sticky navbar (h-16 = 64px) hedefi örtmesin: yukarıdan pay bırakarak kaydır.
          scrollOffset: 96,
        }}
      />

      <Sidebar collapsed={false} onToggle={noop} mobileOpen={false} onMobileClose={noop} />

      <div className="flex flex-1 flex-col min-w-0 md:ml-[260px]">
        <Navbar onMenuClick={noop} />
        <main className="flex-1 min-w-0 p-4 sm:p-6 lg:p-8">
          <ErrorBoundary>
            <PageComponent />
          </ErrorBoundary>
        </main>
      </div>
    </div>
  );
}
