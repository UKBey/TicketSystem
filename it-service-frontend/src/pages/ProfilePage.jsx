import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../context/AuthContext';
import {
  Mail, Key, IdCard, Package, Bell, ChevronRight,
  User, Shield, ShieldCheck, ShieldAlert, Globe, ExternalLink, Settings,
  Pencil, Check, X, Lock,
} from 'lucide-react';
import api from '../services/api';
import userService from '../services/userService';
import i18n from '../i18n';
import ChangePasswordModal from '../components/ChangePasswordModal';
import TwoFactorModal from '../components/TwoFactorModal';

/* ── Role meta ─────────────────────────────────────────────── */
const ROLE_META = {
  CUSTOMER:    { label: 'Customer',    color: '#0ea5e9', bg: 'rgba(14,165,233,0.12)' },
  AGENT:       { label: 'Agent',       color: '#8b5cf6', bg: 'rgba(139,92,246,0.12)' },
  AGENT_ADMIN: { label: 'Agent Admin', color: '#f59e0b', bg: 'rgba(245,158,11,0.12)' },
  MANAGER:     { label: 'Manager',     color: '#22c55e', bg: 'rgba(34,197,94,0.12)'  },
};

/* ── Avatar initials ────────────────────────────────────────── */
function getInitials(name) {
  if (!name) return 'U';
  return name.split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase();
}

/* ── Avatar gradient by role ────────────────────────────────── */
const ROLE_GRADIENT = {
  CUSTOMER:    'linear-gradient(135deg, #0ea5e9 0%, #6366f1 100%)',
  AGENT:       'linear-gradient(135deg, #8b5cf6 0%, #ec4899 100%)',
  AGENT_ADMIN: 'linear-gradient(135deg, #f59e0b 0%, #ef4444 100%)',
  MANAGER:     'linear-gradient(135deg, #22c55e 0%, #0ea5e9 100%)',
  default:     'linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%)',
};

/* ── Small info row ─────────────────────────────────────────── */
function InfoRow({ icon: Icon, label, value, mono = false, onEdit }) {
  return (
    <div className="flex items-center gap-3 py-3 group" style={{ borderBottom: '1px solid var(--border-color)' }}>
      <div
        className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg"
        style={{ backgroundColor: 'var(--bg-surface-secondary)' }}
      >
        <Icon className="h-3.5 w-3.5" style={{ color: 'var(--text-tertiary)' }} />
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-[11px] font-medium uppercase tracking-wide mb-0.5" style={{ color: 'var(--text-tertiary)' }}>
          {label}
        </p>
        <p
          className={`text-sm font-medium truncate ${mono ? 'font-mono text-xs' : ''}`}
          style={{ color: 'var(--text-primary)' }}
          title={value}
        >
          {value || '—'}
        </p>
      </div>
      {onEdit && (
        <button
          type="button"
          onClick={onEdit}
          className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-md opacity-0 group-hover:opacity-100 transition-opacity"
          style={{ color: 'var(--text-tertiary)' }}
          aria-label="edit"
        >
          <Pencil className="h-3.5 w-3.5" />
        </button>
      )}
    </div>
  );
}

