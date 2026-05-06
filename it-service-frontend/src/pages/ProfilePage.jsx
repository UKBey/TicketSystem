import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { User, Mail, Key, IdCard, Package, Bell, ChevronRight } from 'lucide-react';
import api from '../services/api';

export default function ProfilePage() {
  const navigate = useNavigate();
  const { user, getPrimaryRole } = useAuth();
  const primaryRole = getPrimaryRole();
  const [products, setProducts] = useState([]);
  const [loadingProducts, setLoadingProducts] = useState(true);

  useEffect(() => {
    const fetchUserProducts = async () => {
      try {
        // Mevcut kullanicinin yetkili urunlerini almak icin /users endpointinden tum kullanicilar cekilir
        // ve mevcut kullanici ID'sine gore filtrelenir.
        const res = await api.get('/users');
        const currentUser = res.data.find(u => u.id === user?.id);
        if (currentUser?.authorizedProducts) {
          setProducts(currentUser.authorizedProducts);
        }
      } catch (err) {
        // Kullanicinin /users endpointine erisimi yoksa sessizce atla.
        console.debug('Could not fetch authorized products:', err);
      } finally {
        setLoadingProducts(false);
      }
    };
    if (user?.id) fetchUserProducts();
  }, [user?.id]);

  const fields = [
    { icon: User, label: 'Full Name', value: user?.name || '-' },
    { icon: IdCard, label: 'Username', value: user?.username || '-' },
    { icon: Mail, label: 'Email', value: user?.email || '-' },
    { icon: Key, label: 'User ID', value: user?.id || '-', mono: true },
  ];

  return (
    <>
      <div className="mb-6">
        <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>Profile</h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>Your account information and authorized products.</p>
      </div>

      <div className="max-w-2xl space-y-6">
        {/* User Info Card */}
        <div className="rounded-xl border" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
          <div className="px-6 py-4 border-b font-semibold text-sm" style={{ borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}>
            Account Details
          </div>
          <div className="p-6 space-y-5">
            {/* Avatar section */}
            <div className="flex items-center gap-4 pb-5 border-b" style={{ borderColor: 'var(--border-color)' }}>
              <div
                className="flex h-16 w-16 items-center justify-center rounded-full text-xl font-bold text-white"
                style={{ background: 'linear-gradient(135deg, #3b82f6, #8b5cf6)' }}
              >
                {(user?.name || user?.username || 'U').split(' ').map(p => p[0]).slice(0, 2).join('').toUpperCase()}
              </div>
              <div>
                <div className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>{user?.name || user?.username || 'User'}</div>
                <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>{user?.email || '-'}</div>
                <span
                  className="inline-flex items-center rounded-full px-2.5 py-0.5 text-[10px] font-bold mt-1"
                  style={{ backgroundColor: '#dbeafe', color: '#1e40af' }}
                >
                  {primaryRole || 'USER'}
                </span>
              </div>
            </div>

            {/* Field list */}
            {fields.map((field) => (
              <div key={field.label} className="flex items-start gap-3">
                <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg" style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
                  <field.icon className="h-4 w-4" style={{ color: 'var(--text-tertiary)' }} />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-xs font-medium mb-0.5" style={{ color: 'var(--text-tertiary)' }}>{field.label}</div>
                  <div className={`text-sm font-medium ${field.mono ? 'font-mono text-xs break-all' : ''}`} style={{ color: 'var(--text-primary)' }}>
                    {field.value}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Notification Preferences Link Card */}
        <button
          onClick={() => navigate('/notification-preferences')}
          className="w-full rounded-xl border text-left transition-opacity hover:opacity-80 cursor-pointer"
          style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}
        >
          <div className="flex items-center justify-between px-6 py-4">
            <div className="flex items-center gap-3">
              <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg" style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
                <Bell className="h-4 w-4" style={{ color: 'var(--text-tertiary)' }} />
              </div>
              <div>
                <div className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>Notification Preferences</div>
                <div className="text-xs mt-0.5" style={{ color: 'var(--text-secondary)' }}>Manage which events trigger email notifications.</div>
              </div>
            </div>
            <ChevronRight className="h-4 w-4 flex-shrink-0" style={{ color: 'var(--text-tertiary)' }} />
          </div>
        </button>

        {/* Authorized Products Card */}
        <div className="rounded-xl border" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
          <div className="px-6 py-4 border-b font-semibold text-sm flex items-center gap-2" style={{ borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}>
            <Package className="h-4 w-4" style={{ color: 'var(--text-tertiary)' }} />
            Authorized Products
          </div>
          <div className="p-6">
            {loadingProducts ? (
              <div className="flex items-center justify-center py-6">
                <div className="h-6 w-6 rounded-full border-[3px] animate-spin" style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
              </div>
            ) : products.length > 0 ? (
              <div className="flex flex-wrap gap-2">
                {products.map((product) => (
                  <span
                    key={product.id}
                    className="inline-flex items-center gap-1.5 rounded-lg border px-3 py-2 text-sm font-medium transition-colors"
                    style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
                  >
                    <Package className="h-3.5 w-3.5" style={{ color: 'var(--text-tertiary)' }} />
                    {product.name}
                  </span>
                ))}
              </div>
            ) : (
              <div className="text-center py-4">
                <div className="text-sm" style={{ color: 'var(--text-tertiary)' }}>
                  No authorized products assigned.
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </>
  );
}
