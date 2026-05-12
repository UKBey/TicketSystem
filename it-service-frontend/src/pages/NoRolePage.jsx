import { useState, useEffect } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { ShieldAlert, Send, CheckCircle, Clock, Trash2, Loader2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import LanguageSwitcher from '../components/LanguageSwitcher';
import { createAccessRequest, getMyAccessRequests, deleteAccessRequest } from '../services/api';

export default function NoRolePage() {
  const { user, logout, getPrimaryRole } = useAuth();
  const { t } = useTranslation();

  const [message, setMessage]       = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const [submitted, setSubmitted]   = useState(false);

  const [requests, setRequests]         = useState([]);
  const [requestsLoading, setRequestsLoading] = useState(true);
  const [deletingId, setDeletingId]     = useState(null);

  if (getPrimaryRole() !== null) {
    return <Navigate to="/" replace />;
  }

  const fetchRequests = async () => {
    try {
      const res = await getMyAccessRequests();
      setRequests(res.data);
    } catch {
      // sessizce geç
    } finally {
      setRequestsLoading(false);
    }
  };

  // eslint-disable-next-line react-hooks/rules-of-hooks
  useEffect(() => { fetchRequests(); }, []); // eslint-disable-line

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!message.trim()) return;
    setSubmitting(true);
    setSubmitError('');
    try {
      await createAccessRequest(message.trim());
      setMessage('');
      setSubmitted(true);
      setTimeout(() => setSubmitted(false), 4000);
      fetchRequests();
    } catch {
      setSubmitError(t('noRole.request.errorSubmit'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (id) => {
    setDeletingId(id);
    try {
      await deleteAccessRequest(id);
      setRequests((prev) => prev.filter((r) => r.id !== id));
    } catch {
      // sessizce geç
    } finally {
      setDeletingId(null);
    }
  };

  const formatDate = (iso) => {
    if (!iso) return '';
    return new Date(iso).toLocaleString(undefined, {
      year: 'numeric', month: 'short', day: 'numeric',
      hour: '2-digit', minute: '2-digit',
    });
  };

  return (
    <div
      className="flex min-h-screen flex-col items-center justify-center p-6"
      style={{ backgroundColor: 'var(--bg-body)' }}
    >
      <div className="fixed top-4 right-4 z-50">
        <LanguageSwitcher />
      </div>

      <div className="w-full max-w-lg space-y-4">
        {/* Ana kart */}
        <div
          className="rounded-2xl border p-8 text-center"
          style={{
            backgroundColor: 'var(--bg-surface)',
            borderColor: 'var(--border-color)',
            boxShadow: 'var(--shadow-xl)',
          }}
        >
          <div
            className="mx-auto mb-5 flex h-16 w-16 items-center justify-center rounded-2xl"
            style={{ backgroundColor: '#fef3c7' }}
          >
            <ShieldAlert className="h-8 w-8" style={{ color: '#d97706' }} />
          </div>

          <h1 className="text-xl font-bold mb-2" style={{ color: 'var(--text-primary)' }}>
            {t('noRole.title')}
          </h1>
          <p className="text-sm mb-6 leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
            {t('noRole.description')}
          </p>

          {user && (
            <div
              className="rounded-lg px-4 py-3 mb-6 text-left text-sm"
              style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)' }}
            >
              <div className="font-medium mb-0.5" style={{ color: 'var(--text-primary)' }}>
                {user.name || user.username}
              </div>
              <div style={{ color: 'var(--text-tertiary)' }}>{user.email}</div>
            </div>
          )}

          <button
            onClick={logout}
            className="w-full rounded-lg px-4 py-2.5 text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors cursor-pointer"
          >
            {t('noRole.signOut')}
          </button>
        </div>

        {/* Talep formu */}
        <div
          className="rounded-2xl border p-6"
          style={{
            backgroundColor: 'var(--bg-surface)',
            borderColor: 'var(--border-color)',
            boxShadow: 'var(--shadow-sm)',
          }}
        >
          <h2 className="text-base font-bold mb-1" style={{ color: 'var(--text-primary)' }}>
            {t('noRole.request.title')}
          </h2>
          <p className="text-xs mb-4" style={{ color: 'var(--text-secondary)' }}>
            {t('noRole.request.subtitle')}
          </p>

          {submitted && (
            <div className="flex items-center gap-2 rounded-lg px-3 py-2.5 mb-4 text-sm font-medium bg-green-50 text-green-700 dark:bg-green-500/10 dark:text-green-400">
              <CheckCircle className="h-4 w-4 shrink-0" />
              {t('noRole.request.successMsg')}
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate>
            <textarea
              value={message}
              onChange={(e) => { setMessage(e.target.value); setSubmitError(''); }}
              placeholder={t('noRole.request.placeholder')}
              rows={4}
              className="w-full rounded-lg border px-3 py-2.5 text-sm outline-none focus:ring-2 resize-none"
              style={{
                backgroundColor: 'var(--bg-input)',
                borderColor: submitError ? '#f87171' : 'var(--border-color)',
                color: 'var(--text-primary)',
                '--tw-ring-color': 'var(--ring-color)',
              }}
            />
            {submitError && (
              <p className="mt-1 text-xs text-red-400">{submitError}</p>
            )}
            <p className="mt-1 text-xs mb-3" style={{ color: 'var(--text-tertiary)' }}>
              {message.length}/500
            </p>
            <button
              type="submit"
              disabled={submitting || !message.trim() || message.length > 500}
              className="inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {submitting ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Send className="h-4 w-4" />
              )}
              {submitting ? t('noRole.request.submitting') : t('noRole.request.submit')}
            </button>
          </form>
        </div>

        {/* Önceki talepler */}
        {(requestsLoading || requests.length > 0) && (
          <div
            className="rounded-2xl border p-6"
            style={{
              backgroundColor: 'var(--bg-surface)',
              borderColor: 'var(--border-color)',
              boxShadow: 'var(--shadow-sm)',
            }}
          >
            <h2 className="text-base font-bold mb-4" style={{ color: 'var(--text-primary)' }}>
              {t('noRole.request.myRequests')}
            </h2>

            {requestsLoading ? (
              <div className="flex items-center gap-2 py-2" style={{ color: 'var(--text-tertiary)' }}>
                <Loader2 className="h-4 w-4 animate-spin" />
                <span className="text-sm">{t('common.loading')}</span>
              </div>
            ) : (
              <div className="space-y-3">
                {requests.map((req) => (
                  <div
                    key={req.id}
                    className="rounded-lg border p-3"
                    style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)' }}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex items-center gap-1.5 text-xs mb-2" style={{ color: 'var(--text-tertiary)' }}>
                        <Clock className="h-3 w-3" />
                        {formatDate(req.createdAt)}
                      </div>
                      <button
                        onClick={() => handleDelete(req.id)}
                        disabled={deletingId === req.id}
                        className="rounded p-1 transition-colors cursor-pointer hover:text-red-500 disabled:opacity-40"
                        style={{ color: 'var(--text-tertiary)' }}
                        title={t('noRole.request.deleteRequest')}
                      >
                        {deletingId === req.id
                          ? <Loader2 className="h-3.5 w-3.5 animate-spin" />
                          : <Trash2 className="h-3.5 w-3.5" />}
                      </button>
                    </div>
                    <p className="text-sm whitespace-pre-wrap" style={{ color: 'var(--text-primary)' }}>
                      {req.message}
                    </p>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