/* ── Editable row — for fullName (two inputs) or email (one input) ── */
function EditableRow({ icon: Icon, label, fields, onSave, onCancel, saving, error, t }) {
  // fields: [{ name, value, placeholder, type }]
  const [values, setValues] = useState(() =>
    fields.reduce((acc, f) => ({ ...acc, [f.name]: f.value ?? '' }), {})
  );
  const handleChange = (name) => (e) => setValues((v) => ({ ...v, [name]: e.target.value }));

  return (
    <div className="flex items-start gap-3 py-3" style={{ borderBottom: '1px solid var(--border-color)' }}>
      <div
        className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg mt-5"
        style={{ backgroundColor: 'var(--bg-surface-secondary)' }}
      >
        <Icon className="h-3.5 w-3.5" style={{ color: 'var(--text-tertiary)' }} />
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-[11px] font-medium uppercase tracking-wide mb-1.5" style={{ color: 'var(--text-tertiary)' }}>
          {label}
        </p>
        <div className={`flex gap-2 ${fields.length > 1 ? 'flex-col sm:flex-row' : ''}`}>
          {fields.map((f) => (
            <input
              key={f.name}
              type={f.type ?? 'text'}
              value={values[f.name]}
              onChange={handleChange(f.name)}
              placeholder={f.placeholder}
              disabled={saving}
              className="flex-1 min-w-0 rounded-md border px-2.5 py-1.5 text-sm font-medium outline-none disabled:opacity-60"
              style={{
                backgroundColor: 'var(--bg-surface-secondary)',
                borderColor: 'var(--border-color)',
                color: 'var(--text-primary)',
              }}
              autoFocus={f === fields[0]}
            />
          ))}
        </div>
        {error && (
          <p className="mt-1.5 text-xs font-medium" style={{ color: '#ef4444' }}>{error}</p>
        )}
        <div className="mt-2 flex items-center gap-2">
          <button
            type="button"
            onClick={() => onSave(values)}
            disabled={saving}
            className="inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-60"
            style={{ backgroundColor: '#3b82f6', color: 'white' }}
          >
            <Check className="h-3.5 w-3.5" />
            {saving ? t('profile.saving') : t('profile.save')}
          </button>
          <button
            type="button"
            onClick={onCancel}
            disabled={saving}
            className="inline-flex items-center gap-1.5 rounded-md border px-3 py-1.5 text-xs font-semibold transition-colors disabled:opacity-60"
            style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
          >
            <X className="h-3.5 w-3.5" />
            {t('profile.cancel')}
          </button>
        </div>
      </div>
    </div>
  );
}

function splitFullName(fullName) {
  if (!fullName) return { firstName: '', lastName: '' };
  const trimmed = fullName.trim();
  const idx = trimmed.indexOf(' ');
  if (idx === -1) return { firstName: trimmed, lastName: '' };
  return { firstName: trimmed.slice(0, idx), lastName: trimmed.slice(idx + 1).trim() };
}

/* ── Quick-action card ──────────────────────────────────────── */
function ActionCard({ icon: Icon, iconColor, iconBg, title, description, onClick, badge }) {
  const [hovered, setHovered] = useState(false);
  return (
    <button
      onClick={onClick}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      className="w-full text-left rounded-xl border p-4 transition-all duration-200 cursor-pointer flex items-center gap-4"
      style={{
        backgroundColor: hovered ? 'var(--bg-surface-hover)' : 'var(--bg-surface)',
        borderColor: hovered ? 'rgba(59,130,246,0.4)' : 'var(--border-color)',
        boxShadow: hovered ? '0 0 0 3px rgba(59,130,246,0.08)' : 'var(--shadow-sm)',
      }}
    >
      <div
        className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-xl"
        style={{ backgroundColor: iconBg }}
      >
        <Icon className="h-5 w-5" style={{ color: iconColor }} />
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>{title}</p>
        <p className="text-xs mt-0.5 truncate" style={{ color: 'var(--text-secondary)' }}>{description}</p>
      </div>
      {badge && (
        <span
          className="flex-shrink-0 rounded-full px-2 py-0.5 text-[10px] font-bold"
          style={{ backgroundColor: 'rgba(59,130,246,0.12)', color: '#3b82f6' }}
        >
          {badge}
        </span>
      )}
      <ChevronRight
        className="h-4 w-4 flex-shrink-0 transition-transform duration-200"
        style={{
          color: 'var(--text-tertiary)',
          transform: hovered ? 'translateX(3px)' : 'translateX(0)',
        }}
      />
    </button>
  );
}

