import { useEffect, useMemo, useState } from 'react';
import { useParams, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ArrowLeft, Loader2 } from 'lucide-react';
import api from '../../services/api';
import { rolesOf } from '../../utils/userRoles';
import AgentDashboard from '../agent/AgentDashboard';
import CustomerDashboard from '../customer/CustomerDashboard';

/**
 * UserPerformancePage — bir yöneticinin/lead'in başka bir kullanıcının performans
 * chart'larını görüntülediği sayfa. Kullanıcı bilgisi öncelikle yönlendirme state'inden
 * (User Management / Dashboard'dan tıklama) okunur; sayfa yenilenirse `/users/{id}`'den çekilir.
 *
 * Müşteri rolü tekildir; bu yüzden kullanıcı ya müşteri ya da personeldir. Buna göre
 * doğru dashboard (customer/agent) viewUserId ile yeniden kullanılır.
 */
export default function UserPerformancePage() {
  const { userId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const { t } = useTranslation();

  const [user, setUser] = useState(location.state?.user ?? null);
  const [loading, setLoading] = useState(!location.state?.user);
  const [error, setError] = useState('');

  useEffect(() => {
    if (user && user.id === userId) return;
    let cancelled = false;
    api.get(`/users/${userId}`)
      .then((res) => { if (!cancelled) setUser(res.data); })
      .catch(() => { if (!cancelled) setError(t('userPerformance.loadError')); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [userId, user, t]);

  const isCustomer = useMemo(() => rolesOf(user || {}).includes('CUSTOMER'), [user]);
  const displayName = user?.fullName || user?.email || userId;

  return (
    <div className="space-y-4">
      <button
        type="button"
        onClick={() => navigate(-1)}
        className="inline-flex items-center gap-2 rounded-lg border px-3 py-2 text-sm font-semibold transition-colors cursor-pointer hover:bg-primary-50 dark:hover:bg-primary-500/10"
        style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)', backgroundColor: 'var(--bg-surface)' }}
      >
        <ArrowLeft className="h-4 w-4" />
        {t('userPerformance.back')}
      </button>

      {loading ? (
        <div className="flex items-center justify-center gap-2 py-20" style={{ color: 'var(--text-tertiary)' }}>
          <Loader2 className="h-5 w-5 animate-spin" />
          <span className="text-sm">{t('common.loading')}</span>
        </div>
      ) : error ? (
        <div className="rounded-2xl border px-4 py-3 text-sm font-medium" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'rgba(239, 68, 68, 0.25)', color: 'var(--color-danger-600)' }}>
          {error}
        </div>
      ) : isCustomer ? (
        <CustomerDashboard viewUserId={userId} viewUserName={displayName} />
      ) : (
        <AgentDashboard viewUserId={userId} viewUserName={displayName} />
      )}
    </div>
  );
}
