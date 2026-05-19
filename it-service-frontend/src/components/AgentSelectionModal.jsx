import { useState, useEffect } from 'react';
import { X, UserCheck, AlertTriangle, Loader2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { getAgentsWithCapacity, assignTicket } from '../services/api';
import { useEscapeToClose } from '../hooks/useEscapeToClose';

/**
 * AgentSelectionModal — Agent Admin'in bir bileti belirli bir agent'a ataması için
 * yeniden kullanılabilir modal bileşeni.
 *
 * Props:
 *   isOpen     {boolean}  — Modal açık mı?
 *   onClose    {function} — Modal kapatma callback'i
 *   onSuccess  {function} — Başarılı atama sonrası çağrılır: (updatedTicket) => {}
 *   productId  {number}   — Hangi product için agent listesi çekilecek
 *   ticketId   {number}   — Atanacak bilet ID'si
 */
export default function AgentSelectionModal({ isOpen, onClose, onSuccess, productId, ticketId }) {
  const { t } = useTranslation();
  const [agents, setAgents] = useState([]);
  const [loadingAgents, setLoadingAgents] = useState(false);
  const [fetchError, setFetchError] = useState('');

  const [selectedAgentId, setSelectedAgentId] = useState('');
  const [note, setNote] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');

  // Modal açıldığında agent listesini çek ve state'i sıfırla
  useEffect(() => {
    if (!isOpen) return;

    setSelectedAgentId('');
    setNote('');
    setSubmitError('');
    setFetchError('');

    if (!productId) return;

    setLoadingAgents(true);
    getAgentsWithCapacity(productId)
      .then((res) => setAgents(res.data))
      .catch(() => setFetchError(t('ticket.agentModal.fetchError')))
      .finally(() => setLoadingAgents(false));
  }, [isOpen, productId]); // eslint-disable-line

  // ESC tuşu ile kapatma
  useEscapeToClose(isOpen, onClose);

  const handleAssign = async (e) => {
    e.preventDefault();
    if (!selectedAgentId) return;

    setSubmitting(true);
    setSubmitError('');

    try {
      const res = await assignTicket(ticketId, selectedAgentId, note.trim() || undefined);
      onSuccess(res.data);
      onClose();
    } catch (err) {
      setSubmitError(err.response?.data?.message || t('ticket.agentModal.submitError'));
    } finally {
      setSubmitting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in"
      style={{ backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)' }}
      onClick={onClose}
    >
      <div
        className="w-full max-w-md sm:max-w-lg rounded-xl border animate-slide-up flex flex-col max-h-[90vh]"
        style={{
          backgroundColor: 'var(--bg-surface)',
          borderColor: 'var(--border-color)',
          boxShadow: 'var(--shadow-xl)',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div
          className="flex items-start justify-between gap-3 sm:gap-4 px-4 sm:px-6 py-4 border-b flex-shrink-0"
          style={{ borderColor: 'var(--border-color)' }}
        >
          <div className="min-w-0">
            <h3 className="text-lg font-bold break-words" style={{ color: 'var(--text-primary)' }}>
              {t('ticket.agentModal.title')}
            </h3>
            <p className="mt-0.5 text-sm break-words" style={{ color: 'var(--text-secondary)' }}>
              TCK-{String(ticketId).padStart(3, '0')} — {t('ticket.agentModal.subtitle')}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg transition-colors cursor-pointer hover:bg-danger-50 hover:text-danger-500"
            style={{ color: 'var(--text-tertiary)' }}
            aria-label={t('ticket.agentModal.closeModal')}
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <form onSubmit={handleAssign} className="flex flex-col min-h-0 flex-1">
          <div className="px-4 sm:px-6 py-5 space-y-4 overflow-y-auto flex-1">

            {/* Submit error */}
            {submitError && (
              <div className="flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium bg-danger-50 text-danger-600 dark:bg-danger-500/10 dark:text-danger-400">
                <AlertTriangle className="h-4 w-4 shrink-0" />
                {submitError}
              </div>
            )}

            {/* Agent listesi */}
            <div>
              <label className="block text-sm font-semibold mb-2" style={{ color: 'var(--text-primary)' }}>
                {t('ticket.agentModal.selectAgent')} *
              </label>

              {loadingAgents ? (
                <div className="flex items-center justify-center gap-2 py-8" style={{ color: 'var(--text-tertiary)' }}>
                  <Loader2 className="h-5 w-5 animate-spin" />
                  <span className="text-sm">{t('ticket.agentModal.loadingAgents')}</span>
                </div>
              ) : fetchError ? (
                <div className="rounded-lg px-3 py-2 text-sm bg-danger-50 text-danger-600 dark:bg-danger-500/10 dark:text-danger-400">
                  {fetchError}
                </div>
              ) : agents.length === 0 ? (
                <div className="rounded-lg px-3 py-3 text-sm text-center" style={{ color: 'var(--text-tertiary)', backgroundColor: 'var(--bg-muted)' }}>
                  {t('ticket.agentModal.noAgents')}
                </div>
              ) : (
                <div
                  className="rounded-lg border divide-y overflow-hidden"
                  style={{ borderColor: 'var(--border-color)', divideColor: 'var(--border-color)' }}
                >
                  {agents.map((agent) => {
                    const isSelected = selectedAgentId === agent.agentId;
                    const isFull = agent.isFull;
                    const hasLimit = agent.maxLimit != null;

                    // Kapasite yüzdesi (limit varsa)
                    const usageRatio = hasLimit ? agent.currentActiveTickets / agent.maxLimit : null;
                    const isNearLimit = usageRatio !== null && usageRatio >= 0.8 && !isFull;

                    // Kapasite etiketi
                    const capacityLabel = hasLimit
                      ? `${agent.currentActiveTickets}/${agent.maxLimit}`
                      : `${agent.currentActiveTickets}`;

                    return (
                      <button
                        key={agent.agentId}
                        type="button"
                        disabled={isFull}
                        onClick={() => !isFull && setSelectedAgentId(agent.agentId)}
                        className="w-full flex items-center justify-between px-4 py-3 text-left transition-colors"
                        style={{
                          backgroundColor: isSelected
                            ? 'var(--bg-selected, rgba(99,102,241,0.08))'
                            : isFull
                            ? 'var(--bg-muted)'
                            : 'transparent',
                          cursor: isFull ? 'not-allowed' : 'pointer',
                          opacity: isFull ? 0.6 : 1,
                        }}
                      >
                        {/* Agent adı + seçim ikonu */}
                        <div className="flex items-center gap-2 min-w-0 flex-1">
                          {isSelected && (
                            <UserCheck className="h-4 w-4 shrink-0 text-primary-500" />
                          )}
                          <span
                            className="text-sm font-medium break-words min-w-0"
                            style={{ color: isFull ? 'var(--text-tertiary)' : 'var(--text-primary)' }}
                          >
                            {agent.agentName}
                          </span>
                        </div>

                        {/* Kapasite badge */}
                        <div className="flex items-center gap-2 shrink-0 ml-2 sm:ml-3">
                          <span
                            className="text-xs font-semibold px-2 py-0.5 rounded-full"
                            style={{
                              backgroundColor: isFull
                                ? 'var(--bg-danger-subtle, rgba(239,68,68,0.1))'
                                : isNearLimit
                                ? 'rgba(245,158,11,0.12)'
                                : 'rgba(34,197,94,0.12)',
                              color: isFull
                                ? 'var(--color-danger, #ef4444)'
                                : isNearLimit
                                ? '#d97706'
                                : '#16a34a',
                            }}
                          >
                            {capacityLabel}
                          </span>
                          {isFull && (
                            <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-danger-50 text-danger-500 dark:bg-danger-500/10">
                              {t('ticket.agentModal.full')}
                            </span>
                          )}
                        </div>
                      </button>
                    );
                  })}
                </div>
              )}
            </div>

            {/* Opsiyonel not */}
            {selectedAgentId && (
              <div>
                <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>
                  {t('ticket.agentModal.noteLabel')}{' '}
                  <span style={{ color: 'var(--text-tertiary)', fontWeight: 400 }}>({t('ticket.agentModal.noteOptional')})</span>
                </label>
                <textarea
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  rows={3}
                  placeholder={t('ticket.agentModal.placeholderNote')}
                  className="w-full rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2 resize-y min-h-[72px]"
                  style={{
                    backgroundColor: 'var(--bg-input)',
                    borderColor: 'var(--border-color)',
                    color: 'var(--text-primary)',
                    '--tw-ring-color': 'var(--ring-color)',
                  }}
                />
              </div>
            )}
          </div>

          {/* Footer */}
          <div
            className="flex flex-col-reverse sm:flex-row sm:justify-end gap-2 sm:gap-3 px-4 sm:px-6 py-4 border-t flex-shrink-0"
            style={{ borderColor: 'var(--border-color)' }}
          >
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg border px-4 py-2 text-sm font-semibold transition-colors cursor-pointer"
              style={{
                borderColor: 'var(--border-color)',
                color: 'var(--text-secondary)',
                backgroundColor: 'transparent',
              }}
            >
              {t('form.cancel')}
            </button>
            <button
              type="submit"
              disabled={!selectedAgentId || submitting}
              className="rounded-lg px-4 py-2 text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors focus:outline-none focus:ring-4 focus:ring-primary-500/30 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
            >
              {submitting ? (
                <span className="flex items-center gap-2">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  {t('ticket.agentModal.assigning')}
                </span>
              ) : (
                t('ticket.agentModal.assign')
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