/* ── Main page ──────────────────────────────────────────────── */
export default function ProfilePage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { user, getPrimaryRole, refreshUser } = useAuth();
  const primaryRole = getPrimaryRole();
  const roleMeta = ROLE_META[primaryRole] ?? { label: primaryRole ?? 'User', color: '#3b82f6', bg: 'rgba(59,130,246,0.12)' };
  const avatarGradient = ROLE_GRADIENT[primaryRole] ?? ROLE_GRADIENT.default;

  const [products, setProducts] = useState([]);
  const [loadingProducts, setLoadingProducts] = useState(true);
  const currentLang = i18n.language?.startsWith('tr') ? 'tr' : 'en';

  // 'name' | 'email' | null — which field is currently being edited
  const [editing, setEditing] = useState(null);
  const [saving, setSaving]   = useState(false);
  const [error, setError]     = useState('');

  // 2FA badge state — null while unknown so badge doesn't flicker on first paint.
  const [twoFactorEnabled, setTwoFactorEnabled] = useState(null);

  const refreshTwoFactor = useCallback(async () => {
    try {
      const devices = await userService.listTotpDevices();
      setTwoFactorEnabled(Array.isArray(devices) && devices.length > 0);
    } catch {
      // Failure is non-critical — just hide the badge.
      setTwoFactorEnabled(null);
    }
  }, []);

  useEffect(() => {
    if (!user?.id) return;
    api.get(`/users/${user.id}`)
      .then((res) => setProducts(res.data.authorizedProducts ?? []))
      .catch(() => {})
      .finally(() => setLoadingProducts(false));
    refreshTwoFactor();
  }, [user?.id, refreshTwoFactor]);

  const openEdit = (field) => {
    setError('');
    setEditing(field);
  };

  const cancelEdit = () => {
    setError('');
    setEditing(null);
  };

  const persist = async ({ firstName, lastName, email }) => {
    setSaving(true);
    setError('');
    try {
      await userService.updateProfile({ firstName, lastName, email });
      await refreshUser();
      setEditing(null);
    } catch (err) {
      const status = err?.response?.status;
      if (status === 409) {
        setError(t('profile.emailConflict'));
      } else if (status === 400) {
        const fe = err?.response?.data?.fieldErrors ?? {};
        setError(fe.email || fe.firstName || fe.lastName || t('profile.saveError'));
      } else {
        setError(t('profile.saveError'));
      }
    } finally {
      setSaving(false);
    }
  };

  const handleSaveName = (vals) => {
    const firstName = (vals.firstName || '').trim();
    const lastName  = (vals.lastName  || '').trim();
    if (!firstName) { setError(t('profile.firstNameRequired')); return; }
    if (!lastName)  { setError(t('profile.lastNameRequired'));  return; }
    persist({ firstName, lastName, email: user?.email });
  };

  const handleSaveEmail = (vals) => {
    const email = (vals.email || '').trim();
    if (!email || !/^\S+@\S+\.\S+$/.test(email)) {
      setError(t('profile.emailInvalid'));
      return;
    }
    const { firstName, lastName } = splitFullName(user?.name);
    persist({ firstName, lastName, email });
  };

  const [passwordModalOpen, setPasswordModalOpen] = useState(false);
  const triggerPasswordChange = () => setPasswordModalOpen(true);

  const [twoFactorModalOpen, setTwoFactorModalOpen] = useState(false);

  const { firstName: currentFirstName, lastName: currentLastName } = splitFullName(user?.name);

  return (
    <div className="animate-fade-in">
      {/* ── Hero banner ─────────────────────────────────────── */}
      <div
        className="relative rounded-2xl overflow-hidden mb-6"
        style={{
          background: 'linear-gradient(135deg, #1e3a5f 0%, #1e1b4b 50%, #0f172a 100%)',
          boxShadow: 'var(--shadow-lg)',
        }}
      >
        {/* Decorative blobs */}
        <div
          className="absolute -top-12 -right-12 h-48 w-48 rounded-full opacity-20 blur-3xl pointer-events-none"
          style={{ background: avatarGradient }}
        />
        <div
          className="absolute -bottom-8 -left-8 h-32 w-32 rounded-full opacity-10 blur-2xl pointer-events-none"
          style={{ backgroundColor: '#8b5cf6' }}
        />

        <div className="relative flex flex-col sm:flex-row items-start sm:items-center gap-5 px-6 py-7">
          {/* Avatar */}
          <div
            className="flex h-20 w-20 flex-shrink-0 items-center justify-center rounded-2xl text-2xl font-bold text-white shadow-lg"
            style={{ background: avatarGradient }}
          >
            {getInitials(user?.name)}
          </div>

          {/* Name / email / role */}
          <div className="flex-1 min-w-0">
            <h1 className="text-xl font-bold text-white truncate">
              {user?.name || user?.username || 'User'}
            </h1>
            <p className="text-sm mt-0.5 truncate" style={{ color: 'rgba(255,255,255,0.6)' }}>
              {user?.email || '—'}
            </p>
            <div className="flex flex-wrap items-center gap-2 mt-2.5">
              <span
                className="inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-bold"
                style={{ backgroundColor: roleMeta.bg, color: roleMeta.color, backdropFilter: 'blur(4px)' }}
              >
                <Shield className="h-3 w-3" />
                {roleMeta.label}
              </span>
              <span
                className="inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold"
                style={{ backgroundColor: 'rgba(255,255,255,0.1)', color: 'rgba(255,255,255,0.75)' }}
              >
                <Globe className="h-3 w-3" />
                {currentLang === 'tr' ? 'Türkçe' : 'English'}
              </span>
              {twoFactorEnabled !== null && (
                <span
                  className="inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-bold"
                  style={
                    twoFactorEnabled
                      ? { backgroundColor: 'rgba(34,197,94,0.18)', color: '#22c55e',  backdropFilter: 'blur(4px)' }
                      : { backgroundColor: 'rgba(245,158,11,0.18)', color: '#f59e0b', backdropFilter: 'blur(4px)' }
                  }
                  title={twoFactorEnabled ? t('profile.twoFactorActive') : t('profile.twoFactorInactive')}
                >
                  {twoFactorEnabled
                    ? <ShieldCheck className="h-3 w-3" />
                    : <ShieldAlert className="h-3 w-3" />}
                  {twoFactorEnabled ? t('profile.twoFactorActive') : t('profile.twoFactorInactive')}
                </span>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* ── Two-column grid ─────────────────────────────────── */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">

        {/* Left column — account details */}
        <div className="lg:col-span-1 space-y-5">
          <div
            className="rounded-xl border overflow-hidden"
            style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}
          >
            {/* Card header */}
            <div
              className="flex items-center gap-2 px-5 py-3.5 border-b"
              style={{ borderColor: 'var(--border-color)' }}
            >
              <User className="h-4 w-4" style={{ color: 'var(--text-tertiary)' }} />
              <span className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                {t('profile.accountDetails')}
              </span>
            </div>

            {/* Rows */}
            <div className="px-5 [&>*:last-child]:border-b-0">
              {editing === 'name' ? (
                <EditableRow
                  icon={User}
                  label={t('profile.fieldFullName')}
                  fields={[
                    { name: 'firstName', value: currentFirstName, placeholder: t('profile.fieldFirstName') },
                    { name: 'lastName',  value: currentLastName,  placeholder: t('profile.fieldLastName')  },
                  ]}
                  onSave={handleSaveName}
                  onCancel={cancelEdit}
                  saving={saving}
                  error={editing === 'name' ? error : ''}
                  t={t}
                />
              ) : (
                <InfoRow icon={User} label={t('profile.fieldFullName')} value={user?.name} onEdit={() => openEdit('name')} />
              )}

              <InfoRow icon={IdCard} label={t('profile.fieldUsername')} value={user?.username} />

              {editing === 'email' ? (
                <EditableRow
                  icon={Mail}
                  label={t('profile.fieldEmail')}
                  fields={[{ name: 'email', value: user?.email, placeholder: t('profile.fieldEmail'), type: 'email' }]}
                  onSave={handleSaveEmail}
                  onCancel={cancelEdit}
                  saving={saving}
                  error={editing === 'email' ? error : ''}
                  t={t}
                />
              ) : (
                <InfoRow icon={Mail} label={t('profile.fieldEmail')} value={user?.email} onEdit={() => openEdit('email')} />
              )}

              <InfoRow icon={Key} label={t('profile.fieldUserId')} value={user?.id} mono />
            </div>
          </div>
        </div>

        {/* Right column — actions + products */}
        <div className="lg:col-span-2 space-y-5">

          {/* Quick actions */}
          <div
            className="rounded-xl border overflow-hidden"
            style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}
          >
            <div
              className="flex items-center gap-2 px-5 py-3.5 border-b"
              style={{ borderColor: 'var(--border-color)' }}
            >
              <Settings className="h-4 w-4" style={{ color: 'var(--text-tertiary)' }} />
              <span className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                {t('profile.preferences')}
              </span>
            </div>
            <div className="p-4 space-y-3">
              <ActionCard
                icon={Bell}
                iconColor="#3b82f6"
                iconBg="rgba(59,130,246,0.12)"
                title={t('profile.notificationPreferences')}
                description={t('profile.manageNotifications')}
                onClick={() => navigate('/notification-preferences')}
              />
              <ActionCard
                icon={Lock}
                iconColor="#f59e0b"
                iconBg="rgba(245,158,11,0.12)"
                title={t('profile.changePassword')}
                description={t('profile.changePasswordDesc')}
                onClick={triggerPasswordChange}
              />
              <ActionCard
                icon={ShieldCheck}
                iconColor="#10b981"
                iconBg="rgba(16,185,129,0.12)"
                title={t('profile.twoFactor')}
                description={t('profile.manageTwoFactor')}
                onClick={() => setTwoFactorModalOpen(true)}
              />
            </div>
          </div>

          {/* Authorized products */}
          <div
            className="rounded-xl border overflow-hidden"
            style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}
          >
            <div
              className="flex items-center justify-between px-5 py-3.5 border-b"
              style={{ borderColor: 'var(--border-color)' }}
            >
              <div className="flex items-center gap-2">
                <Package className="h-4 w-4" style={{ color: 'var(--text-tertiary)' }} />
                <span className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                  {t('profile.authorizedProducts')}
                </span>
              </div>
              {!loadingProducts && products.length > 0 && (
                <span
                  className="rounded-full px-2 py-0.5 text-[11px] font-bold"
                  style={{ backgroundColor: 'rgba(59,130,246,0.1)', color: '#3b82f6' }}
                >
                  {products.length}
                </span>
              )}
            </div>

            <div className="p-4">
              {loadingProducts ? (
                <div className="flex items-center justify-center py-8">
                  <div
                    className="h-6 w-6 rounded-full border-[3px] animate-spin"
                    style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }}
                  />
                </div>
              ) : products.length > 0 ? (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                  {products.map((product) => (
                    <button
                      key={product.id}
                      onClick={() => navigate(`/products/${product.id}`)}
                      className="group flex items-center gap-3 rounded-xl border p-3.5 text-left transition-all duration-200 cursor-pointer"
                      style={{
                        backgroundColor: 'var(--bg-surface-secondary)',
                        borderColor: 'var(--border-color)',
                      }}
                      onMouseEnter={(e) => {
                        e.currentTarget.style.borderColor = 'rgba(59,130,246,0.4)';
                        e.currentTarget.style.backgroundColor = 'var(--bg-surface-hover)';
                      }}
                      onMouseLeave={(e) => {
                        e.currentTarget.style.borderColor = 'var(--border-color)';
                        e.currentTarget.style.backgroundColor = 'var(--bg-surface-secondary)';
                      }}
                    >
                      <div
                        className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg"
                        style={{ background: 'linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%)' }}
                      >
                        <Package className="h-4 w-4 text-white" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-semibold truncate" style={{ color: 'var(--text-primary)' }}>
                          {product.name}
                        </p>
                        <p className="text-xs mt-0.5" style={{ color: 'var(--text-tertiary)' }}>
                          #{product.id}
                        </p>
                      </div>
                      <ExternalLink
                        className="h-3.5 w-3.5 flex-shrink-0 opacity-0 group-hover:opacity-100 transition-opacity"
                        style={{ color: '#3b82f6' }}
                      />
                    </button>
                  ))}
                </div>
              ) : (
                <div className="flex flex-col items-center justify-center py-10 gap-3">
                  <div
                    className="flex h-12 w-12 items-center justify-center rounded-2xl"
                    style={{ backgroundColor: 'var(--bg-surface-secondary)' }}
                  >
                    <Package className="h-6 w-6" style={{ color: 'var(--text-tertiary)' }} />
                  </div>
                  <p className="text-sm" style={{ color: 'var(--text-tertiary)' }}>
                    {t('profile.noProducts')}
                  </p>
                </div>
              )}
            </div>
          </div>

        </div>
      </div>

      <ChangePasswordModal
        open={passwordModalOpen}
        onClose={() => setPasswordModalOpen(false)}
      />
      <TwoFactorModal
        open={twoFactorModalOpen}
        onClose={() => { setTwoFactorModalOpen(false); refreshTwoFactor(); }}
        lang={currentLang}
      />
    </div>
  );
}
